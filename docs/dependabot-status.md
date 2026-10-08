# Dependabot status

_Last updated: 2026-10-08. Maintained during the Dependabot clean-up pass; update when the state changes._

## Configuration

- Ecosystems covered: maven (`/backend`), npm (`/frontend`), docker (`/backend`, `/frontend`), github-actions (`/`), docker-compose (`/`).
- Grouping: none (one PR per update; open-PR limit 10 for maven and npm).
- Schedule: weekly.
- Ignore rules: docker-compose image majors (stateful services need a deliberate migration).

## State at last update

- Open Dependabot PRs: 0 (each merged or closed only after reading its checks).
- Default-branch CI: green at last check, including the Dependency scan (with the one exemption below).

## Time-limited exemptions

One entry in `osv-scanner.toml` (passed to the scan via `--config` in `security.yml`), by advisory ID, `ignoreUntil = 2026-11-08`. Approved by the repo owner 2026-10-08.

| Advisory | Package | Why it is exempt |
| --- | --- | --- |
| GHSA-vfj7-8cjw-p6xm (CVSS 8.7) | `braces` 3.0.3 (npm, dev-only) | Stack-exhaustion DoS; no patched release. Build/test toolchain only, not in the production bundle. |

Because entries are by advisory ID, any new advisory against this package still fails the scan.

The two Spring Framework advisories (GHSA-j9f9-w8pj-32f8, GHSA-pc63-qcmh-9cmg, `spring-webmvc` 6.2.19) were exempted until 2026-11-08 and are now **fixed, not exempted**: the backend moved to Spring Boot 4.0.8 (Spring Framework 7.0.9) and their exemptions were removed.

## Notes

- The backend parent is Spring Boot 4.0.8 (Spring Framework 7.0.9, Spring Security 7, Hibernate 7, Jackson 3, Tomcat 11, Testcontainers 2). Boot 3.5.x (Framework 6.2.19, newest 6.2.x on Maven Central) has no fix for the two advisories above; Boot 4.1.0 manages Framework 7.0.8 and is still affected, 4.1.1+ is fine.
- Boot 4.0.8's managed Tomcat 11.0.24 and Jackson (2.21.5 / 3.1.5) carry advisories, so `backend/pom.xml` overrides `tomcat.version` (11.0.26), `jackson-bom.version` (Jackson 3, 3.1.7) and `jackson-2-bom.version` (Jackson 2, 2.21.7), plus `postgresql`, `commons-lang3` and `log4j2`. Drop each override once Boot's managed version catches up.
- Jackson 2 is still on the classpath only because `jjwt-jackson` needs it; application code uses `tools.jackson` (Jackson 3).
- Springdoc is on 3.0.3, which targets Boot 4.0.x (3.1.x targets Boot 4.1).
- `security.yml` runs gitleaks, CodeQL and osv-scanner (against a Maven-generated SBOM plus `frontend/package-lock.json`).
- Formatting is enforced by Spotless in `mvn verify`; Dependabot Java PRs do not touch source files, so this only matters for hand-written fixes.

## Deferred (not re-raised each pass)

- Ignored major versions are listed in `.github/dependabot.yml` with the reason for each.
- Re-check exemptions before their `ignoreUntil` date (2026-11-08) and drop them once upstream fixes ship.
