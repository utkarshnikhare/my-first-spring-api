package com.example.my_first_spring_api.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * The Area/Society choices a seller (or the Admin) may pick service-area coverage
 * from.
 *
 * <p>Coverage is persisted by Society ID, so the picker needs the IDs - the existing
 * {@link AreaDto} carries display names only and cannot drive an ID-based write.
 * Only ACTIVE areas and their ACTIVE societies are listed: an inactive record is
 * never offered for a new selection, while the records themselves stay readable so
 * historical orders keep their original location identity.</p>
 */
public class CoverageOptionDto {

    private Long id;
    private String name;
    private List<SocietyOptionDto> societies = new ArrayList<>();

    public CoverageOptionDto() {
    }

    public CoverageOptionDto(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<SocietyOptionDto> getSocieties() { return societies; }
    public void setSocieties(List<SocietyOptionDto> societies) { this.societies = societies; }

    /** One selectable community inside an {@link CoverageOptionDto}. */
    public static class SocietyOptionDto {

        private Long id;
        private String name;

        public SocietyOptionDto() {
        }

        public SocietyOptionDto(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}