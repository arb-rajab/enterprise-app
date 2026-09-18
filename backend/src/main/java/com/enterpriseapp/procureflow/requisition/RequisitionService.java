package com.enterpriseapp.procureflow.requisition;

import com.enterpriseapp.procureflow.audit.AuditService;
import com.enterpriseapp.procureflow.catalog.CatalogItem;
import com.enterpriseapp.procureflow.catalog.CatalogItemService;
import com.enterpriseapp.procureflow.common.exception.InvalidStateTransitionException;
import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.department.Department;
import com.enterpriseapp.procureflow.department.DepartmentService;
import com.enterpriseapp.procureflow.requisition.dto.ApprovalDecisionRequest;
import com.enterpriseapp.procureflow.requisition.dto.CreateRequisitionRequest;
import com.enterpriseapp.procureflow.requisition.dto.LineItemRequest;
import com.enterpriseapp.procureflow.user.ReadScopePolicy;
import com.enterpriseapp.procureflow.user.RoleName;
import com.enterpriseapp.procureflow.user.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequisitionService {

  private final PurchaseRequisitionRepository requisitionRepository;
  private final ApprovalStepRepository approvalStepRepository;
  private final DepartmentService departmentService;
  private final CatalogItemService catalogItemService;
  private final ApprovalWorkflowPolicy approvalWorkflowPolicy;
  private final AuditService auditService;
  private final ReadScopePolicy readScopePolicy;

  public PurchaseRequisition findById(Long id) {
    return requisitionRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("PurchaseRequisition", id));
  }

  /**
   * Requisitions {@code viewer} is allowed to read: all of them for roles with org-wide approval
   * authority (see {@link ReadScopePolicy}), a department manager's own department, or otherwise
   * just the viewer's own requisitions. Write actions were already scoped this way (see {@code
   * requireOwner}/{@code decide}); this closes the previously undocumented gap where every
   * authenticated user could list every requisition regardless of role (see 06-security.md).
   */
  public List<PurchaseRequisition> findVisibleTo(User viewer) {
    if (readScopePolicy.hasOrganizationWideReadAccess(viewer)) {
      return requisitionRepository.findAll();
    }
    if (viewer.getRoles().contains(RoleName.ROLE_DEPARTMENT_MANAGER)
        && viewer.getDepartment() != null) {
      return requisitionRepository.findByDepartmentId(viewer.getDepartment().getId());
    }
    return requisitionRepository.findByRequesterId(viewer.getId());
  }

  /** As {@link #findById(Long)}, but 403s if {@code viewer} isn't allowed to read this one. */
  public PurchaseRequisition findVisibleById(Long id, User viewer) {
    PurchaseRequisition requisition = findById(id);
    if (!isVisibleTo(requisition, viewer)) {
      throw new AccessDeniedException("You do not have permission to view this requisition");
    }
    return requisition;
  }

  private boolean isVisibleTo(PurchaseRequisition requisition, User viewer) {
    if (readScopePolicy.hasOrganizationWideReadAccess(viewer)) {
      return true;
    }
    if (requisition.getRequester().getId().equals(viewer.getId())) {
      return true;
    }
    return viewer.getRoles().contains(RoleName.ROLE_DEPARTMENT_MANAGER)
        && viewer.getDepartment() != null
        && requisition.getDepartment().getId().equals(viewer.getDepartment().getId());
  }

  /** Requisitions with a step pending on any of the caller's roles. */
  public List<PurchaseRequisition> findPendingApprovalFor(Set<RoleName> roles) {
    return approvalStepRepository
        .findByApproverRoleInAndStatus(roles, ApprovalStatus.PENDING)
        .stream()
        .map(ApprovalStep::getRequisition)
        .filter(requisition -> isActionable(requisition, roles))
        .distinct()
        .toList();
  }

  private boolean isActionable(PurchaseRequisition requisition, Set<RoleName> roles) {
    return nextPendingStep(requisition)
        .map(step -> roles.contains(step.getApproverRole()))
        .orElse(false);
  }

  @Transactional
  public PurchaseRequisition create(CreateRequisitionRequest request, User requester) {
    Department department = departmentService.findById(request.departmentId());
    PurchaseRequisition requisition =
        PurchaseRequisition.builder()
            .requester(requester)
            .department(department)
            .justification(request.justification())
            .status(RequisitionStatus.DRAFT)
            .build();

    for (LineItemRequest lineItemRequest : request.lineItems()) {
      requisition.getLineItems().add(buildLineItem(requisition, lineItemRequest));
    }
    requisition.recalculateTotal();

    PurchaseRequisition saved = requisitionRepository.save(requisition);
    auditService.record(
        "PurchaseRequisition",
        saved.getId(),
        "CREATED",
        requester.getEmail(),
        "Draft requisition created");
    return saved;
  }

  private RequisitionLineItem buildLineItem(
      PurchaseRequisition requisition, LineItemRequest request) {
    CatalogItem catalogItem =
        request.catalogItemId() != null
            ? catalogItemService.findById(request.catalogItemId())
            : null;
    RequisitionLineItem lineItem =
        RequisitionLineItem.builder()
            .requisition(requisition)
            .catalogItem(catalogItem)
            .description(request.description())
            .quantity(request.quantity())
            .unitPrice(request.unitPrice())
            .build();
    lineItem.recalculateLineTotal();
    return lineItem;
  }

  @Transactional
  public PurchaseRequisition submit(Long id, User actor) {
    PurchaseRequisition requisition = findById(id);
    requireOwner(requisition, actor);
    if (requisition.getStatus() != RequisitionStatus.DRAFT) {
      throw new InvalidStateTransitionException(
          "Requisition " + id + " cannot be submitted from status " + requisition.getStatus());
    }
    if (requisition.getLineItems().isEmpty()) {
      throw new InvalidStateTransitionException(
          "A requisition must have at least one line item to submit");
    }

    List<RoleName> chain =
        approvalWorkflowPolicy.resolveApprovalChain(requisition.getTotalAmount());
    int order = 1;
    for (RoleName role : chain) {
      requisition
          .getApprovalSteps()
          .add(
              ApprovalStep.builder()
                  .requisition(requisition)
                  .stepOrder(order++)
                  .approverRole(role)
                  .status(ApprovalStatus.PENDING)
                  .build());
    }
    requisition.setStatus(RequisitionStatus.SUBMITTED);
    auditService.record(
        "PurchaseRequisition",
        id,
        "SUBMITTED",
        actor.getEmail(),
        "Routed through " + chain.size() + " approval step(s)");
    return requisition;
  }

  @Transactional
  public PurchaseRequisition decide(Long id, ApprovalDecisionRequest decision, User approver) {
    PurchaseRequisition requisition = findById(id);
    if (requisition.getStatus() != RequisitionStatus.SUBMITTED) {
      throw new InvalidStateTransitionException(
          "Requisition "
              + id
              + " is not awaiting approval (status: "
              + requisition.getStatus()
              + ")");
    }

    ApprovalStep step =
        nextPendingStep(requisition)
            .orElseThrow(
                () ->
                    new InvalidStateTransitionException(
                        "Requisition " + id + " has no pending approval step"));

    if (!approver.getRoles().contains(step.getApproverRole())) {
      throw new InvalidStateTransitionException(
          "Requisition "
              + id
              + " is currently awaiting approval from role "
              + step.getApproverRole());
    }

    step.setDecidedBy(approver);
    step.setComments(decision.comments());
    step.setDecidedAt(Instant.now());

    if (Boolean.TRUE.equals(decision.approve())) {
      step.setStatus(ApprovalStatus.APPROVED);
      auditService.record(
          "PurchaseRequisition",
          id,
          "STEP_APPROVED",
          approver.getEmail(),
          "Step " + step.getStepOrder() + " (" + step.getApproverRole() + ") approved");
      if (nextPendingStep(requisition).isEmpty()) {
        requisition.setStatus(RequisitionStatus.APPROVED);
        auditService.record(
            "PurchaseRequisition",
            id,
            "APPROVED",
            approver.getEmail(),
            "All approval steps complete");
      }
    } else {
      step.setStatus(ApprovalStatus.REJECTED);
      requisition.setStatus(RequisitionStatus.REJECTED);
      auditService.record(
          "PurchaseRequisition",
          id,
          "REJECTED",
          approver.getEmail(),
          "Step " + step.getStepOrder() + " (" + step.getApproverRole() + ") rejected");
    }
    return requisition;
  }

  @Transactional
  public PurchaseRequisition cancel(Long id, User actor) {
    PurchaseRequisition requisition = findById(id);
    requireOwner(requisition, actor);
    if (requisition.getStatus() == RequisitionStatus.CONVERTED) {
      throw new InvalidStateTransitionException("A converted requisition cannot be cancelled");
    }
    requisition.setStatus(RequisitionStatus.CANCELLED);
    auditService.record("PurchaseRequisition", id, "CANCELLED", actor.getEmail(), null);
    return requisition;
  }

  @Transactional
  public void markConverted(Long id) {
    PurchaseRequisition requisition = findById(id);
    if (requisition.getStatus() != RequisitionStatus.APPROVED) {
      throw new InvalidStateTransitionException(
          "Requisition "
              + id
              + " must be APPROVED before it can be converted (status: "
              + requisition.getStatus()
              + ")");
    }
    requisition.setStatus(RequisitionStatus.CONVERTED);
  }

  private void requireOwner(PurchaseRequisition requisition, User actor) {
    boolean isOwner = requisition.getRequester().getId().equals(actor.getId());
    boolean isAdmin = actor.getRoles().contains(RoleName.ROLE_ADMIN);
    if (!isOwner && !isAdmin) {
      throw new InvalidStateTransitionException(
          "Only the requester or an administrator can perform this action");
    }
  }

  static Optional<ApprovalStep> nextPendingStep(PurchaseRequisition requisition) {
    return requisition.getApprovalSteps().stream()
        .filter(step -> step.getStatus() == ApprovalStatus.PENDING)
        .min(Comparator.comparingInt(ApprovalStep::getStepOrder));
  }
}
