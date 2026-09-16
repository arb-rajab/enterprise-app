import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Department, DepartmentRequest } from '../models/department.model';

@Injectable({ providedIn: 'root' })
export class DepartmentService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = `${environment.apiBaseUrl}/departments`;

  findAll(): Observable<Department[]> {
    return this.http.get<Department[]>(this.baseUrl);
  }

  create(request: DepartmentRequest): Observable<Department> {
    return this.http.post<Department>(this.baseUrl, request);
  }
}
