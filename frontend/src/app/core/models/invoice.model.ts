export type InvoiceStatus = 'RECEIVED' | 'APPROVED' | 'PAID' | 'DISPUTED';

export interface Invoice {
  id: number;
  purchaseOrderId: number;
  poNumber: string;
  invoiceNumber: string;
  amount: number;
  status: InvoiceStatus;
  receivedAt: string;
}

export interface InvoiceRequest {
  purchaseOrderId: number;
  invoiceNumber: string;
  amount: number;
}
