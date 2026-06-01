# `page.poli:poli-page-spring-boot-starter` — Specification

> Self-contained specification for **v0.1.0** of the Poli Page Spring Boot starter. A new agent should be able to read this document end-to-end and implement the starter without consulting external chat history.

**Status**: approved design, ready to implement.
**Roadmap slot**: not in `INTEGRATIONS_PLAN.md` yet — promoted from "recipe in `sdk-java/examples/`" (sdk-roadmap §6.2) to a first-class starter on 2026-06-01 because Spring Boot is the highest-volume slice of the JVM ecosystem and the autoconfiguration idiom genuinely earns its own artifact.
**Last updated**: 2026-06-01.

---

## 1. What this starter is, and what it isn't

**Is**: a thin Spring Boot 3 auto-configuration starter that wraps the official Poli Page Java SDK (`page.poli:sdk`, source at `/Users/mickael/Projects/sdk-java/`) so that a Spring Boot application can add one dependency, set one property, and inject a `PoliPageClient` into any `@Component` or `@RestController` via constructor injection. Also ships idiomatic `ResponseEntity` helpers for returning PDFs with correct HTTP headers, an `ApplicationEventPublisher` bridge for the SDK's `onRetry` / `onError` `Consumer` hooks, a Spring Boot Actuator `HealthIndicator`, a Micrometer `MeterBinder` for retry/error counters, and a WebFlux flavour that adapts `renderAsync()` results to `Mono<ResponseEntity<byte[]>>`.

**Is not**:
- A reimplementation of HTTP transport, retries, error mapping, or JSON plumbing — that all lives in `sdk-java` and is exhaustively tested there. The starter's job is **wiring, not behaviour**.
- A "kitchen sink" starter. `WebClient`-based transport replacement, Spring Cache integration, Spring Cloud Sleuth tracing instrumentation, Kotlin DSL, Spring Native AOT hints (best-effort only — see §17), Quarkus parity — all deferred to v0.2 (see §17).

**Quality bar**: match what these starters deliver today: `sentry-spring-boot-starter-jakarta`, `springdoc-openapi-starter-webmvc-ui`, `io.awspring.cloud:spring-cloud-aws-starter`, `com.datadoghq:dd-trace-api` + `dd-java-agent`. If our shape differs from theirs, we have a reason.

---

## 2. Required reading (concrete file paths)

Before writing code, read:

| File | Why |
|---|---|
| `/Users/mickael/Projects/sdk-java/src/main/java/page/poli/sdk/PoliPageClient.java` | The client class we're wiring. Note the `Builder` (§7.2 below). |
| `/Users/mickael/Projects/sdk-java/src/main/java/page/poli/sdk/Render.java` | The `client.render()` facade. Methods: `pdf`, `pdfStream`, `preview`, `document`. |
| `/Users/mickael/Projects/sdk-java/src/main/java/page/poli/sdk/Documents.java` | The `client.documents()` facade. Methods: `get`, `preview`, `thumbnails`, `delete`. |
| `/Users/mickael/Projects/sdk-java/src/main/java/page/poli/sdk/RenderAsync.java` | The async facade returning `CompletableFuture<T>` — used by the WebFlux module. |
| `/Users/mickael/Projects/sdk-java/src/main/java/page/poli/sdk/RetryEvent.java` | Payload of the `onRetry` SDK hook we'll publish as a Spring `ApplicationEvent`. |
| `/Users/mickael/Projects/sdk-java/src/main/java/page/poli/sdk/exception/` (full directory) | Sealed `PoliPageException` hierarchy. Starter does not catch or remap these — they propagate. |
| `/Users/mickael/Projects/sdk-java/README.md` | Quick-start surface — `example-app/` mirrors the same flow. |
| `/Users/mickael/Projects/sdk-java/pom.xml` | SDK's Maven coordinates: `page.poli:sdk:1.0.0`. |
| `/Users/mickael/Projects/poli-page/docs/onboarding/micka/project-briefing.md` | Platform context (what Poli Page is, API model, key prefixes, develop env). |
| `/Users/mickael/Projects/poli-page/docs/onboarding/micka/sdk-specification.md` | API contract every SDK implements. |
| `/Users/mickael/Projects/symfony-bundle/docs/spec/bundle-specification.md` | The most architecturally similar sibling (DI extension, response factory, event bridge, CLI command). Copy the shape; replace PHP idioms with Spring idioms. |
| `/Users/mickael/Projects/INTEGRATIONS_PLAN.md` | Cross-repo verdict and order. Add a Spring Boot row when this spec lands. |

Reference starters to compare patterns against (open on GitHub):
- `getsentry/sentry-java/sentry-spring-boot-starter-jakarta` — closest in shape (third-party SDK + auto-configuration + `ApplicationEventPublisher` integration + Actuator `HealthIndicator`). **Primary reference.**
- `springdoc/springdoc-openapi` — multi-module starter that ships separate `-webmvc` and `-webflux` artifacts. Mirrors our §13.1 split.
- `awspring-io/spring-cloud-aws` — DI binding for a constructor-heavy SDK client, plus actuator + metrics.
- `spring-projects/spring-boot/spring-boot-autoconfigure` itself — for the conditional patterns (`@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`) we lean on heavily.

---

## 3. Version targets

