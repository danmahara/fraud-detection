package com.fraud.detection.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    // Spring injects these two from application.properties (the app.jwt.* lines).
    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // Turn the secret text into a signing key. Must be >= 32 chars for HS256.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Build a signed token whose "subject" is the user's email.
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username) // who this token belongs to
                .issuedAt(now) // when it was created
                .expiration(expiry) // when it expires
                .signWith(key) // sign it so nobody can tamper with it
                .compact(); // produce the final token string
    }

    // Reads the email back out of a token. Also verifies the signature and
    // expiry along the way (throws if the token is bad). You'll use this in 3b.
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}