export type RequisitionStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CONVERTED' | 'CANCELLED';
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface LineItem {
  id: number;
  catalogItemId: number | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface ApprovalStep {
  id: number;
  stepOrder: number;
  approverRole: string;
  status: ApprovalStatus;
  decidedByUserId: number | null;
  comments: string | null;
  decidedAt: string | null;
}

export interface Requisition {
  id: number;
  requesterId: number;
  requesterName: string;
  departmentId: number;
  justification: string | null;
  status: RequisitionStatus;
  totalAmount: number;
  lineItems: LineItem[];
  approvalSteps: ApprovalStep[];
  createdAt: string;
  updatedAt: string;
}

export interface LineItemRequest {
  catalogItemId: number | null;
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface CreateRequisitionRequest {
  departmentId: number;
  justification?: string | null;
  lineItems: LineItemRequest[];
}

export interface ApprovalDecisionRequest {
  approve: boolean;
  comments?: string | null;
}
