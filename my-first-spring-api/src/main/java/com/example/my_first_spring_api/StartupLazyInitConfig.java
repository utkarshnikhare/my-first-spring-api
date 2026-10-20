package com.example.my_first_spring_api;

import org.flywaydb.core.Flyway;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StartupLazyInitConfig {

    @Bean
    public LazyInitializationExcludeFilter eagerSafetyCriticalBeans() {
        return LazyInitializationExcludeFilter.forBeanTypes(
                ProductionStartupValidator.class,
                Flyway.class
        );
    }
}
