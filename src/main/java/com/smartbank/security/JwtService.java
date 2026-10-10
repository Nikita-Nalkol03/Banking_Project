package com.smartbank.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import java.util.UUID;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;




@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {

        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(UserDetails userDetails) {

        return generateToken(
                userDetails,
                accessTokenExpiration,
                "ACCESS"
        );
    }

    public String generateRefreshToken(UserDetails userDetails) {

        return generateToken(
                userDetails,
                refreshTokenExpiration,
                "REFRESH"
        );
    }


private String generateToken(
        UserDetails userDetails,
        long expiration,
        String tokenType) {

    Date now = new Date();

    Date expiryDate = new Date(
            now.getTime() + expiration
    );

    return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userDetails.getUsername())
            .claim("type", tokenType)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(signingKey)
            .compact();
}
    public String extractUsername(String token) {

        return extractAllClaims(token)
                .getSubject();
    }

    public String extractTokenType(String token) {

        return extractAllClaims(token)
                .get("type", String.class);
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails) {

        String username = extractUsername(token);

        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {

        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpiration() {

        return accessTokenExpiration;
    }

public long getRefreshTokenExpiration() {
    return refreshTokenExpiration;
}

public String hashToken(String token) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(
                token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
        throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
}
}