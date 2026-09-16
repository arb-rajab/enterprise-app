export type VendorStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'INACTIVE';

export interface Vendor {
  id: number;
  name: string;
  contactEmail: string;
  phone: string | null;
  address: string | null;
  taxId: string | null;
  status: VendorStatus;
}

export interface VendorRequest {
  name: string;
  contactEmail: string;
  phone?: string | null;
  address?: string | null;
  taxId?: string | null;
}
