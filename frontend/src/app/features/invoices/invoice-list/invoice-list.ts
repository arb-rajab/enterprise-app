import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Auth } from '../../../core/services/auth';
import { InvoiceService } from '../../../core/services/invoice';
import { PurchaseOrderService } from '../../../core/services/purchase-order';
import { Invoice } from '../../../core/models/invoice.model';
import { PurchaseOrder } from '../../../core/models/purchase-order.model';

@Component({
  selector: 'app-invoice-list',
  imports: [DecimalPipe, ReactiveFormsModule],
  templateUrl: './invoice-list.html',
  styleUrl: './invoice-list.scss',
})
export class InvoiceList implements OnInit {
  readonly auth = inject(Auth);
  private readonly invoiceService = inject(InvoiceService);
  private readonly purchaseOrderService = inject(PurchaseOrderService);
  private readonly fb = inject(FormBuilder);

  readonly invoices = signal<Invoice[]>([]);
  readonly purchaseOrders = signal<PurchaseOrder[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    purchaseOrderId: [null as number | null, Validators.required],
    invoiceNumber: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
  });

  ngOnInit(): void {
    this.load();
    if (this.auth.hasAnyRole('ROLE_ADMIN', 'ROLE_PROCUREMENT_OFFICER')) {
      this.purchaseOrderService.findAll().subscribe((orders) => this.purchaseOrders.set(orders));
    }
  }

  private load(): void {
    this.loading.set(true);
    this.invoiceService.findAll().subscribe({
      next: (invoices) => {
        this.invoices.set(invoices);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.invoiceService.record({ ...value, purchaseOrderId: value.purchaseOrderId as number }).subscribe({
      next: () => {
        this.form.reset({ amount: 0 });
        this.load();
      },
      error: () => this.errorMessage.set('Could not record that invoice (is the number unique?).'),
    });
  }

  approve(id: number): void {
    this.invoiceService.approve(id).subscribe(() => this.load());
  }

  pay(id: number): void {
    this.invoiceService.pay(id).subscribe(() => this.load());
  }

  dispute(id: number): void {
    this.invoiceService.dispute(id).subscribe(() => this.load());
  }

  statusBadgeClass(status: string): string {
    if (status === 'PAID') return 'badge badge-success';
    if (status === 'DISPUTED') return 'badge badge-danger';
    if (status === 'APPROVED') return 'badge badge-primary';
    return 'badge badge-warning';
  }
}
