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

- The backend parent is Spring Boot 3.5.x (see `backend/pom.xml`). As of 2026-10-08 osv-scanner reports two critical advisories on `spring-webmvc` 6.2.19 (GHSA-j9f9-w8pj-32f8, GHSA-pc63-qcmh-9cmg) with no fixed version listed; this is awaiting an owner decision (upgrade or a time-limited exemption), so the Dependency scan check is red until then.
- `security.yml` runs gitleaks, CodeQL and osv-scanner (against a Maven-generated SBOM). There is no `osv-scanner.toml`, so there are no time-limited exemptions in this repo.
- Formatting is enforced by Spotless in `mvn verify`; Dependabot Java PRs do not touch source files, so this only matters for hand-written fixes.

## Deferred (not re-raised each pass)

- Ignored major versions are listed in `.github/dependabot.yml` with the reason for each.
- Re-check exemptions before their `effectiveUntil` date (2026-11-15) and drop them once upstream fixes ship.
