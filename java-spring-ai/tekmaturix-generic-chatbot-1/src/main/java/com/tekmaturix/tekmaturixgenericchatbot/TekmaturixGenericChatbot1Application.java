package com.tekmaturix.tekmaturixgenericchatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@SpringBootApplication
@EnableWebSecurity
public class TekmaturixGenericChatbot1Application {

	public static void main(String[] args) {
		SpringApplication.run(TekmaturixGenericChatbot1Application.class, args);
	}

}
