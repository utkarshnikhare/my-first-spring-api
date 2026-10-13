package com.example.my_first_spring_api.dto;

import java.time.LocalDateTime;

public class NotificationEventDto {
    private Long id;
    private String title;
    private String body;
    private LocalDateTime createdAt;
    private Boolean delivered;

    public NotificationEventDto() {}

    public NotificationEventDto(Long id, String title, String body, LocalDateTime createdAt, Boolean delivered) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
        this.delivered = delivered;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getDelivered() { return delivered; }
    public void setDelivered(Boolean delivered) { this.delivered = delivered; }
}
