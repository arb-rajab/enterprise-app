package com.enterpriseapp.procureflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link RateLimitFilter}: the general per-IP limit that now covers the whole REST
 * API (previously unthrottled entirely), and the two extra login-specific limits (per-credential-
 * and-IP, per-IP-alone) - see docs/project-memory/adr/0010-rate-limiting.md.
 */
class RateLimitFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void blocksRequestsOnceTheGeneralPerIpLimitIsExceeded() throws Exception {
    RateLimitFilter filter = newFilter(2, 1000, 1000);

    assertThat(reachesDownstream(filter, get("/api/v1/departments", "10.0.0.1"))).isTrue();
    assertThat(reachesDownstream(filter, get("/api/v1/departments", "10.0.0.1"))).isTrue();

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilter(get("/api/v1/departments", "10.0.0.1"), blocked, noOpChain());
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(blocked.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
  }

  @Test
  void aDifferentCallerIpHasItsOwnIndependentGeneralLimit() throws Exception {
    RateLimitFilter filter = newFilter(1, 1000, 1000);

    assertThat(reachesDownstream(filter, get("/api/v1/departments", "10.0.0.1"))).isTrue();
    // The first IP is now exhausted, but a second, unrelated caller is unaffected.
    assertThat(reachesDownstream(filter, get("/api/v1/departments", "10.0.0.2"))).isTrue();
  }

  @Test
  void perCredentialLimitBlocksRepeatedLoginAttemptsForOneEmailButNotAnother() throws Exception {
    RateLimitFilter filter = newFilter(1000, 2, 1000);

    assertThat(reachesDownstream(filter, loginRequest("victim@test.local", "10.0.0.2"))).isTrue();
    assertThat(reachesDownstream(filter, loginRequest("victim@test.local", "10.0.0.2"))).isTrue();

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilter(loginRequest("victim@test.local", "10.0.0.2"), blocked, noOpChain());
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // A different credential from the same IP has its own, still-untouched bucket.
    assertThat(reachesDownstream(filter, loginRequest("someone-else@test.local", "10.0.0.2")))
        .isTrue();
  }

  @Test
  void perIpLimitBlocksSprayingManyDifferentEmailsFromOneSource() throws Exception {
    RateLimitFilter filter = newFilter(1000, 1000, 2);

    assertThat(reachesDownstream(filter, loginRequest("a@test.local", "10.0.0.3"))).isTrue();
    assertThat(reachesDownstream(filter, loginRequest("b@test.local", "10.0.0.3"))).isTrue();

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilter(loginRequest("c@test.local", "10.0.0.3"), blocked, noOpChain());
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void aMalformedLoginBodyStillCountsAgainstThePerIpLimit() throws Exception {
    RateLimitFilter filter = newFilter(1000, 1000, 1);
    MockHttpServletRequest malformed = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    malformed.setRemoteAddr("10.0.0.4");
    malformed.setContentType(MediaType.APPLICATION_JSON_VALUE);
    malformed.setContent("not valid json".getBytes(StandardCharsets.UTF_8));

    assertThat(reachesDownstream(filter, malformed)).isTrue();

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilter(loginRequest("someone@test.local", "10.0.0.4"), blocked, noOpChain());
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void requestBodyIsStillReadableDownstreamAfterTheFilterInspectsIt() throws Exception {
    RateLimitFilter filter = newFilter(1000, 1000, 1000);
    MockHttpServletRequest request = loginRequest("user@test.local", "10.0.0.5");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> bodySeenDownstream = new AtomicReference<>();
    FilterChain chain =
        (req, res) ->
            bodySeenDownstream.set(
                new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

    filter.doFilter(request, response, chain);

    assertThat(bodySeenDownstream.get()).contains("user@test.local");
  }

  private RateLimitFilter newFilter(int apiCapacity, int loginCredCapacity, int loginIpCapacity) {
    RateLimitProperties properties = new RateLimitProperties();
    properties.getApiPerIp().setCapacity(apiCapacity);
    properties.getApiPerIp().setPeriodMinutes(1);
    properties.getLoginPerCredential().setCapacity(loginCredCapacity);
    properties.getLoginPerCredential().setPeriodMinutes(1);
    properties.getLoginPerIp().setCapacity(loginIpCapacity);
    properties.getLoginPerIp().setPeriodMinutes(1);
    return new RateLimitFilter(properties, objectMapper);
  }

  private boolean reachesDownstream(RateLimitFilter filter, MockHttpServletRequest request)
      throws Exception {
    AtomicBoolean reached = new AtomicBoolean(false);
    FilterChain chain = (req, res) -> reached.set(true);
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    return reached.get();
  }

  private FilterChain noOpChain() {
    return (req, res) -> {
      throw new AssertionError("Downstream must not be reached once the limit is exceeded");
    };
  }

  private MockHttpServletRequest get(String uri, String ip) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRemoteAddr(ip);
    return request;
  }

  private MockHttpServletRequest loginRequest(String email, String ip) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setRemoteAddr(ip);
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(
        objectMapper.writeValueAsBytes(Map.of("email", email, "password", "irrelevant")));
    return request;
  }
}
