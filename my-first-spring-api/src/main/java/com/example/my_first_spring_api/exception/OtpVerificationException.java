package com.example.my_first_spring_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class OtpVerificationException extends RuntimeException {
    public OtpVerificationException(String message) {
        super(message);
    }
}
