import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VendorList } from './vendor-list';

describe('VendorList', () => {
  let fixture: ComponentFixture<VendorList>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VendorList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(VendorList);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock
      .expectOne('/api/v1/vendors')
      .flush([
        { id: 1, name: 'Acme', contactEmail: 'a@acme.test', phone: null, address: null, taxId: null, status: 'PENDING_APPROVAL' },
      ]);
  });

  afterEach(() => httpMock.verify());

  it('loads vendors', () => {
    expect(fixture.componentInstance.vendors().length).toBe(1);
  });

  it('approves a pending vendor', () => {
    fixture.componentInstance.approve(1);
    httpMock.expectOne('/api/v1/vendors/1/approve').flush({
      id: 1,
      name: 'Acme',
      contactEmail: 'a@acme.test',
      phone: null,
      address: null,
      taxId: null,
      status: 'ACTIVE',
    });
    httpMock.expectOne('/api/v1/vendors').flush([]);
    expect(fixture.componentInstance.vendors().length).toBe(0);
  });
});