| Dimension | Constraint | Rationale |
|---|---|---|
| Java | `17+` | Inherits from SDK (`page.poli:sdk` requires Java 17). Spring Boot 3 also requires Java 17. |
| Spring Boot | `3.2.x \|\| 3.3.x \|\| 3.4.x` | Spring Boot 3 is the only major still receiving fixes (2.7 OSS support ended Nov 2024). 3.2 is the floor — first release with Jackson 2.16 stable. 3.4 is the current LTS-track release. |
| Spring Framework | `6.1+` | Transitive from Boot 3.2+. |
| Build | Gradle 8.5+ (development) / Maven 3.9+ (CI parity) | Gradle is the maintainer's daily driver; Maven is mainstream for consumers — published as both. |
| Namespace | `jakarta.*` only | Boot 3 is Jakarta-only. No `javax.*` fallback. |

CI matrix is the 3×2 grid: Spring Boot `{3.2.x, 3.3.x, 3.4.x}` × JDK `{17, 21}`. Six cells. See §15.

---

## 4. Architecture style

Use **modern `@AutoConfiguration`** (introduced in Spring Boot 2.7, mandatory in 3.x for new code). Each starter module declares one or more `@AutoConfiguration` classes whose names are listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (the post-2.7 replacement for `spring.factories`).

**Why**: it's what current Spring Boot docs lead with for new starters, it gives explicit ordering via `@AutoConfiguration(after = ..., before = ...)`, and it surfaces correctly in the Boot 3 Actuator's `/conditions` endpoint. The legacy `spring.factories` mechanism is fine but deprecated for autoconfig.

Bean definitions are written in **Java `@Bean` methods** (not XML), and every bean is wrapped in `@ConditionalOnMissingBean` so the consumer can override any defaulted bean by declaring their own with the same type — the established Spring Boot starter contract.

**Two artifacts, one repo** (a "facets" split — Springdoc and Sentry both do this):

- `poli-page-spring-boot-starter` — depends on `spring-boot-starter-web` (MVC). Auto-configures the blocking facade + `ResponseEntity<byte[]>` / `ResponseEntity<StreamingResponseBody>` helpers.
- `poli-page-spring-boot-starter-webflux` — depends on `spring-boot-starter-webflux`. Auto-configures the async facade + `Mono<ResponseEntity<byte[]>>` / `Flux<DataBuffer>` helpers.

A single application chooses one (Spring Boot apps generally pick MVC **or** WebFlux, not both). Each artifact pulls in `page.poli:sdk` as a transitive dependency.

A third, internal module `poli-page-spring-boot-autoconfigure-common` holds the parts shared by both starters (properties class, event bridge, health indicator, meter binder). Consumers never depend on it directly — the two starter artifacts above bundle it transitively.

---

## 5. File layout

```
spring-boot/
├── settings.gradle.kts                                 # multi-module Gradle build
├── build.gradle.kts                                    # root: spring-dependency-management, java-library, publishing
├── gradle.properties                                   # group=page.poli, version=0.1.0-SNAPSHOT
├── gradlew, gradlew.bat, gradle/                       # Gradle wrapper
├── pom.xml                                             # generated by Gradle for Maven Central parity (see §14)
│
├── poli-page-spring-boot-autoconfigure/                # internal "common" module
│   ├── build.gradle.kts
│   └── src/main/java/page/poli/sdk/spring/
│       ├── PoliPageProperties.java                     # @ConfigurationProperties("poli-page")
│       ├── PoliPageAutoConfiguration.java              # @AutoConfiguration — PoliPageClient @Bean
│       ├── PoliPageEventBridge.java                    # Consumer<RetryEvent>/Consumer<Throwable> → ApplicationEventPublisher
│       ├── event/
│       │   ├── PoliPageRetryEvent.java                 # ApplicationEvent wrapper around SDK RetryEvent
│       │   └── PoliPageErrorEvent.java                 # ApplicationEvent wrapper around PoliPageException
│       ├── actuator/
│       │   └── PoliPageHealthIndicator.java            # HealthIndicator implementation
│       └── metrics/
│           └── PoliPageMetrics.java                    # MeterBinder for Micrometer
│
├── poli-page-spring-boot-starter/                      # MVC starter (the one most users add)
│   ├── build.gradle.kts                                # depends on autoconfigure-common + spring-boot-starter-web
│   ├── src/main/resources/META-INF/spring/
│   │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── src/main/java/page/poli/sdk/spring/web/
│       ├── PoliPageWebMvcAutoConfiguration.java        # @AutoConfiguration(after = PoliPageAutoConfiguration.class)
│       └── PoliPageResponses.java                      # bytes/stream/preview/documentRedirect → ResponseEntity / ResponseBodyEmitter
│
├── poli-page-spring-boot-starter-webflux/              # WebFlux starter
│   ├── build.gradle.kts                                # depends on autoconfigure-common + spring-boot-starter-webflux
│   ├── src/main/resources/META-INF/spring/
│   │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── src/main/java/page/poli/sdk/spring/reactive/
│       ├── PoliPageWebFluxAutoConfiguration.java
│       └── PoliPageReactiveResponses.java              # Mono<ResponseEntity<byte[]>> + DataBuffer Flux helpers
│
├── tests/                                              # top-level integration tests across artifacts (Gradle composite)
│   └── (per-module tests live under each module's src/test/java)
│
├── example-app/                                        # MVC + WebFlux sibling apps (§14)
│   ├── settings.gradle.kts
│   ├── mvc/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/page/poli/example/mvc/
│   │       ├── ExampleApplication.java
│   │       ├── DemoController.java                     # interactive HTML dashboard at GET /
│   │       ├── RenderController.java                   # steps 1, 2, 4
│   │       ├── DocumentController.java                 # steps 5, 6, 7, 8, 9
│   │       └── RenderToFileRunner.java                 # ApplicationRunner — step 3
│   └── webflux/
│       ├── build.gradle.kts
│       └── src/main/java/page/poli/example/webflux/
│           └── (mirror of mvc/, returning Mono/Flux)
│
├── .github/workflows/ci.yml
├── README.md
├── CHANGELOG.md                                        # Keep a Changelog format
├── CONTRIBUTING.md
├── LICENSE                                             # MIT
└── CLAUDE.md                                           # integration-flavoured guidance
```

