# Poli Page for Spring Boot

> Render Poli Page documents as Spring Boot controller responses.

## About

This starter wires the Poli Page Java SDK into Spring Boot's auto-configuration system. You get a `PoliPageClient` bean autowired from `application.yml`, `ResponseEntity` helpers for PDF and stored-document HTML preview routes, an `ApplicationEventPublisher` bridge for the SDK's `onRetry` / `onError` hooks, an Actuator `HealthIndicator`, and Micrometer counters/timers — all conditional, all overridable. A sibling WebFlux artifact does the same for reactive controllers returning `Mono` / `Flux<DataBuffer>`.

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
- The Poli Page Java SDK [`page.poli:sdk`](https://github.com/poli-page/sdk-java) 0.11.0 or later, from Maven Central (the starter brings it in)
- A Poli Page API key — get one at [poli.page/dashboard/keys](https://poli.page/dashboard/keys)

> **Pre-release note**: as of `0.1.0-SNAPSHOT`, this starter is not on Maven Central yet; the coordinates below are what consumers will use once it is published. Its SDK dependency, `page.poli:sdk:0.11.0`, is on Maven Central.

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

Both starters depend on `page.poli:sdk:0.11.0`, which comes with them. To use a later SDK release before the starter moves to it, declare `page.poli:sdk` with that version in your own build.

Set the API key in your environment:

```bash
# .env
POLI_PAGE_API_KEY=pp_sandbox_your_key_here
```

### API keys

An API key belongs to exactly one environment of your organization, and its text names that environment: a key is `pp_{env}_{32 lowercase hex}` (`pp_sandbox_…`, `pp_live_…`, `pp_staging_…`). The API selects the environment from the key, so there is nothing else to configure.

| Environment                           | Key            | Watermark | Counted |
| ------------------------------------- | -------------- | --------- | ------- |
| `sandbox` (every org)                 | `pp_sandbox_…` | yes       | no      |
| `live` (every org, deletable on Pro+) | `pp_live_…`    | no        | yes     |
| created on Pro+ (e.g. `staging`)      | `pp_staging_…` | no        | yes     |

"Counted" means the render counts toward the organization's monthly PDF quota.

The API is the only authority on keys. The starter only requires `poli-page.api-key` to be present and not blank. When the key does not match `^pp_(?=[a-z][a-z0-9-]{0,19}_)[a-z][a-z0-9]*(?:-[a-z0-9]+)*_[0-9a-f]{32}$`, it logs one warning at startup that shows no part of the key, not even the `pp_{env}_` prefix, and starts anyway; the API then answers `401 INVALID_API_KEY`, or `401 MISSING_API_KEY` for a value without `pp_`.

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
import page.poli.sdk.input.RenderInput;
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
        byte[] pdf = client.render().pdf(RenderInput.builder()
            .project("invoices")
            .template("default")
            .data(Map.of("invoice_id", id))
            .build());
        return responses.bytes(pdf, "invoice-" + id + ".pdf");
    }
}
```

The key selects the environment whose published copy of the project is rendered; the input carries no version. Every Poli Page org comes pre-provisioned with the `getting-started` project's `welcome` template in `sandbox`, so swap `invoices`/`default` for `getting-started`/`welcome` to try it with a `pp_sandbox_…` key.

## Configuration

You bind options under the `poli-page.*` prefix. The starter validates them at startup; bad input fails the boot with a `BindValidationException`.

| Option | Default | Description |
|---|---|---|
| `api-key` | _required_ | API key, `pp_{env}_{32 hex}` (`pp_sandbox_…`, `pp_live_…`). Must be present and not blank; any other value starts the app with one warning (see [API keys](#api-keys)). |
| `base-url` | SDK default | Override the API origin (must be absolute). |
| `request-timeout` | SDK default (60s) | Per-attempt request timeout (`Duration`, 1s ≤ t ≤ 10min). |
| `retries.max-attempts` | SDK default (2) | Retry budget (integer, 0–10). |
| `retries.delay` | SDK default (500ms) | Base delay for exponential backoff (`Duration`, 0 ≤ t ≤ 30s). |
| `health.enabled` | `true` | Register the Actuator `HealthIndicator` probing `/v1/health`. |
| `metrics.enabled` | `true` | Register the Micrometer `MeterBinder` (retry/error counters and timer). |
| `events.enabled` | `true` | Publish `PoliPageRetryEvent` / `PoliPageErrorEvent` via `ApplicationEventPublisher`. |
| `error-handler.enabled` | `true` | Register the default error handler, which answers an SDK error with its `toPayload()` body (see [Errors](#errors)). |

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
| `page.poli.sdk.spring.PoliPageExceptionHandler` | Answers a `PoliPageException` with its `toPayload()` body; the default error handler of each stack (`web.PoliPageWebMvcExceptionHandler`, `reactive.PoliPageWebFluxExceptionHandler`) is a `@RestControllerAdvice` subclass. |
| `page.poli.sdk.spring.web.PoliPageResponses` | (MVC) `bytes` / `stream` / `preview` (stored-document HTML) / `documentRedirect` `ResponseEntity` builders. |
| `page.poli.sdk.spring.reactive.PoliPageReactiveResponses` | (WebFlux) same shape, returning `Mono` / `Flux<DataBuffer>`. |
| `page.poli.sdk.spring.event.PoliPageRetryEvent` | `ApplicationEvent` published before each SDK retry sleep. |
| `page.poli.sdk.spring.event.PoliPageErrorEvent` | `ApplicationEvent` published when the SDK gives up after exhausting retries. |
| `page.poli.sdk.spring.actuator.PoliPageHealthIndicator` | `/actuator/health` component named `poliPage`, probing `GET /v1/health`. |
| `page.poli.sdk.spring.metrics.PoliPageMetrics` | `MeterBinder` exposing `poli.page.retries.total`, `.delay`, `.errors.total`. |

Full reference: [docs/auto-configuration.md](docs/auto-configuration.md).

## Errors

The SDK throws `page.poli.sdk.exception.PoliPageException` (or a subclass from the sealed hierarchy).

### Default error handler

Both starters register a default error handler, a `@RestControllerAdvice` (`PoliPageWebMvcExceptionHandler` in MVC, `PoliPageWebFluxExceptionHandler` in WebFlux, both subclasses of `page.poli.sdk.spring.PoliPageExceptionHandler`) that answers any `PoliPageException` an annotated controller throws (or a `Mono` / `CompletableFuture` fails with) as JSON. The body is the SDK's `toPayload()`, exactly:

```json
{ "code": "TEMPLATE_NOT_FOUND", "message": "Template 'does-not-exist' not found in project 'getting-started'.", "status": 404, "requestId": "req_…" }
```

- The HTTP status is the payload's `status`: the API's status, `503` for `network_error`, `504` for `timeout`. An error with no status (for example `aborted`) answers `500`, with `"status": null` in the body.
- `requestId` is the API's `x-request-id`, or `null` when unknown. The `null` fields stay in the body even when you set `spring.jackson.default-property-inclusion=non_null`.
- `Content-Type: application/json`. In MVC, PDF headers already set on the response (`Content-Disposition` and the other content headers, for example from `PoliPageResponses.stream` when the first read of the PDF fails) are removed first, so the error is not saved as a PDF. When part of the PDF was already sent, nothing is written and the error is rethrown: Tomcat and Jetty then close the connection, so the client sees a truncated download; Undertow ends the response normally, so the PDF is cut short with no error signal. JSON is never appended to the PDF.
- Each answered error is logged on `page.poli.sdk.spring.PoliPageExceptionHandler`: `ERROR` for a 5xx or no status, `WARN` otherwise, with the code, status, request id and message (never the API key). In MVC, an error after part of the PDF was sent gets one `WARN` line from `PoliPageWebMvcExceptionHandler`; the servlet container logs the rethrown error itself.
- Like any `@ControllerAdvice`, it covers annotated controllers only: errors from servlet filters, `WebFilter`s or functional `RouterFunction` endpoints do not reach it.
- The `null` fields are kept by a Jackson module (`PoliPageErrorPayloadModule`) that Spring Boot adds to the `ObjectMapper` it builds. If you declare your own `ObjectMapper` bean, build it from Boot's `Jackson2ObjectMapperBuilder` or register the module on it.

It is on by default. Turn it off with:

```yaml
poli-page:
  error-handler:
    enabled: false
