import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, provideRouter } from '@angular/router';

import { Auth } from '../services/auth';
import { roleGuard } from './role-guard';
import { asAuthInternals } from '../testing/auth-test-utils';

describe('roleGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => roleGuard(...guardParameters));
  const noState = {} as RouterStateSnapshot;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([])],
    });
  });

  function routeWithRoles(roles: string[]): ActivatedRouteSnapshot {
    return { data: { roles } } as unknown as ActivatedRouteSnapshot;
  }

  it('allows navigation when the user has one of the required roles', () => {
    const auth = TestBed.inject(Auth);
    asAuthInternals(auth).userSignal.set({
      id: 1,
      email: 'a@b.com',
      firstName: 'A',
      lastName: 'B',
      departmentId: null,
      roles: ['ROLE_ADMIN'],
    });

    const result = executeGuard(routeWithRoles(['ROLE_ADMIN']), noState);
    expect(result).toBe(true);
  });

  it('redirects to the dashboard when the user lacks all required roles', () => {
    const auth = TestBed.inject(Auth);
    asAuthInternals(auth).userSignal.set({
      id: 1,
      email: 'a@b.com',
      firstName: 'A',
      lastName: 'B',
      departmentId: null,
      roles: ['ROLE_EMPLOYEE'],
    });
    const router = TestBed.inject(Router);

    const result = executeGuard(routeWithRoles(['ROLE_ADMIN']), noState);
    expect(result).toEqual(router.createUrlTree(['/dashboard']));
  });

  it('allows navigation when the route requires no specific roles', () => {
    const result = executeGuard(routeWithRoles([]), noState);
    expect(result).toBe(true);
  });
});
