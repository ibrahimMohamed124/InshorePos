package com.inshore.shared.security;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtProvider {

    private final SecretKey SECRET_KEY;

    public JwtProvider(@Value("${jwt.secret}") String jwtSecret) {
        this.SECRET_KEY = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {

        Collection<? extends GrantedAuthority> authorities =
                authentication.getAuthorities();

        String roles = populateRoles(authorities);

        return Jwts.builder()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .claim("email", authentication.getName())
                .claim("authorities", roles)
                .signWith(SECRET_KEY)
                .compact();
    }

    public String getEmailFromToken(String token) {

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Claims claims = Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getPayload();

        return claims.get("email", String.class);
    }

    private String populateRoles(
            Collection<? extends GrantedAuthority> authorities) {

        Set<String> roles = new HashSet<>();

        for (GrantedAuthority authority : authorities) {
            roles.add(authority.getAuthority());
        }

        return String.join(",", roles);
    }
}