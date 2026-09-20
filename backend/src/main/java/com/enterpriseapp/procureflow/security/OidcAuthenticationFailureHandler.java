package com.enterpriseapp.procureflow.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** Sends a failed OIDC login back to the SPA instead of Spring Security's default error page. */
@Component
@RequiredArgsConstructor
public class OidcAuthenticationFailureHandler implements AuthenticationFailureHandler {

  private final OidcProperties oidcProperties;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    response.sendRedirect(
        oidcProperties.getFrontendRedirectUri()
            + "#error="
            + URLEncoder.encode("oidc_login_failed", StandardCharsets.UTF_8));
  }
}
