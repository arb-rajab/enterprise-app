import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { SsoCallback } from './sso-callback';

describe('SsoCallback', () => {
  let fixture: ComponentFixture<SsoCallback>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    window.location.hash = '';
    await TestBed.configureTestingModule({
      imports: [SsoCallback],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    httpMock.verify();
    window.location.hash = '';
  });

  it('stores the token from the URL fragment and navigates to the dashboard once the profile loads', () => {
    window.location.hash = '#token=a-real-token&expiresIn=1800';

    fixture = TestBed.createComponent(SsoCallback);
    fixture.detectChanges();

    expect(localStorage.getItem('procureflow.accessToken')).toBe('a-real-token');

    httpMock.expectOne('/api/v1/users/me').flush({
      id: 1,
      email: 'sso.newhire@procureflow.test',
      firstName: 'Sasha',
      lastName: 'Newhire',
      departmentId: null,
      roles: ['ROLE_EMPLOYEE'],
    });

    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('shows an error and does not call the API when the fragment has no token', () => {
    window.location.hash = '#error=oidc_login_failed';

    fixture = TestBed.createComponent(SsoCallback);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).toContain('SSO sign-in failed');
    httpMock.expectNone('/api/v1/users/me');
  });
});
