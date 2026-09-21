package com.enterpriseapp.procureflow.grpc;

import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.GetPurchaseOrderRequest;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.ListPurchaseOrdersRequest;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.PurchaseOrderGrpcServiceGrpc;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.PurchaseOrderStatusResponse;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.PurchaseOrderStatusValue;
import com.enterpriseapp.procureflow.purchaseorder.PurchaseOrder;
import com.enterpriseapp.procureflow.purchaseorder.PurchaseOrderService;
import com.enterpriseapp.procureflow.requisition.PurchaseRequisition;
import com.enterpriseapp.procureflow.user.User;
import com.enterpriseapp.procureflow.user.UserService;
import com.enterpriseapp.procureflow.vendor.Vendor;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * gRPC counterpart of {@code PurchaseOrderController}'s read endpoints. Deliberately does not
 * duplicate any authorization or scoping decision: identity comes from {@link GrpcAuthInterceptor}
 * (which reuses {@code JwtService}, exactly as REST's {@code JwtAuthenticationFilter} does), and
 * visibility comes straight from {@link PurchaseOrderService#findVisibleById}/{@link
 * PurchaseOrderService#findVisibleTo} - the same methods, same {@code ReadScopePolicy}, same
 * department-scoping rule, that the REST controller calls. See
 * docs/project-memory/adr/0009-grpc-purchase-order-api.md.
 *
 * <p>{@code @Transactional} here (not just on {@code PurchaseOrderService}) so the lazy {@code
 * requisition}/{@code department}/{@code vendor} associations can still be traversed while mapping
 * to the response - the same associations {@code PurchaseOrderResponse.from} reads on the REST
 * path, just read inside this class's own transaction boundary instead of the controller's.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderGrpcServiceImpl
    extends PurchaseOrderGrpcServiceGrpc.PurchaseOrderGrpcServiceImplBase {

  private final PurchaseOrderService purchaseOrderService;
  private final UserService userService;

  @Override
  public void getPurchaseOrder(
      GetPurchaseOrderRequest request,
      StreamObserver<PurchaseOrderStatusResponse> responseObserver) {
    try {
      User viewer = currentViewer();
      PurchaseOrder order = purchaseOrderService.findVisibleById(request.getId(), viewer);
      responseObserver.onNext(toResponse(order));
      responseObserver.onCompleted();
    } catch (AccessDeniedException ex) {
      responseObserver.onError(
          Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
    } catch (ResourceNotFoundException ex) {
      responseObserver.onError(
          Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void listPurchaseOrders(
      ListPurchaseOrdersRequest request,
      StreamObserver<PurchaseOrderStatusResponse> responseObserver) {
    try {
      User viewer = currentViewer();
      purchaseOrderService
          .findVisibleTo(viewer)
          .forEach(order -> responseObserver.onNext(toResponse(order)));
      responseObserver.onCompleted();
    } catch (ResourceNotFoundException ex) {
      responseObserver.onError(
          Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  /** The gRPC analog of {@code Authentication.getName()} in the REST controllers. */
  private User currentViewer() {
    return userService.findByEmail(GrpcAuthContext.AUTHENTICATED_EMAIL.get());
  }

  private PurchaseOrderStatusResponse toResponse(PurchaseOrder order) {
    PurchaseRequisition requisition = order.getRequisition();
    Vendor vendor = order.getVendor();
    Instant issuedAt = order.getIssuedAt();
    return PurchaseOrderStatusResponse.newBuilder()
        .setId(order.getId())
        .setPoNumber(order.getPoNumber())
        .setStatus(PurchaseOrderStatusValue.valueOf(order.getStatus().name()))
        .setRequisitionId(requisition.getId())
        .setDepartmentId(requisition.getDepartment().getId())
        .setDepartmentCode(requisition.getDepartment().getCode())
        .setVendorId(vendor.getId())
        .setVendorName(vendor.getName())
        .setTotalAmount(order.getTotalAmount().toPlainString())
        .setIssuedAt(
            Timestamp.newBuilder()
                .setSeconds(issuedAt.getEpochSecond())
                .setNanos(issuedAt.getNano())
                .build())
        .build();
  }
}
