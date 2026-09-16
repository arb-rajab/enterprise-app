package com.enterpriseapp.procureflow.department.dto;

import com.enterpriseapp.procureflow.department.Department;

public record DepartmentResponse(
    Long id, String code, String name, String costCenter, Long managerUserId) {

  public static DepartmentResponse from(Department department) {
    return new DepartmentResponse(
        department.getId(),
        department.getCode(),
        department.getName(),
        department.getCostCenter(),
        department.getManagerUserId());
  }
}
