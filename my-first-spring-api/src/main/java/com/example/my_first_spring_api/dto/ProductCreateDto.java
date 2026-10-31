package com.example.my_first_spring_api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProductCreateDto {
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @JsonAlias({"priceUnit", "price_unit"})
    private String priceUnit = "portion";

    @JsonAlias({"unit"})
    private String unit = "portion";

    private String imageUrl;
    private Boolean quickPost;
    private String quickPostImageData;
    private String quickPostRequestId;

    @JsonAlias({"is_available", "isAvailable"})
    private Boolean availableToday = Boolean.TRUE;

    @JsonAlias({"is_veg", "isVeg", "veg"})
    private Boolean isVeg = Boolean.TRUE;
    @JsonAlias({"offeringDate", "date"})
    private LocalDate availableDate;

    @JsonAlias({"ordersOpenTime", "openTime"})
    private String orderWindowStart;

    private LocalDate orderOpenDate;

    private String orderOpenTime;

    @JsonAlias({"ordersCloseTime", "closeTime", "cutoffTime"})
    private String orderWindowEnd;

    @JsonAlias({"deliveryTime", "deliveryDateTime", "readyBy"})
    private String readyByTime;

    @Positive(message = "Quantity Available must be at least 1; leave blank for unlimited")
    private Integer maxQuantity;
    private Integer remainingQuantity;
    private Boolean isPreorder;

    @JsonAlias({"is_homemade", "isHomemade"})
    private Boolean isHomemade;

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
    public String getPriceUnit() { return priceUnit != null && !priceUnit.isBlank() ? priceUnit : unit; }
    public void setPriceUnit(String priceUnit) {
        this.priceUnit = priceUnit;
        if (this.unit == null || this.unit.isBlank()) this.unit = priceUnit;
    }
    public String getUnit() { return unit != null && !unit.isBlank() ? unit : (priceUnit != null && !priceUnit.isBlank() ? priceUnit : "portion"); }
    public void setUnit(String unit) {
        this.unit = unit;
        if (this.priceUnit == null || this.priceUnit.isBlank()) this.priceUnit = unit;
    }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Boolean getQuickPost() { return quickPost; }
    public void setQuickPost(Boolean quickPost) { this.quickPost = quickPost; }
    public String getQuickPostImageData() { return quickPostImageData; }
    public void setQuickPostImageData(String quickPostImageData) { this.quickPostImageData = quickPostImageData; }
    public String getQuickPostRequestId() { return quickPostRequestId; }
    public void setQuickPostRequestId(String quickPostRequestId) { this.quickPostRequestId = quickPostRequestId; }
    public Boolean getAvailableToday() { return availableToday != null ? availableToday : Boolean.TRUE; }
    public void setAvailableToday(Boolean availableToday) { this.availableToday = (availableToday != null) ? availableToday : Boolean.TRUE; }
    public Boolean getIsAvailable() { return getAvailableToday(); }
    public void setIsAvailable(Boolean isAvailable) { setAvailableToday(isAvailable); }
    public Boolean getIsVeg() { return isVeg != null ? isVeg : Boolean.TRUE; }
    public void setIsVeg(Boolean isVeg) { this.isVeg = (isVeg != null) ? isVeg : Boolean.TRUE; }
    public LocalDate getAvailableDate() { return availableDate; }
    public void setAvailableDate(LocalDate availableDate) { this.availableDate = availableDate; }
    public String getOrderWindowStart() { return orderWindowStart; }
    public void setOrderWindowStart(String orderWindowStart) { this.orderWindowStart = orderWindowStart; }
    public LocalDate getOrderOpenDate() { return orderOpenDate; }
    public void setOrderOpenDate(LocalDate orderOpenDate) { this.orderOpenDate = orderOpenDate; }
    public String getOrderOpenTime() { return orderOpenTime; }
    public void setOrderOpenTime(String orderOpenTime) { this.orderOpenTime = orderOpenTime; }
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
    public Boolean getIsHomemade() { return isHomemade; }
    public void setIsHomemade(Boolean isHomemade) { this.isHomemade = isHomemade; }
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

    @AssertTrue(message = "Item name is required")
    @JsonIgnore
    public boolean isNameValidForOffering() {
        return Boolean.TRUE.equals(quickPost) || (name != null && !name.isBlank());
    }

    @AssertTrue(message = "Offering For is required")
    @JsonIgnore
    public boolean isAvailableDateValidForOffering() {
        return Boolean.TRUE.equals(quickPost) || availableDate != null;
    }

    @AssertTrue(message = "Delivery / Ready By is required")
    @JsonIgnore
    public boolean isReadyByTimeValidForOffering() {
        return Boolean.TRUE.equals(quickPost) || (readyByTime != null && !readyByTime.isBlank());
    }

    @AssertTrue(message = "Quick Post message is required")
    @JsonIgnore
    public boolean isQuickPostMessageValid() {
        return !Boolean.TRUE.equals(quickPost) || (description != null && !description.isBlank());
    }

    @AssertTrue(message = "Quick Post request reference is required and must be at most 80 characters")
    @JsonIgnore
    public boolean isQuickPostRequestIdValid() {
        return !Boolean.TRUE.equals(quickPost)
                || (quickPostRequestId != null && !quickPostRequestId.isBlank()
                    && quickPostRequestId.trim().length() <= 80);
    }
}
