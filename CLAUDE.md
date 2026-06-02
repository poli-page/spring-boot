# CLAUDE.md

> Instructions for Claude Code agents working in `poli-page/spring-boot`.

## 1. Repo at a glance

| Field        | Value |
| ------------ | ----- |
| Repository   | `poli-page/spring-boot` |
| Type         | Framework integration (Spring Boot 3 starter) |
| Language     | Java 17+ |
| Spring Boot  | 3.2.x \|\| 3.3.x \|\| 3.4.x |
| Registry     | Maven Central — `page.poli:poli-page-spring-boot-starter` (+ `-webflux` + `-autoconfigure`) |
| Depends on   | `page.poli:sdk` (Maven Central) |
| Roadmap slot | not in `INTEGRATIONS_PLAN.md` yet — promoted from `sdk-roadmap.md` §6.2 ("Spring Boot 3 controller" recipe) to a first-class starter on 2026-06-01 |

**Source-of-truth docs (read first):**
- `docs/spec/spring-boot-starter-specification.md` — full design spec for v0.1.0
- `docs/plan/2026-06-01-implementation.md` — implementation plan
- `/Users/mickael/Projects/INTEGRATIONS_PLAN.md` — cross-repo umbrella note; add a Spring Boot row when this lands
- `/Users/mickael/Projects/symfony-bundle/` — closest sibling integration; DI + response factory + event bridge + CLI patterns transfer directly
- `/Users/mickael/Projects/sdk-java/` — the SDK being wrapped; constructor / facade / exception hierarchy is the contract

## 2. The starter's job

This starter is a **thin Spring Boot 3 auto-configuration wrapper** around the official Poli Page Java SDK (`page.poli:sdk`, source at `/Users/mickael/Projects/sdk-java/`). It provides:

- A `PoliPageClient` bean registered via `@AutoConfiguration`, autowireable into any `@Component` / `@RestController` by type
- A `PoliPageProperties` record bound to `poli-page.*` keys with Jakarta Bean Validation
- A `PoliPageResponses` (MVC) and `PoliPageReactiveResponses` (WebFlux) helper for returning PDFs with correct headers
- An `ApplicationEventPublisher` bridge that publishes the SDK's `onRetry` / `onError` `Consumer` hooks as `PoliPageRetryEvent` / `PoliPageErrorEvent`
- A `PoliPageHealthIndicator` (Actuator) backed by `GET /v1/health` (never spends a render call)
- A `PoliPageMetrics` `MeterBinder` (Micrometer) — four meters with bounded tag cardinality
- A runnable example app at `example-app/mvc/` (MVC) and `example-app/webflux/` (reactive) demonstrating all 10 SDK demo steps

**This starter does NOT** reimplement HTTP transport, retries, error mapping, idempotency, or Jackson plumbing — that all lives in `sdk-java` and is exhaustively tested there. Bug in those areas? Fix it in the SDK, not here.

**This starter does NOT** ship: a `WebClient`-based transport replacement, Spring Cache integration, Spring Cloud Sleuth tracing, Kotlin DSL, Spring Native AOT hints beyond best-effort, Quarkus / Micronaut parity, or a `@ConditionalOnPoliPageEnabled` master switch. All deferred — see spec §17.

## 3. Working language

- **Code, comments, file names, commit messages, PR descriptions, repository documentation**: English.
- **Day-to-day conversation with Mickael/Xavier**: French, tutoiement.
- **Conversation in this Claude Code session**: French is fine for the chat; artifacts stay English.

## 4. TDD is mandatory

RED → GREEN → refactor for every change. Tests live in each module's `src/test/java/` (mocked, >90% of the suite) and `src/integrationTest/java/` (one happy-path test against `api-develop.poli.page`, gated on `POLI_PAGE_API_KEY`).

### What to test (integration-specific!)

