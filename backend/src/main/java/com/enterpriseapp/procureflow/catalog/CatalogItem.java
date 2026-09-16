package com.enterpriseapp.procureflow.catalog;

import com.enterpriseapp.procureflow.common.BaseEntity;
import com.enterpriseapp.procureflow.vendor.Vendor;
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
@Table(name = "catalog_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CatalogItem extends BaseEntity {

  @Column(nullable = false, unique = true, length = 40)
  private String sku;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(length = 60)
  private String category;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vendor_id", nullable = false)
  private Vendor vendor;
}
