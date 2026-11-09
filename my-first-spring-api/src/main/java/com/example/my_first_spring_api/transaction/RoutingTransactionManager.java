package com.example.my_first_spring_api.transaction;

import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public final class RoutingTransactionManager implements PlatformTransactionManager {

    private final PlatformTransactionManager delegate;
    private final TransactionPoolRoutingDataSource routingDataSource;
    private final int maximumNestedDepth;
    private final ThreadLocal<Integer> nestedDepth = new ThreadLocal<>();
    private final Map<TransactionStatus, Scope> scopes =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public RoutingTransactionManager(PlatformTransactionManager delegate,
                                     TransactionPoolRoutingDataSource routingDataSource,
                                     int maximumNestedDepth) {
        if (maximumNestedDepth < 1) {
            throw new IllegalArgumentException("Maximum nested transaction depth must be positive.");
        }
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.routingDataSource = Objects.requireNonNull(routingDataSource, "routingDataSource");
        this.maximumNestedDepth = maximumNestedDepth;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        int propagation = definition == null
                ? TransactionDefinition.PROPAGATION_REQUIRED
                : definition.getPropagationBehavior();
        int previousDepth = currentNestedDepth();
        boolean nestedScope = transactionActive
                && (propagation == TransactionDefinition.PROPAGATION_REQUIRES_NEW
                || propagation == TransactionDefinition.PROPAGATION_NOT_SUPPORTED)
                || !transactionActive
                && previousDepth > 0
                && (startsTransaction(propagation)
                || propagation == TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        if (nestedScope && currentNestedDepth() >= maximumNestedDepth) {
            throw new CannotCreateTransactionException(
                    "Maximum nested transaction scope depth exceeded.");
        }

        TransactionPoolRoutingDataSource.Pool route = null;
        if (nestedScope) {
            route = TransactionPoolRoutingDataSource.Pool.NESTED;
        } else if (!transactionActive && startsTransaction(propagation)) {
            route = TransactionPoolRoutingDataSource.Pool.ROOT;
        }

        TransactionPoolRoutingDataSource.Pool previousRoute = null;
        boolean routeSelected = route != null;
        if (routeSelected) {
            previousRoute = routingDataSource.selectPool(route);
        }
        if (nestedScope) {
            nestedDepth.set(previousDepth + 1);
        }

        boolean scopeTransferred = false;
        try {
            TransactionStatus status = delegate.getTransaction(definition);
            if (routeSelected) {
                scopes.put(status, new Scope(previousRoute, previousDepth));
                scopeTransferred = true;
            }
            return status;
        } finally {
            if (routeSelected && !scopeTransferred) {
                routingDataSource.restorePool(previousRoute);
                setNestedDepth(previousDepth);
            }
        }
    }

    @Override
    public void commit(TransactionStatus status) throws TransactionException {
        try {
            delegate.commit(status);
        } finally {
            restoreScope(status);
        }
    }

    @Override
    public void rollback(TransactionStatus status) throws TransactionException {
        try {
            delegate.rollback(status);
        } finally {
            restoreScope(status);
        }
    }

    private void restoreScope(TransactionStatus status) {
        Scope scope = scopes.remove(status);
        if (scope != null) {
            routingDataSource.restorePool(scope.previousRoute());
            setNestedDepth(scope.previousNestedDepth());
        }
    }

    private int currentNestedDepth() {
        Integer depth = nestedDepth.get();
        return depth == null ? 0 : depth;
    }

    private void setNestedDepth(int depth) {
        if (depth == 0) {
            nestedDepth.remove();
        } else {
            nestedDepth.set(depth);
        }
    }

    private static boolean startsTransaction(int propagation) {
        return propagation == TransactionDefinition.PROPAGATION_REQUIRED
                || propagation == TransactionDefinition.PROPAGATION_REQUIRES_NEW
                || propagation == TransactionDefinition.PROPAGATION_NESTED;
    }

    private record Scope(TransactionPoolRoutingDataSource.Pool previousRoute, int previousNestedDepth) {
    }
}
