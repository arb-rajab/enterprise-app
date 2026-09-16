package com.enterpriseapp.procureflow.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {
  boolean existsBySku(String sku);
}
