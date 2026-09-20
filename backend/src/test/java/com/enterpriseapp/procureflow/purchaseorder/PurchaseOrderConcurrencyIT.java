package com.enterpriseapp.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseapp.procureflow.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regression test for the PO-number race: {@code PurchaseOrderService.generatePoNumber()} used to
 * derive the number from {@code purchaseOrderRepository.count() + 1}, a non-atomic read-then-format
 * that lets two concurrent conversions compute the same sequence value. Fixed by generating the
 * number from a Postgres sequence (V3__purchase_order_number_sequence.sql), which guarantees a
 * distinct value per caller regardless of transaction overlap.
 *
 * <p>Converts several independently-approved requisitions to purchase orders from concurrent
 * threads, released simultaneously via a {@link CyclicBarrier} to maximize overlap, and asserts
 * every resulting {@code poNumber} is unique and every request succeeded.
 */
class PurchaseOrderConcurrencyIT extends AbstractIntegrationTest {

  private static final int CONCURRENT_CONVERSIONS = 8;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void concurrentConversionsNeverProduceDuplicatePoNumbers() throws Exception {
    String employeeToken = login("employee@procureflow.test");
    String managerToken = login("manager@procureflow.test");
    String procurementToken = login("procurement@procureflow.test");

    Long departmentId = findDepartmentIdByCode(employeeToken, "ENG");
    Long catalogItemId = findCatalogItemIdBySku(employeeToken, "OFF-CHAIR-01");
    Long vendorId = findVendorIdForCatalogItem(employeeToken, catalogItemId);

    List<Long> requisitionIds = new ArrayList<>();
    for (int i = 0; i < CONCURRENT_CONVERSIONS; i++) {
      long requisitionId =
          createApprovedRequisition(employeeToken, managerToken, departmentId, catalogItemId);
      requisitionIds.add(requisitionId);
    }

    CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_CONVERSIONS);
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CONVERSIONS);
    try {
      List<Callable<MvcResult>> tasks =
          requisitionIds.stream()
              .<Callable<MvcResult>>map(
                  requisitionId ->
                      () -> {
                        barrier.await();
                        return convert(procurementToken, requisitionId, vendorId);
                      })
              .toList();

      List<Future<MvcResult>> futures = executor.invokeAll(tasks);
      List<String> poNumbers = new ArrayList<>();
      for (Future<MvcResult> future : futures) {
        MvcResult result = future.get(30, TimeUnit.SECONDS);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        poNumbers.add(readTree(result).get("poNumber").asText());
      }

      assertThat(poNumbers).hasSize(CONCURRENT_CONVERSIONS);
      assertThat(poNumbers.stream().collect(Collectors.toSet()))
          .as("every concurrently-issued PO number must be unique")
          .hasSize(CONCURRENT_CONVERSIONS);
    } finally {
      executor.shutdownNow();
    }
  }

  private long createApprovedRequisition(
      String employeeToken, String managerToken, Long departmentId, Long catalogItemId)
      throws Exception {
    String createBody =
        objectMapper.writeValueAsString(
            new CreateRequisitionPayload(
                departmentId,
                "Office chair for new hire",
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

  private MvcResult convert(String token, Long requisitionId, Long vendorId) throws Exception {
    String convertBody = objectMapper.writeValueAsString(new ConvertPayload(vendorId));
    return mockMvc
        .perform(
            post("/api/v1/requisitions/" + requisitionId + "/convert-to-po")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody))
        .andReturn();
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
