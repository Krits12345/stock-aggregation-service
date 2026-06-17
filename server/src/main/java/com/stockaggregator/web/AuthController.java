package com.stockaggregator.web;

import com.stockaggregator.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Login/signup endpoints used by the web client. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
public class AuthController {

    /** Request body for both signup and login. */
    public record AuthRequest(String email, String password) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/signup")
    @Operation(summary = "Register a new user and return a token")
    public Map<String, String> signup(@RequestBody AuthRequest body) {
        String token = auth.signup(body.email(), body.password());
        return Map.of("token", token, "email", normalize(body.email()));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and return a token")
    public Map<String, String> login(@RequestBody AuthRequest body) {
        String token = auth.login(body.email(), body.password());
        return Map.of("token", token, "email", normalize(body.email()));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
