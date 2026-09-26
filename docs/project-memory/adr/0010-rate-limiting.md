# ADR-0010: Rate limiting - REST auth, REST general, and gRPC

## Status
Accepted

## Context
Neither API surface had any rate limiting at all before this change. `06-security.md` and
`09-backlog.md` had named "rate limiting on `/api/v1/auth/**`" as deferred work since this
project's first commit; the gRPC purchase-order API (ADR-0009) added a second, equally
unthrottled surface on top of that. Concretely, before this ADR: `POST /api/v1/auth/login` could
be hit at unlimited speed for credential stuffing or a targeted brute-force attempt against one
account; every other REST endpoint and every gRPC RPC could be flooded with no per-caller limit at
all, a plain resource-exhaustion exposure independent of authentication.

This project has no Redis or other shared cache anywhere in its stack (`application.yml` - the
datasource is the only external dependency), and is not documented or deployed as horizontally
scaled. Any solution that assumed distributed state would be new operational infrastructure this
single-instance-shaped app doesn't otherwise need.

## Decision
**Mechanism: `bucket4j` in-memory token buckets, one keyed set per limiter, not a bespoke counter
implementation.** `KeyedRateLimiter` wraps a `ConcurrentHashMap<String, Bucket>`; each limiter
below is one `KeyedRateLimiter` instance configured from `RateLimitProperties`
(`app.rate-limit.*`, all overridable via environment variables - see `application.yml`). In-memory
rather than Redis-backed, deliberately: matches this app's existing "no new infrastructure"
posture (no cache/Redis exists elsewhere in this stack), at the accepted cost that limits reset on
restart and don't share state across instances if this app is ever horizontally scaled - see
Consequences.

**Three independent limiters, applied where each API actually needed one:**

1. **`RateLimitFilter` (REST), general per-IP limit on every request** (`app.rate-limit.api-per-ip`,
   default 1200/minute) - added before `JwtAuthenticationFilter` in `SecurityConfig.apiFilterChain`
   so a caller is throttled before this app spends anything validating their bearer token.
   Resource-exhaustion protection only, not brute-force-specific: this is the fix for "the REST API
   has no rate limiting at all," independent of the login-specific limits below. Deliberately sized
   generously (20/second sustained) rather than tightly: its job is catching a sustained automated
   flood, not constraining normal multi-user traffic - including many real users sharing one NAT'd
   office IP, which a tight per-IP cap would punish for being popular rather than abusive. The
   login-specific limits below are the ones deliberately kept tight, since brute-forcing one
   account is inherently a low-legitimate-volume action in a way "call the API" isn't.
2. **`RateLimitFilter` (REST), two extra limits on `POST /api/v1/auth/login` specifically**
   (brute-force/credential-spray protection, the same two-tier shape used elsewhere in this
   portfolio for a login endpoint - see "Alternatives considered"):
   - **`login-per-credential`** (default 20/minute), keyed on `lowercased-email + IP` - stops a
     targeted brute-force attempt against one account. `String.toLowerCase(Locale.ROOT)` is used
     explicitly here (not the locale-dependent no-arg overload) - see `06-security.md`'s
     CVE-2024-38827 note for why that distinction matters in a security-relevant string comparison.
   - **`login-per-ip`** (default 60/minute), keyed on IP alone - catches one source spraying many
     different emails quickly, which the per-credential limit alone can't see.
   - Both checks run **before** the request body is parsed into a `LoginRequest`: a
     `CachedBodyHttpServletRequest` wrapper lets the filter read the email out of the raw JSON body
     to key the per-credential bucket, then replays the same bytes to `AuthController` downstream.
     A malformed body still consumes from `login-per-ip` (verified by
     `RateLimitFilterTest.aMalformedLoginBodyStillCountsAgainstThePerIpLimit`) - a flood of
     even-invalid login attempts must still be throttled, not reach `AuthController` for free.
3. **`GrpcRateLimitInterceptor`, per-caller-remote-address limit on the gRPC listener**
   (`app.rate-limit.grpc-per-ip`, default 120/minute) - added as the **outermost** interceptor in
   `GrpcServerLifecycle` (`ServerInterceptors.intercept(service, grpcAuthInterceptor,
   grpcRateLimitInterceptor)` - the last interceptor in that call is the one that actually runs
   first), ahead of `GrpcAuthInterceptor`, for the same "throttle before you parse a bearer token"
   reason as the REST login checks. Rejects with `RESOURCE_EXHAUSTED`, gRPC's own status code for
   this condition, rather than reusing `UNAUTHENTICATED`/`PERMISSION_DENIED`.
   `GrpcRateLimitInterceptorTest` proves the ordering directly: every call in that test is sent
   with no bearer token at all, and only the interceptor chain's actual order decides whether the
   caller ever gets past `UNAUTHENTICATED` to see `RESOURCE_EXHAUSTED` once its limit is hit - a
   fully in-process test (no Docker, no real port), unlike `PurchaseOrderGrpcAuthorizationIT`.

