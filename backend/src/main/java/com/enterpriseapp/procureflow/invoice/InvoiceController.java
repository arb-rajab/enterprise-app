package com.enterpriseapp.procureflow.invoice;

import com.enterpriseapp.procureflow.invoice.dto.InvoiceRequest;
import com.enterpriseapp.procureflow.invoice.dto.InvoiceResponse;
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
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

  private final InvoiceService invoiceService;
  private final UserService userService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER','FINANCE_APPROVER')")
  public List<InvoiceResponse> findAll() {
    return invoiceService.findAll().stream().map(InvoiceResponse::from).toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER','FINANCE_APPROVER')")
  public InvoiceResponse findById(@PathVariable Long id) {
    return InvoiceResponse.from(invoiceService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public ResponseEntity<InvoiceResponse> record(
      @Valid @RequestBody InvoiceRequest request, Authentication authentication) {
    User actor = userService.findByEmail(authentication.getName());
    Invoice created = invoiceService.record(request, actor);
    return ResponseEntity.created(URI.create("/api/v1/invoices/" + created.getId()))
        .body(InvoiceResponse.from(created));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('ADMIN','FINANCE_APPROVER')")
  public InvoiceResponse approve(@PathVariable Long id, Authentication authentication) {
    return InvoiceResponse.from(
        invoiceService.transition(
            id, InvoiceStatus.APPROVED, userService.findByEmail(authentication.getName())));
  }

  @PostMapping("/{id}/pay")
  @PreAuthorize("hasAnyRole('ADMIN','FINANCE_APPROVER')")
  public InvoiceResponse pay(@PathVariable Long id, Authentication authentication) {
    return InvoiceResponse.from(
        invoiceService.transition(
            id, InvoiceStatus.PAID, userService.findByEmail(authentication.getName())));
  }

  @PostMapping("/{id}/dispute")
  @PreAuthorize("hasAnyRole('ADMIN','FINANCE_APPROVER')")
  public InvoiceResponse dispute(@PathVariable Long id, Authentication authentication) {
    return InvoiceResponse.from(
        invoiceService.transition(
            id, InvoiceStatus.DISPUTED, userService.findByEmail(authentication.getName())));
  }
}
