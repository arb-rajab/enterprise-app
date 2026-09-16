import { Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CatalogItemService } from '../../../core/services/catalog-item';
import { DepartmentService } from '../../../core/services/department';
import { RequisitionService } from '../../../core/services/requisition';
import { CatalogItem } from '../../../core/models/catalog-item.model';
import { Department } from '../../../core/models/department.model';

@Component({
  selector: 'app-requisition-create',
  imports: [ReactiveFormsModule],
  templateUrl: './requisition-create.html',
  styleUrl: './requisition-create.scss',
})
export class RequisitionCreate implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly departmentService = inject(DepartmentService);
  private readonly catalogItemService = inject(CatalogItemService);
  private readonly requisitionService = inject(RequisitionService);
  private readonly router = inject(Router);

  readonly departments = signal<Department[]>([]);
  readonly catalogItems = signal<CatalogItem[]>([]);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    departmentId: [null as number | null, Validators.required],
    justification: [''],
    lineItems: this.fb.array([this.buildLineItem()]),
  });

  ngOnInit(): void {
    this.departmentService.findAll().subscribe((departments) => this.departments.set(departments));
    this.catalogItemService.findAll().subscribe((items) => this.catalogItems.set(items));
  }

  get lineItems(): FormArray {
    return this.form.get('lineItems') as FormArray;
  }

  private buildLineItem() {
    return this.fb.nonNullable.group({
      catalogItemId: [null as number | null],
      description: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1)]],
      unitPrice: [0, [Validators.required, Validators.min(0.01)]],
    });
  }

  addLineItem(): void {
    this.lineItems.push(this.buildLineItem());
  }

  removeLineItem(index: number): void {
    if (this.lineItems.length > 1) {
      this.lineItems.removeAt(index);
    }
  }

  onCatalogItemSelected(index: number, catalogItemId: string): void {
    const item = this.catalogItems().find((c) => c.id === Number(catalogItemId));
    const group = this.lineItems.at(index);
    if (item) {
      group.patchValue({ description: item.name, unitPrice: item.unitPrice, catalogItemId: item.id });
    } else {
      group.patchValue({ catalogItemId: null });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const value = this.form.getRawValue();
    this.requisitionService
      .create({
        departmentId: value.departmentId as number,
        justification: value.justification,
        lineItems: value.lineItems,
      })
      .subscribe({
        next: (requisition) => this.router.navigate(['/requisitions', requisition.id]),
        error: () => {
          this.submitting.set(false);
          this.errorMessage.set('Could not create the requisition. Check the form and try again.');
        },
      });
  }
}
