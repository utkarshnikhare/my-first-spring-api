package com.example.my_first_spring_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class BuyerNotAuthenticatedException extends RuntimeException {
    public BuyerNotAuthenticatedException(String message) {
        super(message);
    }
}
