# ADR 004: Concurrency and Transaction Pool Routing Architecture

## Status
Accepted

## Context
Under concurrent traffic (e.g. flash sales, dinner order rush hours, or simultaneous seller status updates), applications risk concurrency hazards:
1. **Inventory Overselling:** Multiple buyers checking out the last unit of an offering simultaneously.
2. **Order State Inconsistency:** Simultaneous buyer payment confirmation and seller cancellation or delivery tracking.
3. **Database Connection Pool Deadlocks:** Nested transactions (e.g., `PROPAGATION_REQUIRES_NEW` within an ongoing transaction) competing for connections from a shared pool, causing mutual starvation under load.

## Decision & Enforced Controls

### 1. Atomic SQL Inventory Controls
Instead of in-memory read-modify-write patterns, `ProductRepository` uses atomic conditional SQL updates:
```sql
UPDATE products 
SET remaining_quantity = remaining_quantity - :qty,
    booked_quantity = COALESCE(booked_quantity, 0) + :qty,
    available_today = CASE WHEN remaining_quantity - :qty <= 0 THEN false ELSE available_today END,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :productId 
  AND remaining_quantity IS NOT NULL 
  AND remaining_quantity >= :qty 
  AND archived = false 
  AND COALESCE(orders_paused, false) = false;
```
If two requests attempt to book the last unit concurrently, only one update matches `remaining_quantity >= :qty`. The second returns row count 0 and is immediately rejected with an out-of-stock exception.

### 2. Pessimistic Write Locking for Sensitive State Transitions
Sensitive order lifecycle transitions in `OrderService` utilize `findByIdForUpdate(orderId)`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM Order o WHERE o.id = :id")
Optional<Order> findByIdForUpdate(@Param("id") Long id);
```
This issues a PostgreSQL `SELECT ... FOR UPDATE`, serializing concurrent mutations on that specific order row and preventing state race conditions.

### 3. Transaction Pool Routing
Implemented in `TransactionAdmissionConfiguration` and `RoutingTransactionManager`:
- The Hikari pool is split into two isolated sub-pools:
  - `RootTransactionPool` (for root transactions)
  - `NestedTransactionPool` (for nested / auxiliary transactions)
- Nested transactions can never starve the root pool of connections, completely eliminating connection deadlock under high concurrency.

## Consequences
- Guaranteed transaction safety and zero overselling.
- Complete deadlock immunity for nested database transactions under load.
