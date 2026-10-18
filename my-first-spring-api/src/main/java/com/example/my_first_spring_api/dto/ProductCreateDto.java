package com.example.my_first_spring_api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProductCreateDto {
    @NotBlank(message = "Item name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    private String priceUnit;
    private String imageUrl;
    @JsonAlias({"is_available", "isAvailable"})
    private Boolean availableToday;
    @NotNull(message = "Offering For is required")
    @JsonAlias({"offeringDate", "date"})
    private LocalDate availableDate;

    @JsonAlias({"ordersOpenTime", "openTime"})
    private String orderWindowStart;

    @JsonAlias({"ordersCloseTime", "closeTime", "cutoffTime"})
    private String orderWindowEnd;

    @NotBlank(message = "Delivery / Ready By is required")
    @JsonAlias({"deliveryTime", "deliveryDateTime", "readyBy"})
    private String readyByTime;

    @Positive(message = "Quantity Available must be at least 1; leave blank for unlimited")
    private Integer maxQuantity;
    private Integer remainingQuantity;
    private Boolean isPreorder;

    @JsonAlias({"category"})
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> categories;

    /** Optional recurring-schedule config; when present, the new product is
     * provisioned with a full recurring schedule whose occurrences are
     * materialised in the same atomic transaction. */
    private RecurringScheduleDto recurringSchedule;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getPriceUnit() { return priceUnit; }
    public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Boolean getAvailableToday() { return availableToday; }
    public void setAvailableToday(Boolean availableToday) { this.availableToday = availableToday; }
    public LocalDate getAvailableDate() { return availableDate; }
    public void setAvailableDate(LocalDate availableDate) { this.availableDate = availableDate; }
    public String getOrderWindowStart() { return orderWindowStart; }
    public void setOrderWindowStart(String orderWindowStart) { this.orderWindowStart = orderWindowStart; }
    public String getOrderWindowEnd() { return orderWindowEnd; }
    public void setOrderWindowEnd(String orderWindowEnd) { this.orderWindowEnd = orderWindowEnd; }
    public String getReadyByTime() { return readyByTime; }
    public void setReadyByTime(String readyByTime) { this.readyByTime = readyByTime; }
    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }
    public Integer getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public Boolean getIsPreorder() { return isPreorder; }
    public void setIsPreorder(Boolean isPreorder) { this.isPreorder = isPreorder; }
    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) {
        if (categories == null) {
            this.categories = null;
            return;
        }
        List<String> flattened = new java.util.ArrayList<>();
        for (String c : categories) {
            if (c != null && !c.isBlank()) {
                for (String part : c.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        flattened.add(trimmed);
                    }
                }
            }
        }
        this.categories = flattened;
    }

    public RecurringScheduleDto getRecurringSchedule() { return recurringSchedule; }
    public void setRecurringSchedule(RecurringScheduleDto recurringSchedule) { this.recurringSchedule = recurringSchedule; }
}
