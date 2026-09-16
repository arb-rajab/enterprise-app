import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Auth } from '../../../core/services/auth';
import { PurchaseOrderService } from '../../../core/services/purchase-order';
import { RequisitionService } from '../../../core/services/requisition';
import { VendorService } from '../../../core/services/vendor';
import { Requisition } from '../../../core/models/requisition.model';
import { RoleName } from '../../../core/models/user.model';
import { Vendor } from '../../../core/models/vendor.model';

@Component({
  selector: 'app-requisition-detail',
  imports: [DecimalPipe, ReactiveFormsModule],
  templateUrl: './requisition-detail.html',
  styleUrl: './requisition-detail.scss',
})
export class RequisitionDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(Auth);
  private readonly requisitionService = inject(RequisitionService);
  private readonly purchaseOrderService = inject(PurchaseOrderService);
  private readonly vendorService = inject(VendorService);
  private readonly fb = inject(FormBuilder);

  readonly requisition = signal<Requisition | null>(null);
  readonly loading = signal(true);
  readonly working = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly vendors = signal<Vendor[]>([]);

  readonly convertForm = this.fb.nonNullable.group({
    vendorId: [null as number | null, Validators.required],
  });

  private readonly requisitionId = Number(this.route.snapshot.paramMap.get('id'));

  ngOnInit(): void {
    this.load();
    if (this.auth.hasAnyRole('ROLE_ADMIN', 'ROLE_PROCUREMENT_OFFICER')) {
      this.vendorService.findAll().subscribe((vendors) => this.vendors.set(vendors.filter((v) => v.status === 'ACTIVE')));
    }
  }

  private load(): void {
    this.loading.set(true);
    this.requisitionService.findById(this.requisitionId).subscribe({
      next: (requisition) => {
        this.requisition.set(requisition);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  get isOwner(): boolean {
    return this.requisition()?.requesterId === this.auth.currentUser()?.id;
  }

  get canSubmit(): boolean {
    const requisition = this.requisition();
    return !!requisition && requisition.status === 'DRAFT' && this.isOwner;
  }

  get canCancel(): boolean {
    const requisition = this.requisition();
    return !!requisition && requisition.status !== 'CONVERTED' && requisition.status !== 'CANCELLED' && this.isOwner;
  }

  get canDecide(): boolean {
    const requisition = this.requisition();
    if (!requisition || requisition.status !== 'SUBMITTED') {
      return false;
    }
    const nextStep = requisition.approvalSteps.find((step) => step.status === 'PENDING');
    return !!nextStep && this.auth.hasAnyRole(nextStep.approverRole as RoleName);
  }

  get canConvert(): boolean {
    const requisition = this.requisition();
    return (
      !!requisition && requisition.status === 'APPROVED' && this.auth.hasAnyRole('ROLE_ADMIN', 'ROLE_PROCUREMENT_OFFICER')
    );
  }

  submit(): void {
    this.runAction(this.requisitionService.submit(this.requisitionId));
  }

  cancel(): void {
    this.runAction(this.requisitionService.cancel(this.requisitionId));
  }

  decide(approve: boolean): void {
    this.runAction(this.requisitionService.decide(this.requisitionId, { approve }));
  }

  convertToPurchaseOrder(): void {
    if (this.convertForm.invalid) {
      this.convertForm.markAllAsTouched();
      return;
    }
    const vendorId = this.convertForm.getRawValue().vendorId as number;
    this.working.set(true);
    this.purchaseOrderService.convertFromRequisition(this.requisitionId, { vendorId }).subscribe({
      next: () => this.router.navigate(['/purchase-orders']),
      error: () => {
        this.working.set(false);
        this.errorMessage.set('Could not convert this requisition to a purchase order.');
      },
    });
  }

  private runAction(action: ReturnType<RequisitionService['submit']>): void {
    this.working.set(true);
    this.errorMessage.set(null);
    action.subscribe({
      next: (requisition) => {
        this.requisition.set(requisition);
        this.working.set(false);
      },
      error: () => {
        this.working.set(false);
        this.errorMessage.set('That action could not be completed.');
      },
    });
  }
}