```

It has the lowest precedence. Spring answers an exception with the first `@ControllerAdvice`, in order, that has any matching `@ExceptionHandler`, not with the most specific one across advices. So any advice of yours that handles `PoliPageException` or a supertype (`Exception`, `RuntimeException`), ordered or not, normally answers SDK errors instead of the default handler. If you have such a catch-all and want to keep the SDK answer, handle `PoliPageException` in an advice ordered before it and delegate to the starter's handler. The injected `PoliPageExceptionHandler` is the stack's handler, so this works in MVC and WebFlux alike; in MVC it also removes PDF headers already on the response and never appends JSON to a response already sent:

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PoliPageErrors {
    private final PoliPageExceptionHandler poliPage;

    PoliPageErrors(PoliPageExceptionHandler poliPage) {
        this.poliPage = poliPage;
    }

    @ExceptionHandler(PoliPageException.class)
    ResponseEntity<ErrorPayload> handle(PoliPageException e) {
        return poliPage.handlePoliPageException(e);
    }
}
```

Building your own answer from `e.toPayload()` instead skips that MVC clean-up.

Declaring your own `PoliPageExceptionHandler` bean replaces the default one. `PoliPageExceptionHandler` is abstract and not an advice itself, so the replacement must be a `@ControllerAdvice`, for example a subclass of `PoliPageWebMvcExceptionHandler` or `PoliPageWebFluxExceptionHandler`; a bean of this type that is not an advice leaves SDK errors unhandled.

### Handling errors yourself

You can also catch the SDK's exceptions in your controller. The categories you typically discriminate on:

- **Auth** — `PoliPageAuthException` (HTTP 401 / 403). Invalid or missing API key (`INVALID_API_KEY`, `MISSING_API_KEY`), a protected environment (`ENVIRONMENT_PROTECTED`), or an organisation in payment grace (`ORGANIZATION_PAYMENT_GRACE`).
- **Rate limit** — `PoliPageRateLimitException` (HTTP 429). You hit the rate limit; back off per `retryAfter()`.
- **Request rejected** — `PoliPageValidationException` (400 / 422), `PoliPageNotFoundException` (404, e.g. `TEMPLATE_NOT_FOUND`, `PROJECT_NOT_IN_ENVIRONMENT`), `PoliPageGoneException` (410 `DOCUMENT_GONE`). Template, data, or document lifecycle errors.
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
export POLI_PAGE_API_KEY=pp_sandbox_…
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

See [CONTRIBUTING.md](CONTRIBUTING.md). CI also runs the [API contract](https://github.com/poli-page/api-contract) tests against both example apps; to run them locally: `POLI_CONTRACT=<path to contract.json> ./gradlew --no-daemon :example-app:mvc:test :example-app:webflux:test`.

## License

Released under the [MIT License](LICENSE).
