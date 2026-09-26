package com.enterpriseapp.procureflow.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts and stops the additive gRPC server alongside the existing REST (servlet) stack - see
 * {@code docs/project-memory/adr/0009-grpc-purchase-order-api.md}. REST keeps running on {@code
 * server.port} exactly as before; this is a second, independent listener on {@code app.grpc.port}.
 *
 * <p>{@code app.grpc.port=0} lets the OS pick a free port (used by integration tests, which then
 * read the actual bound port back via {@link #getPort()}).
 */
@Component
@RequiredArgsConstructor
public class GrpcServerLifecycle implements SmartLifecycle {

  private final PurchaseOrderGrpcServiceImpl purchaseOrderGrpcService;
  private final GrpcAuthInterceptor grpcAuthInterceptor;
  private final GrpcRateLimitInterceptor grpcRateLimitInterceptor;

  @Value("${app.grpc.port:9090}")
  private int configuredPort;

  private Server server;
  private volatile boolean running;

  @Override
  public void start() {
    try {
      server =
          ServerBuilder.forPort(configuredPort)
              .addService(
                  ServerInterceptors.intercept(
                      purchaseOrderGrpcService, grpcAuthInterceptor, grpcRateLimitInterceptor))
              .build()
              .start();
      running = true;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start gRPC server", e);
    }
  }

  @Override
  public void stop() {
    if (server != null) {
      server.shutdown();
      try {
        server.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /** The actual bound port - differs from the configured one when that's {@code 0}. */
  public int getPort() {
    return server.getPort();
  }
}
