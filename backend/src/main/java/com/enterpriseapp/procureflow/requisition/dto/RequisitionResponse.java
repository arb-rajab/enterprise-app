package com.enterpriseapp.procureflow.requisition.dto;

import com.enterpriseapp.procureflow.requisition.PurchaseRequisition;
import com.enterpriseapp.procureflow.requisition.RequisitionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RequisitionResponse(
    Long id,
    Long requesterId,
    String requesterName,
    Long departmentId,
    String justification,
    RequisitionStatus status,
    BigDecimal totalAmount,
    List<LineItemResponse> lineItems,
    List<ApprovalStepResponse> approvalSteps,
    Instant createdAt,
    Instant updatedAt) {

  public static RequisitionResponse from(PurchaseRequisition requisition) {
    return new RequisitionResponse(
        requisition.getId(),
        requisition.getRequester().getId(),
        requisition.getRequester().getFullName(),
        requisition.getDepartment().getId(),
        requisition.getJustification(),
        requisition.getStatus(),
        requisition.getTotalAmount(),
        requisition.getLineItems().stream().map(LineItemResponse::from).toList(),
        requisition.getApprovalSteps().stream().map(ApprovalStepResponse::from).toList(),
        requisition.getCreatedAt(),
        requisition.getUpdatedAt());
  }
}
