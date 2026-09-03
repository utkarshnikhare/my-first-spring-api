package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "kitchens")
public class Kitchen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "society")
    private String society;

    @Column(name = "building")
    private String building;

    @Column(name = "whatsapp_link")
    private String whatsappLink;

    @Column(name = "instagram_link")
    private String instagramLink;

    @Column(name = "gallery_images", columnDefinition = "TEXT")
    private String galleryImages;

    @Column(name = "upi_id")
    private String upiId;

    @Column
    private Double rating = 0.0;

    @Column(name = "available_today")
    private Boolean availableToday = true;

    @Column(name = "order_deadline")
    private String orderDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Kitchen() {}

    public Kitchen(String name, String displayName, String description, String imageUrl, User seller) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.imageUrl = imageUrl;
        this.seller = seller;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getSociety() { return society; }
    public void setSociety(String society) { this.society = society; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public String getWhatsappLink() { return whatsappLink; }
    public void setWhatsappLink(String whatsappLink) { this.whatsappLink = whatsappLink; }
    public String getInstagramLink() { return instagramLink; }
    public void setInstagramLink(String instagramLink) { this.instagramLink = instagramLink; }
    public String getGalleryImages() { return galleryImages; }
    public void setGalleryImages(String galleryImages) { this.galleryImages = galleryImages; }
    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Boolean getAvailableToday() { return availableToday; }
    public void setAvailableToday(Boolean availableToday) { this.availableToday = availableToday; }
    public String getOrderDeadline() { return orderDeadline; }
    public void setOrderDeadline(String orderDeadline) { this.orderDeadline = orderDeadline; }
    public User getSeller() { return seller; }
    public void setSeller(User seller) { this.seller = seller; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
