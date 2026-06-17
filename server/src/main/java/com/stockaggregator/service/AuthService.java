package com.stockaggregator.service;

import com.stockaggregator.config.JwtProperties;
import com.stockaggregator.error.BadRequestException;
import com.stockaggregator.error.ConflictException;
import com.stockaggregator.error.UnauthorizedException;
import com.stockaggregator.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Pattern;

/** Signup/login: password hashing, token issuing and verification. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LEN = 6;

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecretKey key;
    private final long ttlHours;

    public AuthService(UserRepository users, JwtProperties jwt) {
        this.users = users;
        this.ttlHours = jwt.getTtlHours();
        // SHA-256 the secret so any configured value yields a valid 256-bit HS256 key.
        this.key = Keys.hmacShaKeyFor(sha256(jwt.getSecret()));
    }

    /** Register a new user; returns a signed token. */
    public String signup(String email, String password) {
        Credentials cred = validate(email, password);
        boolean created = users.create(cred.email, encoder.encode(cred.password));
        if (!created) {
            throw new ConflictException("Email already registered: " + cred.email);
        }
        log.info("new user {}", cred.email);
        return makeToken(cred.email);
    }

    /** Verify credentials; returns a signed token. */
    public String login(String email, String password) {
        Credentials cred = validate(email, password);
        Optional<UserRepository.User> user = users.find(cred.email);
        if (user.isEmpty() || !encoder.matches(cred.password, user.get().passwordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return makeToken(cred.email);
    }

    /** Returns the email inside a valid token, or throws UnauthorizedException. */
    public String verify(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid token");
        }
    }

    private String makeToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    private static Credentials validate(String email, String password) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new BadRequestException("A valid email is required");
        }
        if (password == null || password.length() < MIN_PASSWORD_LEN) {
            throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LEN + " characters");
        }
        return new Credentials(email.trim().toLowerCase(), password);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record Credentials(String email, String password) {
    }
}
