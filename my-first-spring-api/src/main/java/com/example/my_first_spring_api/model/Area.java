package com.example.my_first_spring_api.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A locality that groups the societies inside it, e.g. "Charholi / Lohegaon".
 *
 * <p>This exists purely to express the Area -&gt; Society relationship that the
 * Buyer profile needs. It is NOT a second society catalogue: {@link #societies}
 * holds the same society names the rest of the platform already stores on
 * {@code User.society} and {@code Kitchen.society}, so a society declared here is
 * the very same society the existing {@code SocietyDirectory} reports.
 *
 * <p>Society names are used as the relationship key because that is how the
 * existing architecture identifies a society everywhere else. The Area itself has
 * a stable generated id as well as its unique display name.
 */
@Entity
@Table(name = "areas", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Area {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "area_societies", joinColumns = @JoinColumn(name = "area_id"))
    @Column(name = "society", nullable = false)
    private Set<String> societies = new LinkedHashSet<>();

    public Area() {}

    public Area(String name) { this.name = name; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Set<String> getSocieties() { return societies; }
    public void setSocieties(Set<String> societies) { this.societies = societies == null ? new LinkedHashSet<>() : societies; }

    @Override
    public String toString() { return "Area{id=" + id + ", name='" + name + "', societies=" + societies + "}"; }
}
