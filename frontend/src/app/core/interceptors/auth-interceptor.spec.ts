import { HttpInterceptorFn, HttpRequest, provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Auth } from '../services/auth';
import { authInterceptor } from './auth-interceptor';
import { asAuthInternals } from '../testing/auth-test-utils';

describe('authInterceptor', () => {
  const interceptor: HttpInterceptorFn = (req, next) =>
    TestBed.runInInjectionContext(() => authInterceptor(req, next));

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([])],
    });
  });

  it('attaches a bearer token when one is present', (done) => {
    const auth = TestBed.inject(Auth);
    asAuthInternals(auth).tokenSignal.set('a-token');
    const request = new HttpRequest('GET', '/api/v1/departments');

    interceptor(request, (req) => {
      expect(req.headers.get('Authorization')).toBe('Bearer a-token');
      done();
      return of();
    });
  });

  it('leaves the request unchanged when there is no token', (done) => {
    const request = new HttpRequest('GET', '/api/v1/departments');

    interceptor(request, (req) => {
      expect(req.headers.has('Authorization')).toBeFalse();
      done();
      return of();
    });
  });
});
