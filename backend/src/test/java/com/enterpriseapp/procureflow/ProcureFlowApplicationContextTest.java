package com.enterpriseapp.procureflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;

/**
 * The whole real Spring context boots, against a throwaway in-memory H2 database (not
 * Testcontainers Postgres - this needs no Docker, so it runs in `mvn test`, catching a whole class
 * of wiring bug the mocked unit tests below can't: bean graph errors that only surface when the app
 * actually starts.
 *
 * <p>This caught two real bugs while OIDC login was being added: (1) a circular dependency
 * (SecurityConfig -> OidcAuthenticationSuccessHandler -> UserService -> PasswordEncoder, which was
 * a {@code @Bean} defined inside SecurityConfig itself - fixed by moving it to {@code
 * PasswordEncoderConfig}), and (2) {@code backend/src/test/resources/application.yml} entirely
 * shadowing {@code src/main/resources/application.yml} during any test run (Maven puts
 * `test-classes` ahead of `classes` on the test classpath) - meaning the OIDC client-registration
 * config newly added to the main file was invisible to every test, not just OIDC-specific ones,
 * since {@code SecurityConfig.oidcFilterChain()} unconditionally calls {@code .oauth2Login(...)}.
 * Both classes of bug are Docker-independent and exactly what this test exists to catch fast, in
 * `mvn test`, rather than only in CI's Testcontainers-backed `mvn verify`.
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:contextTest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class ProcureFlowApplicationContextTest {

  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  @Test
  void contextLoads() {
    // Intentionally empty: reaching this point already proves the whole bean graph (including
    // both SecurityConfig filter chains and the OIDC handlers) wired up without error.
  }

  @Test
  void keycloakClientRegistrationIsConfigured() {
    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");

    assertThat(registration).isNotNull();
    assertThat(registration.getClientId()).isNotBlank();
    assertThat(registration.getClientSecret()).isNotBlank();
  }
}
