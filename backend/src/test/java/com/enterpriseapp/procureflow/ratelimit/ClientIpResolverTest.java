package com.enterpriseapp.procureflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link ClientIpResolver}: proves the real client IP is recovered from
 * X-Forwarded-For/X-Real-IP when (and only when) the request actually came from a configured
 * trusted proxy, closing the gap where the rate limiter previously keyed on raw {@code
 * getRemoteAddr()} despite this app's documented reverse-proxy deployment (ADR-0003/ADR-0010).
 */
class ClientIpResolverTest {

  private static final String TRUSTED_PROXY_ADDR = "172.28.0.10";
  private static final String REAL_CLIENT_ADDR = "203.0.113.42";
  private static final String ATTACKER_ADDR = "198.51.100.7";

  @Test
  void usesRawRemoteAddrWhenNoTrustedProxiesAreConfigured() {
    ClientIpResolver resolver = new ClientIpResolver(List.of());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(TRUSTED_PROXY_ADDR);
    request.addHeader("X-Forwarded-For", REAL_CLIENT_ADDR);

    assertThat(resolver.resolve(request)).isEqualTo(TRUSTED_PROXY_ADDR);
  }

  @Test
  void recoversTheRealClientIpFromForwardedForWhenTheRequestComesFromATrustedProxy() {
    ClientIpResolver resolver = new ClientIpResolver(List.of(TRUSTED_PROXY_ADDR + "/32"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(TRUSTED_PROXY_ADDR);
    request.addHeader("X-Forwarded-For", REAL_CLIENT_ADDR);

    assertThat(resolver.resolve(request)).isEqualTo(REAL_CLIENT_ADDR);
  }

  @Test
  void takesOnlyTheFirstHopOfAMultiValueForwardedForHeader() {
    ClientIpResolver resolver = new ClientIpResolver(List.of(TRUSTED_PROXY_ADDR + "/32"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(TRUSTED_PROXY_ADDR);
    request.addHeader("X-Forwarded-For", REAL_CLIENT_ADDR + ", 10.0.0.5, 10.0.0.6");

    assertThat(resolver.resolve(request)).isEqualTo(REAL_CLIENT_ADDR);
  }

  @Test
  void fallsBackToXRealIpWhenForwardedForIsAbsentButTheProxyIsTrusted() {
    ClientIpResolver resolver = new ClientIpResolver(List.of(TRUSTED_PROXY_ADDR + "/32"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(TRUSTED_PROXY_ADDR);
    request.addHeader("X-Real-IP", REAL_CLIENT_ADDR);

    assertThat(resolver.resolve(request)).isEqualTo(REAL_CLIENT_ADDR);
  }

  @Test
  void ignoresASpoofedForwardedForHeaderFromAnUntrustedDirectCaller() {
    ClientIpResolver resolver = new ClientIpResolver(List.of(TRUSTED_PROXY_ADDR + "/32"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    // The attacker connects directly, bypassing the proxy, and claims to be an arbitrary client.
    request.setRemoteAddr(ATTACKER_ADDR);
    request.addHeader("X-Forwarded-For", REAL_CLIENT_ADDR);

    assertThat(resolver.resolve(request)).isEqualTo(ATTACKER_ADDR);
  }

  @Test
  void ignoresASpoofedXRealIpHeaderFromAnUntrustedDirectCaller() {
    ClientIpResolver resolver = new ClientIpResolver(List.of(TRUSTED_PROXY_ADDR + "/32"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(ATTACKER_ADDR);
    request.addHeader("X-Real-IP", REAL_CLIENT_ADDR);

    assertThat(resolver.resolve(request)).isEqualTo(ATTACKER_ADDR);
  }

  @Test
  void treatsAnUnparseableRemoteAddrAsNeverTrusted() {
    ClientIpResolver resolver = new ClientIpResolver(List.of(TRUSTED_PROXY_ADDR + "/32"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("not-an-ip");
    request.addHeader("X-Forwarded-For", REAL_CLIENT_ADDR);

    assertThat(resolver.resolve(request)).isEqualTo("not-an-ip");
  }
}
