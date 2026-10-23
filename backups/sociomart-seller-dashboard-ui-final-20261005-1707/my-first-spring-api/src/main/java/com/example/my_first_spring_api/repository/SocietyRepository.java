package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Society;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SocietyRepository extends JpaRepository<Society, Long> {

    /** Societies of one area, alphabetically, so dropdown order is stable. */
    List<Society> findByAreaIdOrderByNameAsc(Long areaId);

    /** All active societies regardless of area - the selectable master list. */
    List<Society> findByActiveTrueOrderByNameAsc();

    List<Society> findByActiveTrue();

    /** Case-insensitive duplicate detection inside one area. */
    Optional<Society> findByAreaIdAndNameKey(Long areaId, String nameKey);

    boolean existsByAreaIdAndNameKey(Long areaId, String nameKey);

    /**
     * Resolves a legacy free-text society string to its record.
     *
     * <p>Returns empty when the name is unknown, and the caller must treat a name
     * that exists under MORE than one area as ambiguous rather than guessing -
     * see {@code LocationService.resolveLegacySociety}.</p>
     */
    List<Society> findByNameKey(String nameKey);

    /** Active societies of one area. */
    List<Society> findByAreaIdAndActiveTrueOrderByNameAsc(Long areaId);

    @Query("select s from Society s where s.area.id in :areaIds and s.active = true")
    List<Society> findActiveInAreas(@Param("areaIds") Collection<Long> areaIds);
}
