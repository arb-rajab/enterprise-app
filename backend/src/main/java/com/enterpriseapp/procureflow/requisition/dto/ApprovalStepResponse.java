package com.enterpriseapp.procureflow.requisition.dto;

import com.enterpriseapp.procureflow.requisition.ApprovalStatus;
import com.enterpriseapp.procureflow.requisition.ApprovalStep;
import java.time.Instant;

public record ApprovalStepResponse(
    Long id,
    Integer stepOrder,
    String approverRole,
    ApprovalStatus status,
    Long decidedByUserId,
    String comments,
    Instant decidedAt) {

  public static ApprovalStepResponse from(ApprovalStep step) {
    return new ApprovalStepResponse(
        step.getId(),
        step.getStepOrder(),
        step.getApproverRole().name(),
        step.getStatus(),
        step.getDecidedBy() != null ? step.getDecidedBy().getId() : null,
        step.getComments(),
        step.getDecidedAt());
  }
}
