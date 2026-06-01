# `page.poli:poli-page-spring-boot-starter` v0.1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship v0.1.0 of `page.poli:poli-page-spring-boot-starter` (plus the WebFlux sibling artifact) — a Spring Boot 3 auto-configuration starter wrapping the official Java SDK (`page.poli:sdk` at `../sdk-java/`). Apps add one dependency, set `POLI_PAGE_API_KEY`, and inject `PoliPageClient`. Ships `ResponseEntity` helpers for PDFs, an `ApplicationEventPublisher` bridge for SDK retry/error hooks, an Actuator `HealthIndicator`, Micrometer meters, and a WebFlux variant adapting `renderAsync()` to `Mono`.

**Architecture:** Gradle multi-module repo — three published artifacts (`poli-page-spring-boot-autoconfigure`, `poli-page-spring-boot-starter`, `poli-page-spring-boot-starter-webflux`). MVC + WebFlux split mirrors Springdoc / Sentry. Standard `@AutoConfiguration` + `AutoConfiguration.imports`. Each bean wrapped in `@ConditionalOnMissingBean` so consumers override.

**Tech Stack:** Java 17+, Spring Boot 3.2 / 3.3 / 3.4, Spring Framework 6.1+, JUnit 5, AssertJ, Mockito (transitive via `spring-boot-starter-test`), WireMock (HealthIndicator test only), Spotless + google-java-format, Error Prone + NullAway. Gradle 8.5+ primary.

**Spec:** `/Users/mickael/Projects/spring-boot/docs/spec/spring-boot-starter-specification.md` — authoritative source for all design decisions. This plan implements that spec in 15 bite-sized, independently-reviewable tasks.

**Working directory throughout:** `/Users/mickael/Projects/spring-boot/`

---

## Pre-flight: confirm scaffold is clean

Before Task 1, confirm the repo is bare aside from `.git/` and the docs you're reading right now. Anything else (stale `.gitkeep`, leftover code, IDE state) should be removed and folded into Task 1's commit rather than committed separately.

- [ ] **Step 0.1: Inspect current state**

```bash
cd /Users/mickael/Projects/spring-boot
git status
ls -la
```

Expected: `.git/` and `docs/` only. If there are stale files, list them and ask before deleting.

- [ ] **Step 0.2: Confirm sibling SDK is reachable**

```bash
ls /Users/mickael/Projects/sdk-java/src/main/java/page/poli/sdk/PoliPageClient.java
ls /Users/mickael/Projects/sdk-java/pom.xml
```

Both must exist. CI checks out `poli-page/sdk-java` as a sibling under `$GITHUB_WORKSPACE/sdk-java/`; the local layout mirrors that for parity.

- [ ] **Step 0.3: Confirm `sdk-java` v1.0.0 is on Maven Central**

```bash
curl -sf -o /dev/null https://repo1.maven.org/maven2/page/poli/sdk/1.0.0/sdk-1.0.0.pom \
  && echo "SDK published" || echo "SDK NOT YET PUBLISHED — see workaround note below"
```

If the SDK has shipped, depend on it normally from Central. If not, override via `mavenLocal()` — sdk-java is a Maven project, so Gradle's `includeBuild` composite mechanism does not apply. The pattern: run `./mvnw install -DskipTests` inside `../sdk-java/` to publish the local snapshot to `~/.m2/repository/`, then the root `build.gradle.kts`'s `mavenLocal()` repository (listed before `mavenCentral()`) picks it up. CI does not need the override because Central already serves `1.0.0`.

---

## Task 1: Bootstrap Gradle multi-module, tooling, and CI

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (via `gradle wrapper`)
- Create: `.github/workflows/ci.yml`
- Create: `.gitignore`
- Create: `poli-page-spring-boot-autoconfigure/build.gradle.kts`
- Create: `poli-page-spring-boot-starter/build.gradle.kts`
- Create: `poli-page-spring-boot-starter-webflux/build.gradle.kts`
- Create: empty `.gitkeep` files inside each module's `src/main/java/` and `src/test/java/`

**Goal:** repo `./gradlew build` succeeds (no production code yet, no tests yet — just module skeletons + Spotless + the BOM-pinned Spring Boot version). CI green via auto-skip behaviour.

