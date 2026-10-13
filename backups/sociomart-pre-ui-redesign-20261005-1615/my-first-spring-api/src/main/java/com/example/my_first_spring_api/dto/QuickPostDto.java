package com.example.my_first_spring_api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class QuickPostDto {
    private Long id;
    private String message;
    private String imageData;
    private LocalDate postedDate;
    private LocalDateTime createdAt;

    public QuickPostDto() {}
    public QuickPostDto(Long id, String message, String imageData, LocalDate postedDate, LocalDateTime createdAt) {
        this.id = id; this.message = message; this.imageData = imageData;
        this.postedDate = postedDate; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public String getMessage() { return message; }
    public String getImageData() { return imageData; }
    public LocalDate getPostedDate() { return postedDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
