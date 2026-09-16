import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InvoiceList } from './invoice-list';

describe('InvoiceList', () => {
  let fixture: ComponentFixture<InvoiceList>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [InvoiceList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceList);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/invoices').flush([]);
  });

  afterEach(() => httpMock.verify());

  it('does not load purchase orders for a plain employee', () => {
    httpMock.expectNone('/api/v1/purchase-orders');
    expect(fixture.componentInstance.purchaseOrders().length).toBe(0);
  });

  it('marks an approved invoice as paid', () => {
    fixture.componentInstance.pay(1);
    httpMock.expectOne('/api/v1/invoices/1/pay').flush({
      id: 1,
      purchaseOrderId: 1,
      poNumber: 'PO-000001',
      invoiceNumber: 'INV-1',
      amount: 100,
      status: 'PAID',
      receivedAt: '2026-01-01T00:00:00Z',
    });
    httpMock.expectOne('/api/v1/invoices').flush([]);
    expect(fixture.componentInstance.invoices().length).toBe(0);
  });
});