**Source file count**: 12 production source files (properties, two autoconfig classes per module, event bridge, two event classes, health indicator, meter binder, two response-helper classes). That is the entire starter. Anything beyond is scope creep — refer to §17 before adding.

---

## 6. Configuration properties

User-facing properties in `application.yml`:

```yaml
poli-page:
  api-key: ${POLI_PAGE_API_KEY}                # required
  base-url:                                    # optional URI; SDK applies its own default (https://api.poli.page)
  request-timeout:                             # optional Duration (e.g. 30s, PT1M); SDK default applies when omitted
  retries:
    max-attempts:                              # optional integer; SDK default applies when omitted
    delay:                                     # optional Duration; SDK default applies when omitted
  health:
    enabled: true                              # toggle HealthIndicator registration (default: true)
  metrics:
    enabled: true                              # toggle MeterBinder registration (default: true)
  events:
    enabled: true                              # toggle ApplicationEvent publication (default: true)
```

Bound to a Java record:

```java
@ConfigurationProperties("poli-page")
public record PoliPageProperties(
    String apiKey,
    @Nullable URI baseUrl,
    @Nullable Duration requestTimeout,
    Retries retries,
    Health health,
    Metrics metrics,
    Events events
) {
    public record Retries(@Nullable Integer maxAttempts, @Nullable Duration delay) {}
    public record Health(boolean enabled) {}
    public record Metrics(boolean enabled) {}
    public record Events(boolean enabled) {}
}
```

**One-to-one mapping with the SDK `PoliPageClient.Builder`** for the SDK options (`api-key`, `base-url`, `request-timeout`, `retries.max-attempts`, `retries.delay`). The remaining keys (`health.enabled`, `metrics.enabled`, `events.enabled`) gate Spring-specific subsystems, not SDK behaviour.

**Default-value discipline**: for every SDK option except `api-key`, the starter's properties default to `null` and the autoconfiguration passes the value to the builder **only when non-null**. The SDK alone owns default values. A future change in the SDK (e.g. raising the default `requestTimeout` from 60s to 90s) takes effect for starter users automatically.

Spring-specific toggles (`health.enabled`, `metrics.enabled`, `events.enabled`) default to `true`. Consumers turn them off via `poli-page.health.enabled=false` etc.

### 6.1 Validation rules

Properties are validated at startup via Spring's `@Validated` + Jakarta Bean Validation (`jakarta.validation`):

- `api-key`: `@NotBlank`, **`@Pattern(regexp = "^pp_(test|live)_.+$")`** with message `"Poli Page API key must start with pp_test_ or pp_live_. Get one at https://poli.page/dashboard/keys."` — catches the #1 misconfiguration (pasting a dashboard token).
- `base-url`: `@Nullable`, if set must be absolute (validated in autoconfig, not via annotation — `URI.isAbsolute()` is one line and clearer than a regex).
- `request-timeout`: `@DurationMin(seconds = 1)` `@DurationMax(seconds = 600)` (annotations from `spring-boot`).
- `retries.max-attempts`: `@Min(0)` `@Max(10)`.
- `retries.delay`: `@DurationMin(millis = 0)` `@DurationMax(seconds = 30)`.

Failed validation surfaces at startup as `BindValidationException` — the application does not boot. This matches how `spring-boot-starter-data-jpa` handles invalid `spring.datasource` properties.

### 6.2 Environment variable convention

`POLI_PAGE_API_KEY` is the canonical env var. Spring Boot's relaxed binding resolves `${POLI_PAGE_API_KEY}` → `poli-page.api-key` automatically. Documented in README under "Quick start".

### 6.3 Configuration metadata (IDE auto-completion)

Ship a `META-INF/spring-configuration-metadata.json` (handcrafted) so IntelliJ / VS Code suggest `poli-page.api-key`, `poli-page.base-url`, etc. with type hints and descriptions. The `spring-boot-configuration-processor` annotation processor generates most of this from the properties class' Javadoc, but a hand-tuned overlay ensures the user-facing description is clean ("Your Poli Page API key. Find one at https://poli.page/dashboard/keys.").

---

## 7. DI / bean registration

### 7.1 Bean map

Declared across `PoliPageAutoConfiguration` (common), `PoliPageWebMvcAutoConfiguration` (MVC starter), and `PoliPageWebFluxAutoConfiguration` (WebFlux starter):

| Bean name | Type | Module | Notes |
|---|---|---|---|
| `poliPageClient` | `PoliPageClient` | common | `@ConditionalOnMissingBean` — user overrides by declaring their own. Singleton. |
| `poliPageEventBridge` | `PoliPageEventBridge` | common | Internal — wires `Consumer<RetryEvent>` / `Consumer<Throwable>` into `ApplicationEventPublisher`. Disabled by `poli-page.events.enabled=false`. |
| `poliPageHealthIndicator` | `PoliPageHealthIndicator` | common | `@ConditionalOnClass(HealthIndicator.class)` + `@ConditionalOnProperty("poli-page.health.enabled" matchIfMissing=true)`. |
| `poliPageMetrics` | `PoliPageMetrics` | common | `@ConditionalOnClass(MeterRegistry.class)` + `@ConditionalOnProperty("poli-page.metrics.enabled" matchIfMissing=true)`. |
| `poliPageResponses` | `PoliPageResponses` | starter (MVC) | Stateless helper. Constructor injects nothing — pure transformation. |
| `poliPageReactiveResponses` | `PoliPageReactiveResponses` | starter-webflux | Same shape, reactive return types. |

