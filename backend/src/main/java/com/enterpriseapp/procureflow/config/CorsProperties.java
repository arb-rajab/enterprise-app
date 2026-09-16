package com.enterpriseapp.procureflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

  /** Comma-separated list of origins allowed to call the API (e.g. the Angular dev server). */
  private String allowedOrigins = "http://localhost:4200";
}
