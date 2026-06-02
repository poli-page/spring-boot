# Poli Page for Spring Boot

> Render Poli Page documents as Spring Boot controller responses.

## About

This starter wires the Poli Page Java SDK into Spring Boot's auto-configuration system. You get a `PoliPageClient` bean autowired from `application.yml`, `ResponseEntity` helpers for PDF and HTML preview routes, an `ApplicationEventPublisher` bridge for the SDK's `onRetry` / `onError` hooks, an Actuator `HealthIndicator`, and Micrometer counters/timers — all conditional, all overridable. A sibling WebFlux artifact does the same for reactive controllers returning `Mono` / `Flux<DataBuffer>`.

**When to use this:**

- You want `PoliPageClient` available in any `@Component` / `@RestController` via constructor injection, with no manual `@Bean` boilerplate.
- You want PDF responses with the correct `Content-Type`, `Cache-Control`, and RFC 5987-encoded `Content-Disposition` headers without writing them by hand.
- You want the SDK's retry/error hooks delivered through Spring's standard `@EventListener` mechanism, observable via Micrometer.
- You want a reactive variant (`PoliPageReactiveResponses`) that bridges the SDK's `CompletableFuture`-returning async facade into Reactor types.

**When not to:**

- You're not on Spring Boot — install [`page.poli:sdk`](https://github.com/poli-page/sdk-java) directly.
- You're still on Spring Boot 2.x — only Spring Boot 3.x (Jakarta namespace) is supported.

## Requirements

