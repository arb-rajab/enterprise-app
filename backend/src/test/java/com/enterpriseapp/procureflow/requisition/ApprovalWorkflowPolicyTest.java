package com.enterpriseapp.procureflow.requisition;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterpriseapp.procureflow.user.RoleName;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ApprovalWorkflowPolicyTest {

  private final ApprovalWorkflowPolicy policy = new ApprovalWorkflowPolicy();

  @Test
  void smallSpendOnlyNeedsDepartmentManager() {
    assertThat(policy.resolveApprovalChain(new BigDecimal("999.99")))
        .containsExactly(RoleName.ROLE_DEPARTMENT_MANAGER);
  }

  @Test
  void amountAtLowerThresholdStillNeedsOnlyDepartmentManager() {
    assertThat(policy.resolveApprovalChain(new BigDecimal("1000.00")))
        .containsExactly(RoleName.ROLE_DEPARTMENT_MANAGER);
  }

  @Test
  void midRangeSpendAlsoNeedsProcurementOfficer() {
    assertThat(policy.resolveApprovalChain(new BigDecimal("1000.01")))
        .containsExactly(RoleName.ROLE_DEPARTMENT_MANAGER, RoleName.ROLE_PROCUREMENT_OFFICER);
  }

  @Test
  void amountAtUpperThresholdStillSkipsFinance() {
    assertThat(policy.resolveApprovalChain(new BigDecimal("10000.00")))
        .containsExactly(RoleName.ROLE_DEPARTMENT_MANAGER, RoleName.ROLE_PROCUREMENT_OFFICER);
  }

  @Test
  void largeSpendAlsoNeedsFinanceApproval() {
    assertThat(policy.resolveApprovalChain(new BigDecimal("10000.01")))
        .containsExactly(
            RoleName.ROLE_DEPARTMENT_MANAGER,
            RoleName.ROLE_PROCUREMENT_OFFICER,
            RoleName.ROLE_FINANCE_APPROVER);
  }
}
