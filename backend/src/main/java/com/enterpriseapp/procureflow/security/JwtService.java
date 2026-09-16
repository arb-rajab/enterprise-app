package com.enterpriseapp.procureflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates signed, stateless JWT access tokens.
 *
 * <p>Roles are embedded as a claim so that every request can be authorized from the token alone,
 * without a database lookup per request. See ADR-0002 for the rationale behind this choice over
 * server-side sessions.
 */
@Service
public class JwtService {

  private static final String ROLES_CLAIM = "roles";

  private final JwtProperties properties;
  private final SecretKey signingKey;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
    if (properties.getSecret() == null || properties.getSecret().isBlank()) {
      throw new IllegalStateException(
          "app.security.jwt.secret must be configured (set the JWT_SECRET environment variable)");
    }
    byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException(
          "app.security.jwt.secret must be at least 32 bytes for HS256");
    }
    this.signingKey = Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateAccessToken(Long userId, String email, Set<String> roles) {
    Instant now = Instant.now();
    Instant expiry = now.plus(properties.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES);
    return Jwts.builder()
        .issuer(properties.getIssuer())
        .subject(email)
        .claim("uid", userId)
        .claim(ROLES_CLAIM, roles)
        .issuedAt(java.util.Date.from(now))
        .expiration(java.util.Date.from(expiry))
        .signWith(signingKey)
        .compact();
  }

  public long accessTokenTtlSeconds() {
    return properties.getAccessTokenTtlMinutes() * 60;
  }

  public Claims parseClaims(String token) throws JwtException {
    return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
  }

  public String extractEmail(Claims claims) {
    return claims.getSubject();
  }

  public List<String> extractRoles(Claims claims) {
    Object raw = claims.get(ROLES_CLAIM);
    if (raw instanceof Collection<?> collection) {
      return collection.stream().map(String::valueOf).collect(Collectors.toList());
    }
    return List.of();
  }
}