### 7.2 SDK builder mapping

Reference (from `/Users/mickael/Projects/sdk-java/src/main/java/page/poli/sdk/PoliPageClient.java`):

```java
PoliPageClient.builder()
    .apiKey(String)
    .baseUrl(URI)              // optional
    .maxRetries(int)           // optional
    .retryDelay(Duration)      // optional
    .requestTimeout(Duration)  // optional
    .onRetry(Consumer<RetryEvent>)  // optional
    .onError(Consumer<Throwable>)   // optional
    .build();
```

Bean wiring inside `PoliPageAutoConfiguration`:

```java
@Bean
@ConditionalOnMissingBean
public PoliPageClient poliPageClient(
        PoliPageProperties props,
        ObjectProvider<PoliPageEventBridge> bridge) {
    PoliPageClient.Builder b = PoliPageClient.builder().apiKey(props.apiKey());
    if (props.baseUrl() != null) b.baseUrl(props.baseUrl());
    if (props.requestTimeout() != null) b.requestTimeout(props.requestTimeout());
    if (props.retries().maxAttempts() != null) b.maxRetries(props.retries().maxAttempts());
    if (props.retries().delay() != null) b.retryDelay(props.retries().delay());
    bridge.ifAvailable(eb -> {
        b.onRetry(eb::onRetry);
        b.onError(eb::onError);
    });
    return b.build();
}
```

Two important details:

