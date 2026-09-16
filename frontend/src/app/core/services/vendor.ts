import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Vendor, VendorRequest } from '../models/vendor.model';

@Injectable({ providedIn: 'root' })
export class VendorService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = `${environment.apiBaseUrl}/vendors`;

  findAll(): Observable<Vendor[]> {
    return this.http.get<Vendor[]>(this.baseUrl);
  }

  create(request: VendorRequest): Observable<Vendor> {
    return this.http.post<Vendor>(this.baseUrl, request);
  }

  approve(id: number): Observable<Vendor> {
    return this.http.post<Vendor>(`${this.baseUrl}/${id}/approve`, {});
  }

  deactivate(id: number): Observable<Vendor> {
    return this.http.post<Vendor>(`${this.baseUrl}/${id}/deactivate`, {});
  }
}
