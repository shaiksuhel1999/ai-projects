package com.tekmachain.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * JSON authentication over a same-origin session cookie: register, login, logout,
 * and "who am I". On success the authenticated context is written to the HTTP
 * session so subsequent requests from the browser are recognised.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {

    private final AuthService auth;
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository;
    private final UserRepository users;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService auth, AuthenticationManager authManager,
                          SecurityContextRepository contextRepository, UserRepository users,
                          LoginRateLimiter rateLimiter) {
        this.auth = auth;
        this.authManager = authManager;
        this.contextRepository = contextRepository;
        this.users = users;
        this.rateLimiter = rateLimiter;
    }

    public record Credentials(String username, String password, String displayName) {
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Credentials body,
                                        HttpServletRequest request, HttpServletResponse response) {
        User user = auth.register(body.username(), body.password(), body.displayName());
        establishSession(user.getUsername(), body.password(), request, response);
        return me();
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Credentials body,
                                     HttpServletRequest request, HttpServletResponse response) {
        rateLimiter.assertNotLocked(body.username());
        try {
            establishSession(body.username(), body.password(), request, response);
        } catch (org.springframework.security.core.AuthenticationException e) {
            rateLimiter.recordFailure(body.username());
            throw e;
        }
        rateLimiter.recordSuccess(body.username());
        return me();
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("authenticated", false);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            return Map.of("authenticated", false);
        }
        String displayName = users.findByUsername(a.getName())
                .map(User::getDisplayName).orElse(a.getName());
        return Map.of("authenticated", true, "username", a.getName(), "displayName", displayName);
    }

    /** Authenticate credentials and persist the resulting context to the session. */
    private void establishSession(String username, String password,
                                  HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        // Rotate the session id on login to defend against session fixation (we
        // authenticate manually, so Spring's login filter doesn't do it for us).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