1. **`ObjectProvider<PoliPageEventBridge>`** lets the autoconfig respect `poli-page.events.enabled=false` (the bridge bean isn't registered) without forcing a hard dependency. The `ifAvailable` lambda only fires when the bridge bean exists.
2. **Method references** (`eb::onRetry`, `eb::onError`) erase to `Consumer<RetryEvent>` and `Consumer<Throwable>` cleanly — Java picks the right overload by SAM target.

### 7.3 Conditional ordering

```java
@AutoConfiguration
@EnableConfigurationProperties(PoliPageProperties.class)
@ConditionalOnClass(PoliPageClient.class)
public class PoliPageAutoConfiguration { ... }

@AutoConfiguration(after = PoliPageAutoConfiguration.class)
@ConditionalOnClass({ PoliPageClient.class, RestController.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PoliPageWebMvcAutoConfiguration { ... }

@AutoConfiguration(after = PoliPageAutoConfiguration.class)
@ConditionalOnClass({ PoliPageClient.class, WebFluxConfigurer.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class PoliPageWebFluxAutoConfiguration { ... }
```

`@ConditionalOnWebApplication(type = ...)` ensures the MVC autoconfig only fires in a servlet stack and the WebFlux autoconfig only fires in a reactive stack. A project that — atypically — depends on both starter artifacts ends up with the right one active per application, not both.

---

## 8. `PoliPageResponses` contract (MVC)

Single class, `page.poli.sdk.spring.web.PoliPageResponses`, four public methods. **No state.** Pure transformation from SDK output → Spring `ResponseEntity`.

### 8.1 Signatures

```java
public final class PoliPageResponses {

    public ResponseEntity<byte[]> bytes(byte[] pdf, String filename, boolean inline);

    public ResponseEntity<StreamingResponseBody> stream(
        InputStream stream, String filename, boolean inline);

    public ResponseEntity<String> preview(PreviewResult preview);
    public ResponseEntity<String> preview(DocumentPreviewResult preview);

    public ResponseEntity<Void> documentRedirect(DocumentDescriptor doc);
}
```

`bytes` and `stream` have `inline` defaulting to `false` via two-argument overloads (Java doesn't have default args — explicit overloads keep call sites readable).

### 8.2 Headers each method sets

`bytes()` and `stream()`:
- `Content-Type: application/pdf`
- `Content-Length: <byte count>` (computed on `bytes()`; omitted on `stream()` if not known)
- `Content-Disposition: attachment; filename="..."; filename*=UTF-8''...` (or `inline` if `inline == true`) — RFC 5987 encoding for non-ASCII filenames
- `Cache-Control: private, no-store` — PDFs typically contain personalised data; never let intermediaries cache
- `X-Content-Type-Options: nosniff`

`preview()`:
- `Content-Type: text/html; charset=utf-8`
- `Cache-Control: private, no-store`

`documentRedirect()`:
- HTTP 302
- `Location: <descriptor.presignedPdfUrl()>`
- `Cache-Control: private, no-store` (the presigned URL has its own expiry; never cache the redirect itself)

### 8.3 Filename encoding

RFC 5987 helper: if `filename` is pure ASCII, emit only `filename="..."`. If it contains non-ASCII, emit both `filename="..."` (with ASCII fallback) and `filename*=UTF-8''<percent-encoded>`. This is the part users get wrong; the helper exists so they don't have to think about it.

Reference implementation: Spring's `ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build()` produces RFC 5987 output natively. Use it; don't reimplement.

### 8.4 `StreamingResponseBody` ownership

`stream()` returns a `ResponseEntity<StreamingResponseBody>`. The body is a `StreamingResponseBody` lambda that owns the SDK `InputStream` — wraps the read loop in `try (InputStream s = stream) { s.transferTo(out); }` so the connection is released even if the client disconnects mid-stream. The SDK's `pdfStream()` Javadoc states the caller must close; this is the closing point.

---

## 9. WebFlux response helpers

`PoliPageReactiveResponses` mirrors `PoliPageResponses` but returns reactive types:

```java
public final class PoliPageReactiveResponses {

    public Mono<ResponseEntity<byte[]>> bytes(
        CompletableFuture<byte[]> future, String filename, boolean inline);

    public Mono<ResponseEntity<Flux<DataBuffer>>> stream(
        CompletableFuture<InputStream> future, String filename, boolean inline,
        DataBufferFactory bufferFactory);

    public Mono<ResponseEntity<String>> preview(CompletableFuture<PreviewResult> future);

    public Mono<ResponseEntity<Void>> documentRedirect(
        CompletableFuture<DocumentDescriptor> future);
}
```

Headers are identical to §8.2. The async-to-reactive bridge uses `Mono.fromFuture(future)` so cancellation propagates back to the underlying `CompletableFuture` via `Mono.fromFuture(..., true)` — the second-arg `suppressCancel = false` form, available since Reactor 3.5.

`stream()` adapts the SDK's `InputStream` to a `Flux<DataBuffer>` via `DataBufferUtils.readInputStream(() -> stream, bufferFactory, 8192)`. The factory is injected because reactive servers may be Netty (`NettyDataBufferFactory`) or Reactor-Netty + custom allocators.

---

## 10. EventDispatcher integration

The SDK exposes `onRetry: Consumer<RetryEvent>` and `onError: Consumer<Throwable>` builder hooks. We surface these as **Spring `ApplicationEvent`s** so users subscribe with `@EventListener` like any other application event.

### 10.1 Event classes

```java
package page.poli.sdk.spring.event;

public final class PoliPageRetryEvent extends ApplicationEvent {
    private final RetryEvent sdkEvent;

    public PoliPageRetryEvent(Object source, RetryEvent sdkEvent) {
        super(source);
        this.sdkEvent = Objects.requireNonNull(sdkEvent);
    }
    public RetryEvent sdkEvent() { return sdkEvent; }
}

public final class PoliPageErrorEvent extends ApplicationEvent {
    private final PoliPageException exception;

    public PoliPageErrorEvent(Object source, PoliPageException exception) {
        super(source);
        this.exception = Objects.requireNonNull(exception);
    }
    public PoliPageException exception() { return exception; }
}
```

Wrappers, not reimplementations. Carry the SDK's own event/exception verbatim so users have full access to `attempt`, `delay`, `statusCode`, `reason`, `requestId`, etc. **`extends ApplicationEvent`** (not `PayloadApplicationEvent`) because we want a concrete, easily-`instanceof`-able type for `@EventListener` to dispatch on.

### 10.2 Bridge

`PoliPageEventBridge` is the small adapter:

```java
public final class PoliPageEventBridge {
    private final ApplicationEventPublisher publisher;

    public PoliPageEventBridge(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void onRetry(RetryEvent event) {
        publisher.publishEvent(new PoliPageRetryEvent(this, event));
    }

    public void onError(Throwable t) {
        if (t instanceof PoliPageException ppe) {
            publisher.publishEvent(new PoliPageErrorEvent(this, ppe));
        }
        // non-PoliPageException causes are dropped — the SDK only invokes onError with terminal failures,
        // but defensive null-cast keeps the bridge crash-free if the SDK ever broadens it.
    }
}
```

The autoconfig binds the bridge's method references into the builder (§7.2).

### 10.3 User listener example (for docs)

```java
@Component
public class PoliPageObserver {

    private static final Logger log = LoggerFactory.getLogger(PoliPageObserver.class);

    @EventListener
    public void onRetry(PoliPageRetryEvent event) {
        log.warn("Poli Page retry attempt={} delay={} reason={}",
            event.sdkEvent().attempt(),
            event.sdkEvent().delay(),
            event.sdkEvent().reason());
    }

    @EventListener
    public void onError(PoliPageErrorEvent event) {
        log.error("Poli Page terminal failure code={} status={} requestId={}",
            event.exception().code(),
            event.exception().statusCode(),
            event.exception().requestId(),
            event.exception());
    }
}
```

### 10.4 User-defined hook (advanced override)

If a consumer declares their own `@Bean PoliPageClient` (covered by `@ConditionalOnMissingBean`), they take full control of the builder. The starter's autoconfig — and therefore the event bridge — is bypassed for that bean. This matches Sentry's "user-declared `SentryOptionsConfiguration` wins" pattern.

If a consumer wants both their callable **and** the Spring event, they wire their own `Consumer<RetryEvent>` that calls `publisher.publishEvent(...)` first, then their own logic.

---

## 11. Actuator `HealthIndicator`

`PoliPageHealthIndicator` reports the starter's reachability of `api.poli.page`. Registered as a bean named `poliPage` so `/actuator/health` shows:

```json
{
  "status": "UP",
  "components": {
    "poliPage": {
      "status": "UP",
      "details": { "baseUrl": "https://api.poli.page", "lastSuccess": "2026-06-01T12:34:56Z" }
    }
  }
}
```

### 11.1 Implementation

```java
public final class PoliPageHealthIndicator implements HealthIndicator {

    private final PoliPageProperties props;
    private final HttpClient httpClient; // shared JDK HttpClient — same one the SDK uses internally

    @Override
    public Health health() {
        URI url = (props.baseUrl() != null ? props.baseUrl() : URI.create("https://api.poli.page"))
            .resolve("/v1/health");
        try {
            HttpResponse<Void> resp = httpClient.send(
                HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(2)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200
                ? Health.up().withDetail("baseUrl", url.getHost()).build()
                : Health.down().withDetail("status", resp.statusCode()).build();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Health.down(e).build();
        }
    }
}
```

The health endpoint hits a lightweight `GET /v1/health` that the Poli Page platform already exposes (returns 200 with no body when the API is up). We do **not** spend an API call on `render()` for health — that would consume the user's quota.

### 11.2 Toggle and grouping

Disabled by `poli-page.health.enabled=false`. Spring Boot's `management.health.poliPage.enabled=false` also disables it (standard convention).

Belongs to the `liveness` group only — if Poli Page is down, the user's app is still alive; the dependency just degrades. Configure with `management.endpoint.health.group.liveness.include=poliPage` in the example app's `application.yml` for demonstration.

---

## 12. Micrometer metrics

`PoliPageMetrics` registers four meters when a `MeterRegistry` bean is on the classpath:

| Meter name | Type | Tags | Source |
|---|---|---|---|
| `poli.page.retries.total` | `Counter` | `attempt`, `reason` | Incremented from `PoliPageRetryEvent` listener inside the metrics bean. |
| `poli.page.retries.delay` | `Timer.builder().publishPercentiles(...)` | `attempt`, `reason` | Records the `event.delay()` from each retry event. |
| `poli.page.errors.total` | `Counter` | `code`, `status_class` (`4xx`/`5xx`/`network`) | Incremented from `PoliPageErrorEvent` listener. |
| `poli.page.client.up` | `Gauge` | (none) | Mirrors the `HealthIndicator` (1 = up, 0 = down). |

Tag cardinality is bounded: `reason` is one of four known SDK values, `code` is one of the ~9 sealed exception subclasses' codes, `status_class` is one of three buckets. Safe for Prometheus.

The bean implements `MeterBinder` rather than constructing the meters in `@PostConstruct`, so it works with any `MeterRegistry` (including the `CompositeMeterRegistry` Spring Boot auto-configures).

Disabled by `poli-page.metrics.enabled=false`.

---

## 13. Build & publishing

### 13.1 Module structure (Gradle composite)

```kotlin
// settings.gradle.kts
rootProject.name = "poli-page-spring-boot"
include(
    ":poli-page-spring-boot-autoconfigure",
    ":poli-page-spring-boot-starter",
    ":poli-page-spring-boot-starter-webflux",
    ":example-app:mvc",
    ":example-app:webflux",
)
```

The root `build.gradle.kts` applies `io.spring.dependency-management` to inherit the Spring Boot BOM, so submodules pull in pinned versions of every Spring-managed artifact without re-declaring versions.

### 13.2 Publishing

Publish three artifacts to **Maven Central** under group `page.poli`:

- `page.poli:poli-page-spring-boot-autoconfigure:0.1.0`
- `page.poli:poli-page-spring-boot-starter:0.1.0`
- `page.poli:poli-page-spring-boot-starter-webflux:0.1.0`

Use the same `maven-publish` + Sonatype OSSRH staging flow `sdk-java` uses today (signing key + GitHub Actions secrets `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD`). Reuse the `publish.yml` workflow from `sdk-java` with module names swapped.

### 13.3 Consumer install

Maven:

```xml
<dependency>
    <groupId>page.poli</groupId>
    <artifactId>poli-page-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
implementation("page.poli:poli-page-spring-boot-starter:0.1.0")
```

(WebFlux consumers swap the artifactId.) Both transitively pull `page.poli:sdk:1.0.0` from Central.

---

## 14. Testing strategy

### 14.1 Layers

**Unit tests** (>90% of the suite, run in seconds, no network):

| Test | What it covers |
|---|---|
| `PoliPagePropertiesTest` | Boot an `ApplicationContextRunner` with valid + invalid property sets. Assert `BindValidationException` for bad `apiKey` (missing `pp_` prefix), out-of-range `timeout`, out-of-range `retries.*`. |
| `PoliPageAutoConfigurationTest` | `ApplicationContextRunner` asserts: `PoliPageClient` bean exists, user-defined `@Bean PoliPageClient` overrides it (`@ConditionalOnMissingBean`), `poli-page.events.enabled=false` removes the bridge bean, `poli-page.health.enabled=false` removes the health indicator bean, `poli-page.metrics.enabled=false` removes the meter binder. |
| `PoliPageResponsesTest` | Each of the four methods returns the correct `ResponseEntity` headers. ASCII and non-ASCII filenames both encode correctly (RFC 5987). `inline=true` flips `Content-Disposition`. |
| `PoliPageReactiveResponsesTest` | Same coverage for reactive variants. Uses `StepVerifier` to drain `Mono`s; verifies cancellation propagates. |
| `PoliPageEventBridgeTest` | Standalone instantiation with a stub `ApplicationEventPublisher`. Calling `onRetry`/`onError` publishes the right wrapper event. |
| `PoliPageHealthIndicatorTest` | Stub the JDK `HttpClient` (or wire a WireMock server). 200 → UP, non-200 → DOWN, IOException → DOWN with cause. |
| `PoliPageMetricsTest` | Wire a `SimpleMeterRegistry`, publish a `PoliPageRetryEvent`/`PoliPageErrorEvent` via Spring's `ApplicationEventMulticaster`, assert counters and timers updated. |

`ApplicationContextRunner` is Spring Boot's recommended fixture for testing autoconfigurations — much faster than `@SpringBootTest` and gives precise control over property values and bean overrides.

**Integration test** (one test, gated on `POLI_PAGE_API_KEY`):

`RenderAgainstDevelopApiIT`:
- Skipped automatically when `POLI_PAGE_API_KEY` env var is unset (so PR contributors without a key get green local runs).
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`, MVC stack, calls `GET /demo/welcome.pdf` on the example app embedded as a test fixture.
- Asserts the response status is 200, `Content-Type` is `application/pdf`, and the first 5 bytes are `%PDF-`.
- That's it. One test, idempotent, ~5 seconds when it runs.

### 14.2 What we explicitly do NOT test

Anything tested by the SDK:
- HTTP transport (JDK `HttpClient` edge cases).
- Retry policy (exponential backoff, max attempts, `Retry-After` parsing, never retrying 4xx).
- 4xx / 5xx → exception mapping.
- Idempotency-key generation.
- Stream handling, byte-range correctness, etc.

The starter wraps — it does not re-test. If a bug in those areas appears, fix it in `sdk-java`. **If you find yourself writing a WireMock server to test retry behaviour, stop — you're doing the SDK's job.**

### 14.3 Tooling

- **JUnit 5** (Jupiter), Spring Boot's default. No `@RunWith`, no `vintage-engine`.
- **AssertJ** for fluent assertions — Spring Boot's preferred assertion style.
- **`spring-boot-starter-test`** transitively pulls JUnit, AssertJ, Mockito, Spring Test, JsonPath. Use those; don't add competing libraries.
- **WireMock** (`wiremock-standalone`) for the health-indicator test only — the only place we ever stub HTTP. Strictly contained.
- **Spotless** with `google-java-format` for formatting. Pin in `build.gradle.kts`.
- **Error Prone** + **NullAway** for null-safety checks at compile time. Same configuration as `sdk-java`.

---

## 15. CI matrix

`.github/workflows/ci.yml`:

```yaml
name: CI
on:
  push:
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        java: ['17', '21']
        spring-boot: ['3.2.+', '3.3.+', '3.4.+']
    steps:
      - uses: actions/checkout@v4
        with:
          path: spring-boot
      - uses: actions/checkout@v4
        with:
          repository: poli-page/sdk-java
          path: sdk-java
          ref: main
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ matrix.java }}
      - name: Pin Spring Boot version
        working-directory: spring-boot
        run: echo "springBootVersion=${{ matrix.spring-boot }}" >> gradle.properties
      - name: Build
        working-directory: spring-boot
        run: ./gradlew --no-daemon build
      - name: Spotless check
        working-directory: spring-boot
        run: ./gradlew --no-daemon spotlessCheck

  integration:
    runs-on: ubuntu-latest
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - name: Integration test against develop API
        env:
          POLI_PAGE_API_KEY: ${{ secrets.POLI_PAGE_DEVELOP_API_KEY }}
        run: ./gradlew --no-daemon integrationTest
