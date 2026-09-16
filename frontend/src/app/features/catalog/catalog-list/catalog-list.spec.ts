import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CatalogList } from './catalog-list';

describe('CatalogList', () => {
  let fixture: ComponentFixture<CatalogList>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CatalogList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(CatalogList);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads catalog items', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/catalog-items').flush([
      { id: 1, sku: 'HW-1', name: 'Laptop', description: null, category: 'Hardware', unitPrice: 1899, vendorId: 1, vendorName: 'Contoso' },
    ]);

    expect(fixture.componentInstance.items().length).toBe(1);
  });
});
