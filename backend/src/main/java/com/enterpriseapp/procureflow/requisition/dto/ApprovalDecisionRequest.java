package com.enterpriseapp.procureflow.requisition.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(@NotNull Boolean approve, @Size(max = 500) String comments) {}
