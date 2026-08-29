package com.example.my_first_spring_api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private static final Logger logger = LoggerFactory.getLogger(HelloController.class);

    @Value("${app.message:Default Hello}")
    private String appMessage;

    @GetMapping("/")
    public String home() {
        logger.info("Home endpoint accessed with message: {}", appMessage);
        return appMessage;
    }

    @PostMapping("/user")
    public UserDto createUser(@Valid @RequestBody UserDto userDto) {
        logger.info("Creating user with email: {}", userDto.getEmail());
        return userDto;
    }
}