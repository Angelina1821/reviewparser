package com.example.reviewparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReviewparserApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReviewparserApplication.class, args);
	}

}