- [ ] **Step 1.1: Initialise the Gradle wrapper at 8.10**

```bash
cd /Users/mickael/Projects/spring-boot
gradle wrapper --gradle-version 8.10 --distribution-type bin
```

- [ ] **Step 1.2: Author `settings.gradle.kts`** with the four modules listed in spec §13.1. Add the `includeBuild` workaround at the top, gated by the existence of `../sdk-java/settings.gradle*` — so a checkout of just the spring-boot repo (without the sibling SDK) still resolves from Central.

- [ ] **Step 1.3: Author root `build.gradle.kts`** applying `io.spring.dependency-management` and `com.diffplug.spotless` to all subprojects. Pin `springBootVersion` from `gradle.properties` so the matrix can swap it.

- [ ] **Step 1.4: Author CI workflow** matching spec §15 — 6-cell matrix (Java 17/21 × Boot 3.2/3.3/3.4). Auto-skips build step if `settings.gradle.kts` missing.

- [ ] **Step 1.5: Verify local build**

```bash
./gradlew --no-daemon build spotlessCheck
```

Both must pass with zero source files.

- [ ] **Step 1.6: Commit**

```
chore: bootstrap Gradle multi-module + CI workflow

Adds settings.gradle.kts (autoconfigure, starter, starter-webflux,
example-app), root build.gradle.kts pinning Spring Boot 3.4 by default,
Spotless + google-java-format, and a 6-cell CI matrix.

No production code yet — modules are empty skeletons. Build passes
green; tests will light up in subsequent tasks.
```

---

## Task 2: PoliPageProperties record with validation

**Module:** `poli-page-spring-boot-autoconfigure`

**Files:**
- Create: `src/main/java/page/poli/sdk/spring/PoliPageProperties.java`
- Create: `src/main/resources/META-INF/spring-configuration-metadata.json` (handcrafted overlay)
- Create: `src/test/java/page/poli/sdk/spring/PoliPagePropertiesTest.java`

**Goal:** `@ConfigurationProperties("poli-page")` record with Jakarta Bean Validation. Bad inputs fail at startup with documented messages.

- [ ] **Step 2.1: RED** — author `PoliPagePropertiesTest`:
  - Valid `pp_test_*` key + all defaults → binds cleanly.
  - `apiKey` missing → `BindValidationException` mentioning "apiKey".
  - `apiKey` not matching `^pp_(test|live)_.+$` → message contains the docs link.
  - `requestTimeout: PT0S` → fails.
  - `retries.maxAttempts: -1` → fails.
  - `retries.maxAttempts: 11` → fails.
  - `retries.delay: PT60S` → fails (cap 30s).

Use `ApplicationContextRunner` with `withPropertyValues(...)` and assert `applicationContext.getStartupFailure()`.

- [ ] **Step 2.2: GREEN** — author `PoliPageProperties` per spec §6 (record + nested records, `@NotBlank`, `@Pattern`, `@DurationMin`, `@DurationMax`, `@Min`, `@Max`).

- [ ] **Step 2.3: Configuration metadata** — write `META-INF/spring-configuration-metadata.json` with descriptions and default values per spec §6.3. IDE auto-completion verified manually in IntelliJ.

- [ ] **Step 2.4: Commit**

```
feat: PoliPageProperties with Jakarta Bean Validation

@ConfigurationProperties("poli-page") record binding api-key,
base-url, request-timeout, retries.*, and Spring-only toggles
(health/metrics/events). Validation enforces the pp_test_/pp_live_
prefix, positive timeouts, and 0..10 retry attempts.

Adds spring-configuration-metadata.json for IDE auto-completion.
```

---

## Task 3: PoliPageAutoConfiguration with PoliPageClient @Bean

**Module:** `poli-page-spring-boot-autoconfigure`

**Files:**
- Create: `src/main/java/page/poli/sdk/spring/PoliPageAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `src/test/java/page/poli/sdk/spring/PoliPageAutoConfigurationTest.java`

**Goal:** Auto-configure a `PoliPageClient` bean from `PoliPageProperties`. Bean is `@ConditionalOnMissingBean` so user overrides win.

- [ ] **Step 3.1: RED** — author tests using `ApplicationContextRunner.withConfiguration(AutoConfigurations.of(PoliPageAutoConfiguration.class))`:
  - With `poli-page.api-key=pp_test_x` → context has exactly one `PoliPageClient` bean.
  - With `poli-page.api-key=pp_test_x` + user `@Bean PoliPageClient` → user's bean wins (`@ConditionalOnMissingBean`).
  - All defaults: `client.options().baseUrl()` equals the SDK's own default — proves null pass-through (§6).
  - Explicit `poli-page.base-url=https://api-develop.poli.page` → that URI is on the resulting client.

