package com.example.my_first_spring_api;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.service.SlugService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Gives a public-URL slug to any kitchen that has none (null or blank). It is
 * a safety net for rows written outside the application (the Flyway V3
 * migration backfills and enforces NOT NULL on PostgreSQL, so there it finds
 * nothing) and for local H2 databases.
 *
 * <p>It queries only the rows missing a slug, so a normal cold start costs one
 * small query and never loads the kitchen table. Rows that already
 * carry a slug are never touched, and duplicates cannot exist because the
 * column has a unique constraint. A candidate is always checked against every
 * stored slug and alias, and a row that still collides is skipped and logged
 * instead of failing the start-up.</p>
 */
@Configuration
public class KitchenSlugBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(KitchenSlugBackfillRunner.class);

    @Bean
    ApplicationRunner backfillKitchenSlugs(KitchenRepository kitchens, SlugService slugService) {
        return args -> backfill(kitchens, slugService);
    }

    static int backfill(KitchenRepository kitchens, SlugService slugService) {
        int changed = 0;
        for (Kitchen kitchen : kitchens.findWithMissingSlug()) {
            try {
                kitchen.setSlug(slugService.suggestUnique(kitchen.getName()));
                kitchens.save(kitchen);
                changed++;
            } catch (DataIntegrityViolationException ex) {
                log.warn("Could not backfill a slug for kitchen id {}; skipped", kitchen.getId());
            }
        }
        if (changed > 0) {
            log.info("Backfilled public-URL slugs for {} kitchen(s)", changed);
        }
        return changed;
    }
}