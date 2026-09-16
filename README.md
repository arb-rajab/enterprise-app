# ProcureFlow

A skill-demonstration enterprise application: Spring Boot (Java 21) + Angular 20 + PostgreSQL,
covering role-based auth, a multi-step approval workflow, and container-first deployment, built
to professional engineering standards. Part of a developer portfolio — see
`docs/project-memory/01-brief.md` for the full story of what this is and isn't.

## What it does

ProcureFlow models a company's purchase-requisition → approval → purchase-order → invoice
workflow, with five distinct roles (`Employee`, `Department Manager`, `Procurement Officer`,
`Finance Approver`, `Admin`) and an approval chain whose length depends on the requisition's
amount. See `docs/project-memory/01-brief.md` and `docs/project-memory/adr/0004-tiered-approval-thresholds.md`.

## Stack

| Layer | Tech |
| --- | --- |
| Backend | Spring Boot 3, Java 21, Spring Security (JWT), Spring Data JPA, Flyway |
| Frontend | Angular 20 (standalone components, signals), Reactive Forms |
| Database | PostgreSQL 16 |
| Auth | Stateless JWT, BCrypt password hashing |
| Tests | JUnit 5 / Mockito / AssertJ + Testcontainers (backend), Jasmine/Karma (frontend) |
| Containers | Multi-stage, non-root Docker images for both apps |
| CI | GitHub Actions — build, test, lint, Docker image build, for both sides |

## Quick start

```bash
cp .env.example .env      # set a real JWT_SECRET (openssl rand -base64 48)
docker compose up --build
```

- Frontend: http://localhost:8081
- Backend API + Swagger UI: http://localhost:8080/swagger-ui.html

### Demo accounts
All seeded accounts share the password `Password123!` (throwaway, documented on the login
screen itself — see `docs/project-memory/06-security.md`):

| Email | Role |
| --- | --- |
| admin@procureflow.test | Admin |
| manager@procureflow.test | Department Manager (Engineering) |
| procurement@procureflow.test | Procurement Officer |
| finance@procureflow.test | Finance Approver |
| employee@procureflow.test | Employee (Engineering) |

### Local development (without full Docker rebuilds)
```bash
# Backend — needs a reachable Postgres (e.g. `docker compose up postgres`)
cd backend && mvn spring-boot:run

# Frontend — proxies /api to localhost:8080 (see frontend/proxy.conf.json)
cd frontend && npm start
```

## Testing

```bash
cd backend && mvn test      # unit tests, no external dependencies
cd backend && mvn verify    # + integration tests against a real Postgres (needs Docker)

cd frontend && npx ng lint
cd frontend && npx ng test --no-watch --browsers=ChromeHeadlessCI   # needs CHROME_BIN set
```

See `docs/project-memory/07-testing.md` for full coverage details and CI's role in verifying
what this sandboxed environment couldn't (Docker registry access).

## Documentation

Full SDLC documentation lives in `docs/project-memory/`:

| File | Contents |
| --- | --- |
| `01-brief.md` | What this project is and its scope boundaries |
| `02-requirements.md` | Functional & non-functional requirements |
| `03-architecture.md` | Repo layout, data model, request flow, integration pattern |
| `adr/` | Architecture Decision Records (auth, integration pattern, workflow design, repo layout) |
| `05-risk.md` | Risk register |
| `06-security.md` | Security posture and accepted gaps |
| `07-testing.md` | Test strategy and coverage |
| `08-ops.md` | Running it, configuration, known operational gotchas |
| `09-backlog.md` | Deferred work, with reasons |
| `10-release-notes.md` | Release history |
| `11-retirement-plan.md` | What decommissioning this project actually involves |
| `12-session-handoff.md` | Per-session build log: what was done, verified, and left open |

## License

MIT — see `LICENSE`.
