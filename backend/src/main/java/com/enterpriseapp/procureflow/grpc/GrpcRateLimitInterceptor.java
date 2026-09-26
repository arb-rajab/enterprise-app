package com.enterpriseapp.procureflow.grpc;

import com.enterpriseapp.procureflow.ratelimit.KeyedRateLimiter;
import com.enterpriseapp.procureflow.ratelimit.RateLimitProperties;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.springframework.stereotype.Component;

/**
 * Resource-exhaustion protection for the gRPC listener, keyed per caller remote address - this
 * server had no rate limiting of any kind before this interceptor existed. Runs ahead of {@link
 * GrpcAuthInterceptor} in {@code GrpcServerLifecycle}'s interceptor chain deliberately: a flood of
 * calls is throttled before this server spends anything validating their bearer tokens, the same
 * "count it before you parse it" ordering {@link
 * com.enterpriseapp.procureflow.ratelimit.RateLimitFilter} uses for REST's login endpoint.
 */
@Component
public class GrpcRateLimitInterceptor implements ServerInterceptor {

  private final KeyedRateLimiter perRemoteAddress;

  public GrpcRateLimitInterceptor(RateLimitProperties properties) {
    this.perRemoteAddress = new KeyedRateLimiter(properties.getGrpcPerIp());
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    String key = remoteAddressKey(call);
    if (!perRemoteAddress.tryConsume(key)) {
      call.close(
          Status.RESOURCE_EXHAUSTED.withDescription("Rate limit exceeded - try again later"),
          new Metadata());
      return new Listener<>() {};
    }
    return next.startCall(call, headers);
  }

  private String remoteAddressKey(ServerCall<?, ?> call) {
    SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    if (remoteAddr instanceof InetSocketAddress inetSocketAddress) {
      return inetSocketAddress.getAddress().getHostAddress();
    }
    return remoteAddr != null ? remoteAddr.toString() : "unknown";
  }
}
