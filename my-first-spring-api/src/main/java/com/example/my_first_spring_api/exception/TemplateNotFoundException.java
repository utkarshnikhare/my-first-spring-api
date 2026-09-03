package com.example.my_first_spring_api.exception;

/** Thrown when a seller template does not exist — mapped to HTTP 404. */
public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(Long templateId) {
        super("Template not found with id: " + templateId);
    }
}
