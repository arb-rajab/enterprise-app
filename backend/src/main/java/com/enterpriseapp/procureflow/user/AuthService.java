package com.enterpriseapp.procureflow.user;

import com.enterpriseapp.procureflow.security.JwtService;
import com.enterpriseapp.procureflow.security.RefreshTokenService;
import com.enterpriseapp.procureflow.security.RefreshTokenService.IssuedRefreshToken;
import com.enterpriseapp.procureflow.security.RefreshTokenService.RotationResult;
import com.enterpriseapp.procureflow.user.dto.AuthResponse;
import com.enterpriseapp.procureflow.user.dto.LoginRequest;
import com.enterpriseapp.procureflow.user.dto.RefreshTokenRequest;
import com.enterpriseapp.procureflow.user.dto.RegisterRequest;
import com.enterpriseapp.procureflow.user.dto.UserSummary;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final UserService userService;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;

  public AuthResponse register(RegisterRequest request) {
    User user = userService.register(request);
    return issueTokenPairFor(user);
  }

  public AuthResponse login(LoginRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    User user = userService.findByEmail(request.email());
    return issueTokenPairFor(user);
  }

  /** Rotates a refresh token: the presented one is revoked and a new access/refresh pair issued. */
  public AuthResponse refresh(RefreshTokenRequest request) {
    RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
    return buildResponse(rotation.user(), rotation.refreshToken());
  }

  /** Revokes a refresh token so it can no longer be used to mint new access tokens. */
  public void logout(RefreshTokenRequest request) {
    refreshTokenService.revoke(request.refreshToken());
  }

  private AuthResponse issueTokenPairFor(User user) {
    return buildResponse(user, refreshTokenService.issue(user));
  }

  private AuthResponse buildResponse(User user, IssuedRefreshToken refreshToken) {
    Set<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
    String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
    return AuthResponse.bearer(
        accessToken,
        jwtService.accessTokenTtlSeconds(),
        refreshToken.rawToken(),
        UserSummary.from(user));
  }
}
