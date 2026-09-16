import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ConvertToPurchaseOrderRequest, PurchaseOrder } from '../models/purchase-order.model';

@Injectable({ providedIn: 'root' })
export class PurchaseOrderService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = `${environment.apiBaseUrl}/purchase-orders`;

  findAll(): Observable<PurchaseOrder[]> {
    return this.http.get<PurchaseOrder[]>(this.baseUrl);
  }

  convertFromRequisition(requisitionId: number, request: ConvertToPurchaseOrderRequest): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(
      `${environment.apiBaseUrl}/requisitions/${requisitionId}/convert-to-po`,
      request,
    );
  }

  acknowledge(id: number): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.baseUrl}/${id}/acknowledge`, {});
  }

  fulfill(id: number): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.baseUrl}/${id}/fulfill`, {});
  }
}
