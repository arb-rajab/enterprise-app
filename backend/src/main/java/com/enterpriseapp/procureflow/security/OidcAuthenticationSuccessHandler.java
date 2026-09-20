package com.enterpriseapp.procureflow.security;

import com.enterpriseapp.procureflow.user.User;
import com.enterpriseapp.procureflow.user.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Finishes an OIDC login by provisioning/linking the local {@link User} (see ADR-0008) and minting
 * the same access/refresh token pair {@code AuthService} hands out for password logins - including
 * a real, revocable {@link RefreshTokenService} refresh token (ADR-0006), not just an access token
 * - then sending the browser back to the SPA with them. From the frontend's point of view, OIDC
 * login and password login end the same way: an {@code AuthResponse}-shaped token pair it can store
 * and start sending as {@code Authorization: Bearer <token>}.
 */
@Component
@RequiredArgsConstructor
public class OidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private final UserService userService;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final OidcProperties oidcProperties;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
        || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      throw new ServletException(
          "OidcAuthenticationSuccessHandler requires an OIDC OAuth2AuthenticationToken");
    }

    String provider = oauthToken.getAuthorizedClientRegistrationId();
    String email = oidcUser.getEmail();
    if (email == null || email.isBlank()) {
      redirectWithError(response, "oidc_email_missing");
      return;
    }

    User user =
        userService.findOrProvisionForOidc(
            provider, oidcUser.getSubject(), email, firstNameOf(oidcUser), lastNameOf(oidcUser));

    Set<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
    String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
    String refreshToken = refreshTokenService.issue(user).rawToken();

    String redirectUri =
        oidcProperties.getFrontendRedirectUri()
            + "#token="
            + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
            + "&refreshToken="
            + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
            + "&expiresIn="
            + jwtService.accessTokenTtlSeconds();
    response.sendRedirect(redirectUri);
  }

  private void redirectWithError(HttpServletResponse response, String reason) throws IOException {
    response.sendRedirect(
        oidcProperties.getFrontendRedirectUri()
            + "#error="
            + URLEncoder.encode(reason, StandardCharsets.UTF_8));
  }

  private static String firstNameOf(OidcUser oidcUser) {
    String givenName = oidcUser.getGivenName();
    return givenName != null && !givenName.isBlank() ? givenName : oidcUser.getEmail();
  }

  private static String lastNameOf(OidcUser oidcUser) {
    String familyName = oidcUser.getFamilyName();
    return familyName != null && !familyName.isBlank() ? familyName : "SSO";
  }
}
