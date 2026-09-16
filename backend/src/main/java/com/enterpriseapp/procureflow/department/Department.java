package com.enterpriseapp.procureflow.department;

import com.enterpriseapp.procureflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Department extends BaseEntity {

  @Column(nullable = false, unique = true, length = 20)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "cost_center", length = 40)
  private String costCenter;

  /**
   * User id of the department manager. Kept as a plain id to avoid a circular FK at bootstrap time.
   */
  @Column(name = "manager_user_id")
  private Long managerUserId;
}