- [ ] **Step 3.2: GREEN** — author `PoliPageAutoConfiguration` per spec §7.2 (null-guarded builder calls so unset properties don't shadow SDK defaults).

- [ ] **Step 3.3: Wire AutoConfiguration.imports** — list the FQN of `PoliPageAutoConfiguration` in the imports file.

- [ ] **Step 3.4: Commit**

```
feat: PoliPageAutoConfiguration registers PoliPageClient @Bean

@AutoConfiguration + @ConditionalOnMissingBean — consumer can
override by declaring their own @Bean PoliPageClient. Builder
calls are null-guarded so unset properties don't shadow the SDK's
own defaults; that single source of truth is the constraint we
captured in spec §6.

Imports file activates the autoconfig under Boot 2.7+.
```

---

## Task 4: PoliPageEventBridge + event classes

**Module:** `poli-page-spring-boot-autoconfigure`

**Files:**
- Create: `src/main/java/page/poli/sdk/spring/event/PoliPageRetryEvent.java`
- Create: `src/main/java/page/poli/sdk/spring/event/PoliPageErrorEvent.java`
- Create: `src/main/java/page/poli/sdk/spring/PoliPageEventBridge.java`
- Modify: `PoliPageAutoConfiguration` — register bridge bean, pass method refs into builder
- Create: `src/test/java/page/poli/sdk/spring/PoliPageEventBridgeTest.java`
- Modify: `PoliPageAutoConfigurationTest` — assert bridge wiring

**Goal:** SDK `Consumer<RetryEvent>` / `Consumer<Throwable>` hooks publish typed Spring `ApplicationEvent`s. Disabled by `poli-page.events.enabled=false`.

- [ ] **Step 4.1: RED — bridge unit test** — instantiate `PoliPageEventBridge` with a stub `ApplicationEventPublisher`; calling `onRetry(RetryEvent)` and `onError(PoliPageException)` publishes the corresponding wrapper events with the SDK payload intact.

- [ ] **Step 4.2: RED — autoconfig assertion** — `ApplicationContextRunner` test:
  - Default: bridge bean present; publishing a fake `RetryEvent` through the client's `onRetry` hook triggers a `@EventListener` registered in the test.
  - `poli-page.events.enabled=false`: bridge bean absent; client built without `.onRetry(...)`.

- [ ] **Step 4.3: GREEN** — author event classes (per spec §10.1), `PoliPageEventBridge` (§10.2), and update `PoliPageAutoConfiguration` to register the bridge + pipe `bridge::onRetry`, `bridge::onError` into the builder.

- [ ] **Step 4.4: Commit**

```
feat: ApplicationEventPublisher bridge for SDK retry/error hooks

PoliPageRetryEvent + PoliPageErrorEvent wrap the SDK's RetryEvent
and PoliPageException so consumers subscribe with @EventListener
just like any other Spring application event.

The bridge bean is opt-out (poli-page.events.enabled=false) — when
disabled, the SDK client is built without onRetry/onError hooks and
no events fire.
```

---

## Task 5: PoliPageResponses (MVC) — bytes/stream/preview/documentRedirect

**Module:** `poli-page-spring-boot-starter`

**Files:**
- Create: `src/main/java/page/poli/sdk/spring/web/PoliPageResponses.java`
- Create: `src/test/java/page/poli/sdk/spring/web/PoliPageResponsesTest.java`

**Goal:** Stateless helper that adapts SDK return values to `ResponseEntity` with correct PDF/HTML headers and RFC 5987 filename encoding.

- [ ] **Step 5.1: RED** — author `PoliPageResponsesTest` (one test per method, two filename cases per file-producing method):
  - `bytes(...)`: `Content-Type: application/pdf`, exact `Content-Length`, RFC 5987 disposition for `"facture-é.pdf"`, plain `filename="invoice.pdf"` for ASCII, `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`.
  - `bytes(..., inline=true)`: `Content-Disposition` starts with `inline;`.
  - `stream(...)`: same headers, body is a `StreamingResponseBody`; calling `.writeTo(out)` copies the stream and closes it. Use an `InputStream` that tracks `close()` calls; assert it was closed exactly once.
  - `preview(PreviewResult)` and `preview(DocumentPreviewResult)`: `Content-Type: text/html; charset=utf-8`, `Cache-Control: private, no-store`, body is the preview's HTML string.
  - `documentRedirect(...)`: status 302, `Location` set to the descriptor's presigned URL, `Cache-Control: private, no-store`.

- [ ] **Step 5.2: GREEN** — author `PoliPageResponses` using Spring's `ContentDisposition.attachment().filename(name, UTF_8).build()` for RFC 5987.

- [ ] **Step 5.3: Commit**

```
feat: PoliPageResponses with bytes/stream/preview/documentRedirect

Stateless helper turning SDK results into Spring ResponseEntity
with the headers users get wrong: Content-Disposition with RFC
5987 encoding for non-ASCII filenames, Cache-Control: private,
no-store, X-Content-Type-Options: nosniff.

stream() returns ResponseEntity<StreamingResponseBody> that owns
the SDK InputStream lifecycle — the connection is released even
if the client disconnects mid-stream.
```

---

## Task 6: PoliPageWebMvcAutoConfiguration

**Module:** `poli-page-spring-boot-starter`

**Files:**
- Create: `src/main/java/page/poli/sdk/spring/web/PoliPageWebMvcAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `src/test/java/page/poli/sdk/spring/web/PoliPageWebMvcAutoConfigurationTest.java`

**Goal:** Register `PoliPageResponses` only in a servlet stack. Verify the WebFlux autoconfig does NOT fire here.

- [ ] **Step 6.1: RED** — `WebApplicationContextRunner` (`WebApplicationContextRunner.withConfiguration(...)`) asserts the bean is present; `ApplicationContextRunner` (non-web) asserts the bean is absent; `ReactiveWebApplicationContextRunner` also asserts absent.

- [ ] **Step 6.2: GREEN** — author the autoconfig with `@ConditionalOnWebApplication(type = SERVLET)` and `@ConditionalOnMissingBean(PoliPageResponses.class)`.

- [ ] **Step 6.3: Commit**

```
feat: PoliPageWebMvcAutoConfiguration registers PoliPageResponses

Activates only in a servlet stack (@ConditionalOnWebApplication
type = SERVLET). Tests cover all three stack flavours (servlet
present / non-web / reactive present) to prove the conditional.
```

---

## Task 7: PoliPageReactiveResponses + PoliPageWebFluxAutoConfiguration

**Module:** `poli-page-spring-boot-starter-webflux`

**Files:**
- Create: `src/main/java/page/poli/sdk/spring/reactive/PoliPageReactiveResponses.java`
- Create: `src/main/java/page/poli/sdk/spring/reactive/PoliPageWebFluxAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `src/test/java/page/poli/sdk/spring/reactive/PoliPageReactiveResponsesTest.java`
- Create: `src/test/java/page/poli/sdk/spring/reactive/PoliPageWebFluxAutoConfigurationTest.java`

**Goal:** Reactive mirror of Tasks 5+6.

- [ ] **Step 7.1: RED — `PoliPageReactiveResponsesTest`** — `StepVerifier` exercises each method's `Mono`:
  - `bytes(future, "x.pdf")`: completes with the right `ResponseEntity<byte[]>` and headers.
  - `stream(future, "x.pdf", false, factory)`: completes with a `Flux<DataBuffer>` body that emits the bytes when subscribed; cancellation closes the underlying `InputStream`.
  - `preview(future)` / `documentRedirect(future)`: trivial passthroughs.

- [ ] **Step 7.2: RED — autoconfig** — `ReactiveWebApplicationContextRunner` asserts the bean is present; servlet runner asserts absent.

- [ ] **Step 7.3: GREEN** — author both classes per spec §9 + §7.3.

- [ ] **Step 7.4: Commit**

```
feat: WebFlux starter with PoliPageReactiveResponses

Mono<ResponseEntity<byte[]>> + Flux<DataBuffer> helpers bridging
sdk-java's renderAsync() CompletableFuture results into reactive
return types. Cancellation propagates back to the underlying
future via Mono.fromFuture(..., suppressCancel=false).

Autoconfig activates only in a reactive stack — parallels the
MVC autoconfig in the sibling starter artifact.
```

---

## Task 8: PoliPageHealthIndicator + actuator wiring

**Module:** `poli-page-spring-boot-autoconfigure`

**Files:**
- Create: `src/main/java/page/poli/sdk/spring/actuator/PoliPageHealthIndicator.java`
- Modify: `PoliPageAutoConfiguration` — nested `@Configuration` class registering the indicator when actuator + property allow it
- Create: `src/test/java/page/poli/sdk/spring/actuator/PoliPageHealthIndicatorTest.java`

**Goal:** `/actuator/health` shows a `poliPage` component reporting whether `GET <baseUrl>/v1/health` returns 200.

- [ ] **Step 8.1: RED** — start a WireMock server on a random port:
  - `200 OK` on `/v1/health` → `health.getStatus() == Status.UP`, details include `baseUrl`.
  - `503` → `Status.DOWN`, details include `status: 503`.
  - WireMock stopped (connection refused) → `Status.DOWN`, exception captured.
  - Then an autoconfig test: with actuator on classpath + default props → bean present; with `poli-page.health.enabled=false` → bean absent.

- [ ] **Step 8.2: GREEN** — author indicator per spec §11. Wire it inside a nested `@Configuration` class guarded by `@ConditionalOnClass(HealthIndicator.class)` and `@ConditionalOnProperty(value="poli-page.health.enabled", matchIfMissing=true)`.

- [ ] **Step 8.3: Commit**

```
feat: PoliPageHealthIndicator backed by GET /v1/health

Probes the lightweight health endpoint (never spends an API
quota call on health). Up/Down semantics with cause details
on failure. Toggle via poli-page.health.enabled=false.

Test pins behaviour with a WireMock server (the only place in
the starter that ever stubs HTTP — see spec §14.2).
```

---

## Task 9: PoliPageMetrics MeterBinder

**Module:** `poli-page-spring-boot-autoconfigure`

**Files:**
- Create: `src/main/java/page/poli/sdk/spring/metrics/PoliPageMetrics.java`
- Modify: `PoliPageAutoConfiguration` — register the binder when Micrometer is on the classpath
- Create: `src/test/java/page/poli/sdk/spring/metrics/PoliPageMetricsTest.java`

**Goal:** Four meters as specified in §12. Bounded tag cardinality.

- [ ] **Step 9.1: RED** — `SimpleMeterRegistry` test:
  - Publish a `PoliPageRetryEvent` with `attempt=1`, `reason="5xx"`; assert `poli.page.retries.total{attempt="1",reason="5xx"}` is `1` and `poli.page.retries.delay` recorded a sample with the right value.
  - Publish a `PoliPageErrorEvent` whose exception is `PoliPageRateLimitException(status=429)`; assert `poli.page.errors.total{code="rate_limited",status_class="4xx"}` is `1`.
  - With `poli-page.metrics.enabled=false`: bean absent.

- [ ] **Step 9.2: GREEN** — author `PoliPageMetrics implements MeterBinder` per spec §12.

- [ ] **Step 9.3: Commit**

```
feat: PoliPageMetrics MeterBinder for Micrometer

Four meters: poli.page.retries.total (counter), poli.page.retries.delay
(timer), poli.page.errors.total (counter), poli.page.client.up (gauge).
Tag cardinality bounded by the SDK's documented enum values — safe
for Prometheus scraping.

Disabled by poli-page.metrics.enabled=false or by simply not having
Micrometer on the classpath.
```

---

## Task 10: Integration test against develop API (gated)

**Module:** root + `poli-page-spring-boot-starter`

**Files:**
- Create: `src/integrationTest/java/page/poli/sdk/spring/IntegrationApplication.java`
- Create: `src/integrationTest/java/page/poli/sdk/spring/RenderAgainstDevelopApiIT.java`
- Modify: `build.gradle.kts` (root) — register the `integrationTest` source set + task

**Goal:** One happy-path test against `api-develop.poli.page`, skipped automatically when `POLI_PAGE_API_KEY` is unset.

- [ ] **Step 10.1: Source set setup** — Gradle `testing.suites` block declares the `integrationTest` suite that depends on the starter module + `spring-boot-starter-test`.

- [ ] **Step 10.2: Write test**:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "POLI_PAGE_API_KEY", matches = "pp_(test|live)_.+")
class RenderAgainstDevelopApiIT { ... }
```

The test boots a minimal `@SpringBootApplication` with one `@RestController` exposing `GET /welcome.pdf`, calls it via `TestRestTemplate`, asserts the response is 200, `Content-Type: application/pdf`, body starts with `%PDF-`.

- [ ] **Step 10.3: CI** — wire the `integrationTest` task into the `integration` job in `.github/workflows/ci.yml`. Gate on `secrets.POLI_PAGE_DEVELOP_API_KEY` being set.

- [ ] **Step 10.4: Commit**

```
test: integration test against develop API (gated)

