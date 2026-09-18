package com.enterpriseapp.procureflow.purchaseorder;

import com.enterpriseapp.procureflow.audit.AuditService;
import com.enterpriseapp.procureflow.common.exception.InvalidStateTransitionException;
import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.purchaseorder.dto.ConvertToPurchaseOrderRequest;
import com.enterpriseapp.procureflow.requisition.PurchaseRequisition;
import com.enterpriseapp.procureflow.requisition.RequisitionService;
import com.enterpriseapp.procureflow.requisition.RequisitionStatus;
import com.enterpriseapp.procureflow.user.ReadScopePolicy;
import com.enterpriseapp.procureflow.user.RoleName;
import com.enterpriseapp.procureflow.user.User;
import com.enterpriseapp.procureflow.vendor.Vendor;
import com.enterpriseapp.procureflow.vendor.VendorService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
  private final ReadScopePolicy readScopePolicy;

  public PurchaseOrder findById(Long id) {
    return purchaseOrderRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("PurchaseOrder", id));
  }

  /**
   * Purchase orders {@code viewer} is allowed to read, scoped the same way as {@code
   * RequisitionService.findVisibleTo} (org-wide roles see everything, a department manager sees
   * their department's orders, everyone else sees only orders for requisitions they requested).
   * Previously this endpoint had no scoping at all — any authenticated user, of any role, could
   * list or fetch any purchase order.
   */
  public List<PurchaseOrder> findVisibleTo(User viewer) {
    if (readScopePolicy.hasOrganizationWideReadAccess(viewer)) {
      return purchaseOrderRepository.findAll();
    }
    if (viewer.getRoles().contains(RoleName.ROLE_DEPARTMENT_MANAGER)
        && viewer.getDepartment() != null) {
      return purchaseOrderRepository.findByRequisition_Department_Id(
          viewer.getDepartment().getId());
    }
    return purchaseOrderRepository.findByRequisition_Requester_Id(viewer.getId());
  }

  /** As {@link #findById(Long)}, but 403s if {@code viewer} isn't allowed to read this one. */
  public PurchaseOrder findVisibleById(Long id, User viewer) {
    PurchaseOrder order = findById(id);
    if (!isVisibleTo(order, viewer)) {
      throw new AccessDeniedException("You do not have permission to view this purchase order");
    }
    return order;
  }

  private boolean isVisibleTo(PurchaseOrder order, User viewer) {
    if (readScopePolicy.hasOrganizationWideReadAccess(viewer)) {
      return true;
    }
    PurchaseRequisition requisition = order.getRequisition();
    if (requisition.getRequester().getId().equals(viewer.getId())) {
      return true;
    }
    return viewer.getRoles().contains(RoleName.ROLE_DEPARTMENT_MANAGER)
        && viewer.getDepartment() != null
        && requisition.getDepartment().getId().equals(viewer.getDepartment().getId());
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
    long sequence = purchaseOrderRepository.nextPoNumberSequenceValue();
    return "PO-%06d".formatted(sequence);
  }
}
