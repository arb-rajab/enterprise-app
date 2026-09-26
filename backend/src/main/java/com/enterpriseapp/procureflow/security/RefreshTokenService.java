package com.enterpriseapp.procureflow.security;

import com.enterpriseapp.procureflow.common.exception.InvalidRefreshTokenException;
import com.enterpriseapp.procureflow.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, and revokes opaque refresh tokens (see ADR-0006).
 *
 * <p>Unlike access tokens, refresh tokens are checked against the database, which is what makes
 * them revocable: only the raw token's SHA-256 hash is ever persisted (the raw value is
 * high-entropy random data, not a low-entropy secret, so a fast hash is appropriate here - this is
 * not password storage). Rotation always issues a new token and revokes the presented one, so a
 * refresh token is single-use; presenting an already-rotated or revoked token is rejected.
 *
 * <p>Rotation also re-checks the token owner's account status on every call, not just at initial
 * login - an already-issued refresh token (from either login path: password or OIDC/SSO, ADR-0008)
 * must stop renewing the moment its user is deactivated, the same guarantee password login gets for
 * free from {@code AuthenticationManager} on every fresh login attempt.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;

  @Transactional
  public IssuedRefreshToken issue(User user) {
    String rawToken = generateRawToken();
    Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS);
    RefreshToken entity =
        RefreshToken.builder().user(user).tokenHash(hash(rawToken)).expiresAt(expiresAt).build();
    refreshTokenRepository.save(entity);
    return new IssuedRefreshToken(rawToken, expiresAt);
  }

  /** Validates and consumes {@code rawToken}, atomically replacing it with a new one. */
  @Transactional
  public RotationResult rotate(String rawToken) {
    RefreshToken existing =
        refreshTokenRepository
            .findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
    if (!existing.isActive(Instant.now())) {
      throw new InvalidRefreshTokenException("Refresh token has expired or been revoked");
    }
    existing.setRevokedAt(Instant.now());
    if (!existing.getUser().isActive()) {
      throw new InvalidRefreshTokenException("Refresh token is invalid");
    }
    return new RotationResult(existing.getUser(), issue(existing.getUser()));
  }

  /** Revokes {@code rawToken} if it exists and is still active; idempotent otherwise. */
  @Transactional
  public void revoke(String rawToken) {
    refreshTokenRepository
        .findByTokenHash(hash(rawToken))
        .filter(token -> token.isActive(Instant.now()))
        .ifPresent(token -> token.setRevokedAt(Instant.now()));
  }

  private String generateRawToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available on this JVM", e);
    }
  }

  public record IssuedRefreshToken(String rawToken, Instant expiresAt) {}

  public record RotationResult(User user, IssuedRefreshToken refreshToken) {}
}
