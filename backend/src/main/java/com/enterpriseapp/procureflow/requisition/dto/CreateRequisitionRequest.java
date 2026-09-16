package com.enterpriseapp.procureflow.requisition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRequisitionRequest(
    @NotNull Long departmentId,
    @Size(max = 1000) String justification,
    @NotEmpty @Valid List<LineItemRequest> lineItems) {}
