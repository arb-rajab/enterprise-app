# Dependabot status

_Last updated: 2026-10-09. Maintained during the Dependabot clean-up pass; update when the state changes._

## Configuration

- Ecosystems covered: maven (`/backend`), npm (`/frontend`), docker (`/backend`, `/frontend`), github-actions (`/`), docker-compose (`/`).
- Grouping: none (one PR per update; open-PR limit 10 for maven and npm).
- Schedule: weekly.
- Ignore rules (reasons are in `.github/dependabot.yml`): npm majors of `@angular/*`, `zone.js`, `typescript`, `eslint`, `jasmine-core`, `@types/jasmine`; Docker `eclipse-temurin` majors, the `maven` build image, and `node` majors; docker-compose image majors (stateful services need a deliberate migration).
- Dependabot version updates were not enabled on this repo until 2026-10-08 (the owner turned them on), which is why no Dependabot PRs, including GitHub Actions bumps, had ever been opened.

## State at last update

- Open Dependabot PRs: 0 (each merged or closed only after reading its checks).
- Default-branch CI: green at last check, including the Dependency scan (with the one exemption below).
- Last full rescan: 2026-10-09. Checked open PRs (none), default-branch and scheduled CI, Dependabot update jobs, ecosystem coverage (no new manifests since 2026-10-08), Actions pins, exemption expiry dates and stray branches, plus three new dimensions: branch-protection required contexts against the check runs a PR actually produces, the repo's `security_and_analysis` settings, and check-run annotations on `main`. No required context is stale. The annotations showed `ubuntu-latest` moving to Ubuntu 26 from 2026-10-19, so every job is now pinned to `ubuntu-24.04` (see Notes). The full-history gitleaks scan was not repeated: the only commits since 2026-10-08 are docs and CI changes, each scanned by the push-run gitleaks job.

## Time-limited exemptions

One entry in `osv-scanner.toml` (passed to the scan via `--config` in `security.yml`), by advisory ID, `ignoreUntil = 2026-11-08`. Approved by the repo owner 2026-10-08.

| Advisory | Package | Why it is exempt |
| --- | --- | --- |
| GHSA-vfj7-8cjw-p6xm (CVSS 8.7) | `braces` 3.0.3 (npm, dev-only) | Stack-exhaustion DoS; no patched release. Build/test toolchain only, not in the production bundle. |

Because entries are by advisory ID, any new advisory against this package still fails the scan.

The two Spring Framework advisories (GHSA-j9f9-w8pj-32f8, GHSA-pc63-qcmh-9cmg, `spring-webmvc` 6.2.19) were exempted until 2026-11-08 and are now **fixed, not exempted**: the backend moved to Spring Boot 4.0.8 (Spring Framework 7.0.9) and their exemptions were removed.

## Notes

- The backend parent is Spring Boot 4.1.1 (Spring Framework 7.0.9, Spring Security 7.1, Hibernate 7.4, Flyway 12, Jackson 3, Tomcat 11, Testcontainers 2); it moved from 4.0.8 with springdoc 3.1.1 in one PR after Dependabot proposed the two separately (#26, #27). Boot 3.5.x (Framework 6.2.19, newest 6.2.x on Maven Central) has no fix for the two advisories above; Boot 4.1.0 manages Framework 7.0.8 and is still affected, 4.1.1+ is fine.
- Boot 4.1.1's managed Tomcat 11.0.24 and Jackson (2.21.5 / 3.1.5) carry advisories, so `backend/pom.xml` overrides `tomcat.version` (11.0.26), `jackson-bom.version` (Jackson 3, 3.1.7) and `jackson-2-bom.version` (Jackson 2, 2.21.7), plus `postgresql`, `commons-lang3` and `log4j2`. Drop each override once Boot's managed version catches up.
- Jackson 2 is still on the classpath only because `jjwt-jackson` needs it; application code uses `tools.jackson` (Jackson 3).
- Springdoc is on 3.1.1, which targets Boot 4.1.
- GitHub Actions pins were refreshed by hand on 2026-10-08 (checkout v7, setup-java v6, setup-node v7, upload-artifact v7, buildx v4, build-push v7, codeql-action v4, gitleaks-action v3), matching the other repos. Dependabot was not enabled at the time (see Configuration), so it had not proposed them.
- `security.yml` runs gitleaks, CodeQL and osv-scanner (against a Maven-generated SBOM plus `frontend/package-lock.json`).
- Formatting is enforced by Spotless in `mvn verify`; Dependabot Java PRs do not touch source files, so this only matters for hand-written fixes.
- `.gitleaksignore` (added 2026-10-08 in #43): four fingerprints, all test fixtures (two Keycloak test subject ids in `UserServiceOidcProvisioningTest.java`, and the integration-test JWT secret in `backend/src/test/resources/application.yml` in two commits). The scheduled Security run scans full history and failed on them (run 37304315619); push runs only scan new commits, so they stay green.
- Every workflow declares a top-level `permissions: contents: read` (added 2026-10-08, rescan cycle 3). Jobs that need more, such as CodeQL's `security-events: write`, declare it at job level.
- Merge policy (deliberate choice by the repo owner, 2026-10-08): every PR, major-version dependency bumps included, is merged as soon as all of its required checks are green, confirmed per PR. This repo is a code showcase with no business or sensitive dependency, so green checks are the only gate. Red, pending or conflicted PRs are fixed or closed instead.
- Every Linux job runs on `ubuntu-24.04` (pinned 2026-10-09; it is what `ubuntu-latest` resolved to). GitHub moves `ubuntu-latest` to Ubuntu 26 from 2026-10-19, and an unattended image change could turn every check red at once. Move to `ubuntu-26.04` deliberately, in one PR whose CI has run on it. Dependabot does not bump `runs-on` labels.

## Deferred (not re-raised each pass)

- Ignored major versions are listed in `.github/dependabot.yml` with the reason for each.
- Re-check exemptions before their `ignoreUntil` date (2026-11-08) and drop them once upstream fixes ship.
- Alerts read 2026-10-09 with the repo owner's PAT, run on their machine (Claude sessions still get 403: the proxy sends a GitHub App token instead of `GH_ALERTS_TOKEN`, even a PAT passed explicitly). No Dependabot alerts. Code scanning had two `java/spring-disabled-csrf-protection` alerts in `SecurityConfig.java`: #1 (OIDC chain, which keeps an HttpSession) is fixed by leaving Spring's CSRF protection on there, since every endpoint it serves is a GET; #2 (API chain) is a false positive, because that chain is stateless and only trusts the `Authorization: Bearer` header. #2 was dismissed on GitHub as a false positive on 2026-10-09, and #1 shows as fixed after the CodeQL run on `main`. No open alerts.
