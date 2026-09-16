package com.enterpriseapp.procureflow.requisition;

import com.enterpriseapp.procureflow.common.BaseEntity;
import com.enterpriseapp.procureflow.department.Department;
import com.enterpriseapp.procureflow.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "purchase_requisitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PurchaseRequisition extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requester_id", nullable = false)
  private User requester;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id", nullable = false)
  private Department department;

  @Column(length = 1000)
  private String justification;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private RequisitionStatus status = RequisitionStatus.DRAFT;

  @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
  @Builder.Default
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<RequisitionLineItem> lineItems = new ArrayList<>();

  @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("stepOrder ASC")
  @Builder.Default
  private List<ApprovalStep> approvalSteps = new ArrayList<>();

  public void recalculateTotal() {
    this.totalAmount =
        lineItems.stream()
            .map(RequisitionLineItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
