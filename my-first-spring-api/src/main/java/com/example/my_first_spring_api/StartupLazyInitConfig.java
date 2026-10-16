package com.example.my_first_spring_api;

import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps safety-critical beans eager even when lazy initialization is turned on.
 *
 * <p>The production profile enables {@code spring.main.lazy-initialization=true} so that
 * Spring creates beans on first use instead of all at once at boot. That is what gets the
 * application under Render's health-check deadline, but it must not defer the production
 * fail-fast guard: {@link ProductionStartupValidator} validates its environment inside its
 * constructor and throws when the required credentials or database settings are missing.
 * Nothing injects it, so under lazy initialization it would never be created and the
 * "refuse to start in production" behaviour would be silently disabled - a bad
 * configuration would then surface only on the first request, or never.
 *
 * <p>Excluding it here forces it to be instantiated during startup, which both preserves
 * the fail-fast guard and keeps {@code ProductionStartupValidatorTest} meaningful. No
 * database or other heavyweight dependency is involved, so the eager creation costs
 * effectively nothing.
 *
 * <p>The {@code ApplicationRunner} beans ({@link ProductionBootstrap},
 * {@link KitchenSlugBackfillRunner}) do not need an exclusion: Spring always invokes
 * runners at boot, so the database bootstrap still runs eagerly.
 */
@Configuration(proxyBeanMethods = false)
public class StartupLazyInitConfig {

    @Bean
    public LazyInitializationExcludeFilter eagerProductionStartupValidator() {
        return LazyInitializationExcludeFilter.forBeanTypes(ProductionStartupValidator.class);
    }
}
