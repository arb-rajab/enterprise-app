package com.enterpriseapp.procureflow.purchaseorder;

import com.enterpriseapp.procureflow.audit.AuditService;
import com.enterpriseapp.procureflow.common.exception.InvalidStateTransitionException;
import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.purchaseorder.dto.ConvertToPurchaseOrderRequest;
import com.enterpriseapp.procureflow.requisition.PurchaseRequisition;
import com.enterpriseapp.procureflow.requisition.RequisitionService;
import com.enterpriseapp.procureflow.requisition.RequisitionStatus;
import com.enterpriseapp.procureflow.user.User;
import com.enterpriseapp.procureflow.vendor.Vendor;
import com.enterpriseapp.procureflow.vendor.VendorService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderService {

  private final PurchaseOrderRepository purchaseOrderRepository;
  private final RequisitionService requisitionService;
  private final VendorService vendorService;
  private final AuditService auditService;

  public List<PurchaseOrder> findAll() {
    return purchaseOrderRepository.findAll();
  }

  public PurchaseOrder findById(Long id) {
    return purchaseOrderRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("PurchaseOrder", id));
  }

  @Transactional
  public PurchaseOrder convertFromRequisition(
      Long requisitionId, ConvertToPurchaseOrderRequest request, User actor) {
    PurchaseRequisition requisition = requisitionService.findById(requisitionId);
    if (requisition.getStatus() != RequisitionStatus.APPROVED) {
      throw new InvalidStateTransitionException(
          "Requisition "
              + requisitionId
              + " must be APPROVED before conversion (status: "
              + requisition.getStatus()
              + ")");
    }
    if (purchaseOrderRepository.findByRequisitionId(requisitionId).isPresent()) {
      throw new InvalidStateTransitionException(
          "Requisition " + requisitionId + " has already been converted to a purchase order");
    }

    Vendor vendor = vendorService.findById(request.vendorId());
    PurchaseOrder order =
        PurchaseOrder.builder()
            .requisition(requisition)
            .vendor(vendor)
            .poNumber(generatePoNumber())
            .totalAmount(requisition.getTotalAmount())
            .status(PurchaseOrderStatus.ISSUED)
            .issuedAt(Instant.now())
            .build();
    PurchaseOrder saved = purchaseOrderRepository.save(order);

    requisitionService.markConverted(requisitionId);
    auditService.record(
        "PurchaseOrder",
        saved.getId(),
        "ISSUED",
        actor.getEmail(),
        "Converted from requisition " + requisitionId);
    return saved;
  }

  @Transactional
  public PurchaseOrder updateStatus(Long id, PurchaseOrderStatus status, User actor) {
    PurchaseOrder order = findById(id);
    if (order.getStatus() == PurchaseOrderStatus.CANCELLED
        || order.getStatus() == PurchaseOrderStatus.FULFILLED) {
      throw new InvalidStateTransitionException(
          "Purchase order " + id + " is already in a terminal state (" + order.getStatus() + ")");
    }
    order.setStatus(status);
    auditService.record("PurchaseOrder", id, "STATUS_" + status, actor.getEmail(), null);
    return order;
  }

  private String generatePoNumber() {
    long sequence = purchaseOrderRepository.count() + 1;
    return "PO-%06d".formatted(sequence);
  }
}
