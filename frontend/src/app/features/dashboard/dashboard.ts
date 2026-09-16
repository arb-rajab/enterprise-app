import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../../core/services/auth';
import { RequisitionService } from '../../core/services/requisition';
import { Requisition } from '../../core/models/requisition.model';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  readonly auth = inject(Auth);
  private readonly requisitionService = inject(RequisitionService);

  readonly pendingApprovals = signal<Requisition[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    if (this.auth.hasAnyRole('ROLE_DEPARTMENT_MANAGER', 'ROLE_PROCUREMENT_OFFICER', 'ROLE_FINANCE_APPROVER')) {
      this.requisitionService.findPendingMyApproval().subscribe({
        next: (requisitions) => {
          this.pendingApprovals.set(requisitions);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    } else {
      this.loading.set(false);
    }
  }
}
