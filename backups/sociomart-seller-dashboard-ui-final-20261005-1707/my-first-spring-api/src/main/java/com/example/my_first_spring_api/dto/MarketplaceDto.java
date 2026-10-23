package com.example.my_first_spring_api.dto;

import java.util.List;

public class MarketplaceDto {
    private List<KitchenDto> kitchens;
    private List<ProductDto> popularProducts;
    private List<ProductDto> newProducts;
    private List<ProductDto> availableToday;

    public MarketplaceDto() {}

    public MarketplaceDto(List<KitchenDto> kitchens, List<ProductDto> popularProducts,
                          List<ProductDto> newProducts, List<ProductDto> availableToday) {
        this.kitchens = kitchens;
        this.popularProducts = popularProducts;
        this.newProducts = newProducts;
        this.availableToday = availableToday;
    }

    public List<KitchenDto> getKitchens() { return kitchens; }
    public void setKitchens(List<KitchenDto> kitchens) { this.kitchens = kitchens; }
    public List<ProductDto> getPopularProducts() { return popularProducts; }
    public void setPopularProducts(List<ProductDto> popularProducts) { this.popularProducts = popularProducts; }
    public List<ProductDto> getNewProducts() { return newProducts; }
    public void setNewProducts(List<ProductDto> newProducts) { this.newProducts = newProducts; }
    public List<ProductDto> getAvailableToday() { return availableToday; }
    public void setAvailableToday(List<ProductDto> availableToday) { this.availableToday = availableToday; }
}
