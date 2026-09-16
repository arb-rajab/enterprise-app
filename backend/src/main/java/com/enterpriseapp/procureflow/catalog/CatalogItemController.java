package com.enterpriseapp.procureflow.catalog;

import com.enterpriseapp.procureflow.catalog.dto.CatalogItemRequest;
import com.enterpriseapp.procureflow.catalog.dto.CatalogItemResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog-items")
@RequiredArgsConstructor
public class CatalogItemController {

  private final CatalogItemService catalogItemService;

  @GetMapping
  public List<CatalogItemResponse> findAll() {
    return catalogItemService.findAll().stream().map(CatalogItemResponse::from).toList();
  }

  @GetMapping("/{id}")
  public CatalogItemResponse findById(@PathVariable Long id) {
    return CatalogItemResponse.from(catalogItemService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public ResponseEntity<CatalogItemResponse> create(
      @Valid @RequestBody CatalogItemRequest request) {
    CatalogItem created = catalogItemService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/catalog-items/" + created.getId()))
        .body(CatalogItemResponse.from(created));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public CatalogItemResponse update(
      @PathVariable Long id, @Valid @RequestBody CatalogItemRequest request) {
    return CatalogItemResponse.from(catalogItemService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    catalogItemService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
