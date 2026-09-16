import { WritableSignal } from '@angular/core';
import { UserSummary } from '../models/user.model';
import { Auth } from '../services/auth';

/**
 * Exposes {@link Auth}'s private signals for tests, instead of scattering `as any` casts (which
 * ESLint's `no-explicit-any` rule forbids) across every spec that needs to seed a fake session.
 */
interface AuthInternals {
  tokenSignal: WritableSignal<string | null>;
  userSignal: WritableSignal<UserSummary | null>;
}

export function asAuthInternals(auth: Auth): AuthInternals {
  return auth as unknown as AuthInternals;
}
