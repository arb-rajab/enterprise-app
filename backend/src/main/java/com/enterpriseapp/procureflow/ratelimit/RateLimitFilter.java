package com.enterpriseapp.procureflow.ratelimit;

import com.enterpriseapp.procureflow.common.exception.ApiError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limits the REST API: a general per-IP limit on every request (resource-exhaustion protection
 * - this API had no throttling of any kind before this filter existed), plus two extra, tighter
 * limits specifically on {@code POST /api/v1/auth/login} keyed per-credential-and-IP and
 * per-IP-alone (brute-force/credential-spray protection - see
 * docs/project-memory/adr/0010-rate-limiting.md for the full threshold reasoning).
 *
 * <p>The login checks run ahead of body validation, deliberately: a request counts against the
 * limit whether or not its JSON body is well-formed, so a flood of even-malformed login attempts is
 * still throttled rather than reaching {@code AuthController} first.
 *
 * <p>Every bucket is keyed via {@link ClientIpResolver}, not raw {@code getRemoteAddr()} - this
 * app's documented deployment sits behind a reverse proxy (see ADR-0003), so the direct TCP peer is
 * normally that proxy, not the real caller. See {@link ClientIpResolver} for how the real address
 * is recovered safely.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String LOGIN_PATH = "/api/v1/auth/login";

  private final ObjectMapper objectMapper;
  private final ClientIpResolver clientIpResolver;
  private final KeyedRateLimiter apiPerIp;
  private final KeyedRateLimiter loginPerCredential;
  private final KeyedRateLimiter loginPerIp;

  public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.clientIpResolver = new ClientIpResolver(properties.getTrustedProxies());
    this.apiPerIp = new KeyedRateLimiter(properties.getApiPerIp());
    this.loginPerCredential = new KeyedRateLimiter(properties.getLoginPerCredential());
    this.loginPerIp = new KeyedRateLimiter(properties.getLoginPerIp());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String ip = clientIpResolver.resolve(request);

    if (!apiPerIp.tryConsume(ip)) {
      respondTooManyRequests(request, response);
      return;
    }

    if (!isLoginRequest(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
    if (!loginPerIp.tryConsume(ip)) {
      respondTooManyRequests(request, response);
      return;
    }
    String email = extractEmail(cachedRequest.bodyAsString());
    if (email != null
        && !loginPerCredential.tryConsume(email.toLowerCase(Locale.ROOT) + '|' + ip)) {
      respondTooManyRequests(request, response);
      return;
    }

    filterChain.doFilter(cachedRequest, response);
  }

  private boolean isLoginRequest(HttpServletRequest request) {
    return "POST".equalsIgnoreCase(request.getMethod())
        && LOGIN_PATH.equals(request.getRequestURI());
  }

  private String extractEmail(String body) {
    try {
      JsonNode node = objectMapper.readTree(body);
      JsonNode email = node.get("email");
      return email != null && email.isTextual() ? email.asText() : null;
    } catch (IOException | RuntimeException ex) {
      return null;
    }
  }

  private void respondTooManyRequests(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiError body =
        new ApiError(
            Instant.now(),
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            "Too many requests - try again later",
            request.getRequestURI(),
            List.of());
    objectMapper.writeValue(response.getWriter(), body);
  }
}
