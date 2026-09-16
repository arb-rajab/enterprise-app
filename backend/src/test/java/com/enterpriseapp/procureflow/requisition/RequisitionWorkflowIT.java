package com.enterpriseapp.procureflow.requisition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseapp.procureflow.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end test of the full requisition lifecycle against a real, migrated Postgres database:
 * draft -> submit -> multi-step approval -> conversion to a purchase order. Exercises the seeded
 * demo accounts (see V2__seed_reference_data.sql) so the RBAC rules are checked with real JWTs, not
 * mocked authorities.
 */
class RequisitionWorkflowIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void midRangeRequisitionRequiresManagerThenProcurementApprovalBeforeConversion()
      throws Exception {
    String employeeToken = login("employee@procureflow.test");
    String managerToken = login("manager@procureflow.test");
    String procurementToken = login("procurement@procureflow.test");

    Long departmentId = findDepartmentIdByCode(employeeToken, "ENG");
    Long catalogItemId = findCatalogItemIdBySku(employeeToken, "HW-LAPTOP-14");
    Long vendorId = findVendorIdForCatalogItem(employeeToken, catalogItemId);

    String createBody =
        objectMapper.writeValueAsString(
            new CreateRequisitionPayload(
                departmentId,
                "New laptops for onboarding",
                new LineItemPayload[] {
                  new LineItemPayload(catalogItemId, "Developer laptops", 3, "1899.00")
                }));

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/requisitions")
                    .header("Authorization", "Bearer " + employeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalAmount").value(5697.0))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();
    long requisitionId = readTree(createResult).get("id").asLong();

    mockMvc
        .perform(
            post("/api/v1/requisitions/" + requisitionId + "/submit")
                .header("Authorization", "Bearer " + employeeToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.approvalSteps.length()").value(2));

    // The department manager is the first pending approver.
    mockMvc
        .perform(
            post("/api/v1/requisitions/" + requisitionId + "/decide")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\": true, \"comments\": \"Approved by manager\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"));

    // The procurement officer is the second and final approver.
    mockMvc
        .perform(
            post("/api/v1/requisitions/" + requisitionId + "/decide")
                .header("Authorization", "Bearer " + procurementToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\": true, \"comments\": \"Approved by procurement\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    String convertBody = objectMapper.writeValueAsString(new ConvertPayload(vendorId));
    mockMvc
        .perform(
            post("/api/v1/requisitions/" + requisitionId + "/convert-to-po")
                .header("Authorization", "Bearer " + procurementToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.poNumber").isNotEmpty())
        .andExpect(jsonPath("$.totalAmount").value(5697.0));

    mockMvc
        .perform(
            get("/api/v1/requisitions/" + requisitionId)
                .header("Authorization", "Bearer " + employeeToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONVERTED"));
  }

  @Test
  void employeeCannotApproveTheirOwnRequisition() throws Exception {
    String employeeToken = login("employee@procureflow.test");
    Long departmentId = findDepartmentIdByCode(employeeToken, "ENG");

    String createBody =
        objectMapper.writeValueAsString(
            new CreateRequisitionPayload(
                departmentId,
                "Office chair",
                new LineItemPayload[] {new LineItemPayload(null, "Chair", 1, "249.99")}));
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
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\": true}"))
        .andExpect(status().isConflict());
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
}
