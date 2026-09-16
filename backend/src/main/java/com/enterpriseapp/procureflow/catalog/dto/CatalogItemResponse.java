package com.enterpriseapp.procureflow.catalog.dto;

import com.enterpriseapp.procureflow.catalog.CatalogItem;
import java.math.BigDecimal;

public record CatalogItemResponse(
    Long id,
    String sku,
    String name,
    String description,
    String category,
    BigDecimal unitPrice,
    Long vendorId,
    String vendorName) {

  public static CatalogItemResponse from(CatalogItem item) {
    return new CatalogItemResponse(
        item.getId(),
        item.getSku(),
        item.getName(),
        item.getDescription(),
        item.getCategory(),
        item.getUnitPrice(),
        item.getVendor().getId(),
        item.getVendor().getName());
  }
}
