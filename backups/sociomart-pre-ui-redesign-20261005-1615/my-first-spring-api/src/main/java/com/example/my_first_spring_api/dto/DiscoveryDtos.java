package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs powering the buyer discovery experience (Screens 2, 2A, 3 & 7). */
public final class DiscoveryDtos {
    private DiscoveryDtos() {}

    /** Screen 3 kitchen card. */
    public static class KitchenCard {
        private Long id;
        private String slug;
        private String displayName;
        private String imageUrl;
        private String shortDescription;
        /** LIVE_NOW | TOMORROW | PRE_ORDER | CLOSED */
        private String status;
        private Integer orderableItemCount;
        private List<String> itemNames;
        private Boolean previouslyOrdered;
        private Double rating;

        public KitchenCard() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getShortDescription() { return shortDescription; }
        public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getOrderableItemCount() { return orderableItemCount; }
        public void setOrderableItemCount(Integer orderableItemCount) { this.orderableItemCount = orderableItemCount; }
        public List<String> getItemNames() { return itemNames; }
        public void setItemNames(List<String> itemNames) { this.itemNames = itemNames; }
        public Boolean getPreviouslyOrdered() { return previouslyOrdered; }
        public void setPreviouslyOrdered(Boolean previouslyOrdered) { this.previouslyOrdered = previouslyOrdered; }
        public Double getRating() { return rating; }
        public void setRating(Double rating) { this.rating = rating; }
    }

    /** Screen 3 header counts: "8 Live · 3 Tomorrow · 5 Pre-order · 12 All". */
    public static class KitchenCounts {
        private long live;
        private long tomorrow;
        private long preorder;
        private long all;

        public KitchenCounts(long live, long tomorrow, long preorder, long all) {
            this.live = live; this.tomorrow = tomorrow; this.preorder = preorder; this.all = all;
        }
        public long getLive() { return live; }
        public long getTomorrow() { return tomorrow; }
        public long getPreorder() { return preorder; }
        public long getAll() { return all; }
    }

    /** Screen 2 "By Items" preview card, e.g. "Poha - 4 kitchens". */
    public static class ItemGroup {
        private String name;
        private long kitchenCount;
        private String category;
        private String imageUrl;
        private List<Long> kitchenIds;

        public ItemGroup() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getKitchenCount() { return kitchenCount; }
        public void setKitchenCount(long kitchenCount) { this.kitchenCount = kitchenCount; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public List<Long> getKitchenIds() { return kitchenIds; }
        public void setKitchenIds(List<Long> kitchenIds) { this.kitchenIds = kitchenIds; }
    }

    /** Screen 7 comparison offer: one kitchen's take on a searched dish. */
    public static class ComparisonOffer {
        private Long kitchenId;
        private String kitchenSlug;
        private String kitchenDisplayName;
        private String kitchenImageUrl;
        private String tagline;
        private String status;
        private BigDecimal price;
        private String priceUnit;
        /** ORDER BY (offering cutoff) — only revealed here per offering-cutoff rule. */
        private String orderBy;
        private String readyBy;
        private Boolean soldOut;
        private Boolean preorder;
        private LocalDate availableDate;

        public ComparisonOffer() {}

        public Long getKitchenId() { return kitchenId; }
        public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
        public String getKitchenSlug() { return kitchenSlug; }
        public void setKitchenSlug(String kitchenSlug) { this.kitchenSlug = kitchenSlug; }
        public String getKitchenDisplayName() { return kitchenDisplayName; }
        public void setKitchenDisplayName(String kitchenDisplayName) { this.kitchenDisplayName = kitchenDisplayName; }
        public String getKitchenImageUrl() { return kitchenImageUrl; }
        public void setKitchenImageUrl(String kitchenImageUrl) { this.kitchenImageUrl = kitchenImageUrl; }
        public String getTagline() { return tagline; }
        public void setTagline(String tagline) { this.tagline = tagline; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getPriceUnit() { return priceUnit; }
        public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }
        public String getOrderBy() { return orderBy; }
        public void setOrderBy(String orderBy) { this.orderBy = orderBy; }
        public String getReadyBy() { return readyBy; }
        public void setReadyBy(String readyBy) { this.readyBy = readyBy; }
        public Boolean getSoldOut() { return soldOut; }
        public void setSoldOut(Boolean soldOut) { this.soldOut = soldOut; }
        public Boolean getPreorder() { return preorder; }
        public void setPreorder(Boolean preorder) { this.preorder = preorder; }
        public LocalDate getAvailableDate() { return availableDate; }
        public void setAvailableDate(LocalDate availableDate) { this.availableDate = availableDate; }
    }

    /** Category tile with live counts for Screen 2. */
    public static class CategoryTile {
        private String category;
        private String label;
        private String emoji;
        private long itemCount;

        public CategoryTile(String category, String label, String emoji, long itemCount) {
            this.category = category; this.label = label; this.emoji = emoji; this.itemCount = itemCount;
        }
        public String getCategory() { return category; }
        public String getLabel() { return label; }
        public String getEmoji() { return emoji; }
        public long getItemCount() { return itemCount; }
    }
}
