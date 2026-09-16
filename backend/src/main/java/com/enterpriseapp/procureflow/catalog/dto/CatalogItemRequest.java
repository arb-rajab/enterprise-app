package com.enterpriseapp.procureflow.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CatalogItemRequest(
    @NotBlank @Size(max = 40) String sku,
    @NotBlank @Size(max = 160) String name,
    @Size(max = 500) String description,
    @Size(max = 60) String category,
    @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice,
    @NotNull Long vendorId) {}
