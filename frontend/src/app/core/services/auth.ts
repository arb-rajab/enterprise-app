import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, RoleName, UserSummary } from '../models/user.model';

const TOKEN_KEY = 'procureflow.accessToken';
const REFRESH_TOKEN_KEY = 'procureflow.refreshToken';
const USER_KEY = 'procureflow.currentUser';

/**
 * Holds the signed-in user's JWT and profile in memory (backed by localStorage so a page
 * refresh doesn't force a re-login) and exposes them as signals for the rest of the app.
 */
@Injectable({
  providedIn: 'root',
})
export class Auth {
  private readonly http = inject(HttpClient);

  private readonly tokenSignal = signal<string | null>(readStoredValue(TOKEN_KEY));
  private readonly refreshTokenSignal = signal<string | null>(readStoredValue(REFRESH_TOKEN_KEY));
  private readonly userSignal = signal<UserSummary | null>(readStoredUser());

  readonly token = this.tokenSignal.asReadonly();
  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, request)
      .pipe(tap((response) => this.applySession(response)));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, request)
      .pipe(tap((response) => this.applySession(response)));
  }

  /**
   * Sends the browser to the backend's OIDC login handshake. A full top-level navigation, not an
   * HTTP call this service makes - Keycloak's login page has to run in the real browser - so it
   * returns nothing; the flow finishes back on {@link completeSsoLogin} once the backend redirects
   * to `/sso/callback` with a token pair, exactly like {@link login} but reached via SSO instead
   * of a password. See docs/project-memory/adr/0008-oidc-sso-identity-linking.md.
   */
  startSsoLogin(): void {
    window.location.href = '/oauth2/authorization/keycloak';
  }

  /**
   * Finishes an SSO login: stores the access/refresh token pair the backend minted (the same
   * revocable pair a password login gets, per ADR-0006), then fetches the profile the same way a
   * password login's `AuthResponse.user` would have arrived, so both login paths leave the app in
   * an identical signed-in state.
   */
  completeSsoLogin(accessToken: string, refreshToken: string): Observable<UserSummary> {
    this.tokenSignal.set(accessToken);
    this.refreshTokenSignal.set(refreshToken);
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    return this.http.get<UserSummary>(`${environment.apiBaseUrl}/users/me`).pipe(
      tap((user) => {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        this.userSignal.set(user);
      }),
    );
  }

  /**
   * Clears the local session immediately and, best-effort, asks the server to revoke the
   * refresh token so it can't be used to mint new access tokens later (see ADR-0006). The
   * revoke call's outcome doesn't block sign-out: a network failure here shouldn't strand the
   * user in a signed-in-looking UI.
   */
  logout(): void {
    const refreshToken = this.refreshTokenSignal();
    this.clearSession();
    if (refreshToken) {
      this.http
        .post(`${environment.apiBaseUrl}/auth/logout`, { refreshToken })
        .subscribe({ error: () => undefined });
    }
  }

  hasAnyRole(...roles: RoleName[]): boolean {
    const user = this.userSignal();
    if (!user) {
      return false;
    }
    return roles.some((role) => user.roles.includes(role));
  }

  private applySession(response: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
    this.tokenSignal.set(response.accessToken);
    this.refreshTokenSignal.set(response.refreshToken);
    this.userSignal.set(response.user);
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.tokenSignal.set(null);
    this.refreshTokenSignal.set(null);
    this.userSignal.set(null);
  }
}

function readStoredValue(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function readStoredUser(): UserSummary | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as UserSummary) : null;
  } catch {
    return null;
  }
}
