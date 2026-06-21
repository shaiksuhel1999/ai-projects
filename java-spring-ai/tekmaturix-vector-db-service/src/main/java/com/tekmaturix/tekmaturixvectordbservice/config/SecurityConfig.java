package com.tekmaturix.tekmaturixvectordbservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		UserDetails userDetails = User.builder().username("user").password(passwordEncoder.encode("user")).roles("USER")
				.build();
		UserDetails adminDetails = User.builder().username("admin").password(passwordEncoder.encode("admin"))
				.roles("ADMIN").build();
		return new InMemoryUserDetailsManager(userDetails, adminDetails);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) {
		return httpSecurity.csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(
						auth -> auth.requestMatchers("/actuator/**").permitAll().requestMatchers("/api/v1/common/**")
								.permitAll().requestMatchers("/api/v1/user/**").hasRole("USER")
								.requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
				.httpBasic(withDefaults()).build();
	}

}