- Java 17+
- Spring Boot 3.2 / 3.3 / 3.4 (Jakarta namespace only)
- A Poli Page API key — get one at [poli.page/dashboard/keys](https://poli.page/dashboard/keys)

> **Pre-release note**: as of `0.1.0-SNAPSHOT`, neither this starter nor the underlying [`page.poli:sdk`](https://github.com/poli-page/sdk-java) is on Maven Central yet. Until the first stable release both need to be installed locally: `cd ../sdk-java && ./mvnw install -DskipTests`. The coordinates below are what consumers will use once both publish to Central.

## Install

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

For reactive WebFlux apps, swap the artifact id:

```kotlin
implementation("page.poli:poli-page-spring-boot-starter-webflux:0.1.0")
```

Set the API key in your environment:

```bash
# .env
POLI_PAGE_API_KEY=pp_test_your_key_here
```

Verify everything is wired by booting the example app (see [Example app](#example-app)).

## Quick start

`application.yml`:

```yaml
poli-page:
  api-key: ${POLI_PAGE_API_KEY}
```

`InvoiceController.java`:

```java
package com.example.billing;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.ProjectModeInput;
import page.poli.sdk.spring.web.PoliPageResponses;

@RestController
public class InvoiceController {

    private final PoliPageClient client;
    private final PoliPageResponses responses;

    public InvoiceController(PoliPageClient client, PoliPageResponses responses) {
        this.client = client;
        this.responses = responses;
    }

    @GetMapping("/invoice/{id}.pdf")
    public ResponseEntity<byte[]> show(@PathVariable String id) {
        byte[] pdf = client.render().pdf(ProjectModeInput.builder()
            .project("invoices")
            .template("default")
            .version("1.0.0")
            .data(Map.of("invoice_id", id))
            .build());
        return responses.bytes(pdf, "invoice-" + id + ".pdf");
    }
}
```

Every Poli Page org comes pre-provisioned with a `getting-started/welcome` template, so the snippet above runs as-is the moment you have an API key.

## Configuration

You bind options under the `poli-page.*` prefix. The starter validates them at startup; bad input fails the boot with a `BindValidationException`.

| Option | Default | Description |
|---|---|---|
| `api-key` | _required_ | API key starting with `pp_test_` or `pp_live_`. |
| `base-url` | SDK default | Override the API origin (must be absolute). |
| `request-timeout` | SDK default (60s) | Per-attempt request timeout (`Duration`, 1s ≤ t ≤ 10min). |
| `retries.max-attempts` | SDK default (2) | Retry budget (integer, 0–10). |
| `retries.delay` | SDK default (500ms) | Base delay for exponential backoff (`Duration`, 0 ≤ t ≤ 30s). |
| `health.enabled` | `true` | Register the Actuator `HealthIndicator` probing `/v1/health`. |
| `metrics.enabled` | `true` | Register the Micrometer `MeterBinder` (retry/error counters and timer). |
| `events.enabled` | `true` | Publish `PoliPageRetryEvent` / `PoliPageErrorEvent` via `ApplicationEventPublisher`. |

```yaml
# application.yml
poli-page:
  api-key: ${POLI_PAGE_API_KEY}
  request-timeout: 30s
  retries:
    max-attempts: 4
```

You inspect the resolved bean graph with `/actuator/conditions` (when Actuator is on the classpath).

## API at a glance

| Symbol | Purpose |
|---|---|
| `page.poli.sdk.PoliPageClient` | Autowired SDK client (`render()`, `documents()`, `renderAsync()`, `documentsAsync()`). |
| `page.poli.sdk.spring.PoliPageProperties` | `@ConfigurationProperties("poli-page")` record bound from `application.yml`. |
| `page.poli.sdk.spring.web.PoliPageResponses` | (MVC) `bytes` / `stream` / `preview` / `documentRedirect` `ResponseEntity` builders. |
| `page.poli.sdk.spring.reactive.PoliPageReactiveResponses` | (WebFlux) same shape, returning `Mono` / `Flux<DataBuffer>`. |
| `page.poli.sdk.spring.event.PoliPageRetryEvent` | `ApplicationEvent` published before each SDK retry sleep. |
| `page.poli.sdk.spring.event.PoliPageErrorEvent` | `ApplicationEvent` published when the SDK gives up after exhausting retries. |
| `page.poli.sdk.spring.actuator.PoliPageHealthIndicator` | `/actuator/health` component named `poliPage`, probing `GET /v1/health`. |
| `page.poli.sdk.spring.metrics.PoliPageMetrics` | `MeterBinder` exposing `poli.page.retries.total`, `.delay`, `.errors.total`. |

Full reference: [docs/auto-configuration.md](docs/auto-configuration.md).

## Errors

The SDK throws `page.poli.sdk.exception.PoliPageException` (or a subclass from the sealed hierarchy). The starter does not catch or transform them — you handle them in your controller, an `@ExceptionHandler`, or a `@ControllerAdvice`. The categories you typically discriminate on:

- **Auth** — `PoliPageAuthException` (HTTP 401 / 403). Invalid or missing API key.
- **Rate limit** — `PoliPageRateLimitException` (HTTP 429). You hit the rate limit; back off per `retryAfter()`.
- **Request rejected** — `PoliPageValidationException`, `PoliPageNotFoundException`, `PoliPageGoneException`, `PoliPagePaymentRequiredException`. Template, data, version, or organisation lifecycle errors.
- **Network / transport** — `PoliPageNetworkException` (no HTTP status). Connection failure, DNS, TLS, or timeout.

Pattern-match exhaustively against the sealed hierarchy:

```java
try {
    byte[] pdf = client.render().pdf(input);
} catch (PoliPageAuthException e) {
    // re-check POLI_PAGE_API_KEY
    throw e;
} catch (PoliPageRateLimitException e) {
    // honour e.retryAfter()
    throw e;
} catch (PoliPageValidationException e) {
    // surface e.code() to the user
    throw e;
} catch (PoliPageNetworkException e) {
    // network/timeout — safe to retry the whole request
    throw e;
} catch (PoliPageException e) {
    // anything else from the SDK (5xx)
    throw e;
}
```

`PoliPageErrorEvent` fires for terminal failures in addition to the throw, so you can wire global alerting through a single `@EventListener` without controller-level boilerplate. See [docs/events.md](docs/events.md).

## Example app

Two runnable Spring Boot apps live under [`example-app/`](example-app/) — one MVC, one WebFlux — covering every SDK method. Each ships an interactive dashboard at `/` with one button per feature, plus the underlying JSON / PDF routes for scripted use.

```bash
export POLI_PAGE_API_KEY=pp_test_…
./gradlew :example-app:mvc:bootRun        # http://localhost:8080
./gradlew :example-app:webflux:bootRun    # http://localhost:8081
```

See [example-app/README.md](example-app/README.md) for the full route map.

## Going further

- [docs/auto-configuration.md](docs/auto-configuration.md) — How beans are registered, overridden, and disabled.
- [docs/configuration-properties.md](docs/configuration-properties.md) — Every `poli-page.*` key, its validation, and relaxed-binding rules.
- [docs/responses.md](docs/responses.md) — The four `ResponseEntity` builders, their headers, and RFC 5987 filename encoding.
- [docs/streaming.md](docs/streaming.md) — `StreamingResponseBody` for multi-MB PDFs without buffering.
- [docs/webflux.md](docs/webflux.md) — Reactive variant: `Mono<ResponseEntity<byte[]>>` and `Flux<DataBuffer>`.
- [docs/events.md](docs/events.md) — Subscribe to `PoliPageRetryEvent` / `PoliPageErrorEvent` with `@EventListener`.
- [docs/actuator.md](docs/actuator.md) — The `HealthIndicator` and three Micrometer meters.
- [docs/testing.md](docs/testing.md) — Stubbing `PoliPageClient` with `@MockitoBean`, WireMock-backed integration tests.

## Compatibility

| Starter | Spring Boot | Java |
|---|---|---|
| `0.1.x` | `3.2.x` / `3.3.x` / `3.4.x` | `17` / `21` |

Spring Boot 2.x (`javax.*` namespace) is not supported. Maintenance follows the Spring Boot upstream support window — new majors land within one minor of upstream release.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Released under the [MIT License](LICENSE).