```

Six matrix cells (Java 17/21 × Boot 3.2/3.3/3.4). Each step short-circuits if `build.gradle.kts` is missing — keeps a freshly-scaffolded repo green from day one.

After v0.1.0 publishes (§13), the sibling `sdk-java` is resolved from Maven Central; the `Checkout SDK alongside starter` step is removed.

---

## 16. Versioning & release

- **SemVer**. v0.x while the API stabilises; v1.0.0 when we ship to Maven Central with explicit API stability promise.
- **`CHANGELOG.md`** in [Keep a Changelog](https://keepachangelog.com/) format. Updated in the same commit as every version bump.
- **Conventional Commits** for every commit (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`).
- **Release process**:
  1. Bump `version` in `gradle.properties`.
  2. Update `CHANGELOG.md`.
  3. `git tag v0.x.y && git push --tags`.
  4. GitHub Actions `publish.yml` builds + signs + uploads to Sonatype OSSRH.
  5. Promote staging repo to Maven Central (or use the `nexus-publish-plugin` to auto-promote).
- **v0.1.0 launch sequence**:
  1. CI green on all 6 matrix cells.
  2. Tag v0.1.0.
  3. Wait for Central indexing (~30 min – 4 h).
  4. Smoke-test with `curl https://repo1.maven.org/maven2/page/poli/poli-page-spring-boot-starter/0.1.0/`.
  5. Update `INTEGRATIONS_PLAN.md` to add the Spring Boot row.

