import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CatalogItem } from '../models/catalog-item.model';

@Injectable({ providedIn: 'root' })
export class CatalogItemService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = `${environment.apiBaseUrl}/catalog-items`;

  findAll(): Observable<CatalogItem[]> {
    return this.http.get<CatalogItem[]>(this.baseUrl);
  }

  findById(id: number): Observable<CatalogItem> {
    return this.http.get<CatalogItem>(`${this.baseUrl}/${id}`);
  }
}
