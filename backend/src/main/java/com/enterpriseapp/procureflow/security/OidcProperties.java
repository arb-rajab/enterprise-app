package com.enterpriseapp.procureflow.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.oidc")
public class OidcProperties {

  /**
   * Where the browser is sent, as a top-level navigation, after a successful or failed OIDC login.
   * The app JWT (or an error flag) is appended as a URL fragment so it never reaches this server's
   * own access logs or a downstream {@code Referer} header - see ADR-0005.
   */
  private String frontendRedirectUri = "http://localhost:4200/sso/callback";
}
