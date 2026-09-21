package com.enterpriseapp.procureflow.grpc;

import io.grpc.Context;

/**
 * Holds the identity {@link GrpcAuthInterceptor} extracts from a validated JWT, for gRPC service
 * implementations to read - the gRPC analog of {@code Authentication.getName()} in a REST
 * controller (see {@code PurchaseOrderController}).
 */
public final class GrpcAuthContext {

  public static final Context.Key<String> AUTHENTICATED_EMAIL = Context.key("authenticatedEmail");

  private GrpcAuthContext() {}
}
