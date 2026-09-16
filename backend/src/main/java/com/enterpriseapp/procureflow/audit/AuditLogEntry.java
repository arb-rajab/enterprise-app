package com.enterpriseapp.procureflow.audit;

import com.enterpriseapp.procureflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Append-only record of significant workflow actions, for traceability. */
@Entity
@Table(name = "audit_log_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AuditLogEntry extends BaseEntity {

  @Column(name = "entity_type", nullable = false, length = 60)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private Long entityId;

  @Column(nullable = false, length = 60)
  private String action;

  @Column(name = "performed_by", length = 190)
  private String performedBy;

  @Column(length = 1000)
  private String details;
}
