package com.enterpriseapp.procureflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Split out of {@link SecurityConfig} so that beans which need a {@link PasswordEncoder} (e.g.
 * {@code UserService}, transitively required by {@code OidcAuthenticationSuccessHandler}, which
 * {@code SecurityConfig} constructor-injects) don't create a circular dependency on {@code
 * SecurityConfig} itself having to finish being constructed first just to run its {@code @Bean}
 * method.
 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
