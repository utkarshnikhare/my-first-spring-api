package com.example.my_first_spring_api;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.service.SlugService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Backfills the public-URL slug for every kitchen, on every startup, so the
 * column is never null and never duplicated regardless of how the schema was
 * created (Hibernate {@code ddl-auto} on H2, where Flyway is off, or a fresh
 * database). Idempotent: kitchens that already carry a slug are left untouched.
 *
 * <p>The Flyway migration {@code V3__kitchen_public_url_slug.sql} does the same
 * job for the Postgres profile; this runner is the equivalent guarantee for the
 * H2-backed demo/dev/default profiles and for any legacy row Flyway did not
 * see.</p>
 */
@Configuration
public class KitchenSlugBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(KitchenSlugBackfillRunner.class);

    @Bean
    ApplicationRunner backfillKitchenSlugs(KitchenRepository kitchens, SlugService slugService) {
        return args -> {
            List<Kitchen> all = kitchens.findAll();
            if (all.isEmpty()) return;
            Set<String> used = new HashSet<>();
            int changed = 0;
            for (Kitchen k : all) {
                String current = k.getSlug();
                if (current != null && !current.isBlank()) {
                    used.add(current);
                }
            }
            for (Kitchen k : all) {
                String current = k.getSlug();
                if (current != null && !current.isBlank() && used.contains(current)) {
                    continue; // already unique and present
                }
                String base = SlugService.slugify(
                        (current == null || current.isBlank()) ? k.getName() : current);
                if (base.isBlank()) base = "kitchen";
                String candidate = base;
                int suffix = 2;
                while (used.contains(candidate)) {
                    candidate = base + "-" + suffix++;
                }
                k.setSlug(candidate);
                used.add(candidate);
                kitchens.save(k);
                changed++;
            }
            if (changed > 0) {
                log.info("Backfilled public-URL slugs for {} kitchen(s)", changed);
            }
        };
    }
}
