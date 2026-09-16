import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Register } from './register';

describe('Register', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('requires a password of at least 8 characters', () => {
    component.form.controls.password.setValue('short');
    expect(component.form.controls.password.valid).toBeFalse();
  });

  it('surfaces a friendly message on a duplicate email', () => {
    component.form.setValue({
      firstName: 'Dup',
      lastName: 'User',
      email: 'admin@procureflow.test',
      password: 'Password123!',
    });
    component.submit();

    httpMock.expectOne('/api/v1/auth/register').flush('Conflict', { status: 409, statusText: 'Conflict' });

    expect(component.errorMessage()).toBe('An account with that email already exists.');
  });
});
