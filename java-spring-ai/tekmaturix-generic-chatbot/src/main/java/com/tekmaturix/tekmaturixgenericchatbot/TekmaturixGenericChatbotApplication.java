package com.tekmaturix.tekmaturixgenericchatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@SpringBootApplication
@EnableWebSecurity
public class TekmaturixGenericChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(TekmaturixGenericChatbotApplication.class, args);
	}

}
