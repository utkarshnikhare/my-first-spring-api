package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;

/**
 * Screen 4: Result of parsing a raw WhatsApp promotional message.
 * All fields are best-effort; missing fields are null. The seller must
 * manually confirm before publishing (Safety Gate rule).
 */
public class QuickPostParseResultDto {

    private String name;
    private BigDecimal price;
    private String priceUnit;
    private Integer maxQuantity;
    private String cutoffTime;
    private String description;
    private String rawText;

    /** True when the parser is confident enough to suggest a publishable offering. */
    private boolean publishable;

    public QuickPostParseResultDto() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getPriceUnit() { return priceUnit; }
    public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }
    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }
    public String getCutoffTime() { return cutoffTime; }
    public void setCutoffTime(String cutoffTime) { this.cutoffTime = cutoffTime; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public boolean isPublishable() { return publishable; }
    public void setPublishable(boolean publishable) { this.publishable = publishable; }
}

