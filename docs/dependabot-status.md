# Dependabot status

_Last updated: 2026-10-08. Maintained during the Dependabot clean-up pass; update when the state changes._

## Configuration

- Ecosystems covered: maven (`/backend`), npm (`/frontend`), docker (`/backend`, `/frontend`), github-actions (`/`), docker-compose (`/`).
- Grouping: none (one PR per update; open-PR limit 10 for maven and npm).
- Schedule: weekly.
- Ignore rules: docker-compose image majors (stateful services need a deliberate migration).

## State at last update

- Open Dependabot PRs: 0 (each merged or closed only after reading its checks).
- Default-branch CI: green at last check, including the Dependency scan (with the exemptions below).

## Time-limited exemptions

All in `osv-scanner.toml` (passed to the scan via `--config` in `security.yml`), by advisory ID, `ignoreUntil = 2026-11-08`. Approved by the repo owner 2026-10-08.

| Advisory | Package | Why it is exempt |
| --- | --- | --- |
| GHSA-j9f9-w8pj-32f8 (CVE-2026-47890, CVSS 9.8) | `spring-webmvc` 6.2.19 | SSE stream corruption when rendering view fragments. Affects Framework 6.2.0-6.2.19 and 7.0.0-7.0.8; no fix on 6.2, fixed in 7.0.9. Backend is JSON-only REST: no SSE endpoints, no view rendering. |
| GHSA-pc63-qcmh-9cmg (CVE-2026-47884, CVSS 9.8) | `spring-webmvc` 6.2.19 | `XsltView` SSRF/RCE. Same affected range and fix as above. Needs `XsltView` plus a catch-all `/**` view mapping; the backend has neither. |
| GHSA-vfj7-8cjw-p6xm (CVSS 8.7) | `braces` 3.0.3 (npm, dev-only) | Stack-exhaustion DoS; no patched release. Build/test toolchain only, not in the production bundle. |

Because entries are by advisory ID, any new advisory against these packages still fails the scan.

## Notes

- The backend parent is Spring Boot 3.5.16, the newest 3.5.x. It manages Spring Framework 6.2.19, which is the newest 6.2.x on Maven Central, so no 3.x bump clears the two `spring-webmvc` advisories.
- **Real fix:** Spring Boot 4.0.8 or 4.1.1+ (both manage Framework 7.0.9; Boot 4.1.0 manages 7.0.8 and is still affected). This is a major migration (Spring Security 7, Jackson 3, springdoc and Testcontainers versions) and is a separate follow-up PR, to land before 2026-11-08. Do not override `spring-framework.version` to 7.x on Boot 3.5; that combination is unsupported.
- `security.yml` runs gitleaks, CodeQL and osv-scanner (against a Maven-generated SBOM plus `frontend/package-lock.json`).
- Formatting is enforced by Spotless in `mvn verify`; Dependabot Java PRs do not touch source files, so this only matters for hand-written fixes.

## Deferred (not re-raised each pass)

- Ignored major versions are listed in `.github/dependabot.yml` with the reason for each.
- Re-check exemptions before their `ignoreUntil` date (2026-11-08) and drop them once upstream fixes ship.
