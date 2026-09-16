export type PurchaseOrderStatus = 'ISSUED' | 'ACKNOWLEDGED' | 'FULFILLED' | 'CANCELLED';

export interface PurchaseOrder {
  id: number;
  poNumber: string;
  requisitionId: number;
  vendorId: number;
  vendorName: string;
  totalAmount: number;
  status: PurchaseOrderStatus;
  issuedAt: string;
}

export interface ConvertToPurchaseOrderRequest {
  vendorId: number;
}
