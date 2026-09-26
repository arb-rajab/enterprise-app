package com.enterpriseapp.procureflow.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thresholds for this app's rate limiters (REST auth, REST general, gRPC) - see
 * docs/project-memory/adr/0010-rate-limiting.md for the mechanism and the reasoning behind each
 * default.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

  /**
   * Brute-force protection on {@code POST /api/v1/auth/login}, keyed per (lowercased email + caller
   * IP) - targets repeated attempts against one specific account.
   */
  private Rule loginPerCredential = new Rule(20, 1);

  /**
   * Credential-spray/enumeration protection on the same endpoint, keyed per caller IP alone -
   * catches one source trying many different emails quickly, which the per-credential rule above
   * can't see on its own.
   */
  private Rule loginPerIp = new Rule(60, 1);

  /**
   * General resource-exhaustion protection across the whole REST API, keyed per caller IP. Sized
   * generously on purpose - this guards against a sustained automated flood, not normal multi-user
   * traffic (including many users sharing one NAT'd office IP), so it stays well clear of
   * legitimate burst volume; the login-specific limits above are the ones deliberately kept tight.
   */
  private Rule apiPerIp = new Rule(1200, 1);

  /** Resource-exhaustion protection on the gRPC listener, keyed per caller remote address. */
  private Rule grpcPerIp = new Rule(120, 1);

  /** A token-bucket rule: up to {@code capacity} requests per {@code periodMinutes}. */
  @Getter
  @Setter
  public static class Rule {

    private int capacity;
    private int periodMinutes;

    public Rule() {}

    public Rule(int capacity, int periodMinutes) {
      this.capacity = capacity;
      this.periodMinutes = periodMinutes;
    }
  }
}
