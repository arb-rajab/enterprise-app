export interface Department {
  id: number;
  code: string;
  name: string;
  costCenter: string | null;
  managerUserId: number | null;
}

export interface DepartmentRequest {
  code: string;
  name: string;
  costCenter?: string | null;
  managerUserId?: number | null;
}
