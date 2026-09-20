import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../../core/services/auth';

/**
 * Lands here after the backend's OIDC handshake redirects back to the SPA (see
 * `OidcAuthenticationSuccessHandler` and docs/project-memory/adr/0008-oidc-sso-identity-linking.md).
 * The access/refresh token pair travels as a URL fragment (`#token=...&refreshToken=...`), not
 * query params, so neither reaches this page's own server access logs or an outbound `Referer`
 * header.
 */
@Component({
  selector: 'app-sso-callback',
  imports: [RouterLink],
  templateUrl: './sso-callback.html',
  styleUrl: './sso-callback.scss',
})
export class SsoCallback implements OnInit {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const token = params.get('token');
    const refreshToken = params.get('refreshToken');

    if (!token || !refreshToken) {
      this.errorMessage.set('SSO sign-in failed. Please try again or use your password.');
      return;
    }

    this.auth.completeSsoLogin(token, refreshToken).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: () => {
        this.auth.logout();
        this.errorMessage.set('SSO sign-in failed. Please try again or use your password.');
      },
    });
  }
}
