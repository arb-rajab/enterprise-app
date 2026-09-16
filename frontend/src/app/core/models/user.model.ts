export type RoleName =
  | 'ROLE_ADMIN'
  | 'ROLE_EMPLOYEE'
  | 'ROLE_DEPARTMENT_MANAGER'
  | 'ROLE_PROCUREMENT_OFFICER'
  | 'ROLE_FINANCE_APPROVER';

export interface UserSummary {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  departmentId: number | null;
  roles: RoleName[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  departmentId?: number | null;
}
