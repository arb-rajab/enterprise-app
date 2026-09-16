import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { CatalogItemService } from './catalog-item';

describe('CatalogItemService', () => {
  let service: CatalogItemService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CatalogItemService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
