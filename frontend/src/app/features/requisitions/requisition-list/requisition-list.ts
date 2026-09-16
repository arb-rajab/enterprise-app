import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../../../core/services/auth';
import { RequisitionService } from '../../../core/services/requisition';
import { Requisition } from '../../../core/models/requisition.model';

@Component({
  selector: 'app-requisition-list',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './requisition-list.html',
  styleUrl: './requisition-list.scss',
})
export class RequisitionList implements OnInit {
  readonly auth = inject(Auth);
  private readonly requisitionService = inject(RequisitionService);

  private readonly allRequisitions = signal<Requisition[]>([]);
  readonly loading = signal(true);

  readonly myRequisitions = computed(() => {
    const me = this.auth.currentUser();
    return this.allRequisitions().filter((r) => r.requesterId === me?.id);
  });

  ngOnInit(): void {
    this.requisitionService.findAll().subscribe({
      next: (requisitions) => {
        this.allRequisitions.set(requisitions);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'APPROVED':
      case 'CONVERTED':
        return 'badge badge-success';
      case 'REJECTED':
      case 'CANCELLED':
        return 'badge badge-danger';
      case 'SUBMITTED':
        return 'badge badge-warning';
      default:
        return 'badge';
    }
  }
}
