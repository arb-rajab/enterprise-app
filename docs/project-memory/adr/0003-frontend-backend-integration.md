# ADR-0003: Same-origin API access via reverse proxy, not cross-origin CORS calls

## Status
Accepted

## Context
The Angular SPA needs to call the Spring Boot API. There are two common integration patterns:
(a) the frontend calls the backend's own origin directly (`https://api.example.com/...`), relying
on CORS to permit it, or (b) the frontend calls a **relative** path on its own origin
(`/api/...`), and something in front of it (a dev-server proxy locally, a reverse proxy/gateway in
deployment) forwards that path to the backend.

## Decision
Pattern (b). The Angular app's `environment.ts`/`environment.prod.ts` both set
`apiBaseUrl: '/api/v1'` — a relative path, identical in every environment. What changes per
environment is what sits in front of the Angular app and where it forwards `/api`:

- **Local `ng serve`:** `frontend/proxy.conf.json` forwards `/api` to `http://localhost:8080`
  (wired via `serve.options.proxyConfig` in `angular.json`).
- **Docker Compose / containerized deployment:** the frontend's own container runs nginx serving
  the built Angular bundle, and `frontend/nginx.conf` proxies `location /api/` to the `backend`
  service on the Docker network.

Spring Security's CORS configuration (`app.cors.allowed-origins`, consumed in
`SecurityConfig.corsConfigurationSource()`) still exists and is exercised — it's what allows the
`ng serve` dev server (a genuinely different origin, `localhost:4200`) to call the backend
directly when a developer isn't running the full Compose stack — but it is not the mechanism the
*shipped* app relies on.

## Alternatives considered

1. **Direct cross-origin calls everywhere, CORS always in play.** Rejected as the primary
   pattern because it means the browser must be trusted to enforce origin checks correctly on
   every request in every environment, the JWT `Authorization` header has to survive preflight
   `OPTIONS` handling correctly in every deployment topology, and the frontend needs environment
   -specific knowledge of the backend's real address baked into its build. A relative path needs
   none of that — the frontend genuinely does not know or care where the backend lives.

2. **A dedicated API gateway (Spring Cloud Gateway, Kong, etc.) in front of both apps.** This is
   the right shape for a system with more than one backend service. Rejected here as unnecessary
   indirection for a single backend — nginx already ships in the frontend container and needs no
   extra moving part to do this one job.

## Consequences
- The Angular build artifact is identical regardless of target environment — no
  environment-specific API URL is baked into the JS bundle, which also means the same Docker
  image can be promoted between environments without a rebuild (a real deployment would still
  want to rebuild for other reasons, e.g. cache-busting hashed filenames, but *this specific
  decision* isn't what forces it).
- nginx must be kept correctly configured wherever the app is deployed; a misconfigured
  `location /api/` block breaks all API access with no CORS error to hint at the cause (the
  browser sees a same-origin 404, not a CORS rejection) — noted as an operational gotcha in
  `08-ops.md`.
- Local development requires either `ng serve` (using the proxy) or the full Compose stack; a
  developer running only `mvn spring-boot:run` and opening `frontend/dist/.../index.html` as a
  static file would get CORS failures, since nothing is proxying for them in that ad hoc setup.
