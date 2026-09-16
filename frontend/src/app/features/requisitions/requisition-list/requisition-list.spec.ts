import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { RequisitionList } from './requisition-list';
import { Requisition } from '../../../core/models/requisition.model';
import { asAuthInternals } from '../../../core/testing/auth-test-utils';

describe('RequisitionList', () => {
  let fixture: ComponentFixture<RequisitionList>;
  let httpMock: HttpTestingController;

  const requisitions: Requisition[] = [
    {
      id: 1,
      requesterId: 5,
      requesterName: 'Eli Employee',
      departmentId: 1,
      justification: 'Laptops',
      status: 'DRAFT',
      totalAmount: 100,
      lineItems: [],
      approvalSteps: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 2,
      requesterId: 9,
      requesterName: 'Someone Else',
      departmentId: 1,
      justification: 'Chairs',
      status: 'APPROVED',
      totalAmount: 200,
      lineItems: [],
      approvalSteps: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [RequisitionList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(RequisitionList);
  });

  it('only shows requisitions created by the current user', () => {
    asAuthInternals(fixture.componentInstance.auth).userSignal.set({
      id: 5,
      email: 'e@x.com',
      firstName: 'Eli',
      lastName: 'E',
      departmentId: 1,
      roles: ['ROLE_EMPLOYEE'],
    });

    fixture.detectChanges();
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/v1/requisitions').flush(requisitions);

    expect(fixture.componentInstance.myRequisitions().length).toBe(1);
    expect(fixture.componentInstance.myRequisitions()[0].id).toBe(1);
    httpMock.verify();
  });
});
