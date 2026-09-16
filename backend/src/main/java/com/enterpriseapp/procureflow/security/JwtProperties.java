package com.enterpriseapp.procureflow.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

  /** Base64 or plain secret used to sign HS256 tokens. Must be at least 256 bits. */
  private String secret;

  private long accessTokenTtlMinutes = 30;

  private long refreshTokenTtlDays = 7;

  private String issuer = "procureflow";
}
