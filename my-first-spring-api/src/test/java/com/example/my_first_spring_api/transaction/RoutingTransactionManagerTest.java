package com.example.my_first_spring_api.transaction;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingTransactionManagerTest {

    @Test
    void nestedTransactionsKeepConnectionsWhenOneWaitsOnAnotherRootsLock() throws Exception {
        String databaseName = "nested_lock_cycle_" + System.nanoTime();
        HikariDataSource rootPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                3,
                "RootPool-" + databaseName);
        HikariDataSource nestedPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                7,
                "NestedPool-" + databaseName);
        TransactionPoolRoutingDataSource dataSource =
                new TransactionPoolRoutingDataSource(rootPool, nestedPool);
        RoutingTransactionManager manager = new RoutingTransactionManager(
                new DataSourceTransactionManager(dataSource), dataSource, 2);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        JdbcTemplate nestedObserver = new JdbcTemplate(nestedPool);
        jdbc.execute("CREATE TABLE tx_cycle (id INT PRIMARY KEY, amount INT NOT NULL)");
        jdbc.update("INSERT INTO tx_cycle (id, amount) VALUES (1, 0), (2, 0), (3, 0)");

        TransactionTemplate outer = new TransactionTemplate(manager);
        TransactionTemplate requiresNew = new TransactionTemplate(manager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        CountDownLatch secondRootLocked = new CountDownLatch(1);
        CountDownLatch firstNestedIsBlocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> firstRoot = null;
        Future<?> secondRoot = null;
        Connection directPoolConsumer = null;
        try {
            firstRoot = executor.submit(() -> outer.executeWithoutResult(outerStatus -> {
                jdbc.update("UPDATE tx_cycle SET amount = 10 WHERE id = 1");
                await(secondRootLocked, "second root lock");
                requiresNew.executeWithoutResult(nestedStatus -> {
                    jdbc.update("UPDATE tx_cycle SET amount = amount + 1 WHERE id = 2");
                });
                outerStatus.setRollbackOnly();
            }));
            secondRoot = executor.submit(() -> outer.executeWithoutResult(outerStatus -> {
                jdbc.update("UPDATE tx_cycle SET amount = 20 WHERE id = 2");
                secondRootLocked.countDown();
                await(firstNestedIsBlocked, "first nested lock wait");
                requiresNew.executeWithoutResult(nestedStatus ->
                        jdbc.update("UPDATE tx_cycle SET amount = amount + 1 WHERE id = 3"));
                outerStatus.setRollbackOnly();
            }));

            assertThat(secondRootLocked.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitNestedLockWait(nestedObserver, Duration.ofSeconds(5))).isTrue();

            directPoolConsumer = dataSource.getConnection();
            assertThat(rootPool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(3);

            firstNestedIsBlocked.countDown();
            firstRoot.get(5, TimeUnit.SECONDS);
            secondRoot.get(5, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("SELECT amount FROM tx_cycle WHERE id = 1", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT amount FROM tx_cycle WHERE id = 2", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT amount FROM tx_cycle WHERE id = 3", Integer.class))
                    .isEqualTo(1);
        } finally {
            firstNestedIsBlocked.countDown();
            if (directPoolConsumer != null) {
                directPoolConsumer.close();
            }
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            dataSource.close();
        }
    }

    @Test
    void nestedDepthTwoCommitsIndependentlyAndDeeperScopesFailFast() {
        String databaseName = "nested_depth_" + System.nanoTime();
        HikariDataSource rootPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                3,
                "RootPool-" + databaseName);
        HikariDataSource nestedPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                7,
                "NestedPool-" + databaseName);
        TransactionPoolRoutingDataSource dataSource =
                new TransactionPoolRoutingDataSource(rootPool, nestedPool);
        RoutingTransactionManager manager = new RoutingTransactionManager(
                new DataSourceTransactionManager(dataSource), dataSource, 2);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE tx_depth (id INT PRIMARY KEY, amount INT NOT NULL)");
        jdbc.update("INSERT INTO tx_depth (id, amount) VALUES (1, 0), (2, 0), (3, 0)");

        TransactionTemplate outer = new TransactionTemplate(manager);
        TransactionTemplate requiresNew = new TransactionTemplate(manager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            outer.executeWithoutResult(outerStatus -> {
                requiresNew.executeWithoutResult(firstNested ->
                        jdbc.update("UPDATE tx_depth SET amount = amount + 1 WHERE id = 1"));
                requiresNew.executeWithoutResult(firstNested ->
                        requiresNew.executeWithoutResult(secondNested ->
                                jdbc.update("UPDATE tx_depth SET amount = amount + 1 WHERE id = 2")));
                assertThatThrownBy(() -> requiresNew.executeWithoutResult(firstNested ->
                        requiresNew.executeWithoutResult(secondNested ->
                                requiresNew.executeWithoutResult(thirdNested ->
                                        jdbc.update("UPDATE tx_depth SET amount = amount + 1 WHERE id = 3")))))
                        .isInstanceOf(CannotCreateTransactionException.class)
                        .hasMessageContaining("Maximum nested transaction scope depth exceeded");
                outerStatus.setRollbackOnly();
            });

            assertThat(jdbc.queryForObject("SELECT amount FROM tx_depth WHERE id = 1", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT amount FROM tx_depth WHERE id = 2", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT amount FROM tx_depth WHERE id = 3", Integer.class))
                    .isZero();
        } finally {
            dataSource.close();
        }
    }

    @Test
    void nestedConnectionTimeoutRestoresDepthAndRouteForLaterWork() throws Exception {
        String databaseName = "nested_timeout_" + System.nanoTime();
        HikariDataSource rootPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                1,
                "RootPool-" + databaseName);
        HikariDataSource nestedPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                2,
                "NestedPool-" + databaseName,
                300);
        TransactionPoolRoutingDataSource dataSource =
                new TransactionPoolRoutingDataSource(rootPool, nestedPool);
        RoutingTransactionManager manager = new RoutingTransactionManager(
                new DataSourceTransactionManager(dataSource), dataSource, 1);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE tx_timeout (id INT PRIMARY KEY, amount INT NOT NULL)");
        jdbc.update("INSERT INTO tx_timeout (id, amount) VALUES (1, 0)");

        TransactionTemplate outer = new TransactionTemplate(manager);
        TransactionTemplate requiresNew = new TransactionTemplate(manager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        CountDownLatch nestedConnectionsHeld = new CountDownLatch(1);
        CountDownLatch releaseNestedConnections = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> directConsumer;
        try {
            directConsumer = executor.submit(() -> {
                try (Connection first = nestedPool.getConnection();
                     Connection second = nestedPool.getConnection()) {
                    nestedConnectionsHeld.countDown();
                    await(releaseNestedConnections, "nested pool release");
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            assertThat(nestedConnectionsHeld.await(5, TimeUnit.SECONDS)).isTrue();

            outer.executeWithoutResult(outerStatus -> {
                assertThatThrownBy(() -> requiresNew.executeWithoutResult(innerStatus ->
                        jdbc.update("UPDATE tx_timeout SET amount = amount + 1 WHERE id = 1")))
                        .isInstanceOf(CannotCreateTransactionException.class);
                releaseNestedConnections.countDown();
                try {
                    directConsumer.get(5, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                requiresNew.executeWithoutResult(innerStatus ->
                        jdbc.update("UPDATE tx_timeout SET amount = amount + 1 WHERE id = 1"));
                outerStatus.setRollbackOnly();
            });

            assertThat(jdbc.queryForObject("SELECT amount FROM tx_timeout WHERE id = 1", Integer.class))
                    .isEqualTo(1);
            try (Connection connection = dataSource.getConnection()) {
                assertThat(rootPool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                assertThat(nestedPool.getHikariPoolMXBean().getActiveConnections()).isZero();
            }
        } finally {
            releaseNestedConnections.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            dataSource.close();
        }
    }

    @Test
    void nestedConnectionInterruptionPreservesInterruptAndRestoresManagerState() throws Exception {
        String databaseName = "nested_interrupt_" + System.nanoTime();
        HikariDataSource rootPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                1,
                "RootPool-" + databaseName);
        HikariDataSource nestedPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                1,
                "NestedPool-" + databaseName,
                10000);
        TransactionPoolRoutingDataSource dataSource =
                new TransactionPoolRoutingDataSource(rootPool, nestedPool);
        RoutingTransactionManager manager = new RoutingTransactionManager(
                new DataSourceTransactionManager(dataSource), dataSource, 1);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE tx_interrupt (id INT PRIMARY KEY, amount INT NOT NULL)");
        jdbc.update("INSERT INTO tx_interrupt (id, amount) VALUES (1, 0)");

        TransactionTemplate outer = new TransactionTemplate(manager);
        TransactionTemplate requiresNew = new TransactionTemplate(manager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        CountDownLatch nestedAttemptStarted = new CountDownLatch(1);
        CountDownLatch retryAllowed = new CountDownLatch(1);
        CountDownLatch interruptionObserved = new CountDownLatch(1);
        AtomicBoolean interruptRestored = new AtomicBoolean();
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Connection nestedConnection = nestedPool.getConnection();
        try {
            Future<?> work = executor.submit(() -> {
                workerThread.set(Thread.currentThread());
                outer.executeWithoutResult(outerStatus -> {
                    nestedAttemptStarted.countDown();
                    try {
                        requiresNew.executeWithoutResult(innerStatus ->
                                jdbc.update("UPDATE tx_interrupt SET amount = amount + 1 WHERE id = 1"));
                        throw new AssertionError("Nested connection acquisition should have been interrupted");
                    } catch (CannotCreateTransactionException expected) {
                        interruptRestored.set(Thread.currentThread().isInterrupted());
                        Thread.interrupted();
                        interruptionObserved.countDown();
                    }
                    await(retryAllowed, "nested retry release");
                    requiresNew.executeWithoutResult(innerStatus ->
                            jdbc.update("UPDATE tx_interrupt SET amount = amount + 1 WHERE id = 1"));
                    outerStatus.setRollbackOnly();
                });
            });
            assertThat(nestedAttemptStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitConnectionWait(nestedPool, Duration.ofSeconds(5))).isTrue();
            workerThread.get().interrupt();
            assertThat(interruptionObserved.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(interruptRestored).isTrue();

            nestedConnection.close();
            nestedConnection = null;
            retryAllowed.countDown();
            work.get(5, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("SELECT amount FROM tx_interrupt WHERE id = 1", Integer.class))
                    .isEqualTo(1);
        } finally {
            retryAllowed.countDown();
            if (nestedConnection != null) {
                nestedConnection.close();
            }
            if (workerThread.get() != null) {
                workerThread.get().interrupt();
            }
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            dataSource.close();
        }
    }

    @Test
    void requiresNewInsideSynchronizingSupportsScopeDoesNotExhaustRootPool() {
        String databaseName = "supports_scope_" + System.nanoTime();
        HikariDataSource rootPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                1,
                "RootPool-" + databaseName,
                300);
        HikariDataSource nestedPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                2,
                "NestedPool-" + databaseName,
                300);
        TransactionPoolRoutingDataSource dataSource =
                new TransactionPoolRoutingDataSource(rootPool, nestedPool);
        RoutingTransactionManager manager = new RoutingTransactionManager(
                new DataSourceTransactionManager(dataSource), dataSource, 2);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE tx_supports (id INT PRIMARY KEY, amount INT NOT NULL)");
        jdbc.update("INSERT INTO tx_supports (id, amount) VALUES (1, 0)");
        TransactionTemplate supports = new TransactionTemplate(manager);
        supports.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
        TransactionTemplate requiresNew = new TransactionTemplate(manager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            supports.executeWithoutResult(status -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                jdbc.queryForObject("SELECT amount FROM tx_supports WHERE id = 1", Integer.class);
                assertThat(rootPool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                requiresNew.executeWithoutResult(inner ->
                        jdbc.update("UPDATE tx_supports SET amount = 1 WHERE id = 1"));
                assertThat(jdbc.queryForObject("SELECT amount FROM tx_supports WHERE id = 1", Integer.class))
                        .isEqualTo(1);
            });
            assertThat(rootPool.getHikariPoolMXBean().getActiveConnections()).isZero();
            assertThat(nestedPool.getHikariPoolMXBean().getActiveConnections()).isZero();
        } finally {
            dataSource.close();
        }
    }

    @Test
    void notSupportedSuspendsOuterTransactionAndUsesNestedPoolWithoutStartingATransaction() {
        String databaseName = "not_supported_" + System.nanoTime();
        HikariDataSource rootPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                1,
                "RootPool-" + databaseName);
        HikariDataSource nestedPool = createPool(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                2,
                "NestedPool-" + databaseName);
        TransactionPoolRoutingDataSource dataSource =
                new TransactionPoolRoutingDataSource(rootPool, nestedPool);
        RoutingTransactionManager manager = new RoutingTransactionManager(
                new DataSourceTransactionManager(dataSource), dataSource, 2);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE tx_not_supported (id INT PRIMARY KEY, amount INT NOT NULL)");
        jdbc.update("INSERT INTO tx_not_supported (id, amount) VALUES (1, 0), (2, 0), (3, 0)");

        TransactionTemplate outer = new TransactionTemplate(manager);
        TransactionTemplate notSupported = new TransactionTemplate(manager);
        notSupported.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        TransactionTemplate required = new TransactionTemplate(manager);

        outer.executeWithoutResult(outerStatus -> {
            jdbc.update("UPDATE tx_not_supported SET amount = 10 WHERE id = 1");
            try {
                notSupported.executeWithoutResult(noTransactionStatus -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    jdbc.update("UPDATE tx_not_supported SET amount = 20 WHERE id = 2");
                    required.executeWithoutResult(requiredStatus -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                        jdbc.update("UPDATE tx_not_supported SET amount = 30 WHERE id = 3");
                    });
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    throw new IllegalStateException("Verify auto-commit behavior");
                });
            } catch (IllegalStateException ex) {
                assertThat(ex).hasMessage("Verify auto-commit behavior");
            }
            outerStatus.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT amount FROM tx_not_supported WHERE id = 1", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT amount FROM tx_not_supported WHERE id = 2", Integer.class))
                .isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT amount FROM tx_not_supported WHERE id = 3", Integer.class))
                .isEqualTo(30);
        dataSource.close();
    }

    private static boolean awaitNestedLockWait(JdbcTemplate observer, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Integer waiting = observer.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS "
                            + "WHERE BLOCKER_ID IS NOT NULL "
                            + "AND UPPER(EXECUTING_STATEMENT) LIKE 'UPDATE TX_CYCLE%'",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static boolean awaitConnectionWait(HikariDataSource pool, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (pool.getHikariPoolMXBean().getThreadsAwaitingConnection() > 0) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static HikariDataSource createPool(String jdbcUrl, int maximumPoolSize, String poolName) {
        return createPool(jdbcUrl, maximumPoolSize, poolName, 3000);
    }

    private static HikariDataSource createPool(
            String jdbcUrl, int maximumPoolSize, String poolName, long connectionTimeoutMillis) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(connectionTimeoutMillis);
        config.setPoolName(poolName);
        return new HikariDataSource(config);
    }

    private static void await(CountDownLatch latch, String label) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for " + label);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for " + label, ex);
        }
    }
}
