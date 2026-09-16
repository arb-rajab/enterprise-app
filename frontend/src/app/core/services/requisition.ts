import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApprovalDecisionRequest,
  CreateRequisitionRequest,
  Requisition,
} from '../models/requisition.model';

@Injectable({ providedIn: 'root' })
export class RequisitionService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = `${environment.apiBaseUrl}/requisitions`;

  findAll(): Observable<Requisition[]> {
    return this.http.get<Requisition[]>(this.baseUrl);
  }

  findPendingMyApproval(): Observable<Requisition[]> {
    return this.http.get<Requisition[]>(`${this.baseUrl}/pending-my-approval`);
  }

  findById(id: number): Observable<Requisition> {
    return this.http.get<Requisition>(`${this.baseUrl}/${id}`);
  }

  create(request: CreateRequisitionRequest): Observable<Requisition> {
    return this.http.post<Requisition>(this.baseUrl, request);
  }

  submit(id: number): Observable<Requisition> {
    return this.http.post<Requisition>(`${this.baseUrl}/${id}/submit`, {});
  }

  decide(id: number, decision: ApprovalDecisionRequest): Observable<Requisition> {
    return this.http.post<Requisition>(`${this.baseUrl}/${id}/decide`, decision);
  }

  cancel(id: number): Observable<Requisition> {
    return this.http.post<Requisition>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
