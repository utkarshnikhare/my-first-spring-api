package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.KitchenSlugAliasRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Server-side authority for kitchen public-URL slugs.
 *
 * <p>The slug is the stable, permanent public identifier of a storefront
 * ({@code /kitchen/{slug}}). It is generated and reserved HERE, never in the
 * browser, so two simultaneous registrations can never claim the same slug: the
 * database's unique constraint on {@link Kitchen#getSlug()} is the final
 * arbiter and this service retries with the next {@code -2, -3, …} suffix when a
 * race loses.</p>
 *
 * <p>The slug is deliberately decoupled from the display name: renaming a
 * kitchen never changes its slug, and an Admin rename keeps the old slug alive
 * as a redirecting alias (see {@link #changeSlug}).</p>
 */
@Service
public class SlugService {

    private final KitchenRepository kitchenRepository;
    private final KitchenSlugAliasRepository aliasRepository;

    @Autowired
    public SlugService(KitchenRepository kitchenRepository, KitchenSlugAliasRepository aliasRepository) {
        this.kitchenRepository = kitchenRepository;
        this.aliasRepository = aliasRepository;
    }

    /**
     * Normalise a kitchen name into a URL slug: lowercase, strip accents,
     * remove apostrophes and other unsafe punctuation, turn whitespace into
     * single hyphens, collapse repeats and trim edge hyphens.
     *
     * <p>{@code "Aarti's Cloud Kitchen"} → {@code "aartis-cloud-kitchen"}.</p>
     */
    public static String slugify(String raw) {
        if (raw == null) return "";
        String lower = Normalizer.normalize(raw, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")   // drop combining accents
                .toLowerCase(Locale.ROOT);
        // Remove apostrophes (straight and curly) and every character that is not
        // a letter or digit; whitespace and separators become hyphens below.
        String cleaned = lower.replaceAll("['\\u2019]", "")
                .replaceAll("[^a-z0-9]+", "-");
        cleaned = cleaned.replaceAll("-{2,}", "-").replaceAll("^-+", "").replaceAll("-+$", "");
        return cleaned;
    }

    /** True when the slug (or a live alias pointing at a kitchen) is already taken. */
    @Transactional(readOnly = true)
    public boolean isTaken(String slug) {
        if (slug == null || slug.isBlank()) return false;
        return kitchenRepository.findBySlug(slug).isPresent()
                || aliasRepository.existsByOldSlug(slug);
    }

    /**
     * A unique slug for a desired base, appending {@code -2, -3, …} until free.
     * Advisory for display (the registration suggestion endpoint); the binding
     * reservation still races through the DB constraint.
     */
    @Transactional(readOnly = true)
    public String suggestUnique(String base) {
        String slug = slugify(base);
        if (slug.isBlank()) slug = "kitchen";
        if (!isTaken(slug)) return slug;
        for (int i = 2; i < 10_000; i++) {
            String candidate = slug + "-" + i;
            if (!isTaken(candidate)) return candidate;
        }
        return slug + "-" + System.nanoTime(); // astronomically unlikely
    }

    /**
     * Reserve a concrete slug for a brand-new kitchen and persist it.
     *
     * <p>Uniqueness is guaranteed by the database's unique index on
     * {@link Kitchen#getSlug()}, which is the authority under concurrency - two
     * simultaneous registrations can never end up with the same slug because one
     * of the two INSERTs is rejected. To keep the common case collision-free the
     * candidate is first chosen with {@link #suggestUnique}, which already
     * suffixes {@code -2, -3, …} against every slug and alias currently stored,
     * so a second kitchen with the same name gets the next free suffix without
     * any race at all. The legacy unique {@code name} column is set to the same
     * value so every existing {@code findByName(...)} caller keeps resolving the
     * same kitchen.</p>
     *
     * @return the slug actually persisted on {@code kitchen}
     */
    @Transactional
    public String reserveUniqueSlug(Kitchen kitchen, String desiredBase) {
        String slug = suggestUnique(desiredBase);
        kitchen.setSlug(slug);
        kitchen.setName(slug);
        kitchenRepository.saveAndFlush(kitchen);
        return slug;
    }

    /**
     * Resolve a slug to its kitchen, following aliases so an old URL keeps
     * working after an Admin rename. Returns the canonical kitchen.
     */
    @Transactional(readOnly = true)
    public Optional<Kitchen> resolveBySlug(String slug) {
        if (slug == null || slug.isBlank()) return Optional.empty();
        Optional<Kitchen> direct = kitchenRepository.findBySlug(slug);
        if (direct.isPresent()) return direct;
        return aliasRepository.findByOldSlug(slug).map(alias -> alias.getKitchen());
    }

    /**
     * Admin-only slug change. The previous slug is preserved as a redirecting
     * alias so every already-shared {@code /kitchen/{old}} link keeps working.
     * The display name is untouched - renaming the URL never renames the kitchen.
     */
    @Transactional
    public String changeSlug(Kitchen kitchen, String desiredBase) {
        String target = slugify(desiredBase);
        if (target.isBlank()) throw new IllegalArgumentException("Choose a kitchen URL containing letters or numbers.");
        if (target.equals(kitchen.getSlug())) return kitchen.getSlug();
        if (kitchenRepository.findBySlug(target).isPresent()) {
            throw new IllegalArgumentException("That kitchen URL is already in use.");
        }
        // A retired alias held by ANOTHER kitchen also owns the value: adopting
        // it would shadow that kitchen's permanent redirect - direct resolution
        // wins over aliases - and silently re-point every already-shared
        // /kitchen/{target} link here. An alias pointing at THIS kitchen is
        // deliberately allowed so the revert case (X -> Y, then back Y -> X)
        // still resolves to the same storefront it always did.
        aliasRepository.findByOldSlug(target)
                .filter(a -> a.getKitchen() != null
                        && !kitchen.getId().equals(a.getKitchen().getId()))
                .ifPresent(a -> {
                    throw new IllegalArgumentException("That kitchen URL is already in use.");
                });
        String previous = kitchen.getSlug();
        kitchen.setSlug(target);
        kitchenRepository.save(kitchen);
        if (previous != null && !previous.isBlank() && !previous.equals(target)) {
            aliasRepository.save(new com.example.my_first_spring_api.model.KitchenSlugAlias(previous, kitchen));
        }
        return target;
    }

    /**
     * For the public forward route: if {@code slug} is a retired alias, return the
     * canonical {@code /kitchen/{current-slug}} path so the controller can 301 to
     * it; otherwise {@code null} (the slug is current or unknown - serve the SPA
     * and let the client resolve / 404). The request is accepted so the redirect
     * can stay relative to the deployment origin; it is not required today.
     */
    @Transactional(readOnly = true)
    public String canonicalSlugForPublic(String slug, jakarta.servlet.http.HttpServletRequest request) {
        return resolveBySlug(slug)
                .filter(k -> k.getSlug() != null && !k.getSlug().equals(slug))
                .map(k -> "/kitchen/" + k.getSlug())
                .orElse(null);
    }
}

