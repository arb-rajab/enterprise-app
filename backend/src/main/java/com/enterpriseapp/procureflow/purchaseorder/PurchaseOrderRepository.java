package com.enterpriseapp.procureflow.purchaseorder;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
  Optional<PurchaseOrder> findByRequisitionId(Long requisitionId);
}
