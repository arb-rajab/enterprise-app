package com.enterpriseapp.procureflow.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditLogRepository auditLogRepository;

  @Transactional
  public void record(
      String entityType, Long entityId, String action, String performedBy, String details) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .performedBy(performedBy)
            .details(details)
            .build();
    auditLogRepository.save(entry);
  }
}
