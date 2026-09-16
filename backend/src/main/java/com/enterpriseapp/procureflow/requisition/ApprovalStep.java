package com.enterpriseapp.procureflow.requisition;

import com.enterpriseapp.procureflow.common.BaseEntity;
import com.enterpriseapp.procureflow.user.RoleName;
import com.enterpriseapp.procureflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A single step in a requisition's sequential approval chain. Steps are evaluated in {@code
 * stepOrder} order; only the lowest-order {@code PENDING} step is actionable at any given time (see
 * {@code RequisitionService#nextPendingStep}).
 */
@Entity
@Table(name = "approval_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApprovalStep extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requisition_id", nullable = false)
  private PurchaseRequisition requisition;

  @Column(name = "step_order", nullable = false)
  private Integer stepOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "approver_role", nullable = false, length = 40)
  private RoleName approverRole;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decided_by_user_id")
  private User decidedBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private ApprovalStatus status = ApprovalStatus.PENDING;

  @Column(length = 500)
  private String comments;

  @Column(name = "decided_at")
  private Instant decidedAt;
}
