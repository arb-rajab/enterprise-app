package com.enterpriseapp.procureflow.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.GetPurchaseOrderRequest;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.PurchaseOrderGrpcServiceGrpc;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.PurchaseOrderStatusResponse;
import com.enterpriseapp.procureflow.ratelimit.RateLimitProperties;
import com.enterpriseapp.procureflow.security.JwtProperties;
import com.enterpriseapp.procureflow.security.JwtService;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link GrpcRateLimitInterceptor} runs ahead of {@link GrpcAuthInterceptor} in the same
 * chain shape {@code GrpcServerLifecycle} builds in production - a flood of calls must be throttled
 * before this server spends anything validating their bearer tokens, not after. Fully in-process
 * (no Docker, no real network port, no Spring context): every call here is deliberately
 * unauthenticated, which is exactly what lets this test tell the two orderings apart - if auth ran
 * first, every call would fail {@code UNAUTHENTICATED} and the rate limiter would never see any of
 * them.
 */
class GrpcRateLimitInterceptorTest {

  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() throws InterruptedException {
    if (channel != null) {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (server != null) {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void blocksFurtherCallsWithResourceExhaustedOnceTheLimitIsHit_beforeAuthEverRuns()
      throws Exception {
    RateLimitProperties properties = new RateLimitProperties();
    properties.getGrpcPerIp().setCapacity(2);
    properties.getGrpcPerIp().setPeriodMinutes(1);
    GrpcRateLimitInterceptor rateLimitInterceptor = new GrpcRateLimitInterceptor(properties);
    GrpcAuthInterceptor authInterceptor = new GrpcAuthInterceptor(realJwtService());

    BindableService dummyService =
        new PurchaseOrderGrpcServiceGrpc.PurchaseOrderGrpcServiceImplBase() {
          @Override
          public void getPurchaseOrder(
              GetPurchaseOrderRequest request,
              StreamObserver<PurchaseOrderStatusResponse> responseObserver) {
            responseObserver.onNext(PurchaseOrderStatusResponse.newBuilder().build());
            responseObserver.onCompleted();
          }
        };

    String serverName = InProcessServerBuilder.generateName();
    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(
                ServerInterceptors.intercept(dummyService, authInterceptor, rateLimitInterceptor))
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    PurchaseOrderGrpcServiceGrpc.PurchaseOrderGrpcServiceBlockingStub stub =
        PurchaseOrderGrpcServiceGrpc.newBlockingStub(channel);

    assertThat(callAndGetStatus(stub)).isEqualTo(Status.Code.UNAUTHENTICATED);
    assertThat(callAndGetStatus(stub)).isEqualTo(Status.Code.UNAUTHENTICATED);
    // The third call exceeds the 2-per-window cap: rejected by the rate limiter itself before the
    // auth interceptor ever gets a chance to reject it for its (also missing) bearer token.
    assertThat(callAndGetStatus(stub)).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
  }

  private Status.Code callAndGetStatus(
      PurchaseOrderGrpcServiceGrpc.PurchaseOrderGrpcServiceBlockingStub stub) {
    try {
      stub.getPurchaseOrder(GetPurchaseOrderRequest.newBuilder().setId(1L).build());
      return Status.Code.OK;
    } catch (StatusRuntimeException e) {
      return e.getStatus().getCode();
    }
  }

  private JwtService realJwtService() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("unit-test-signing-secret-at-least-32-bytes-long");
    return new JwtService(properties);
  }
}
