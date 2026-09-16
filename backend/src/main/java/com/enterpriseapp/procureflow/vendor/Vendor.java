package com.enterpriseapp.procureflow.vendor;

import com.enterpriseapp.procureflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Vendor extends BaseEntity {

  @Column(nullable = false, length = 160)
  private String name;

  @Column(name = "contact_email", nullable = false, length = 190)
  private String contactEmail;

  @Column(length = 30)
  private String phone;

  @Column(length = 250)
  private String address;

  @Column(name = "tax_id", length = 40)
  private String taxId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private VendorStatus status = VendorStatus.PENDING_APPROVAL;
}
