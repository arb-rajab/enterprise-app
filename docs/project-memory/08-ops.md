# Operations

## Running locally

```bash
cp .env.example .env        # set a real JWT_SECRET
docker compose up --build
# frontend: http://localhost:8081
# backend:  http://localhost:8080  (Swagger UI: /swagger-ui.html)
```

`docker-compose.yml` runs four services: `postgres` (with a healthcheck gating backend startup),
`keycloak` (a real, local, dev-only OIDC provider — see `keycloak/README.md` and
`adr/0005-oidc-sso-identity-linking.md`, also healthcheck-gated), `backend` (waits for both to be
healthy, runs Flyway migrations automatically on boot), and `frontend` (nginx, proxies `/api`,
`/oauth2`, and `/login` to `backend` — see `adr/0003-frontend-backend-integration.md`).

For active backend development without rebuilding the container each time:
```bash
cd backend && mvn spring-boot:run   # profile: dev, needs a local/Dockerized Postgres reachable
cd frontend && npm start            # ng serve, proxies /api to localhost:8080 via proxy.conf.json
```

## Configuration / environment variables

| Variable | Where consumed | Required? | Notes |
| --- | --- | --- | --- |
| `JWT_SECRET` | backend | **Yes**, no default outside `dev` profile | ≥ 32 bytes; app fails to start otherwise |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | backend | Yes (Compose supplies dev defaults) | |
| `JWT_ACCESS_TTL_MINUTES` | backend | No (default 30) | |
| `CORS_ALLOWED_ORIGINS` | backend | No (default `http://localhost:4200`) | Only matters for direct `ng serve` calls, see ADR-0003 |
| `SPRING_PROFILES_ACTIVE` | backend | No (default `dev`) | Set to `prod` for production-style logging/behavior |
| `OIDC_CLIENT_SECRET` | backend | No (default `procureflow-dev-secret`) | Dev-only, matches `keycloak/procureflow-realm.json` — see `06-security.md` |
| `OIDC_AUTHORIZATION_URI` | backend | No (default `http://localhost:8180/...`) | Must be reachable by the **browser** — see `adr/0005-oidc-sso-identity-linking.md` |
| `OIDC_TOKEN_URI`, `OIDC_JWKS_URI`, `OIDC_USERINFO_URI` | backend | No (default `http://localhost:8180/...`) | Must be reachable by the **backend**; docker-compose overrides these to the `keycloak` service name |
| `OIDC_FRONTEND_REDIRECT_URI` | backend | No (default `http://localhost:4200/sso/callback`) | Where the browser lands after OIDC login; docker-compose overrides to `http://localhost:8081/sso/callback` |

## Health checks
- Backend: Spring Boot Actuator, `/actuator/health/liveness` and `/actuator/health/readiness`
  (Kubernetes-style probe split is enabled via `management.endpoint.health.probes.enabled=true`).
  The backend Docker image's `HEALTHCHECK` hits `/actuator/health/liveness`.
- Frontend: nginx's `/health` returns a static `200 ok`, used by the frontend image's
  `HEALTHCHECK`.

## Database migrations
Flyway runs automatically on backend startup (`spring.flyway.enabled=true`,
`baseline-on-migrate=true`). To add a migration: add a new
`Vn__description.sql` file under `backend/src/main/resources/db/migration/` with the next
sequential version number — never edit a migration that has already shipped (this is Flyway's
own integrity model: it checksums applied migrations).

## Logging
- `dev` profile: `DEBUG` for `com.enterpriseapp.procureflow`, SQL logging on
  (`spring.jpa.show-sql=true`).
- `prod` profile: `INFO` root and app level, SQL logging off. No log aggregation/shipping is
  configured — logs go to stdout, which is the correct posture for a container to be picked up
  by whatever the deployment platform provides (not configured here, since none exists for this
  demo — see `11-retirement-plan.md`/`09-backlog.md`).

## Known operational gotchas
- **Purchase order numbering** (`PurchaseOrderService.generatePoNumber()`) derives the next
  number from `COUNT(*) + 1` inside the same transaction that inserts the row. This is **not
  safe under concurrent PO creation** (a race could produce a duplicate `po_number`, which the
  unique DB constraint would then reject as a 500, not a friendly error). At realistic demo
  traffic (one procurement officer clicking a button) this never manifests; it is called out here
  and in `09-backlog.md` rather than silently left as a latent bug.
- **A misconfigured nginx `/api` proxy fails silently as a 404**, not a CORS error, because the
  browser sees it as same-origin (see ADR-0003) — if the frontend container is up but every API
  call 404s, check `frontend/nginx.conf` and that the `backend` service name resolves on the
  Compose network before suspecting the backend itself.
- **JWT secret rotation invalidates every currently-issued token immediately** (there's no
  overlap/grace window) — expected given ADR-0002's stateless design, but worth knowing before
  rotating `JWT_SECRET` in a live environment.

## What's deliberately not set up (see `11-retirement-plan.md` for the honest framing)
No real hosting/deployment target, no CD pipeline beyond CI build verification, no log
aggregation, no metrics/tracing export (Actuator only exposes `health` and `info`, not
`prometheus`), no automated backups for the Postgres volume. This repository's CI proves the
containers *build and pass tests*; it does not deploy them anywhere, because there is nowhere for
a portfolio demo to deploy to that wouldn't itself need ongoing maintenance and cost.
