import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { RequisitionService } from './requisition';

describe('RequisitionService', () => {
  let service: RequisitionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RequisitionService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
