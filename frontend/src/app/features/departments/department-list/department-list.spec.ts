import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DepartmentList } from './department-list';

describe('DepartmentList', () => {
  let fixture: ComponentFixture<DepartmentList>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DepartmentList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(DepartmentList);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads and displays departments', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/v1/departments')
      .flush([{ id: 1, code: 'ENG', name: 'Engineering', costCenter: 'CC-100', managerUserId: null }]);

    expect(fixture.componentInstance.departments().length).toBe(1);
  });

  it('rejects submission when the form is incomplete', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/departments').flush([]);

    fixture.componentInstance.submit();
    httpMock.expectNone((req) => req.method === 'POST');
    expect(fixture.componentInstance.form.invalid).toBeTrue();
  });
});
