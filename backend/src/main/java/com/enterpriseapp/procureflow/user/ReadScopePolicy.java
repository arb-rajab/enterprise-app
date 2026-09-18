package com.enterpriseapp.procureflow.user;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Which roles can read requisitions and purchase orders across department/ownership boundaries.
 *
 * <p>Mirrors the approval authority {@code RequisitionService.decide()} already grants these roles:
 * a Procurement Officer or Finance Approver can act on any department's pending step (see
 * ADR-0004), so restricting their *read* visibility to their own department while leaving their
 * *write* (approval) authority org-wide would be inconsistent and would break the pending-approval
 * review workflow. Department Managers and Employees have no such org-wide write authority, so
 * their read access stays scoped to their own department/requisitions respectively.
 */
@Component
public class ReadScopePolicy {

  private static final Set<RoleName> ORGANIZATION_WIDE_ROLES =
      Set.of(
          RoleName.ROLE_ADMIN, RoleName.ROLE_PROCUREMENT_OFFICER, RoleName.ROLE_FINANCE_APPROVER);

  public boolean hasOrganizationWideReadAccess(User user) {
    return user.getRoles().stream().anyMatch(ORGANIZATION_WIDE_ROLES::contains);
  }
}
