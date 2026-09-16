package com.enterpriseapp.procureflow.purchaseorder.dto;

import jakarta.validation.constraints.NotNull;

public record ConvertToPurchaseOrderRequest(@NotNull Long vendorId) {}
