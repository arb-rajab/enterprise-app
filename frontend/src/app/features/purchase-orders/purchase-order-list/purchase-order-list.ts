import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal, inject } from '@angular/core';
import { PurchaseOrderService } from '../../../core/services/purchase-order';
import { PurchaseOrder } from '../../../core/models/purchase-order.model';

@Component({
  selector: 'app-purchase-order-list',
  imports: [DecimalPipe],
  templateUrl: './purchase-order-list.html',
  styleUrl: './purchase-order-list.scss',
})
export class PurchaseOrderList implements OnInit {
  private readonly purchaseOrderService = inject(PurchaseOrderService);

  readonly orders = signal<PurchaseOrder[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.purchaseOrderService.findAll().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  acknowledge(id: number): void {
    this.purchaseOrderService.acknowledge(id).subscribe(() => this.load());
  }

  fulfill(id: number): void {
    this.purchaseOrderService.fulfill(id).subscribe(() => this.load());
  }
}