---

## 17. Deferred to v0.2+ (do not build in v0.1.0)

Calling these out explicitly so they don't sneak in mid-implementation. Each has a real use case but adds maintenance surface beyond v0.1.0's scope.

| Feature | Why deferred |
|---|---|
| **`WebClient`-based transport replacement** | SDK uses JDK `HttpClient`; replacing it requires a Transport-injection seam in the SDK first. Land that in `sdk-java` v1.1 before exposing here. |
| **Spring Cache integration** (`documents.get(id)` cache) | Premature optimisation — no evidence users need it. Easy add later. |
| **Spring Native AOT hints** (`@ImportRuntimeHints`) | Best-effort note in README; full AOT support requires reflective-config audits across the SDK's Jackson use. Defer to v0.2 once `sdk-java` ships reflect-config metadata. |
| **Sleuth / Micrometer Tracing spans** for SDK calls | Real value but only if the user is already using tracing — gate on `@ConditionalOnClass(Tracer.class)` in a separate `-tracing` artifact later. |
| **Kotlin DSL** for client configuration | Niche; idiomatic Kotlin users can hand-write a `@Bean` already. |
| **Quarkus / Micronaut parity packages** | Different DI containers, different module conventions. Separate repos when there's user demand. |
| **`@ConditionalOnPoliPageEnabled` master switch** | YAGNI — Spring Boot's standard `spring.autoconfigure.exclude=...` handles disabling. |
| **Named/multi-client config** (`clients.live`, `clients.test`) | Decided already (sibling rationale: symfony-bundle §17). v0.2 add-on; v0.1 single-client config is purely additive. |
| **`/actuator/poliPage` custom endpoint** with config dump | Health indicator covers the operational need; full endpoint adds surface to maintain. |

**Discipline rule**: when implementing, if a "small addition" feels tempting, check this list first. If it's here, defer. If it's not here, ask before adding.

---

## 18. Decision log

Capturing the "why we chose X" so future-agents don't relitigate:

