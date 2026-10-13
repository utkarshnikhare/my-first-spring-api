package com.example.my_first_spring_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyFirstSpringApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyFirstSpringApiApplication.class, args);
	}

}
