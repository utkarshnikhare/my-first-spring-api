package com.example.my_first_spring_api.config;

import com.example.my_first_spring_api.transaction.RoutingTransactionManager;
import com.example.my_first_spring_api.transaction.TransactionPoolRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class TransactionAdmissionConfiguration {

    @Bean
    static BeanPostProcessor transactionPoolPostProcessor(
            ConfigurableListableBeanFactory beanFactory,
            @Value("${spring.datasource.transaction.max-nesting-depth:2}") int maximumNestedDepth) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("dataSource".equals(beanName) && bean instanceof HikariDataSource rootPool) {
                    return routePools(rootPool, maximumNestedDepth);
                }
                if ("transactionManager".equals(beanName) && bean instanceof PlatformTransactionManager manager) {
                    Object dataSource = beanFactory.getBean("dataSource");
                    if (!(dataSource instanceof TransactionPoolRoutingDataSource routingDataSource)) {
                        throw new IllegalStateException(
                                "The application transaction manager requires the routed Hikari data source.");
                    }
                    return new RoutingTransactionManager(manager, routingDataSource, maximumNestedDepth);
                }
                return bean;
            }
        };
    }

    private static TransactionPoolRoutingDataSource routePools(HikariDataSource rootPool, int maximumNestedDepth) {
        if (maximumNestedDepth < 1) {
            throw new IllegalArgumentException("Maximum nested transaction depth must be positive.");
        }
        int totalMaximumPoolSize = rootPool.getMaximumPoolSize();
        // Keep enough isolated nested connections for every root to reach the configured depth.
        int rootPoolSize = totalMaximumPoolSize / (maximumNestedDepth + 1);
        if (rootPoolSize < 1) {
            throw new IllegalStateException(
                    "Hikari maximum pool size must support the configured nested transaction depth.");
        }
        int nestedPoolSize = totalMaximumPoolSize - rootPoolSize;
        int totalMinimumIdle = rootPool.getMinimumIdle() < 0
                ? totalMaximumPoolSize
                : Math.min(rootPool.getMinimumIdle(), totalMaximumPoolSize);
        int rootMinimumIdle = Math.min(totalMinimumIdle, rootPoolSize);
        int nestedMinimumIdle = Math.min(nestedPoolSize, totalMinimumIdle - rootMinimumIdle);

        HikariConfig rootConfig = new HikariConfig();
        rootPool.copyStateTo(rootConfig);
        rootConfig.setMaximumPoolSize(rootPoolSize);
        rootConfig.setMinimumIdle(rootMinimumIdle);
        rootConfig.setPoolName("RootTransactionPool");

        HikariConfig nestedConfig = new HikariConfig();
        rootPool.copyStateTo(nestedConfig);
        nestedConfig.setMaximumPoolSize(nestedPoolSize);
        nestedConfig.setMinimumIdle(nestedMinimumIdle);
        nestedConfig.setPoolName("NestedTransactionPool");

        HikariDataSource routedRootPool = new HikariDataSource(rootConfig);
        HikariDataSource nestedTransactionPool = null;
        boolean poolsTransferred = false;
        try {
            nestedTransactionPool = new HikariDataSource(nestedConfig);
            TransactionPoolRoutingDataSource routedDataSource =
                    new TransactionPoolRoutingDataSource(routedRootPool, nestedTransactionPool);
            rootPool.close();
            poolsTransferred = true;
            return routedDataSource;
        } finally {
            if (!poolsTransferred) {
                routedRootPool.close();
                if (nestedTransactionPool != null) {
                    nestedTransactionPool.close();
                }
            }
        }
    }
}
