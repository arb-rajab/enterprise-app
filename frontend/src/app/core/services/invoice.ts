import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Invoice, InvoiceRequest } from '../models/invoice.model';

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = `${environment.apiBaseUrl}/invoices`;

  findAll(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(this.baseUrl);
  }

  record(request: InvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(this.baseUrl, request);
  }

  approve(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.baseUrl}/${id}/approve`, {});
  }

  pay(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.baseUrl}/${id}/pay`, {});
  }

  dispute(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.baseUrl}/${id}/dispute`, {});
  }
}
