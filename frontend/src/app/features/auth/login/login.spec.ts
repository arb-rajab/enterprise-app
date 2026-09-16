import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Login } from './login';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('marks the form invalid when fields are empty', () => {
    expect(component.form.invalid).toBeTrue();
  });

  it('shows an error message on a failed login', () => {
    component.form.setValue({ email: 'wrong@procureflow.test', password: 'bad' });
    component.submit();

    httpMock.expectOne('/api/v1/auth/login').flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(component.errorMessage()).toBe('Invalid email or password.');
    expect(component.submitting()).toBeFalse();
  });
});
