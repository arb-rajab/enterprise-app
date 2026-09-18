package com.enterpriseapp.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.enterpriseapp.procureflow.audit.AuditService;
import com.enterpriseapp.procureflow.department.Department;
import com.enterpriseapp.procureflow.requisition.PurchaseRequisition;
import com.enterpriseapp.procureflow.requisition.RequisitionService;
import com.enterpriseapp.procureflow.user.ReadScopePolicy;
import com.enterpriseapp.procureflow.user.RoleName;
import com.enterpriseapp.procureflow.user.User;
import com.enterpriseapp.procureflow.vendor.Vendor;
import com.enterpriseapp.procureflow.vendor.VendorService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * Previously {@code PurchaseOrderController.findAll()/findById()} had no scoping at all - any
 * authenticated user, of any role, could list or fetch any purchase order. These tests pin down the
 * fix: visibility now mirrors {@code RequisitionService}'s read scoping (org-wide roles see
 * everything, a department manager sees their department's orders via the requisition they were
 * raised from, everyone else sees only their own).
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

  @Mock private PurchaseOrderRepository purchaseOrderRepository;

  @Mock private RequisitionService requisitionService;

  @Mock private VendorService vendorService;

  @Mock private AuditService auditService;

  private PurchaseOrderService service;

  private Department engineering;
  private Department sales;
  private User requester;
  private User departmentManager;
  private User stranger;
  private PurchaseOrder order;

  @BeforeEach
  void setUp() {
    service =
        new PurchaseOrderService(
            purchaseOrderRepository,
            requisitionService,
            vendorService,
            auditService,
            new ReadScopePolicy());

    engineering = Department.builder().code("ENG").name("Engineering").build();
    engineering.setId(10L);
    sales = Department.builder().code("SALES").name("Sales").build();
    sales.setId(20L);

    requester =
        User.builder()
            .email("requester@test.local")
            .firstName("Rae")
            .lastName("Requester")
            .roles(EnumSet.of(RoleName.ROLE_EMPLOYEE))
            .department(engineering)
            .build();
    requester.setId(1L);

    departmentManager =
        User.builder()
            .email("manager@test.local")
            .firstName("Morgan")
            .lastName("Manager")
            .roles(EnumSet.of(RoleName.ROLE_DEPARTMENT_MANAGER))
            .department(engineering)
            .build();
    departmentManager.setId(2L);

    stranger =
        User.builder()
            .email("stranger@test.local")
            .firstName("Sam")
            .lastName("Stranger")
            .roles(EnumSet.of(RoleName.ROLE_EMPLOYEE))
            .department(sales)
            .build();
    stranger.setId(3L);

    PurchaseRequisition requisition =
        PurchaseRequisition.builder().requester(requester).department(engineering).build();
    requisition.setId(100L);

    order =
        PurchaseOrder.builder()
            .requisition(requisition)
            .vendor(Vendor.builder().name("Acme").build())
            .poNumber("PO-000001")
            .totalAmount(new BigDecimal("500.00"))
            .status(PurchaseOrderStatus.ISSUED)
            .issuedAt(Instant.now())
            .build();
    order.setId(200L);
  }

  @Test
  void requesterCanSeeTheirOwnPurchaseOrder() {
    when(purchaseOrderRepository.findById(200L)).thenReturn(Optional.of(order));

    assertThat(service.findVisibleById(200L, requester)).isEqualTo(order);
  }

  @Test
  void departmentManagerCanSeeAPurchaseOrderFromTheirDepartment() {
    when(purchaseOrderRepository.findById(200L)).thenReturn(Optional.of(order));

    assertThat(service.findVisibleById(200L, departmentManager)).isEqualTo(order);
  }

  @Test
  void strangerFromAnotherDepartmentIsDenied() {
    when(purchaseOrderRepository.findById(200L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> service.findVisibleById(200L, stranger))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void procurementOfficerSeesEveryPurchaseOrder() {
    User procurementOfficer =
        User.builder()
            .email("procurement@test.local")
            .firstName("Pat")
            .lastName("Procurement")
            .roles(EnumSet.of(RoleName.ROLE_PROCUREMENT_OFFICER))
            .build();
    procurementOfficer.setId(4L);
    List<PurchaseOrder> all = List.of(order);
    when(purchaseOrderRepository.findAll()).thenReturn(all);

    assertThat(service.findVisibleTo(procurementOfficer)).isEqualTo(all);
  }

  @Test
  void employeeListingIsScopedToTheirOwnPurchaseOrders() {
    List<PurchaseOrder> own = List.of(order);
    when(purchaseOrderRepository.findByRequisition_Requester_Id(requester.getId())).thenReturn(own);

    assertThat(service.findVisibleTo(requester)).isEqualTo(own);
  }
}
