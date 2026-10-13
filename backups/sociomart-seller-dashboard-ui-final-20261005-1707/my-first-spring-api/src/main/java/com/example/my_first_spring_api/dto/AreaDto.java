package com.example.my_first_spring_api.dto;

import java.util.List;

/** An approved locality and the societies inside it, for the Buyer profile dropdowns. */
public class AreaDto {

    private String name;
    private List<String> societies;

    public AreaDto() {}

    public AreaDto(String name, List<String> societies) {
        this.name = name;
        this.societies = societies;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getSocieties() { return societies; }
    public void setSocieties(List<String> societies) { this.societies = societies; }
}
