package com.tekmaturix.tekmaturixgenericchatbot.config;

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
public class TekmaturixGenericChatbot1SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {

		UserDetails userDetail = User.builder().username("user").password(passwordEncoder.encode("user")).roles("USER")
				.build();

		UserDetails adminDetail = User.builder().username("admin").password(passwordEncoder.encode("admin"))
				.roles("ADMIN").build();

		return new InMemoryUserDetailsManager(userDetail, adminDetail);

	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) {
		httpSecurity.cors(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(
						auth -> auth.requestMatchers("/actuator/**").permitAll().requestMatchers("/api/v1/common/**")
								.permitAll().requestMatchers("/api/v1/user/**").hasAllRoles("USER")
								.requestMatchers("/api/v1/admin/**").hasAllRoles("ADMIN").anyRequest().authenticated())
				.csrf(AbstractHttpConfigurer::disable).httpBasic(withDefaults());
		return httpSecurity.build();
	}

}
