package com.enterpriseapp.procureflow.user;

/**
 * Roles recognized by the application. Names are stored with the {@code ROLE_} prefix so they can
 * be used directly with Spring Security's {@code hasRole(...)} expressions (which add the prefix
 * implicitly) as well as {@code hasAuthority(...)} checks.
 */
public enum RoleName {
  ROLE_ADMIN,
  ROLE_EMPLOYEE,
  ROLE_DEPARTMENT_MANAGER,
  ROLE_PROCUREMENT_OFFICER,
  ROLE_FINANCE_APPROVER
}
