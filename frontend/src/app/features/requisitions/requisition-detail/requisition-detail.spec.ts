import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { RequisitionDetail } from './requisition-detail';
import { Requisition } from '../../../core/models/requisition.model';
import { asAuthInternals } from '../../../core/testing/auth-test-utils';

describe('RequisitionDetail', () => {
  let fixture: ComponentFixture<RequisitionDetail>;
  let component: RequisitionDetail;
  let httpMock: HttpTestingController;

  const requisition: Requisition = {
    id: 42,
    requesterId: 5,
    requesterName: 'Eli Employee',
    departmentId: 1,
    justification: 'Laptops',
    status: 'SUBMITTED',
    totalAmount: 5697,
    lineItems: [],
    approvalSteps: [
      { id: 1, stepOrder: 1, approverRole: 'ROLE_DEPARTMENT_MANAGER', status: 'PENDING', decidedByUserId: null, comments: null, decidedAt: null },
    ],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  async function setup(fixtureRequisition: Requisition) {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [RequisitionDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '42' }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RequisitionDetail);
    component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/v1/requisitions/42').flush(fixtureRequisition);
  }

  afterEach(() => httpMock.verify());

  it('lets the pending approver decide on the requisition', async () => {
    await setup(requisition);
    asAuthInternals(component.auth).userSignal.set({
      id: 2,
      email: 'm@x.com',
      firstName: 'Morgan',
      lastName: 'M',
      departmentId: 1,
      roles: ['ROLE_DEPARTMENT_MANAGER'],
    });

    expect(component.canDecide).toBeTrue();
  });

  it('does not let an unrelated role decide on the requisition', async () => {
    await setup(requisition);
    asAuthInternals(component.auth).userSignal.set({
      id: 3,
      email: 'f@x.com',
      firstName: 'Frank',
      lastName: 'F',
      departmentId: 3,
      roles: ['ROLE_FINANCE_APPROVER'],
    });

    expect(component.canDecide).toBeFalse();
  });

  it('lets the owner submit a draft requisition', async () => {
    const draft: Requisition = { ...requisition, status: 'DRAFT', approvalSteps: [] };
    await setup(draft);

    asAuthInternals(component.auth).userSignal.set({
      id: 5,
      email: 'e@x.com',
      firstName: 'Eli',
      lastName: 'E',
      departmentId: 1,
      roles: ['ROLE_EMPLOYEE'],
    });

    expect(component.canSubmit).toBeTrue();
  });
});
