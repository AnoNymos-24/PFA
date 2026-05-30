package com.smartintern.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_TIME = 86400000; // 24h

    public String generateToken(String email, String role, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("role", role);
        claims.put("userId", userId);
        return createToken(claims, email);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSignKey() {
        byte[] keyBytes = java.util.Base64.getDecoder().decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception e) {
            return true; // si on ne peut pas lire l'expiration → token invalide
        }
    }

    // ✅ CORRECTION : retourne boolean primitif (pas Boolean objet)
    // pour éviter NullPointerException dans JwtAuthFilter
    public boolean validateToken(String token, String email) {
        try {
            final String extractedEmail = extractEmail(token);
            return extractedEmail != null
                && extractedEmail.equals(email)
                && !isTokenExpired(token);
        } catch (Exception e) {
            System.err.println("=== validateToken exception : " + e.getMessage());
            return false;
        }
    }

    // ✅ CORRECTION : isValid utilise validateToken pour cohérence
    public boolean isValid(String token) {
        try {
            String email = extractEmail(token);
            return email != null && !isTokenExpired(token);
        } catch (Exception e) {
            System.err.println("=== isValid exception : " + e.getMessage());
            return false;
        }
    }
}