- **Auto-configuration**: `PoliPageClient` bean resolves with `ApplicationContextRunner`, all `poli-page.*` property combinations produce the right builder calls, user `@Bean PoliPageClient` overrides via `@ConditionalOnMissingBean`, conditional toggles (`health.enabled`, `metrics.enabled`, `events.enabled`) add/remove the right beans.
- **Property validation**: missing/malformed `api-key`, out-of-range `request-timeout`, `retries.*` raise `BindValidationException` at startup with the documented messages.
- **`PoliPageResponses` (MVC)**: every method sets the right headers (`Content-Type`, RFC 5987 `Content-Disposition`, `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`). ASCII and non-ASCII filenames both encode correctly. `stream(...)` closes the underlying SDK `InputStream` exactly once.
- **`PoliPageReactiveResponses` (WebFlux)**: same coverage in reactive style. `StepVerifier` drains `Mono`s and verifies cancellation propagates.
- **`PoliPageEventBridge`**: standalone tests publish `PoliPageRetryEvent` / `PoliPageErrorEvent` via a stub `ApplicationEventPublisher`. Integration test asserts the bridge is wired into the builder.
- **`PoliPageHealthIndicator`**: WireMock-backed (the ONLY place we stub HTTP). 200 → UP, non-200 → DOWN, exception → DOWN with cause.
- **`PoliPageMetrics`**: `SimpleMeterRegistry` test asserts counter and timer updates per published event.

### What NOT to test (the SDK already does)

- HTTP transport behaviour (JDK `HttpClient` edge cases)
- Retry policy (backoff, max attempts, `Retry-After`, never-retry-4xx)
- 4xx / 5xx → exception mapping
- Idempotency key generation
- Stream handling, byte-range correctness, etc.

Re-testing these here wastes time and creates double-maintenance burden. The SDK's `src/test/` suite is the home for transport behaviour. **If you find yourself writing a WireMock server for anything other than the HealthIndicator, stop — you're doing the SDK's job.**

## 5. Robustness over shortcuts

Mickael's hard rule (validated across the symfony-bundle / laravel / nestjs sessions): **no hacks to make a test pass or a corner case go away**. Fix root causes. If a workaround is genuinely required (framework bug, SDK quirk), document it inline with a `// Why:` comment naming the constraint.

Concretely: do not disable Spotless or Error Prone checks, do not `@SuppressWarnings("...")` to silence the analyser, do not widen `@Catch` to swallow generic exceptions, do not introduce per-test `@DirtiesContext` to paper over context-pollution bugs.

## 6. Code conventions

- **Spotless** with `google-java-format`. Pinned in `build.gradle.kts`.
- **Error Prone** + **NullAway** for null-safety at compile time. Same configuration as `sdk-java`.
- **JSpecify `@Nullable`** for nullable fields and parameters (matches the SDK).
- **No commented-out code, no `TODO` without a linked issue, no debug `System.out` prints.**
- **Default to no comments.** Add one only when the *why* is non-obvious. Javadoc on public API is required (Maven Central enforces it); comments restating *what* the code does are noise.
- **`@ConditionalOnMissingBean` on every starter-provided bean.** Consumers must always be able to override by declaring their own bean of the same type.

## 7. Commits and PRs