Single happy-path test: boots a minimal Spring Boot app embedding
the starter, calls a controller that renders the getting-started/
welcome template, asserts the response is a real PDF.

Skipped when POLI_PAGE_API_KEY is unset (so PR contributors get
green local runs).
```

---

## Task 11: Example app (MVC)

**Module:** `example-app/mvc`

**Files:**
- Create: `build.gradle.kts`
- Create: `src/main/java/page/poli/example/mvc/ExampleApplication.java`
- Create: `src/main/java/page/poli/example/mvc/DemoController.java`
- Create: `src/main/java/page/poli/example/mvc/RenderController.java`
- Create: `src/main/java/page/poli/example/mvc/DocumentController.java`
- Create: `src/main/java/page/poli/example/mvc/RenderToFileRunner.java`
- Create: `src/main/resources/application.yml`
- Create: `README.md`

**Goal:** Runnable MVC Spring Boot app that demonstrates all 10 SDK demo steps. Includes an interactive HTML dashboard at `GET /` matching the aesthetic of symfony-bundle and nestjs example apps (white surface, indigo `#4f5d99`, Manrope + IBM Plex Sans + JetBrains Mono).

- [ ] **Step 11.1**: route map per spec §14 — `GET /render/pdf`, `GET /render/stream`, `app:demo:render-to-file` runner, `GET /render/preview`, `POST /documents`, `GET /documents/{id}`, `GET /documents/{id}/thumbnails`, `GET /documents/{id}/preview`, `DELETE /documents/{id}`, `GET /errors/bad-version`.

