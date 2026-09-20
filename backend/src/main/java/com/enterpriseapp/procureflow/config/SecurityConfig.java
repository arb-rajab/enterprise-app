package com.enterpriseapp.procureflow.config;

import com.enterpriseapp.procureflow.security.JwtAuthenticationFilter;
import com.enterpriseapp.procureflow.security.JwtProperties;
import com.enterpriseapp.procureflow.security.JwtService;
import com.enterpriseapp.procureflow.security.OidcAuthenticationFailureHandler;
import com.enterpriseapp.procureflow.security.OidcAuthenticationSuccessHandler;
import com.enterpriseapp.procureflow.security.OidcProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security configuration: two independent filter chains, so the OIDC login handshake (which needs
 * an {@code HttpSession} to hold its redirect state/nonce, per the OAuth2 spec) can't weaken the
 * existing stateless JWT API - see {@code
 * docs/project-memory/adr/0008-oidc-sso-identity-linking.md}.
 *
 * <ul>
 *   <li>{@link #oidcFilterChain} ({@code @Order(1)}), matching only {@code /oauth2/**} and {@code
 *       /login/**}: Spring's {@code oauth2Login}, sessions allowed.
 *   <li>{@link #apiFilterChain} ({@code @Order(2)}, everything else): unchanged from before OIDC
 *       existed - stateless, {@link JwtAuthenticationFilter} only.
 * </ul>
 *
 * <p>See {@code docs/project-memory/adr/0002-jwt-based-authentication.md} for why the API chain
 * uses stateless JWTs instead of server-side sessions, and {@code
 * docs/project-memory/adr/0003-frontend-backend-integration.md} for how the Angular dev server is
 * allowed to call this API across origins in local development.
 *
 * <p>{@link PasswordEncoderConfig} holds the {@code PasswordEncoder} bean separately from this
 * class specifically to avoid a circular dependency: this class now constructor-injects {@link
 * OidcAuthenticationSuccessHandler}, which needs {@code UserService}, which needs a {@code
 * PasswordEncoder} - if that bean were defined here, Spring couldn't finish constructing this class
 * before running its own {@code @Bean} method.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, OidcProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtService jwtService;
  private final CorsProperties corsProperties;
  private final OidcAuthenticationSuccessHandler oidcAuthenticationSuccessHandler;
  private final OidcAuthenticationFailureHandler oidcAuthenticationFailureHandler;

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  @Order(1)
  public SecurityFilterChain oidcFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/oauth2/**", "/login/**")
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .successHandler(oidcAuthenticationSuccessHandler)
                    .failureHandler(oidcAuthenticationFailureHandler));
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/**",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/departments/**",
                        "/api/v1/vendors/**",
                        "/api/v1/catalog-items/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(corsProperties.getAllowedOrigins().split(",")));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
