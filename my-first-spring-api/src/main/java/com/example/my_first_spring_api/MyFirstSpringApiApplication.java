package com.example.my_first_spring_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyFirstSpringApiApplication {

	// Runs when the class loads (main() and @SpringBootTest alike), before any bean or clock read.
	static {
		AppTimeZone.apply();
	}

	public static void main(String[] args) {
		SpringApplication.run(MyFirstSpringApiApplication.class, args);
	}

}
