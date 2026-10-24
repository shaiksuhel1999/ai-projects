package com.tekmachain.auth;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and account lookup. Implements Spring Security's
 * {@link org.springframework.security.core.userdetails.UserDetailsService} contract
 * so the framework can authenticate a login against our stored BCrypt hashes.
 */
@Service
public class AuthService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Create a new account. Throws if the username is taken or the input is invalid. */
    @Transactional
    public User register(String username, String rawPassword, String displayName) {
        String u = username == null ? "" : username.trim();
        if (u.length() < 3 || u.length() > 40 || !u.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Username must be 3–40 chars: letters, digits, . _ -");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (users.existsByUsername(u)) {
            throw new IllegalStateException("That username is already taken");
        }
        String name = (displayName == null || displayName.isBlank()) ? u : displayName.trim();
        return users.save(new User(u, encoder.encode(rawPassword), name));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(AuthorityUtils.createAuthorityList("ROLE_USER"))
                .build();
    }
}
