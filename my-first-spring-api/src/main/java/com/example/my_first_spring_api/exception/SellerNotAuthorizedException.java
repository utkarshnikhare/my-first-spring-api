package com.example.my_first_spring_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class SellerNotAuthorizedException extends RuntimeException {
    public SellerNotAuthorizedException(String message) {
        super(message);
    }
}
