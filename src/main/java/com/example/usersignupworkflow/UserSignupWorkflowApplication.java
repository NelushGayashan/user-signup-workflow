package com.example.usersignupworkflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class UserSignupWorkflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserSignupWorkflowApplication.class, args);
	}
}