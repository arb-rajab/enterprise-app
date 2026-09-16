package com.enterpriseapp.procureflow.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
  List<Invoice> findByPurchaseOrderId(Long purchaseOrderId);

  boolean existsByInvoiceNumber(String invoiceNumber);
}
