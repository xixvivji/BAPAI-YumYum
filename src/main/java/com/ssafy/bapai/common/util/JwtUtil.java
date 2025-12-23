package com.ssafy.bapai.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // Access Token 생성용 (기본값)
    public String createAccessToken(Long userId, String role) {
        return createToken(userId, role, accessExpiration);
    }

    // Refresh Token 생성용
    public String createRefreshToken(Long userId, String role) {
        return createToken(userId, role, refreshExpiration);
    }

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }


    // 2. 만료 시간 직접 지정 (리프레시 토큰 등에 사용)
    public String createToken(Long userId, String role, long expireTime) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expireTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 3. 토큰에서 UserId 꺼내기
    public Long getUserId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    // 4. 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            parseClaims(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            System.out.println("🚨 토큰 만료됨: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.out.println("🚨 유효하지 않은 토큰: " + e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getExpiration(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Date expiration = parseClaims(token).getExpiration();
        long now = new Date().getTime();
        return (expiration.getTime() - now);
    }
}