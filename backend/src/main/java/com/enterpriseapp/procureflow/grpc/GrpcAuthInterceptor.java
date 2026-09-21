package com.enterpriseapp.procureflow.grpc;

import com.enterpriseapp.procureflow.security.JwtService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Transport-side gRPC analog of {@link
 * com.enterpriseapp.procureflow.security.JwtAuthenticationFilter}: reads the bearer token from call
 * metadata and validates it with the exact same {@link JwtService} the REST API uses, rather than a
 * second, parallel token-validation implementation.
 *
 * <p>This interceptor only establishes identity (who is calling). Row-level and department read
 * scoping are decided entirely by {@code PurchaseOrderService}/{@code ReadScopePolicy}, exactly as
 * for REST - see {@code docs/project-memory/adr/0009-grpc-purchase-order-api.md}.
 */
@Component
@RequiredArgsConstructor
public class GrpcAuthInterceptor implements ServerInterceptor {

  private static final Metadata.Key<String> AUTHORIZATION =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    String header = headers.get(AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      call.close(Status.UNAUTHENTICATED.withDescription("Missing bearer token"), new Metadata());
      return new Listener<>() {};
    }

    String token = header.substring(BEARER_PREFIX.length());
    String email;
    try {
      Claims claims = jwtService.parseClaims(token);
      email = jwtService.extractEmail(claims);
    } catch (JwtException | IllegalArgumentException ex) {
      call.close(
          Status.UNAUTHENTICATED.withDescription("Invalid or expired token"), new Metadata());
      return new Listener<>() {};
    }

    Context context = Context.current().withValue(GrpcAuthContext.AUTHENTICATED_EMAIL, email);
    return Contexts.interceptCall(context, call, headers, next);
  }
}
