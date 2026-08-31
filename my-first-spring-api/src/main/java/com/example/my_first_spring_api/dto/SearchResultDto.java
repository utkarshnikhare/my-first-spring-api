package com.example.my_first_spring_api.dto;

import java.util.List;

public class SearchResultDto {
    private List<ProductDto> products;
    private List<KitchenDto> kitchens;

    public SearchResultDto() {}

    public SearchResultDto(List<ProductDto> products, List<KitchenDto> kitchens) {
        this.products = products;
        this.kitchens = kitchens;
    }

    public List<ProductDto> getProducts() { return products; }
    public void setProducts(List<ProductDto> products) { this.products = products; }
    public List<KitchenDto> getKitchens() { return kitchens; }
    public void setKitchens(List<KitchenDto> kitchens) { this.kitchens = kitchens; }
}
