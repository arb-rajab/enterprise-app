package com.enterpriseapp.procureflow.invoice;

import com.enterpriseapp.procureflow.audit.AuditService;
import com.enterpriseapp.procureflow.common.exception.DuplicateResourceException;
import com.enterpriseapp.procureflow.common.exception.InvalidStateTransitionException;
import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.invoice.dto.InvoiceRequest;
import com.enterpriseapp.procureflow.purchaseorder.PurchaseOrder;
import com.enterpriseapp.procureflow.purchaseorder.PurchaseOrderService;
import com.enterpriseapp.procureflow.user.User;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final PurchaseOrderService purchaseOrderService;
  private final AuditService auditService;

  public List<Invoice> findAll() {
    return invoiceRepository.findAll();
  }

  public Invoice findById(Long id) {
    return invoiceRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("Invoice", id));
  }

  @Transactional
  public Invoice record(InvoiceRequest request, User actor) {
    if (invoiceRepository.existsByInvoiceNumber(request.invoiceNumber())) {
      throw new DuplicateResourceException(
          "An invoice with number " + request.invoiceNumber() + " already exists");
    }
    PurchaseOrder purchaseOrder = purchaseOrderService.findById(request.purchaseOrderId());
    Invoice invoice =
        Invoice.builder()
            .purchaseOrder(purchaseOrder)
            .invoiceNumber(request.invoiceNumber())
            .amount(request.amount())
            .status(InvoiceStatus.RECEIVED)
            .receivedAt(Instant.now())
            .build();
    Invoice saved = invoiceRepository.save(invoice);
    auditService.record(
        "Invoice",
        saved.getId(),
        "RECEIVED",
        actor.getEmail(),
        "Against PO " + purchaseOrder.getPoNumber());
    return saved;
  }

  @Transactional
  public Invoice transition(Long id, InvoiceStatus target, User actor) {
    Invoice invoice = findById(id);
    if (invoice.getStatus() == InvoiceStatus.PAID) {
      throw new InvalidStateTransitionException("Invoice " + id + " has already been paid");
    }
    invoice.setStatus(target);
    auditService.record("Invoice", id, "STATUS_" + target, actor.getEmail(), null);
    return invoice;
  }
}
