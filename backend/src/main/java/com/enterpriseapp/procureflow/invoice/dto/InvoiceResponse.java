package com.enterpriseapp.procureflow.invoice.dto;

import com.enterpriseapp.procureflow.invoice.Invoice;
import com.enterpriseapp.procureflow.invoice.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
    Long id,
    Long purchaseOrderId,
    String poNumber,
    String invoiceNumber,
    BigDecimal amount,
    InvoiceStatus status,
    Instant receivedAt) {

  public static InvoiceResponse from(Invoice invoice) {
    return new InvoiceResponse(
        invoice.getId(),
        invoice.getPurchaseOrder().getId(),
        invoice.getPurchaseOrder().getPoNumber(),
        invoice.getInvoiceNumber(),
        invoice.getAmount(),
        invoice.getStatus(),
        invoice.getReceivedAt());
  }
}
