import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { VendorService } from '../../../core/services/vendor';
import { Vendor } from '../../../core/models/vendor.model';

@Component({
  selector: 'app-vendor-list',
  imports: [ReactiveFormsModule],
  templateUrl: './vendor-list.html',
  styleUrl: './vendor-list.scss',
})
export class VendorList implements OnInit {
  private readonly vendorService = inject(VendorService);
  private readonly fb = inject(FormBuilder);

  readonly vendors = signal<Vendor[]>([]);
  readonly loading = signal(true);

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    contactEmail: ['', [Validators.required, Validators.email]],
    phone: [''],
    address: [''],
    taxId: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.vendorService.findAll().subscribe({
      next: (vendors) => {
        this.vendors.set(vendors);
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
    this.vendorService.create(this.form.getRawValue()).subscribe(() => {
      this.form.reset();
      this.load();
    });
  }

  approve(id: number): void {
    this.vendorService.approve(id).subscribe(() => this.load());
  }

  deactivate(id: number): void {
    this.vendorService.deactivate(id).subscribe(() => this.load());
  }

  statusBadgeClass(status: string): string {
    if (status === 'ACTIVE') return 'badge badge-success';
    if (status === 'INACTIVE') return 'badge badge-danger';
    return 'badge badge-warning';
  }
}
