package com.enterpriseapp.procureflow.requisition;

import com.enterpriseapp.procureflow.catalog.CatalogItem;
import com.enterpriseapp.procureflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "requisition_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RequisitionLineItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requisition_id", nullable = false)
  private PurchaseRequisition requisition;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "catalog_item_id")
  private CatalogItem catalogItem;

  @Column(nullable = false, length = 250)
  private String description;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
  private BigDecimal lineTotal;

  public void recalculateLineTotal() {
    this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
  }
}
