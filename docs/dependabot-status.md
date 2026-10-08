# Dependabot status

_Last updated: 2026-10-08. Maintained during the Dependabot clean-up pass; update when the state changes._

## Configuration

- Ecosystems covered: maven (`/backend`), npm (`/frontend`), docker (`/backend`, `/frontend`), github-actions (`/`), docker-compose (`/`).
- Grouping: none (one PR per update; open-PR limit 10 for maven and npm).
- Schedule: weekly.
- Ignore rules: docker-compose image majors (stateful services need a deliberate migration).

## State at last update

- Open Dependabot PRs: 0 (each merged or closed only after reading its checks).
- Default-branch CI: green at last check.

## Time-limited exemptions

- None.

## Notes

- The backend is on Spring Boot 3.3.x, which is end-of-life; a Spring Boot 4.x migration has been investigated and explicitly deferred by the owner (see `docs/project-memory`), so Dependabot Spring Boot major bumps should be read, not merged blindly.
- `security.yml` runs gitleaks, CodeQL and osv-scanner (against a Maven-generated SBOM). There is no `osv-scanner.toml`, so there are no time-limited exemptions in this repo.
- Formatting is enforced by Spotless in `mvn verify`; Dependabot Java PRs do not touch source files, so this only matters for hand-written fixes.

## Deferred (not re-raised each pass)

- Ignored major versions are listed in `.github/dependabot.yml` with the reason for each.
- Re-check exemptions before their `effectiveUntil` date (2026-11-15) and drop them once upstream fixes ship.
