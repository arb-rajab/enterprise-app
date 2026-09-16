import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, provideRouter } from '@angular/router';

import { Auth } from '../services/auth';
import { authGuard } from './auth-guard';
import { asAuthInternals } from '../testing/auth-test-utils';

describe('authGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => authGuard(...guardParameters));
  const noRoute = {} as ActivatedRouteSnapshot;
  const noState = {} as RouterStateSnapshot;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([])],
    });
  });

  it('allows navigation when a user is authenticated', () => {
    const auth = TestBed.inject(Auth);
    asAuthInternals(auth).tokenSignal.set('a-token');

    expect(executeGuard(noRoute, noState)).toBe(true);
  });

  it('redirects to /login when no user is authenticated', () => {
    const router = TestBed.inject(Router);
    const result = executeGuard(noRoute, noState);

    expect(result).toEqual(router.createUrlTree(['/login']));
  });
});
