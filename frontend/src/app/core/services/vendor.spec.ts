import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { VendorService } from './vendor';

describe('VendorService', () => {
  let service: VendorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VendorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