**Response shape matches this codebase's own existing convention**, not a framework default: REST
returns the same `ApiError` record `GlobalExceptionHandler` already uses everywhere else (status
429, `error: "Too Many Requests"`), written directly by the filter since a `Filter` (as opposed to
a controller-thrown exception) never reaches `@RestControllerAdvice`.

**Thresholds are reasoned defaults, not empirically tuned against this repo's own IT suite.**
Unlike a prior rate-limiting change elsewhere in this portfolio (a Laravel app whose final numbers
were checked directly against its own real Playwright suite before being finalized), this repo's
integration tests are Docker-gated and could not be run in this session's sandbox (see
`07-testing.md`) - so these defaults were sized with headroom by inspecting the IT suite's source
directly (a handful of `/api/v1/auth/login` calls per IT class, across roughly half a dozen classes,
sharing one cached Spring context and therefore one set of buckets for the whole `mvn verify` run)
rather than by executing it and observing real numbers. **This is a real, disclosed gap in how
these thresholds were validated, not a hidden one** - if CI's `backend` job (which does run the
full IT suite on a real Docker-enabled runner) produces spurious 429s from legitimate test traffic,
that's this ADR's numbers needing to go up, not a bug in the mechanism, and should be fixed by
raising `app.rate-limit.*` in this file's own decision rather than disabling a limiter.

## Alternatives considered
1. **Spring Cloud Gateway / a dedicated API gateway's built-in rate limiting.** Rejected: pulls in
   an entire gateway framework (and, for its distributed limiter, Redis) for a single-instance app
   that has no gateway layer today - disproportionate to what two rate limiters need.
2. **`Resilience4j`'s `RateLimiter`.** A reasonable alternative with a similar in-memory, no-new-
   infrastructure shape to `bucket4j`. `bucket4j` was chosen instead because its token-bucket model
   maps directly onto "N requests per window with burst allowance," the exact shape both the login
   and general limiters need, without needing a circuit-breaker/retry vocabulary this change has no
   use for.
3. **A distributed limiter (Redis-backed `bucket4j-redis`, or a database table).** Rejected for the
   same reason ADR-0006 rejected a server-side revocation deny-list: real new infrastructure (a
   cache store) this app doesn't otherwise run, for a single-instance deployment that doesn't need
   cross-instance shared state today. Revisit if this app is ever actually horizontally scaled - see
   Consequences.
4. **Account lockout after N failed logins (persisted, e.g. a `failed_login_count` column).**
   Deliberately not built here: lockout is a distinct mechanism from rate limiting (it's per-account
   state that persists past the rate window and needs its own unlock story), still listed
   separately in `09-backlog.md`. This ADR closes the rate-limiting half of that backlog line, not
   both.

## Consequences
- New dependency: `com.bucket4j:bucket4j_jdk17-core` (`pom.xml`), no other new runtime
  infrastructure.
- New package `com.enterpriseapp.procureflow.ratelimit`: `RateLimitProperties`, `KeyedRateLimiter`,
  `RateLimitFilter`, `CachedBodyHttpServletRequest`. `GrpcRateLimitInterceptor` lives in the
  existing `grpc` package alongside `GrpcAuthInterceptor`, matching that package's existing shape.
- **Buckets are per-instance, in-memory, and reset on restart.** If this app is ever run as more
  than one instance behind a load balancer, each instance enforces its own independent limit
  (effectively multiplying the real ceiling by instance count) rather than one shared limit - an
  accepted gap for this project's current single-instance shape, the same kind of trade-off ADR-
  0002/0006 already made for JWT statelessness and refresh-token storage. Revisit with a
  Redis-backed `bucket4j` distribution if this app is ever actually scaled horizontally.
- `06-security.md` and `09-backlog.md`'s "rate limiting on `/api/v1/auth/**`" line is resolved;
  "account lockout" (a distinct mechanism, see Alternatives) remains open.
- Covered by `RateLimitFilterTest` (unit: general per-IP limiting, independent buckets per IP,
  per-credential vs. per-IP login limiting, malformed-body-still-counts, request body remains
  readable downstream after inspection) and `GrpcRateLimitInterceptorTest` (in-process: interceptor
  ordering, `RESOURCE_EXHAUSTED` once the limit is hit). Not covered by a new Docker-gated
  integration test - the existing `AuthControllerIT`/`PurchaseOrderGrpcAuthorizationIT` suites
  exercise these endpoints below any of this ADR's thresholds, so they're a passive check that the
  defaults don't false-positive on real traffic, not a dedicated proof the limiters engage (that's
  what the new unit/in-process tests are for).
