package com.enterpriseapp.procureflow.vendor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VendorRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Email @Size(max = 190) String contactEmail,
    @Size(max = 30) String phone,
    @Size(max = 250) String address,
    @Size(max = 40) String taxId) {}