| Decision | Choice | Why |
|---|---|---|
| Auto-configuration style | `@AutoConfiguration` + `AutoConfiguration.imports` | Post-2.7 standard; explicit ordering; surfaces in `/actuator/conditions`. Legacy `spring.factories` is deprecated for autoconfig. |
| Spring Boot version range | `3.2.x \|\| 3.3.x \|\| 3.4.x` | Boot 2.7 OSS support ended Nov 2024. 3.2 is the floor (Jackson 2.16). 3.4 is the LTS-track current. |
| MVC vs WebFlux | Separate starter artifacts, shared autoconfig module | Springdoc and Sentry both do this. Single application picks one stack; bundling both into one starter would force a dependency on the wrong web stack. |
| Properties shape | Java record with nested records | Spring Boot 3 has first-class record support for `@ConfigurationProperties`. Concise, immutable, IDE-friendly. |
| Validation | Jakarta Bean Validation via `@Validated` + `@NotBlank`/`@Pattern`/`@DurationMin`/`@DurationMax` | Spring Boot's recommended path; surfaces failures at startup, not at first call. |
| Event publishing | `ApplicationEventPublisher` + `extends ApplicationEvent` | Idiomatic Spring; `@EventListener` consumer surface is well-understood. `PayloadApplicationEvent` is for generic carriers — concrete classes give better type-safety. |
| Bean override | `@ConditionalOnMissingBean` on every starter-provided bean | Standard Spring Boot starter contract; a consumer who defines their own `@Bean PoliPageClient` takes full control. |
| Properties prefix | `poli-page` (kebab-case) | Spring Boot's idiomatic relaxed binding — works with `POLI_PAGE_API_KEY` env vars and `poliPage.apiKey` Java property names alike. |
| Actuator integration | `HealthIndicator` + four Micrometer meters | Industry expectation. Health hits a dedicated `/v1/health` endpoint (no API-quota consumption). Metrics are gauges + counters with bounded tag cardinality. |
| WebFlux bridge | `Mono.fromFuture(CompletableFuture)` | The SDK's `renderAsync()` already returns `CompletableFuture`. Adapting at the helper layer (not inside the SDK) keeps `sdk-java` reactive-agnostic. |
| Health hit | `GET /v1/health` (lightweight, unauthenticated) | Never spend a render call on health. Matches Stripe / Twilio convention. |
| No CLI command | None | Spring Boot has no `bin/console`-style command convention. The `RenderToFileRunner` `ApplicationRunner` in the example app is the equivalent — runs once on app start. |
| Build tool | Gradle (primary) + published `.pom` | Maintainer's daily driver. Gradle generates a sensible `.pom` for Maven consumers via `maven-publish`. |
| Java version | `17+` | Inherits from `sdk-java`. Spring Boot 3 requires the same. |

---

## 19. Implementation order (for the agent picking this up)

A suggested commit-by-commit sequence — each commit ships green CI, each step is independently reviewable. Strict TDD per the inherited convention (RED → GREEN → refactor).

1. **`chore: bootstrap Gradle multi-module + CI workflow`** — settings.gradle.kts, root build.gradle.kts, three module skeletons, CI stub, spotless config. CI green (auto-skip on missing tests).
2. **`feat: PoliPageProperties record with validation`** — §6. Standalone test asserts good/bad bindings.
3. **`feat: PoliPageAutoConfiguration with PoliPageClient @Bean`** — §7. `ApplicationContextRunner` asserts the bean resolves with all property combinations.
4. **`feat: PoliPageEventBridge + PoliPageRetryEvent / PoliPageErrorEvent`** — §10. Bridge unit test + autoconfig assertion that the bridge is wired into the client.
5. **`feat: PoliPageResponses (MVC) with bytes/stream/preview/documentRedirect`** — §8. Unit tests for each method's headers.
6. **`feat: PoliPageWebMvcAutoConfiguration registering PoliPageResponses`** — §7.3. Web-stack conditional test.
7. **`feat: PoliPageReactiveResponses + PoliPageWebFluxAutoConfiguration`** — §9. Mirror of step 5 + 6 for reactive.
8. **`feat: PoliPageHealthIndicator + actuator wiring`** — §11. Test with WireMock-stubbed `/v1/health`.
9. **`feat: PoliPageMetrics MeterBinder`** — §12. SimpleMeterRegistry-backed test.
10. **`docs: spring-configuration-metadata.json for IDE auto-completion`** — §6.3.
11. **`test: integration test against develop API (gated)`** — §14.1.
12. **`feat: example-app MVC demonstrating all 10 SDK steps`** — §14. README walkthrough.
13. **`feat: example-app WebFlux mirroring the MVC app`** — same coverage in reactive style.
14. **`docs: README, CHANGELOG initial entry`** — install snippet, 5-line quick start, link to docs.poli.page.
15. **`chore: publish.yml + Sonatype OSSRH credentials wiring`** — §13.2.

Estimated effort: **4-6 working days** for a single agent with this spec in hand.

---

## 20. Open questions (none blocking v0.1.0)

- Should the starter's `User-Agent` augmentation include the starter's own version (`poli-page-spring-boot-starter/0.1.0`) alongside the SDK's default? Probably yes; defer to first PR review.
- Does Spring Boot's `spring-boot-configuration-processor` generate enough metadata that the hand-tuned overlay (§6.3) becomes redundant? Try generation-only first; add overlay only for descriptions that need polish.
- For the example WebFlux app, should the demo HTML dashboard be the same as MVC (rendered as a string) or use Thymeleaf-reactive? Use the same string approach — keeps the demo dependency-free.

These are noted, not blocking. Implementor can decide at first encounter.
