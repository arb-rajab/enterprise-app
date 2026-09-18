package com.enterpriseapp.procureflow.purchaseorder;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
  Optional<PurchaseOrder> findByRequisitionId(Long requisitionId);

  /**
   * Atomically hands out the next PO sequence value at the database level (see
   * V3__purchase_order_number_sequence.sql). {@code nextval()} is guaranteed by Postgres to never
   * return the same value to two concurrent callers, unlike the previous {@code count() + 1}
   * approach.
   */
  @Query(value = "SELECT nextval('purchase_order_number_seq')", nativeQuery = true)
  long nextPoNumberSequenceValue();

  List<PurchaseOrder> findByRequisition_Department_Id(Long departmentId);

  List<PurchaseOrder> findByRequisition_Requester_Id(Long requesterId);
}
