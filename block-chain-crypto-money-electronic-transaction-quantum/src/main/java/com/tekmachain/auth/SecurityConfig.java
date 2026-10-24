package com.tekmachain.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Web security: session-based login guarding the write endpoints, with the public,
 * read-only market/chain/explorer views left open so a logged-out visitor can still
 * look around. Only active in a servlet context, so the non-web unit-test contexts
 * are unaffected.
 *
 * <p><b>POC note:</b> CSRF is disabled and the login is a plain JSON endpoint over
 * the same-origin session cookie. A production deployment would enable CSRF (or use
 * short-lived tokens) and serve strictly over HTTPS.</p>
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return contextRepository;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthService authService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(authService);
        provider.setPasswordEncoder(encoder);
        return provider::authenticate;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // the embedded H2 console (default profile only, localhost-only) renders in frames
                .headers(h -> h.frameOptions(fo -> fo.sameOrigin()))
                .securityContext(sc -> sc.securityContextRepository(contextRepository))
                .authorizeHttpRequests(auth -> auth
                        // static app shell + local H2 data console
                        .requestMatchers("/", "/index.html", "/*.js", "/*.css", "/favicon.ico", "/h2-console/**").permitAll()
                        // auth endpoints, the guidance assistant (helps visitors before they sign in), and health probes
                        .requestMatchers("/api/auth/**", "/api/assistant/**", "/actuator/health/**").permitAll()
                        // public, read-only views (browse without an account)
                        .requestMatchers(HttpMethod.GET,
                                "/api/chain/**", "/api/blocks/**", "/api/market/**",
                                "/api/currencies/**", "/api/peers/**", "/api/pq/**").permitAll()
                        // public exchange market data — but NOT /account or /orders, which are personal
                        .requestMatchers(HttpMethod.GET,
                                "/api/exchange/pairs", "/api/exchange/orderbook", "/api/exchange/ticker",
                                "/api/exchange/trades", "/api/exchange/hotwallet").permitAll()
                        // everything else (wallets, send, mine, orders, deposits, profile, assistant) needs login
                        .anyRequest().authenticated())
                // for an API we return 401 rather than redirecting to a login page
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());
        return http.build();
    }
}
