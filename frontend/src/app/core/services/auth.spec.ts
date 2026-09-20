import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { AuthResponse } from '../models/user.model';
import { Auth } from './auth';

describe('Auth', () => {
  let service: Auth;
  let httpMock: HttpTestingController;

  const sampleResponse: AuthResponse = {
    accessToken: 'a-token',
    tokenType: 'Bearer',
    expiresInSeconds: 1800,
    user: {
      id: 1,
      email: 'employee@procureflow.test',
      firstName: 'Eli',
      lastName: 'Employee',
      departmentId: 2,
      roles: ['ROLE_EMPLOYEE'],
    },
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Auth);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('starts unauthenticated with no stored session', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.currentUser()).toBeNull();
  });

  it('stores the token and user after a successful login', () => {
    service.login({ email: sampleResponse.user.email, password: 'Password123!' }).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/login');
    req.flush(sampleResponse);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.currentUser()?.email).toBe('employee@procureflow.test');
    expect(localStorage.getItem('procureflow.accessToken')).toBe('a-token');
  });

  it('clears the session on logout', () => {
    service.login({ email: sampleResponse.user.email, password: 'Password123!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush(sampleResponse);

    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('procureflow.accessToken')).toBeNull();
  });

  it('hasAnyRole checks the current user roles', () => {
    service.login({ email: sampleResponse.user.email, password: 'Password123!' }).subscribe();
    httpMock.expectOne('/api/v1/auth/login').flush(sampleResponse);

    expect(service.hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN')).toBeTrue();
    expect(service.hasAnyRole('ROLE_ADMIN')).toBeFalse();
  });

  it('completeSsoLogin stores the token immediately and the profile once /users/me responds', () => {
    service.completeSsoLogin('an-sso-token').subscribe();

    // The token (and its Authorization header, via authInterceptor) must be usable right away -
    // it is set synchronously, before the /users/me call that fetches the profile even resolves.
    expect(service.isAuthenticated()).toBeTrue();
    expect(localStorage.getItem('procureflow.accessToken')).toBe('an-sso-token');

    const req = httpMock.expectOne('/api/v1/users/me');
    req.flush(sampleResponse.user);

    expect(service.currentUser()?.email).toBe('employee@procureflow.test');
    expect(JSON.parse(localStorage.getItem('procureflow.currentUser')!).email).toBe(
      'employee@procureflow.test',
    );
  });
});
