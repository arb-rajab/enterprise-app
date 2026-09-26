package com.enterpriseapp.procureflow.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Resolves the address {@link RateLimitFilter} should key its buckets on, trusting {@code
 * X-Forwarded-For}/{@code X-Real-IP} only when the request's actual {@code getRemoteAddr()} is one
 * of the configured trusted proxies (see {@link RateLimitProperties#getTrustedProxies()}).
 *
 * <p>Without this check, a caller connecting directly to the app (bypassing the documented reverse
 * proxy) could set either header itself and pick an arbitrary rate-limit key - trivially evading
 * its own limit, or worse, framing another caller by exhausting their bucket. Requiring the direct
 * TCP peer to be a known proxy closes that gap while still letting real client addresses be
 * recovered once traffic does legitimately arrive via the proxy.
 */
public class ClientIpResolver {

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
  private static final String REAL_IP_HEADER = "X-Real-IP";

  private final List<IpAddressMatcher> trustedProxyMatchers;

  public ClientIpResolver(List<String> trustedProxyCidrs) {
    this.trustedProxyMatchers = trustedProxyCidrs.stream().map(IpAddressMatcher::new).toList();
  }

  public String resolve(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    if (!isFromTrustedProxy(remoteAddr)) {
      return remoteAddr;
    }

    String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }

    String realIp = request.getHeader(REAL_IP_HEADER);
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }

    return remoteAddr;
  }

  private boolean isFromTrustedProxy(String remoteAddr) {
    return trustedProxyMatchers.stream().anyMatch(matcher -> matchesSafely(matcher, remoteAddr));
  }

  private boolean matchesSafely(IpAddressMatcher matcher, String remoteAddr) {
    try {
      return matcher.matches(remoteAddr);
    } catch (IllegalArgumentException ex) {
      // Not a parseable IP (e.g. a test double's placeholder address) - never treat as trusted.
      return false;
    }
  }
}
