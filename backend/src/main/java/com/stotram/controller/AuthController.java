package com.stotram.controller;

import com.stotram.dto.AdminSessionResponse;
import com.stotram.dto.LoginRequest;
import com.stotram.repository.AdminUserRepository;
import com.stotram.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin login for the React panel. Logout is handled by Spring Security itself
 * ({@code POST /admin/logout}, see SecurityConfig).
 */
@RestController
@RequestMapping("/admin")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final LoginRateLimiter rateLimiter;
    private final AdminUserRepository admins;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository,
                          LoginRateLimiter rateLimiter,
                          AdminUserRepository admins) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.rateLimiter = rateLimiter;
        this.admins = admins;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest body,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        // Behind Cloudflare this becomes the CF-Connecting-IP header — only trust that once
        // the origin accepts traffic from Cloudflare alone, or it can be spoofed.
        String client = request.getRemoteAddr();

        if (rateLimiter.isBlocked(client)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in 15 minutes.");
        }
        if (body == null || isBlank(body.email()) || isBlank(body.password())) {
            return error(HttpStatus.BAD_REQUEST, "Email and password are required.");
        }

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email().trim(), body.password()));
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(client);
            // Same message whether the email or the password was wrong.
            return error(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
        }
        rateLimiter.recordSuccess(client);

        // New session id on login, so a session id planted before login is worthless after.
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        admins.findByEmailIgnoreCase(auth.getName()).ifPresent(admin -> {
            admin.setLastLoginAt(OffsetDateTime.now());
            admins.save(admin);
        });

        return ResponseEntity.ok(new AdminSessionResponse(auth.getName()));
    }

    /** Who is logged in. 401 (from SecurityConfig) when nobody is. */
    @GetMapping("/me")
    public AdminSessionResponse me(Authentication auth) {
        return new AdminSessionResponse(auth.getName());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
