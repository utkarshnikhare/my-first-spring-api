package com.example.my_first_spring_api.model;

import com.example.my_first_spring_api.model.Kitchen;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A seller's lightweight Today-only WhatsApp post. It is intentionally not a
 * Product: Quick Posts are announcements, not structured/orderable offerings.
 */
@Entity
@Table(name = "quick_posts")
public class QuickPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kitchen_id", nullable = false)
    private Kitchen kitchen;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Optional client-selected image represented as a validated data URL. */
    @Column(name = "image_data", columnDefinition = "TEXT")
    private String imageData;

    /** Server-owned; V1 Quick Posts are always dated today. */
    @Column(name = "posted_date", nullable = false)
    private LocalDate postedDate;

    @Column(name = "request_id", nullable = false, unique = true, length = 80)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected QuickPost() {}

    public QuickPost(Kitchen kitchen, String message, String imageData,
                     LocalDate postedDate, String requestId) {
        this.kitchen = kitchen;
        this.message = message;
        this.imageData = imageData;
        this.postedDate = postedDate;
        this.requestId = requestId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Kitchen getKitchen() { return kitchen; }
    public String getMessage() { return message; }
    public String getImageData() { return imageData; }
    public LocalDate getPostedDate() { return postedDate; }
    public String getRequestId() { return requestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
