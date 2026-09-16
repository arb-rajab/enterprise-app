package com.enterpriseapp.procureflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtProperties properties() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("unit-test-signing-secret-at-least-32-bytes-long");
    properties.setAccessTokenTtlMinutes(30);
    properties.setIssuer("procureflow-test");
    return properties;
  }

  @Test
  void generatedTokenRoundTripsEmailAndRoles() {
    JwtService jwtService = new JwtService(properties());

    String token = jwtService.generateAccessToken(42L, "user@test.local", Set.of("ROLE_EMPLOYEE"));
    Claims claims = jwtService.parseClaims(token);

    assertThat(jwtService.extractEmail(claims)).isEqualTo("user@test.local");
    assertThat(jwtService.extractRoles(claims)).containsExactly("ROLE_EMPLOYEE");
    assertThat(claims.get("uid", Integer.class)).isEqualTo(42);
  }

  @Test
  void rejectsTokenSignedWithADifferentSecret() {
    JwtService issuer = new JwtService(properties());
    String token = issuer.generateAccessToken(1L, "user@test.local", Set.of("ROLE_EMPLOYEE"));

    JwtProperties otherProperties = properties();
    otherProperties.setSecret("a-completely-different-signing-secret-32bytes");
    JwtService verifier = new JwtService(otherProperties);

    assertThatThrownBy(() -> verifier.parseClaims(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void refusesToStartWithoutASecret() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(null);

    assertThatThrownBy(() -> new JwtService(properties)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesASecretShorterThan32Bytes() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("too-short");

    assertThatThrownBy(() -> new JwtService(properties)).isInstanceOf(IllegalStateException.class);
  }
}
