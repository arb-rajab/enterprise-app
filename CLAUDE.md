# Notes for Claude Code sessions on this repo

Repo-specific, concrete suggestions for reducing token/quota use here — not generic advice.

## Don't re-verify things that don't change
- `backend/pom.xml` dependencies and `frontend/package.json` dependencies are stable; don't
  re-read or re-diff them "just to check" unless you're actually adding/removing a dependency.
- `docs/project-memory/*.md` is prose, not code — grep for the specific file/section you need
  (e.g. `grep -l "approval chain" docs/project-memory/*.md`) instead of reading the whole
  directory into context when you only need one fact.
- The Flyway migrations in `backend/src/main/resources/db/migration/` are append-only by
  convention (see ADR-0001) — never edit `V1__init_schema.sql` or `V2__seed_reference_data.sql`
  once they've shipped; add a new `Vn__...sql` instead. You don't need to re-read the existing
  migrations to confirm this rule, just follow it.

## Every `mvn` invocation prints a large, identical proxy/JAVA_TOOL_OPTIONS banner
Every single `mvn ...` command in this sandboxed environment prefixes its output with a ~10-line
`Picked up JAVA_TOOL_OPTIONS: ...` banner (proxy config for the sandbox). It's identical every
time and adds nothing. Pipe Maven output through something that drops the first matching line,
e.g.:
```bash
mvn -q test 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS'
```
or just `tail -n +2` if you know there's exactly one such line. This alone saves a meaningful
chunk of tokens across a session with many `mvn` calls.

## Docker registry access is blocked in this kind of sandbox
`docker pull` (for Testcontainers-backed `mvn verify`/`*IT.java` tests, and for `docker build` on
either Dockerfile) fails here with `403`/`429` from every registry tried (Docker Hub, ECR public
mirror) — this is a network-policy fact about the sandbox, not something that will fix itself on
retry. **Don't loop retrying it.** If you need to prove those paths work, that's what
`.github/workflows/ci.yml`'s `backend` and `docker` jobs are for — push and check CI instead of
spending sandbox time on doomed local attempts. (`mvn test` — unit tests only, no Docker — works
fine locally and is what you should run for fast local backend feedback.)

## Frontend test runs need `CHROME_BIN` and a custom launcher
`ng test` needs a real Chrome/Chromium binary and, in this sandbox (and most CI), `--no-sandbox`
because it runs as root. Don't rediscover this each time — the fix is already wired up:
```bash
CHROME_BIN=/opt/pw-browsers/chromium npx ng test --no-watch --no-progress \
  --browsers=ChromeHeadlessCI --karma-config=karma.conf.js
```
(`ChromeHeadlessCI` is defined in `frontend/karma.conf.js` with `--no-sandbox` already set; don't
add a second custom launcher or pass `--browsers=ChromeHeadless` bare — that one has no
`--no-sandbox` and will fail as root.)

## Large/generated files not worth reading in full
- `frontend/package-lock.json` — never read this to understand dependencies; read
  `frontend/package.json` instead.
- `frontend/dist/` and `backend/target/` are build output, gitignored, and regenerated on every
  build — never worth inspecting unless actively debugging a specific build failure, and even
  then prefer the build log over the artifact.
- `~/.m2/repository/` (Maven local cache) and `frontend/node_modules/` — never read into context.

## When adding a new backend feature package
Follow the existing pattern exactly (see `docs/project-memory/03-architecture.md`): one package
per domain concept, with `Entity.java`, `EntityRepository.java`, `EntityService.java`,
`EntityController.java`, and a `dto/` subfolder with request/response records. You don't need to
re-derive this structure by reading multiple existing packages each time — `requisition/` is the
canonical, most complete example to copy the shape of.

## Formatting is enforced, not a style suggestion
`mvn verify` fails the build if Spotless (Google Java Format) isn't satisfied. Don't hand-format
Java code and then separately check it — after editing any `.java` file, just run
`mvn spotless:apply` once before running tests; it's idempotent and faster than manual formatting
plus a separate check-fail-reformat loop.
