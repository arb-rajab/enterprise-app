package com.enterpriseapp.procureflow.vendor;

import com.enterpriseapp.procureflow.vendor.dto.VendorRequest;
import com.enterpriseapp.procureflow.vendor.dto.VendorResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

  private final VendorService vendorService;

  @GetMapping
  public List<VendorResponse> findAll() {
    return vendorService.findAll().stream().map(VendorResponse::from).toList();
  }

  @GetMapping("/{id}")
  public VendorResponse findById(@PathVariable Long id) {
    return VendorResponse.from(vendorService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public ResponseEntity<VendorResponse> create(@Valid @RequestBody VendorRequest request) {
    Vendor created = vendorService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/vendors/" + created.getId()))
        .body(VendorResponse.from(created));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public VendorResponse update(@PathVariable Long id, @Valid @RequestBody VendorRequest request) {
    return VendorResponse.from(vendorService.update(id, request));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public VendorResponse approve(@PathVariable Long id) {
    return VendorResponse.from(vendorService.approve(id));
  }

  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public VendorResponse deactivate(@PathVariable Long id) {
    return VendorResponse.from(vendorService.deactivate(id));
  }
}
