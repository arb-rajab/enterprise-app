package com.enterpriseapp.procureflow.purchaseorder;

import com.enterpriseapp.procureflow.purchaseorder.dto.ConvertToPurchaseOrderRequest;
import com.enterpriseapp.procureflow.purchaseorder.dto.PurchaseOrderResponse;
import com.enterpriseapp.procureflow.user.User;
import com.enterpriseapp.procureflow.user.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PurchaseOrderController {

  private final PurchaseOrderService purchaseOrderService;
  private final UserService userService;

  @GetMapping("/purchase-orders")
  public List<PurchaseOrderResponse> findAll(Authentication authentication) {
    User viewer = userService.findByEmail(authentication.getName());
    return purchaseOrderService.findVisibleTo(viewer).stream()
        .map(PurchaseOrderResponse::from)
        .toList();
  }

  @GetMapping("/purchase-orders/{id}")
  public PurchaseOrderResponse findById(@PathVariable Long id, Authentication authentication) {
    User viewer = userService.findByEmail(authentication.getName());
    return PurchaseOrderResponse.from(purchaseOrderService.findVisibleById(id, viewer));
  }

  @PostMapping("/requisitions/{requisitionId}/convert-to-po")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public ResponseEntity<PurchaseOrderResponse> convert(
      @PathVariable Long requisitionId,
      @Valid @RequestBody ConvertToPurchaseOrderRequest request,
      Authentication authentication) {
    User actor = userService.findByEmail(authentication.getName());
    PurchaseOrder created =
        purchaseOrderService.convertFromRequisition(requisitionId, request, actor);
    return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + created.getId()))
        .body(PurchaseOrderResponse.from(created));
  }

  @PostMapping("/purchase-orders/{id}/acknowledge")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public PurchaseOrderResponse acknowledge(@PathVariable Long id, Authentication authentication) {
    return PurchaseOrderResponse.from(
        purchaseOrderService.updateStatus(
            id,
            PurchaseOrderStatus.ACKNOWLEDGED,
            userService.findByEmail(authentication.getName())));
  }

  @PostMapping("/purchase-orders/{id}/fulfill")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public PurchaseOrderResponse fulfill(@PathVariable Long id, Authentication authentication) {
    return PurchaseOrderResponse.from(
        purchaseOrderService.updateStatus(
            id, PurchaseOrderStatus.FULFILLED, userService.findByEmail(authentication.getName())));
  }
}
