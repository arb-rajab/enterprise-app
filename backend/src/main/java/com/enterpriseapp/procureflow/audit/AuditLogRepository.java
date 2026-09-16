package com.enterpriseapp.procureflow.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
  List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(
      String entityType, Long entityId);
}