- [ ] **Step 11.2**: write `DemoController` returning the single-page HTML dashboard as a `String` (no template engine — same approach as nestjs/symfony).

- [ ] **Step 11.3**: write `RenderToFileRunner` implementing `ApplicationRunner` so `./gradlew :example-app:mvc:bootRun --args=--render-to-file` triggers the SDK's `renderToFile()` helper.

- [ ] **Step 11.4**: README walkthrough — `./gradlew :example-app:mvc:bootRun`, `curl localhost:8080/render/pdf -o invoice.pdf`, open `localhost:8080/` in a browser.

- [ ] **Step 11.5**: Commit

```
feat: example-app/mvc demonstrating all 10 SDK methods

Runnable Spring Boot 3.4 MVC app at example-app/mvc/. Routes mirror
the SDK's demo.java step-by-step. GET / serves the interactive
HTML dashboard (single string, no template engine), aesthetic shared
with the symfony-bundle and nestjs example apps.
```

---

## Task 12: Example app (WebFlux)

**Module:** `example-app/webflux`

**Files:**
- Mirror of Task 11 under `example-app/webflux/`, using `Mono`/`Flux` return types and `PoliPageReactiveResponses`.

- [ ] **Step 12.1**: copy structure from MVC sibling, swap return types and helpers.
- [ ] **Step 12.2**: README addendum noting `./gradlew :example-app:webflux:bootRun`.
- [ ] **Step 12.3**: Commit

