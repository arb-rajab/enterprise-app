package com.enterpriseapp.procureflow.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 40) String costCenter,
    Long managerUserId) {}
