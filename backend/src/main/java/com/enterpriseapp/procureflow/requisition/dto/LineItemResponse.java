package com.enterpriseapp.procureflow.requisition.dto;

import com.enterpriseapp.procureflow.requisition.RequisitionLineItem;
import java.math.BigDecimal;

public record LineItemResponse(
    Long id,
    Long catalogItemId,
    String description,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal) {

  public static LineItemResponse from(RequisitionLineItem item) {
    return new LineItemResponse(
        item.getId(),
        item.getCatalogItem() != null ? item.getCatalogItem().getId() : null,
        item.getDescription(),
        item.getQuantity(),
        item.getUnitPrice(),
        item.getLineTotal());
  }
}
