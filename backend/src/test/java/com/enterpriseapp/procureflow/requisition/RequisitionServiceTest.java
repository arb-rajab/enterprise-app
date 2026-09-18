package com.enterpriseapp.procureflow.requisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.enterpriseapp.procureflow.audit.AuditService;
import com.enterpriseapp.procureflow.catalog.CatalogItemService;
import com.enterpriseapp.procureflow.common.exception.InvalidStateTransitionException;
import com.enterpriseapp.procureflow.department.Department;
import com.enterpriseapp.procureflow.department.DepartmentService;
import com.enterpriseapp.procureflow.requisition.dto.ApprovalDecisionRequest;
import com.enterpriseapp.procureflow.requisition.dto.CreateRequisitionRequest;
import com.enterpriseapp.procureflow.requisition.dto.LineItemRequest;
import com.enterpriseapp.procureflow.user.ReadScopePolicy;
import com.enterpriseapp.procureflow.user.RoleName;
import com.enterpriseapp.procureflow.user.User;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequisitionServiceTest {

  @Mock private PurchaseRequisitionRepository requisitionRepository;

  @Mock private ApprovalStepRepository approvalStepRepository;

  @Mock private DepartmentService departmentService;

  @Mock private CatalogItemService catalogItemService;

  @Mock private AuditService auditService;

  private RequisitionService service;

  private User employee;
  private User manager;
  private Department department;

  @BeforeEach
  void setUp() {
    service =
        new RequisitionService(
            requisitionRepository,
            approvalStepRepository,
            departmentService,
            catalogItemService,
            new ApprovalWorkflowPolicy(),
            auditService,
            new ReadScopePolicy());

    department = Department.builder().code("ENG").name("Engineering").build();
    department.setId(10L);

    employee =
        User.builder()
            .email("employee@test.local")
            .firstName("Eli")
            .lastName("Employee")
            .roles(EnumSet.of(RoleName.ROLE_EMPLOYEE))
            .build();
    employee.setId(1L);

    manager =
        User.builder()
            .email("manager@test.local")
            .firstName("Morgan")
            .lastName("Manager")
            .roles(EnumSet.of(RoleName.ROLE_DEPARTMENT_MANAGER))
            .department(department)
            .build();
    manager.setId(2L);

    lenient()
        .when(requisitionRepository.save(any(PurchaseRequisition.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(departmentService.findById(10L)).thenReturn(department);
  }

  private PurchaseRequisition createDraft(BigDecimal unitPrice, int quantity) {
    CreateRequisitionRequest request =
        new CreateRequisitionRequest(
            10L,
            "Need supplies",
            List.of(new LineItemRequest(null, "Widgets", quantity, unitPrice)));
    return service.create(request, employee);
  }

  @Test
  void createComputesTotalFromLineItems() {
    PurchaseRequisition requisition = createDraft(new BigDecimal("100.00"), 3);
    assertThat(requisition.getTotalAmount()).isEqualByComparingTo("300.00");
    assertThat(requisition.getStatus()).isEqualTo(RequisitionStatus.DRAFT);
  }

  @Test
  void submitBuildsApprovalChainMatchingAmount() {
    PurchaseRequisition requisition = createDraft(new BigDecimal("5000.00"), 1);
    requisition.setId(6L);
    when(requisitionRepository.findById(6L)).thenReturn(java.util.Optional.of(requisition));

    service.submit(requisition.getId(), employee);

    assertThat(requisition.getStatus()).isEqualTo(RequisitionStatus.SUBMITTED);
    assertThat(requisition.getApprovalSteps())
        .extracting(ApprovalStep::getApproverRole)
        .containsExactly(RoleName.ROLE_DEPARTMENT_MANAGER, RoleName.ROLE_PROCUREMENT_OFFICER);
  }

  @Test
  void submitRejectsRequisitionWithNoLineItems() {
    // built directly (rather than via create()) to bypass the @NotEmpty constraint on the request
    // DTO
    PurchaseRequisition requisition =
        PurchaseRequisition.builder()
            .requester(employee)
            .department(department)
            .status(RequisitionStatus.DRAFT)
            .build();
    requisition.setId(99L);
    org.mockito.Mockito.when(requisitionRepository.findById(99L))
        .thenReturn(java.util.Optional.of(requisition));

    assertThatThrownBy(() -> service.submit(99L, employee))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  void nonOwnerCannotSubmitAnotherUsersRequisition() {
    PurchaseRequisition requisition = createDraft(new BigDecimal("10.00"), 1);
    requisition.setId(5L);
    when(requisitionRepository.findById(5L)).thenReturn(java.util.Optional.of(requisition));

    assertThatThrownBy(() -> service.submit(5L, manager))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  void requisitionIsApprovedOnceAllStepsApprove() {
    PurchaseRequisition requisition = createDraft(new BigDecimal("50.00"), 1);
    requisition.setId(7L);
    when(requisitionRepository.findById(7L)).thenReturn(java.util.Optional.of(requisition));

    service.submit(7L, employee);
    assertThat(requisition.getApprovalSteps()).hasSize(1);

    PurchaseRequisition decided =
        service.decide(7L, new ApprovalDecisionRequest(true, "Looks good"), manager);

    assertThat(decided.getStatus()).isEqualTo(RequisitionStatus.APPROVED);
    assertThat(decided.getApprovalSteps().get(0).getStatus()).isEqualTo(ApprovalStatus.APPROVED);
  }

  @Test
  void requisitionIsRejectedWhenAnyStepIsRejected() {
    PurchaseRequisition requisition = createDraft(new BigDecimal("50.00"), 1);
    requisition.setId(8L);
    when(requisitionRepository.findById(8L)).thenReturn(java.util.Optional.of(requisition));

    service.submit(8L, employee);
    PurchaseRequisition decided =
        service.decide(8L, new ApprovalDecisionRequest(false, "Not needed"), manager);

    assertThat(decided.getStatus()).isEqualTo(RequisitionStatus.REJECTED);
  }

  @Test
  void wrongRoleCannotDecideOnAStep() {
    PurchaseRequisition requisition = createDraft(new BigDecimal("50.00"), 1);
    requisition.setId(9L);
    when(requisitionRepository.findById(9L)).thenReturn(java.util.Optional.of(requisition));
    service.submit(9L, employee);

    assertThatThrownBy(() -> service.decide(9L, new ApprovalDecisionRequest(true, null), employee))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  void employeeOnlySeesTheirOwnRequisitions() {
    List<PurchaseRequisition> own = List.of(createDraft(new BigDecimal("10.00"), 1));
    when(requisitionRepository.findByRequesterId(employee.getId())).thenReturn(own);

    assertThat(service.findVisibleTo(employee)).isEqualTo(own);
  }

  @Test
  void departmentManagerSeesTheirDepartmentsRequisitions() {
    List<PurchaseRequisition> departmentRequisitions =
        List.of(createDraft(new BigDecimal("10.00"), 1));
    when(requisitionRepository.findByDepartmentId(department.getId()))
        .thenReturn(departmentRequisitions);

    assertThat(service.findVisibleTo(manager)).isEqualTo(departmentRequisitions);
  }

  @Test
  void procurementOfficerSeesEveryRequisition() {
    User procurementOfficer =
        User.builder()
            .email("procurement@test.local")
            .firstName("Pat")
            .lastName("Procurement")
            .roles(EnumSet.of(RoleName.ROLE_PROCUREMENT_OFFICER))
            .build();
    procurementOfficer.setId(3L);
    List<PurchaseRequisition> all = List.of(createDraft(new BigDecimal("10.00"), 1));
    when(requisitionRepository.findAll()).thenReturn(all);

    assertThat(service.findVisibleTo(procurementOfficer)).isEqualTo(all);
  }

  @Test
  void findVisibleByIdDeniesAStrangerOutsideTheOwnersDepartment() {
    PurchaseRequisition requisition = createDraft(new BigDecimal("10.00"), 1);
    requisition.setId(11L);
    when(requisitionRepository.findById(11L)).thenReturn(java.util.Optional.of(requisition));

    Department otherDepartment = Department.builder().code("SALES").name("Sales").build();
    otherDepartment.setId(20L);
    User stranger =
        User.builder()
            .email("stranger@test.local")
            .firstName("Sam")
            .lastName("Stranger")
            .roles(EnumSet.of(RoleName.ROLE_EMPLOYEE))
            .department(otherDepartment)
            .build();
    stranger.setId(4L);

    assertThatThrownBy(() -> service.findVisibleById(11L, stranger))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void findVisibleByIdAllowsTheOwnersDepartmentManager() {
    PurchaseRequisition requisition = createDraft(new BigDecimal("10.00"), 1);
    requisition.setId(12L);
    when(requisitionRepository.findById(12L)).thenReturn(java.util.Optional.of(requisition));

    assertThat(service.findVisibleById(12L, manager)).isEqualTo(requisition);
  }
}
