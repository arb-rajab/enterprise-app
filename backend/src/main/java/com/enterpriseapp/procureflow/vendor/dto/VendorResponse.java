package com.enterpriseapp.procureflow.vendor.dto;

import com.enterpriseapp.procureflow.vendor.Vendor;
import com.enterpriseapp.procureflow.vendor.VendorStatus;

public record VendorResponse(
    Long id,
    String name,
    String contactEmail,
    String phone,
    String address,
    String taxId,
    VendorStatus status) {

  public static VendorResponse from(Vendor vendor) {
    return new VendorResponse(
        vendor.getId(),
        vendor.getName(),
        vendor.getContactEmail(),
        vendor.getPhone(),
        vendor.getAddress(),
        vendor.getTaxId(),
        vendor.getStatus());
  }
}