- **Conventional Commits**: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`.
- **One concern per PR**, reviewable in under 30 minutes.
- PR description: what changed, why, how it was tested.
- CI must be green on all 6 matrix cells before merge.

## 8. CI

Workflow: `.github/workflows/ci.yml`. Matrix: Java `17`/`21` × Spring Boot `3.2.+`/`3.3.+`/`3.4.+` (6 cells). Each step auto-skips if the relevant config file is missing (so a freshly scaffolded repo is green from day one). Don't change that behaviour.

When working in this repo:
- After adding `settings.gradle.kts`, the build step lights up.
- After adding a module's `build.gradle.kts`, the per-module compile step lights up.
- After adding tests in a module's `src/test/java/`, the test step lights up.

Local mirror:
```bash
./gradlew --no-daemon build spotlessCheck
./gradlew --no-daemon :poli-page-spring-boot-autoconfigure:test
./gradlew --no-daemon integrationTest    # gated on POLI_PAGE_API_KEY
```

## 9. Unpublished SDK note

`page.poli:sdk` is **not yet on Maven Central** as of 2026-06-01. Current version is `1.0.0-SNAPSHOT`, only available via `~/.m2/repository/` after a local `mvn install`. The starter's autoconfigure module pins this snapshot version explicitly.

The dev workflow: `cd /Users/mickael/Projects/sdk-java/ && ./mvnw install -DskipTests` publishes the snapshot locally; the root `build.gradle.kts` lists `mavenLocal()` before `mavenCentral()` so Gradle picks it up. (Gradle's `includeBuild` composite mechanism is NOT usable here — sdk-java is a Maven project, not a Gradle project, so they cannot be composed.)

When the SDK publishes `1.0.0` (or later) to Central:
1. Bump the dependency in `poli-page-spring-boot-autoconfigure/build.gradle.kts` from `1.0.0-SNAPSHOT` to the release version.
2. CI will resolve from Central instead of requiring the developer's local `~/.m2/`.

## 10. Known gotchas (battle-tested — don't relearn the hard way)

These surfaced across sibling integrations (symfony-bundle, laravel, nestjs) or are Spring Boot-specific. Recorded so future agents don't burn a session rediscovering them.

### 10.1 Three artifacts, one repo — Gradle composite-style layout

Spring Boot starters that ship both MVC and WebFlux flavours (Springdoc, Sentry) use a multi-module Gradle build with three published artifacts: `-autoconfigure` (internal common code) + `-starter` (MVC) + `-starter-webflux`. Keep this layout. Trying to ship one fat artifact that depends on both web stacks forces consumers to exclude one — much worse DX.

### 10.2 `@ConditionalOnWebApplication(type = ...)` is type-strict

`type = SERVLET` activates only when Spring MVC is the runtime stack; `type = REACTIVE` only when WebFlux is. A hybrid app (extremely rare) that pulls in both `spring-boot-starter-web` and `spring-boot-starter-webflux` ends up servlet-dominant — Boot detects that combination and activates the servlet stack. The MVC autoconfig fires in that case; the WebFlux one stays inert. This is correct behaviour; don't try to "fix" it by removing the conditional.

### 10.3 `@MockitoBean` is Boot 3.4+ only

Spring Boot 3.4 shipped `@MockitoBean` to replace the deprecated `@MockBean`. The starter's own tests use `@MockitoBean`. If a contributor is on Boot 3.2 or 3.3 locally, point them at the same-functionality `@MockBean` annotation (still present, just deprecated). CI matrix already covers both.

### 10.4 `@ConfigurationProperties` records require `@EnableConfigurationProperties`

Java records work as `@ConfigurationProperties` targets since Boot 3.0, but the autoconfig must register them explicitly via `@EnableConfigurationProperties(PoliPageProperties.class)` — record-form properties are NOT auto-discovered through `@ConfigurationPropertiesScan`. The record's constructor is the binding seam; field accessors are passive.

### 10.5 Health probe uses `/v1/health`, NEVER `/v1/render`

Tempting to "verify end-to-end" by rendering a tiny PDF in the health indicator. **DON'T.** Every Kubernetes readiness probe would consume an API quota call. The platform exposes a lightweight unauthenticated `/v1/health` for exactly this purpose. The HealthIndicator hits that.

### 10.6 Spotless + google-java-format quirks

`google-java-format` ignores `// region` / `// endregion` comments and re-indents around `@Builder` methods aggressively. If a contributor's IDE inserts non-conformant formatting, run `./gradlew spotlessApply` locally before committing — the CI's `spotlessCheck` is strict.

### 10.7 Single root `.env`, no per-app `.env.local`

The integration test suite and both example apps read the workspace root `.env` (`/Users/mickael/Projects/.env`) at bootstrap. Real shell exports always win.

**Do NOT** introduce a per-module `.env.local` or instruct users to `cp .env .env.local`. This was an explicit hard requirement from Mickael during the symfony-bundle session, ported into every integration since. See `INTEGRATIONS_PLAN.md` §"Cross-cutting DX patterns" §2.

### 10.8 The interactive demo UI is mandatory, not optional

`GET /` in each example app returns a single-page HTML dashboard with one button per SDK feature, inline `<iframe>` previews, JSON pretty-print, and a document-lifecycle state machine in client JS. Aesthetic copied from `/Users/mickael/Projects/symfony-bundle/example-app/templates/demo.html` (white surface, indigo `#4f5d99`, Manrope + IBM Plex Sans + JetBrains Mono). Implemented as a plain string returned from `DemoController` — no template engine dependency. See `INTEGRATIONS_PLAN.md` §"Cross-cutting DX patterns" §1 for the bar.

## 11. When stuck

- Re-read `docs/spec/spring-boot-starter-specification.md` first; most "open questions" are answered there or in §18 "Decision log".
- Compare with the SDK at `/Users/mickael/Projects/sdk-java/`.
- Compare with `/Users/mickael/Projects/symfony-bundle/` — same SDK, sister DI shape (bundle / starter parallel).
- Look at industry benchmarks: `sentry-spring-boot-starter-jakarta`, `springdoc-openapi-starter-webmvc-ui`, `io.awspring.cloud:spring-cloud-aws-starter`. Their auto-configuration shape is the bar.
- Ask Mickael early. A two-line message is faster than a half-day rebuilding the wrong thing.
- If a CI failure looks unrelated to your change, check `main` first before assuming you caused it.
