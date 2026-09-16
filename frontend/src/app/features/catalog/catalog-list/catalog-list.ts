import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal, inject } from '@angular/core';
import { CatalogItemService } from '../../../core/services/catalog-item';
import { CatalogItem } from '../../../core/models/catalog-item.model';

@Component({
  selector: 'app-catalog-list',
  imports: [DecimalPipe],
  templateUrl: './catalog-list.html',
  styleUrl: './catalog-list.scss',
})
export class CatalogList implements OnInit {
  private readonly catalogItemService = inject(CatalogItemService);

  readonly items = signal<CatalogItem[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.catalogItemService.findAll().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
