package com.enterpriseapp.procureflow.catalog;

import com.enterpriseapp.procureflow.catalog.dto.CatalogItemRequest;
import com.enterpriseapp.procureflow.common.exception.DuplicateResourceException;
import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.vendor.Vendor;
import com.enterpriseapp.procureflow.vendor.VendorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogItemService {

  private final CatalogItemRepository catalogItemRepository;
  private final VendorService vendorService;

  public List<CatalogItem> findAll() {
    return catalogItemRepository.findAll();
  }

  public CatalogItem findById(Long id) {
    return catalogItemRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("CatalogItem", id));
  }

  @Transactional
  public CatalogItem create(CatalogItemRequest request) {
    if (catalogItemRepository.existsBySku(request.sku())) {
      throw new DuplicateResourceException(
          "A catalog item with SKU " + request.sku() + " already exists");
    }
    Vendor vendor = vendorService.findById(request.vendorId());
    CatalogItem item =
        CatalogItem.builder()
            .sku(request.sku())
            .name(request.name())
            .description(request.description())
            .category(request.category())
            .unitPrice(request.unitPrice())
            .vendor(vendor)
            .build();
    return catalogItemRepository.save(item);
  }

  @Transactional
  public CatalogItem update(Long id, CatalogItemRequest request) {
    CatalogItem item = findById(id);
    Vendor vendor = vendorService.findById(request.vendorId());
    item.setName(request.name());
    item.setDescription(request.description());
    item.setCategory(request.category());
    item.setUnitPrice(request.unitPrice());
    item.setVendor(vendor);
    return item;
  }

  @Transactional
  public void delete(Long id) {
    if (!catalogItemRepository.existsById(id)) {
      throw ResourceNotFoundException.of("CatalogItem", id);
    }
    catalogItemRepository.deleteById(id);
  }
}
