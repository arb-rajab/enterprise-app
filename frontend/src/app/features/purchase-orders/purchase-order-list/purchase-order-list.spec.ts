import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PurchaseOrderList } from './purchase-order-list';

describe('PurchaseOrderList', () => {
  let fixture: ComponentFixture<PurchaseOrderList>;
  let httpMock: HttpTestingController;

  const order = {
    id: 1,
    poNumber: 'PO-000001',
    requisitionId: 10,
    vendorId: 2,
    vendorName: 'Contoso',
    totalAmount: 5697,
    status: 'ISSUED' as const,
    issuedAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PurchaseOrderList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(PurchaseOrderList);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/purchase-orders').flush([order]);
  });

  afterEach(() => httpMock.verify());

  it('loads purchase orders', () => {
    expect(fixture.componentInstance.orders().length).toBe(1);
  });

  it('acknowledges an issued order and reloads the list', () => {
    fixture.componentInstance.acknowledge(1);
    httpMock.expectOne('/api/v1/purchase-orders/1/acknowledge').flush({ ...order, status: 'ACKNOWLEDGED' });
    httpMock.expectOne('/api/v1/purchase-orders').flush([]);
    expect(fixture.componentInstance.orders().length).toBe(0);
  });
});
