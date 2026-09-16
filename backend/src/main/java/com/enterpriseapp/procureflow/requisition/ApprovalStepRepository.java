package com.enterpriseapp.procureflow.requisition;

import com.enterpriseapp.procureflow.user.RoleName;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {
  List<ApprovalStep> findByApproverRoleInAndStatus(
      Collection<RoleName> roles, ApprovalStatus status);
}
