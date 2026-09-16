package com.enterpriseapp.procureflow.requisition;

import com.enterpriseapp.procureflow.user.RoleName;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Decides which approval chain a requisition must go through, based on its total amount.
 *
 * <p>Thresholds are deliberately simple for this demo domain (see ADR-0004): department manager
 * sign-off is always required; larger spend pulls in procurement, and the largest spend also
 * requires finance sign-off.
 */
@Component
public class ApprovalWorkflowPolicy {

  static final BigDecimal PROCUREMENT_THRESHOLD = new BigDecimal("1000.00");
  static final BigDecimal FINANCE_THRESHOLD = new BigDecimal("10000.00");

  public List<RoleName> resolveApprovalChain(BigDecimal totalAmount) {
    if (totalAmount.compareTo(FINANCE_THRESHOLD) > 0) {
      return List.of(
          RoleName.ROLE_DEPARTMENT_MANAGER,
          RoleName.ROLE_PROCUREMENT_OFFICER,
          RoleName.ROLE_FINANCE_APPROVER);
    }
    if (totalAmount.compareTo(PROCUREMENT_THRESHOLD) > 0) {
      return List.of(RoleName.ROLE_DEPARTMENT_MANAGER, RoleName.ROLE_PROCUREMENT_OFFICER);
    }
    return List.of(RoleName.ROLE_DEPARTMENT_MANAGER);
  }
}
