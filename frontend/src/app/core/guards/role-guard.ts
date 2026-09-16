import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { RoleName } from '../models/user.model';
import { Auth } from '../services/auth';

/** Reads the `roles` array from route data; the route is only reachable if the user has one of them. */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(Auth);
  const router = inject(Router);
  const requiredRoles = (route.data['roles'] as RoleName[] | undefined) ?? [];

  if (requiredRoles.length === 0 || auth.hasAnyRole(...requiredRoles)) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};
