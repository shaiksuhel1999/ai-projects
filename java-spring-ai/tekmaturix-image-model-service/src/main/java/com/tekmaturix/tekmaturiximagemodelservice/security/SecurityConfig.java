package com.tekmaturix.tekmaturiximagemodelservice.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		UserDetails user = User.builder().username("user").password(passwordEncoder.encode("user123")).roles("USER")
				.build();
		UserDetails admin = User.builder().username("admin").password(passwordEncoder.encode("admin123")).roles("ADMIN")
				.build();
		return new InMemoryUserDetailsManager(user, admin);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
		httpSecurity.csrf(csrf -> csrf.disable()) // important for POST APIs
				.authorizeHttpRequests(
						auth -> auth.requestMatchers("/actuator/**").permitAll().requestMatchers("/api/v1/common/**")
								.permitAll().requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
								.requestMatchers("/api/v1/user/**").hasRole("USER").anyRequest().authenticated())
				.httpBasic(withDefaults());

		return httpSecurity.build();
	}

}
