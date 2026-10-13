package com.example.my_first_spring_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class KitchenNotFoundException extends RuntimeException {
    public KitchenNotFoundException(String name) {
        super("Kitchen not found: " + name);
    }

    public KitchenNotFoundException(Long id) {
        super("Kitchen not found with id: " + id);
    }
}
