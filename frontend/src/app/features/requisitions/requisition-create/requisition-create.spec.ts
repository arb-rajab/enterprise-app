import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { RequisitionCreate } from './requisition-create';

describe('RequisitionCreate', () => {
  let fixture: ComponentFixture<RequisitionCreate>;
  let component: RequisitionCreate;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [RequisitionCreate],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(RequisitionCreate);
    component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/v1/departments').flush([]);
    httpMock.expectOne('/api/v1/catalog-items').flush([]);
  });

  afterEach(() => httpMock.verify());

  it('should create with one empty line item by default', () => {
    expect(component.lineItems.length).toBe(1);
  });

  it('adds and removes line items, but never below one', () => {
    component.addLineItem();
    expect(component.lineItems.length).toBe(2);

    component.removeLineItem(0);
    expect(component.lineItems.length).toBe(1);

    component.removeLineItem(0);
    expect(component.lineItems.length).toBe(1);
  });

  it('is invalid until the department and line item fields are filled in', () => {
    expect(component.form.invalid).toBeTrue();

    component.form.patchValue({ departmentId: 1 });
    component.lineItems.at(0).patchValue({ description: 'Widgets', quantity: 2, unitPrice: 10 });

    expect(component.form.valid).toBeTrue();
  });
});
