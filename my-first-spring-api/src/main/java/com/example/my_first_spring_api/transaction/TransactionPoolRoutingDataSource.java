package com.example.my_first_spring_api.transaction;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.Map;
import java.util.Objects;

public final class TransactionPoolRoutingDataSource extends AbstractRoutingDataSource
        implements AutoCloseable, DisposableBean {

    public enum Pool {
        ROOT,
        NESTED
    }

    private final HikariDataSource rootPool;
    private final HikariDataSource nestedPool;
    private final ThreadLocal<Pool> selectedPool = new ThreadLocal<>();

    public TransactionPoolRoutingDataSource(HikariDataSource rootPool, HikariDataSource nestedPool) {
        this.rootPool = Objects.requireNonNull(rootPool, "rootPool");
        this.nestedPool = Objects.requireNonNull(nestedPool, "nestedPool");
        setTargetDataSources(Map.of(Pool.ROOT, rootPool, Pool.NESTED, nestedPool));
        setDefaultTargetDataSource(rootPool);
        setLenientFallback(false);
        afterPropertiesSet();
    }

    Pool selectPool(Pool pool) {
        Pool previous = selectedPool.get();
        selectedPool.set(pool);
        return previous;
    }

    void restorePool(Pool pool) {
        if (pool == null) {
            selectedPool.remove();
        } else {
            selectedPool.set(pool);
        }
    }

    @Override
    protected Object determineCurrentLookupKey() {
        Pool pool = selectedPool.get();
        return pool == null ? Pool.ROOT : pool;
    }

    @Override
    public void close() {
        nestedPool.close();
        rootPool.close();
    }

    @Override
    public void destroy() {
        close();
    }
}
