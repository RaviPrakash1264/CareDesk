package com.project.caredesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class CareDeskApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareDeskApplication.class, args);
	}

}
