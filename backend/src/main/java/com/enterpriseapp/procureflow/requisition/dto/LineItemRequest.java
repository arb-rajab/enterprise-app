package com.enterpriseapp.procureflow.requisition.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LineItemRequest(
    Long catalogItemId,
    @NotBlank String description,
    @NotNull @Min(1) Integer quantity,
    @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice) {}
