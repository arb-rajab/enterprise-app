package com.enterpriseapp.procureflow.requisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseapp.procureflow.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regression test for the documented row-level read gap (see 06-security.md, "Known gap, accepted
 * for this demo"): {@code GET /api/v1/requisitions} used to return every requisition to any
 * authenticated user, and {@code GET /api/v1/requisitions/{id}} let anyone fetch any requisition by
 * id, regardless of department or ownership. Verifies the fix against real seeded accounts (see
 * V2__seed_reference_data.sql): employee/manager are both in ENG, a freshly-registered outsider is
 * placed in SALES.
 */
class RequisitionReadScopingIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void listAndDetailEndpointsAreScopedByRoleAndDepartment() throws Exception {
    String employeeToken = login("employee@procureflow.test");
    String managerToken = login("manager@procureflow.test");
    String procurementToken = login("procurement@procureflow.test");

    Long engDepartmentId = findDepartmentIdByCode(employeeToken, "ENG");
    Long salesDepartmentId = findDepartmentIdByCode(employeeToken, "SALES");
    String outsiderToken = registerAndLogin("outsider@procureflow.test", salesDepartmentId);

    long requisitionId = createDraftRequisition(employeeToken, engDepartmentId);

    // The requester, their department manager, and org-wide approval roles can all fetch it.
    mockMvc
        .perform(
            get("/api/v1/requisitions/" + requisitionId)
                .header("Authorization", "Bearer " + employeeToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/requisitions/" + requisitionId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/requisitions/" + requisitionId)
                .header("Authorization", "Bearer " + procurementToken))
        .andExpect(status().isOk());

    // A user with no ownership or department relationship to it is forbidden.
    mockMvc
        .perform(
            get("/api/v1/requisitions/" + requisitionId)
                .header("Authorization", "Bearer " + outsiderToken))
        .andExpect(status().isForbidden());

    // The list endpoint is scoped the same way: the outsider's list never contains it...
    assertThat(requisitionIdsVisibleTo(outsiderToken)).doesNotContain(requisitionId);
    // ...but the requester's, the department manager's, and procurement's all do.
    assertThat(requisitionIdsVisibleTo(employeeToken)).contains(requisitionId);
    assertThat(requisitionIdsVisibleTo(managerToken)).contains(requisitionId);
    assertThat(requisitionIdsVisibleTo(procurementToken)).contains(requisitionId);
  }

  private List<Long> requisitionIdsVisibleTo(String token) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/requisitions").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    List<Long> ids = new ArrayList<>();
    StreamSupport.stream(readTree(result).spliterator(), false)
        .forEach(node -> ids.add(node.get("id").asLong()));
    return ids;
  }

  private long createDraftRequisition(String employeeToken, Long departmentId) throws Exception {
    String createBody =
        objectMapper.writeValueAsString(
            new CreateRequisitionPayload(
                departmentId,
                "Scoping test requisition",
                new LineItemPayload[] {new LineItemPayload(null, "Notebook", 1, "19.99")}));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/requisitions")
                    .header("Authorization", "Bearer " + employeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    return readTree(createResult).get("id").asLong();
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

  private String registerAndLogin(String email, Long departmentId) throws Exception {
    String registerBody =
        objectMapper.writeValueAsString(
            new RegisterPayload(email, "Password123!", "Otto", "Outsider", departmentId));
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
        .andExpect(status().isCreated());
    return login(email);
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

  private JsonNode readTree(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private record CreateRequisitionPayload(
      Long departmentId, String justification, LineItemPayload[] lineItems) {}

  private record LineItemPayload(
      Long catalogItemId, String description, int quantity, String unitPrice) {}

  private record RegisterPayload(
      String email, String password, String firstName, String lastName, Long departmentId) {}
}
