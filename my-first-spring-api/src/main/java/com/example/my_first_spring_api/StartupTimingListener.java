package com.example.my_first_spring_api;

import java.lang.management.ManagementFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Lightweight startup-phase timing for production diagnostics (e.g. Render
 * "port scan timeout" investigations).
 *
 * <p>Event ordering in Spring Boot (servlet stack): the context is refreshed
 * (this starts the embedded Tomcat — see its "started on port" line), then
 * {@link ApplicationStartedEvent} is published, then {@code ApplicationRunner}
 * beans execute, then {@link ApplicationReadyEvent} is published. So
 * "since-refresh" also covers the production runners
 * ({@code ProductionBootstrap}, slug backfill).</p>
 *
 * <p>Totals are measured against JVM start
 * ({@link ManagementFactory#getRuntimeMXBean}) so they are meaningful even
 * though this bean is created mid-refresh. Logs contain only phase names and
 * elapsed times — never credentials, JDBC URLs, tokens or personal data.</p>
 */
@Component
public class StartupTimingListener {

    private static final Logger log = LoggerFactory.getLogger(StartupTimingListener.class);

    private static volatile long contextRefreshedNanos = -1;

    private static long msSinceJvmStart() {
        return System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    @Component
    static class ContextRefreshedTiming
            implements ApplicationListener<ApplicationStartedEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartedEvent event) {
            contextRefreshedNanos = System.nanoTime();
            log.info("startup-timing: context-refreshed jvm-uptime-ms={} "
                    + "(embedded server already listening; "
                    + "see Tomcat 'started on port' line)",
                    msSinceJvmStart());
        }
    }

    @Component
    static class ReadyTiming implements ApplicationListener<ApplicationReadyEvent> {
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            long sinceRefreshMs = contextRefreshedNanos < 0 ? -1
                    : (System.nanoTime() - contextRefreshedNanos) / 1_000_000;
            log.info("startup-timing: application-ready jvm-uptime-ms={} since-refresh-ms={}",
                    msSinceJvmStart(), sinceRefreshMs);
        }
    }
}
