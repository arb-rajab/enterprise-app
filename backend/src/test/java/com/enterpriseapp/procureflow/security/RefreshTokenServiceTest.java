package com.enterpriseapp.procureflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterpriseapp.procureflow.common.exception.InvalidRefreshTokenException;
import com.enterpriseapp.procureflow.security.RefreshTokenService.IssuedRefreshToken;
import com.enterpriseapp.procureflow.security.RefreshTokenService.RotationResult;
import com.enterpriseapp.procureflow.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;

  private RefreshTokenService service;
  private User user;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("unit-test-signing-secret-at-least-32-bytes-long");
    properties.setRefreshTokenTtlDays(7);
    service = new RefreshTokenService(refreshTokenRepository, properties);

    user = User.builder().email("user@test.local").firstName("U").lastName("Ser").build();
    user.setId(1L);
  }

  @Test
  void issueStoresOnlyTheHashOfTheRawToken() {
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

    IssuedRefreshToken issued = service.issue(user);

    verify(refreshTokenRepository).save(captor.capture());
    RefreshToken saved = captor.getValue();
    assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(issued.rawToken()));
    assertThat(saved.getTokenHash()).isNotEqualTo(issued.rawToken());
    assertThat(saved.getUser()).isEqualTo(user);
    assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
    assertThat(issued.expiresAt()).isEqualTo(saved.getExpiresAt());
  }

  @Test
  void rotateRevokesTheOldTokenAndIssuesADistinctNewOne() {
    RefreshToken stored = activeStoredToken("raw-token-1");
    when(refreshTokenRepository.findByTokenHash(sha256Hex("raw-token-1")))
        .thenReturn(Optional.of(stored));

    RotationResult rotation = service.rotate("raw-token-1");

    assertThat(stored.getRevokedAt()).isNotNull();
    assertThat(rotation.user()).isEqualTo(user);
    assertThat(rotation.refreshToken().rawToken()).isNotEqualTo("raw-token-1");
  }

  @Test
  void rotateRejectsAnUnknownToken() {
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rotate("does-not-exist"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void rotateRejectsAnAlreadyRevokedToken() {
    RefreshToken stored = activeStoredToken("raw-token-2");
    stored.setRevokedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
    when(refreshTokenRepository.findByTokenHash(sha256Hex("raw-token-2")))
        .thenReturn(Optional.of(stored));

    assertThatThrownBy(() -> service.rotate("raw-token-2"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void rotateRejectsATokenWhoseUserHasSinceBeenDeactivated() {
    user.setActive(false);
    RefreshToken stored = activeStoredToken("raw-token-deactivated");
    when(refreshTokenRepository.findByTokenHash(sha256Hex("raw-token-deactivated")))
        .thenReturn(Optional.of(stored));

    assertThatThrownBy(() -> service.rotate("raw-token-deactivated"))
        .isInstanceOf(InvalidRefreshTokenException.class);

    // The presented token must still be consumed (single-use), not left usable for a later retry.
    assertThat(stored.getRevokedAt()).isNotNull();
  }

  @Test
  void rotateRejectsAnExpiredToken() {
    RefreshToken stored = activeStoredToken("raw-token-3");
    stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
    when(refreshTokenRepository.findByTokenHash(sha256Hex("raw-token-3")))
        .thenReturn(Optional.of(stored));

    assertThatThrownBy(() -> service.rotate("raw-token-3"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void revokeMarksAnActiveTokenRevoked() {
    RefreshToken stored = activeStoredToken("raw-token-4");
    when(refreshTokenRepository.findByTokenHash(sha256Hex("raw-token-4")))
        .thenReturn(Optional.of(stored));

    service.revoke("raw-token-4");

    assertThat(stored.getRevokedAt()).isNotNull();
  }

  @Test
  void revokeOfAnUnknownTokenIsANoOp() {
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    service.revoke("does-not-exist");
  }

  private RefreshToken activeStoredToken(String rawToken) {
    return RefreshToken.builder()
        .user(user)
        .tokenHash(sha256Hex(rawToken))
        .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
        .build();
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
