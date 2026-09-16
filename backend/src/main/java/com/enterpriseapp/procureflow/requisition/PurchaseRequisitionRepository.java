package com.enterpriseapp.procureflow.requisition;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {
  List<PurchaseRequisition> findByRequesterId(Long requesterId);
}
