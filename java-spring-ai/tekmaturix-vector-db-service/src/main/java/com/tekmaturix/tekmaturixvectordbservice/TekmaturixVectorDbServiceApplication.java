package com.tekmaturix.tekmaturixvectordbservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@SpringBootApplication
@EnableWebSecurity
public class TekmaturixVectorDbServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TekmaturixVectorDbServiceApplication.class, args);
	}

}
