package com.enterpriseapp.procureflow.purchaseorder.dto;

import com.enterpriseapp.procureflow.purchaseorder.PurchaseOrder;
import com.enterpriseapp.procureflow.purchaseorder.PurchaseOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseOrderResponse(
    Long id,
    String poNumber,
    Long requisitionId,
    Long vendorId,
    String vendorName,
    BigDecimal totalAmount,
    PurchaseOrderStatus status,
    Instant issuedAt) {

  public static PurchaseOrderResponse from(PurchaseOrder order) {
    return new PurchaseOrderResponse(
        order.getId(),
        order.getPoNumber(),
        order.getRequisition().getId(),
        order.getVendor().getId(),
        order.getVendor().getName(),
        order.getTotalAmount(),
        order.getStatus(),
        order.getIssuedAt());
  }
}
