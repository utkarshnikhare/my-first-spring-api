package com.example.my_first_spring_api.dto;

import java.util.List;

public class KitchenDetailDto {
    private KitchenDto kitchen;
    private List<ProductDto> products;
    private List<ProductDto> preorderProducts;
    private List<QuickPostDto> quickPosts;
    private List<ProductDto> quickPostProducts;

    public KitchenDetailDto() {}

    public KitchenDetailDto(KitchenDto kitchen, List<ProductDto> products) {
        this.kitchen = kitchen;
        this.products = products;
    }

    public KitchenDto getKitchen() { return kitchen; }
    public void setKitchen(KitchenDto kitchen) { this.kitchen = kitchen; }
    public List<ProductDto> getProducts() { return products; }
    public void setProducts(List<ProductDto> products) { this.products = products; }
    public List<ProductDto> getPreorderProducts() { return preorderProducts; }
    public void setPreorderProducts(List<ProductDto> preorderProducts) { this.preorderProducts = preorderProducts; }
    public List<QuickPostDto> getQuickPosts() { return quickPosts; }
    public void setQuickPosts(List<QuickPostDto> quickPosts) { this.quickPosts = quickPosts; }
    public List<ProductDto> getQuickPostProducts() { return quickPostProducts; }
    public void setQuickPostProducts(List<ProductDto> quickPostProducts) { this.quickPostProducts = quickPostProducts; }
}