```
feat: example-app/webflux mirroring the MVC sibling

Same 10 SDK demo steps, reactive style. Mono<ResponseEntity<...>>
for buffered responses, Flux<DataBuffer> for streamed PDFs. Lets
a reader compare the two stacks side-by-side.
```

---

## Task 13: spring-configuration-metadata.json polish

**Module:** `poli-page-spring-boot-autoconfigure`

**Files:**
- Modify: `src/main/resources/META-INF/spring-configuration-metadata.json`
- Add: `src/main/resources/META-INF/additional-spring-configuration-metadata.json` (handcrafted overlay)

**Goal:** IntelliJ shows clean descriptions + default values for every `poli-page.*` property. The `spring-boot-configuration-processor` generates a baseline; we overlay descriptions for keys whose default Javadoc is too terse.

- [ ] **Step 13.1**: Run `./gradlew :poli-page-spring-boot-autoconfigure:compileJava` and inspect the generated metadata.
- [ ] **Step 13.2**: For each property whose description is unhelpful, add an entry to `additional-spring-configuration-metadata.json` with a fluent description (Spring Boot's processor merges the two).
- [ ] **Step 13.3**: Manually verify in IntelliJ.
- [ ] **Step 13.4**: Commit

```
docs: handcrafted spring-configuration-metadata overlay

Adds descriptions for keys the annotation processor can't infer
cleanly: poli-page.api-key gets the docs URL, health/metrics/events
toggles get one-line summaries explaining what's gated.
```

---

## Task 14: README + CHANGELOG initial entry

**Files:**
- Replace: `README.md`
- Modify: `CHANGELOG.md` — fill in 0.1.0 release notes

**Goal:** A reader can `composer require`-equivalent in one minute.

- [ ] **Step 14.1**: README sections: badges, Install (Maven + Gradle), Quick start (`@Autowired PoliPageClient`), Configuration table, Demo app pointer, Documentation pointer (to `docs/`), Versioning, License.
- [ ] **Step 14.2**: CHANGELOG entry per the existing draft.
- [ ] **Step 14.3**: Commit

```
docs: README + CHANGELOG initial entry

Quick-start snippet, install instructions for Maven and Gradle,
links to the per-feature deep-dives under docs/.
```

---

## Task 15: Sonatype OSSRH publishing wiring

**Files:**
- Create: `.github/workflows/publish.yml`
- Modify: each module `build.gradle.kts` — `maven-publish` + `signing` blocks
- Modify: root `build.gradle.kts` — apply `io.github.gradle-nexus.publish-plugin`
- Modify: `CONTRIBUTING.md` — release process

**Goal:** Pushing a `v0.1.0` tag publishes three artifacts to Maven Central.

- [ ] **Step 15.1**: configure `maven-publish` per artifact (groupId `page.poli`, artifactId per module).
- [ ] **Step 15.2**: configure `nexus-publish-plugin` for OSSRH staging + auto-release.
- [ ] **Step 15.3**: configure `signing` to use `GPG_PRIVATE_KEY` + `GPG_PASSPHRASE` secrets.
- [ ] **Step 15.4**: dry-run publish to a staging local Maven repo (`./gradlew publishToMavenLocal`) and inspect the generated `.pom` for each module.
- [ ] **Step 15.5**: Commit

```
chore: Sonatype OSSRH publish workflow

publish.yml fires on tag push v*. Three artifacts publish under
page.poli with the same group-id as the sdk-java project. Signing
key + OSSRH credentials live in GitHub Actions secrets; CONTRIBUTING
documents the rotation procedure.
```

---

## Done criteria

The implementation is "v0.1.0 done" when:

- [ ] All 15 tasks committed on `main`.
- [ ] CI green on all 6 matrix cells.
- [ ] Integration test passed at least once against `api-develop.poli.page` (manual local run is acceptable for the first release).
- [ ] `./gradlew :example-app:mvc:bootRun` and `./gradlew :example-app:webflux:bootRun` both serve `GET /` and `GET /render/pdf` correctly with a valid `POLI_PAGE_API_KEY`.
- [ ] `v0.1.0` tag pushed; the three artifacts visible on `https://central.sonatype.com/artifact/page.poli/poli-page-spring-boot-starter`.
- [ ] `INTEGRATIONS_PLAN.md` updated with a Spring Boot row (verdict: **Strong yes**, reasoning: highest-volume JVM framework, auto-configuration is a load-bearing convention).

Estimated total effort with this plan in hand: **4-6 working days** for a single agent, working strict TDD.
