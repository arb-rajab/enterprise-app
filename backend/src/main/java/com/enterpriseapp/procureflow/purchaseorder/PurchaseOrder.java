package com.enterpriseapp.procureflow.purchaseorder;

import com.enterpriseapp.procureflow.common.BaseEntity;
import com.enterpriseapp.procureflow.requisition.PurchaseRequisition;
import com.enterpriseapp.procureflow.vendor.Vendor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PurchaseOrder extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requisition_id", nullable = false, unique = true)
  private PurchaseRequisition requisition;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vendor_id", nullable = false)
  private Vendor vendor;

  @Column(name = "po_number", nullable = false, unique = true, length = 30)
  private String poNumber;

  @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private PurchaseOrderStatus status = PurchaseOrderStatus.ISSUED;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;
}
