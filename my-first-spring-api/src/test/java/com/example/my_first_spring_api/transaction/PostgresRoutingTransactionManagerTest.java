package com.example.my_first_spring_api.transaction;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresRoutingTransactionManagerTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void crossRootLockWaitAllowsAnotherNestedCommitAndOuterRollbacks() throws Exception {
        HikariDataSource rootPool = createPool(3, "PgRootPool");
        HikariDataSource nestedPool = createPool(7, "PgNestedPool");
        TransactionPoolRoutingDataSource dataSource =
                new TransactionPoolRoutingDataSource(rootPool, nestedPool);
        RoutingTransactionManager manager = new RoutingTransactionManager(
                new DataSourceTransactionManager(dataSource), dataSource, 2);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        JdbcTemplate observer = new JdbcTemplate(nestedPool);
        jdbc.execute("CREATE TABLE tx_pool_cycle (id INT PRIMARY KEY, amount INT NOT NULL)");
        jdbc.update("INSERT INTO tx_pool_cycle (id, amount) VALUES (1, 0), (2, 0), (3, 0)");

        TransactionTemplate outer = new TransactionTemplate(manager);
        TransactionTemplate requiresNew = new TransactionTemplate(manager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        CountDownLatch secondRootLocked = new CountDownLatch(1);
        CountDownLatch releaseSecondRoot = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Connection directConsumer = null;
        try {
            Future<?> first = executor.submit(() -> outer.executeWithoutResult(status -> {
                jdbc.update("UPDATE tx_pool_cycle SET amount = 10 WHERE id = 1");
                await(secondRootLocked);
                requiresNew.executeWithoutResult(inner ->
                        jdbc.update("UPDATE tx_pool_cycle SET amount = amount + 1 WHERE id = 2"));
                status.setRollbackOnly();
            }));
            Future<?> second = executor.submit(() -> outer.executeWithoutResult(status -> {
                jdbc.update("UPDATE tx_pool_cycle SET amount = 20 WHERE id = 2");
                secondRootLocked.countDown();
                await(releaseSecondRoot);
                requiresNew.executeWithoutResult(inner ->
                        jdbc.update("UPDATE tx_pool_cycle SET amount = amount + 1 WHERE id = 3"));
                status.setRollbackOnly();
            }));

            assertThat(secondRootLocked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitLockWait(observer, Duration.ofSeconds(10))).isTrue();
            directConsumer = dataSource.getConnection();
            assertThat(rootPool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(3);
            releaseSecondRoot.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("SELECT amount FROM tx_pool_cycle WHERE id = 1", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT amount FROM tx_pool_cycle WHERE id = 2", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT amount FROM tx_pool_cycle WHERE id = 3", Integer.class))
                    .isEqualTo(1);
        } finally {
            releaseSecondRoot.countDown();
            if (directConsumer != null) {
                directConsumer.close();
            }
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                dataSource.close();
            }
        }
    }

    private static HikariDataSource createPool(int maximum, String name) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(maximum);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(15000);
        config.setPoolName(name);
        return new HikariDataSource(config);
    }

    private static boolean awaitLockWait(JdbcTemplate observer, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Integer count = observer.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                            + "AND wait_event_type = 'Lock' AND query LIKE 'UPDATE tx_pool_cycle%'",
                    Integer.class);
            if (count != null && count > 0) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for transaction coordination");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for transaction coordination", ex);
        }
    }
}
