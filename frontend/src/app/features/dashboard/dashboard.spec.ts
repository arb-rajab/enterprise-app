import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Dashboard } from './dashboard';
import { asAuthInternals } from '../../core/testing/auth-test-utils';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create and not call the API for an employee', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    httpMock.expectNone('/api/v1/requisitions/pending-my-approval');
  });

  it('loads pending approvals for a department manager', () => {
    asAuthInternals(fixture.componentInstance.auth).userSignal.set({
      id: 2,
      email: 'manager@procureflow.test',
      firstName: 'Morgan',
      lastName: 'Manager',
      departmentId: 1,
      roles: ['ROLE_DEPARTMENT_MANAGER'],
    });

    fixture.detectChanges();

    const req = httpMock.expectOne('/api/v1/requisitions/pending-my-approval');
    req.flush([]);

    expect(fixture.componentInstance.loading()).toBeFalse();
  });
});
