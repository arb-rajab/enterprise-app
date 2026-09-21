package com.enterpriseapp.procureflow.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.GetPurchaseOrderRequest;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.ListPurchaseOrdersRequest;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.PurchaseOrderGrpcServiceGrpc;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.PurchaseOrderGrpcServiceGrpc.PurchaseOrderGrpcServiceBlockingStub;
import com.enterpriseapp.procureflow.grpc.purchaseorder.v1.PurchaseOrderStatusResponse;
import com.enterpriseapp.procureflow.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves the gRPC purchase-order API (docs/project-memory/adr/0009-grpc-purchase-order-api.md)
 * enforces the exact same authentication and department-scoped read authorization as REST, against
 * a real generated gRPC client/server pair (no MockMvc, no hand-rolled protocol stand-in).
 *
 * <p>The key regression this guards is the read-path analog of the cross-department bypass ADR-
 * 0007 fixed on the REST write (approval) path: a Department Manager from a department other than
 * the requisition's must be denied, over gRPC exactly as over REST (ADR-0005's {@code
 * ReadScopePolicy}), rather than a second, gRPC-only scoping check that could silently diverge.
 */
class PurchaseOrderGrpcAuthorizationIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private GrpcServerLifecycle grpcServerLifecycle;

  private ManagedChannel channel;

  @BeforeEach
  void openChannel() {
    channel =
        ManagedChannelBuilder.forAddress("localhost", grpcServerLifecycle.getPort())
            .usePlaintext()
            .build();
  }

  @AfterEach
  void closeChannel() throws InterruptedException {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  void enforcesAuthenticationAndCrossDepartmentReadScopingIdenticallyToRest() throws Exception {
    String employeeToken = login("employee@procureflow.test");
    String engManagerToken = login("manager@procureflow.test");
    String procurementToken = login("procurement@procureflow.test");
    String adminToken = login("admin@procureflow.test");

    Long engDepartmentId = findDepartmentIdByCode(adminToken, "ENG");
    Long salesDepartmentId = findDepartmentIdByCode(adminToken, "SALES");
    Long catalogItemId = findCatalogItemIdBySku(adminToken, "OFF-CHAIR-01");
    Long vendorId = findVendorIdForCatalogItem(adminToken, catalogItemId);
    String salesManagerToken =
        registerDepartmentManager(adminToken, "sales.manager@procureflow.test", salesDepartmentId);

    long requisitionId =
        createApprovedRequisition(employeeToken, engManagerToken, engDepartmentId, catalogItemId);
    long purchaseOrderId = convertToPurchaseOrder(procurementToken, requisitionId, vendorId);

    // No token at all: rejected before any scoping decision is even made.
    assertThat(
            statusCodeOf(() -> unauthenticatedStub().getPurchaseOrder(getRequest(purchaseOrderId))))
        .isEqualTo(Status.Code.UNAUTHENTICATED);

    // The requester, their department manager, and an org-wide role can all read it via gRPC -
    // exactly who REST's PurchaseOrderService.findVisibleById already allows.
    assertThat(stubFor(employeeToken).getPurchaseOrder(getRequest(purchaseOrderId)).getId())
        .isEqualTo(purchaseOrderId);
    assertThat(stubFor(engManagerToken).getPurchaseOrder(getRequest(purchaseOrderId)).getId())
        .isEqualTo(purchaseOrderId);
    assertThat(stubFor(procurementToken).getPurchaseOrder(getRequest(purchaseOrderId)).getId())
        .isEqualTo(purchaseOrderId);

    // The regression under test: a Department Manager from a DIFFERENT department than the
    // requisition's is denied - the read-path sibling of the write-path bypass ADR-0007 fixed.
    assertThat(
            statusCodeOf(
                () -> stubFor(salesManagerToken).getPurchaseOrder(getRequest(purchaseOrderId))))
        .isEqualTo(Status.Code.PERMISSION_DENIED);

    // The streaming list endpoint is scoped the same way: it never contains the other
    // department's order for the cross-department manager...
    assertThat(purchaseOrderIdsVisibleTo(salesManagerToken)).doesNotContain(purchaseOrderId);
    // ...but does for the requester, their manager, and procurement.
    assertThat(purchaseOrderIdsVisibleTo(employeeToken)).contains(purchaseOrderId);
    assertThat(purchaseOrderIdsVisibleTo(engManagerToken)).contains(purchaseOrderId);
    assertThat(purchaseOrderIdsVisibleTo(procurementToken)).contains(purchaseOrderId);
  }

  private Status.Code statusCodeOf(Runnable grpcCall) {
    try {
      grpcCall.run();
    } catch (StatusRuntimeException ex) {
      return ex.getStatus().getCode();
    }
    throw new AssertionError("Expected call to fail with a StatusRuntimeException");
  }

  private List<Long> purchaseOrderIdsVisibleTo(String token) {
    Iterator<PurchaseOrderStatusResponse> responses =
        stubFor(token).listPurchaseOrders(ListPurchaseOrdersRequest.getDefaultInstance());
    List<Long> ids = new ArrayList<>();
    responses.forEachRemaining(response -> ids.add(response.getId()));
    return ids;
  }

  private GetPurchaseOrderRequest getRequest(long id) {
    return GetPurchaseOrderRequest.newBuilder().setId(id).build();
  }

  private PurchaseOrderGrpcServiceBlockingStub stubFor(String token) {
    Metadata headers = new Metadata();
    headers.put(
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
    return unauthenticatedStub()
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
  }

  private PurchaseOrderGrpcServiceBlockingStub unauthenticatedStub() {
    return PurchaseOrderGrpcServiceGrpc.newBlockingStub(channel);
  }

  private long convertToPurchaseOrder(String procurementToken, long requisitionId, Long vendorId)
      throws Exception {
    String convertBody = objectMapper.writeValueAsString(new ConvertPayload(vendorId));
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/requisitions/" + requisitionId + "/convert-to-po")
                    .header("Authorization", "Bearer " + procurementToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(convertBody))
            .andExpect(status().isCreated())
            .andReturn();
    return readTree(result).get("id").asLong();
  }

  private long createApprovedRequisition(
      String employeeToken, String managerToken, Long departmentId, Long catalogItemId)
      throws Exception {
    String createBody =
        objectMapper.writeValueAsString(
            new CreateRequisitionPayload(
                departmentId,
                "gRPC scoping test requisition",
                new LineItemPayload[] {
                  new LineItemPayload(catalogItemId, "Office chair", 1, "249.99")
                }));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/requisitions")
                    .header("Authorization", "Bearer " + employeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    long requisitionId = readTree(createResult).get("id").asLong();

    mockMvc
        .perform(
            post("/api/v1/requisitions/" + requisitionId + "/submit")
                .header("Authorization", "Bearer " + employeeToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/requisitions/" + requisitionId + "/decide")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\": true, \"comments\": \"Approved\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    return requisitionId;
  }

  /**
   * Registers a brand-new user in {@code departmentId}, promotes them to Department Manager (as an
   * admin would), and returns a freshly-logged-in token carrying that role.
   */
  private String registerDepartmentManager(String adminToken, String email, Long departmentId)
      throws Exception {
    String registerBody =
        objectMapper.writeValueAsString(
            new RegisterPayload(email, "Password123!", "Sam", "SalesManager", departmentId));
    MvcResult registerResult =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody))
            .andExpect(status().isCreated())
            .andReturn();
    long userId = readTree(registerResult).get("user").get("id").asLong();

    mockMvc
        .perform(
            put("/api/v1/users/" + userId + "/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\": [\"ROLE_DEPARTMENT_MANAGER\"]}"))
        .andExpect(status().isOk());

    return login(email);
  }

  private String login(String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"" + email + "\", \"password\": \"Password123!\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return readTree(result).get("accessToken").asText();
  }

  private Long findDepartmentIdByCode(String token, String code) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/departments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    for (JsonNode node : readTree(result)) {
      if (node.get("code").asText().equals(code)) {
        return node.get("id").asLong();
      }
    }
    throw new IllegalStateException("Seed department " + code + " not found");
  }

  private Long findCatalogItemIdBySku(String token, String sku) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/catalog-items").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    for (JsonNode node : readTree(result)) {
      if (node.get("sku").asText().equals(sku)) {
        return node.get("id").asLong();
      }
    }
    throw new IllegalStateException("Seed catalog item " + sku + " not found");
  }

  private Long findVendorIdForCatalogItem(String token, Long catalogItemId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/catalog-items/" + catalogItemId)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return readTree(result).get("vendorId").asLong();
  }

  private JsonNode readTree(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private record CreateRequisitionPayload(
      Long departmentId, String justification, LineItemPayload[] lineItems) {}

  private record LineItemPayload(
      Long catalogItemId, String description, int quantity, String unitPrice) {}

  private record ConvertPayload(Long vendorId) {}

  private record RegisterPayload(
      String email, String password, String firstName, String lastName, Long departmentId) {}
}
