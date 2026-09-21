package com.enterpriseapp.procureflow.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a real, migrated PostgreSQL database.
 *
 * <p>These tests require a Docker daemon (Testcontainers spins up a disposable Postgres container).
 * They run in CI via the Failsafe plugin (`mvn verify`) on GitHub Actions' docker-enabled runners.
 * They are not runnable in sandboxes without a Docker daemon - see
 * docs/project-memory/07-testing.md for the caveat this implies for this specific dev sandbox.
 *
 * <p>The container is started manually (the "singleton container" pattern) rather than via
 * {@code @Testcontainers}/{@code @Container}, which would stop and restart it - on a new port -
 * between each IT subclass. Spring's test-context cache then keeps reusing the first subclass's
 * DataSource pointing at the now-dead old port, so every IT class after the first one fails with
 * connection-refused/timeout errors. Starting it once, here, and letting the JVM (and
 * Testcontainers' Ryuk reaper) tear it down at shutdown keeps one Postgres instance - and one port
 * - alive for every IT class in the run.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("procureflow_test")
          .withUsername("procureflow_test")
          .withPassword("procureflow_test");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  /**
   * Let the OS pick a free port for the gRPC listener (see {@code GrpcServerLifecycle}) instead of
   * the fixed default, so every IT class's Spring context - not just gRPC-specific tests - can boot
   * concurrently without fighting over port 9090.
   */
  @DynamicPropertySource
  static void grpcProperties(DynamicPropertyRegistry registry) {
    registry.add("app.grpc.port", () -> 0);
  }
}
