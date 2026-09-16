package com.enterpriseapp.procureflow.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InvoiceRequest(
    @NotNull Long purchaseOrderId,
    @NotBlank @Size(max = 60) String invoiceNumber,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {}
