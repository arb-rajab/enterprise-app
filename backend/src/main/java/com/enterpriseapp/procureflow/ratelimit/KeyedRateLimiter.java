package com.enterpriseapp.procureflow.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A keyed set of independent in-memory token buckets (one per distinct key - an IP, or a
 * credential+IP pair), the building block the REST rate-limiting filter and the gRPC rate-limiting
 * interceptor are both built from.
 *
 * <p>In-memory and per-instance by design, not backed by a shared cache: this app has no
 * Redis/distributed cache anywhere else in its stack (see {@code application.yml}), and adding one
 * solely for rate limiting would be new operational infrastructure for a single-instance-shaped
 * deployment (matching this repo's existing "no new infrastructure" posture - see
 * docs/project-memory/adr/0010-rate-limiting.md, "Alternatives considered"). Each bucket refills
 * fully at the start of every window rather than trickling continuously, which is simpler to reason
 * about and to test than a smooth/greedy refill.
 */
public class KeyedRateLimiter {

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final RateLimitProperties.Rule rule;

  public KeyedRateLimiter(RateLimitProperties.Rule rule) {
    this.rule = rule;
  }

  public boolean tryConsume(String key) {
    return buckets.computeIfAbsent(key, unused -> newBucket()).tryConsume(1);
  }

  private Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.classic(
            rule.getCapacity(),
            Refill.intervally(rule.getCapacity(), Duration.ofMinutes(rule.getPeriodMinutes())));
    return Bucket.builder().addLimit(limit).build();
  }
}
