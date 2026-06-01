# Poli Page Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/page.poli/poli-page-spring-boot-starter.svg)](https://central.sonatype.com/artifact/page.poli/poli-page-spring-boot-starter)
[![CI](https://github.com/poli-page/spring-boot/actions/workflows/ci.yml/badge.svg)](https://github.com/poli-page/spring-boot/actions/workflows/ci.yml)
[![Javadoc](https://javadoc.io/badge2/page.poli/poli-page-spring-boot-starter/javadoc.svg)](https://javadoc.io/doc/page.poli/poli-page-spring-boot-starter)
[![License](https://img.shields.io/github/license/poli-page/spring-boot)](LICENSE)

Official Spring Boot 3 starter for [Poli Page](https://poli.page) — auto-configures the [Java SDK](https://github.com/poli-page/sdk-java) into your Spring application context, ships `ResponseEntity` helpers for PDFs, an `ApplicationEventPublisher` bridge for retry/error hooks, an Actuator `HealthIndicator`, and Micrometer metrics. WebFlux variant included.

→ **Documentation**: deep-dive guides under [`docs/`](docs/).
→ Underlying SDK: [`page.poli:sdk`](https://github.com/poli-page/sdk-java).

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

Requires Java 17+ and Spring Boot 3.2 / 3.3 / 3.4.

## Quick start

`application.yml`:

```yaml
poli-page:
  api-key: ${POLI_PAGE_API_KEY}
```

`InvoiceController.java`:

```java
@RestController
public class InvoiceController {

    private final PoliPageClient client;
    private final PoliPageResponses responses;

    public InvoiceController(PoliPageClient client, PoliPageResponses responses) {
        this.client = client;
        this.responses = responses;
    }

    @GetMapping("/{id}.pdf")
    public ResponseEntity<byte[]> invoice(@PathVariable String id) {
        byte[] pdf = client.render().pdf(ProjectModeInput.builder()
            .project("billing")
            .template("invoice")
            .version("1.0.0")
            .data(Map.of("invoice_id", id))
            .build());
        return responses.bytes(pdf, "invoice-" + id + ".pdf");
    }
}
```

Run with `POLI_PAGE_API_KEY=pp_test_...` in the environment and `GET /123.pdf` returns a real PDF with the right headers.

Every Poli Page org comes pre-provisioned with a `getting-started/welcome` template, so the snippet above works as-is the moment you have an API key.

## What you get

| Bean | Type | When |
|---|---|---|
| `poliPageClient` | `PoliPageClient` | Always (overridable with your own `@Bean`). |
| `poliPageResponses` | `PoliPageResponses` | MVC starter only. |
| `poliPageReactiveResponses` | `PoliPageReactiveResponses` | WebFlux starter only. |
| `poliPageEventBridge` | `PoliPageEventBridge` | `poli-page.events.enabled` (default `true`). |
| `poliPageHealthIndicator` | `HealthIndicator` | Actuator on classpath + `poli-page.health.enabled` (default `true`). |
| `poliPageMetrics` | `MeterBinder` | Micrometer on classpath + `poli-page.metrics.enabled` (default `true`). |

## Configuration

| Property | Type | Default | Notes |
|---|---|---|---|
| `poli-page.api-key` | `String` | — (required) | Must match `pp_test_…` or `pp_live_…`. |
| `poli-page.base-url` | `URI` | SDK default | Override for staging or self-hosted deployments. |
| `poli-page.request-timeout` | `Duration` | SDK default (60 s) | Per-attempt request timeout. |
| `poli-page.retries.max-attempts` | `int` | SDK default (2) | `0` disables retries. |
| `poli-page.retries.delay` | `Duration` | SDK default (500 ms) | Base delay for exponential backoff. |
| `poli-page.health.enabled` | `boolean` | `true` | Toggle the HealthIndicator. |
| `poli-page.metrics.enabled` | `boolean` | `true` | Toggle Micrometer metrics. |
| `poli-page.events.enabled` | `boolean` | `true` | Toggle the ApplicationEvent bridge. |

See [`docs/configuration-properties.md`](docs/configuration-properties.md) for the full reference, validation rules, and IDE auto-completion notes.

## Documentation

| Topic | What it covers |
|---|---|
| [Auto-configuration](docs/auto-configuration.md) | How beans are registered, overridden, and disabled. |
| [Configuration properties](docs/configuration-properties.md) | Every property the starter reads, its validation rules, and how relaxed binding works. |
| [Responses](docs/responses.md) | `ResponseEntity` helpers for PDFs and HTML previews (MVC). |
| [Streaming](docs/streaming.md) | `StreamingResponseBody` for large PDFs without buffering. |
| [Events](docs/events.md) | `@EventListener` integration with the SDK's retry / error hooks. |
| [Actuator](docs/actuator.md) | `HealthIndicator` plus four Micrometer meters with bounded tag cardinality. |
| [WebFlux](docs/webflux.md) | The reactive sibling: `Mono<ResponseEntity<byte[]>>` and `Flux<DataBuffer>`. |
| [Testing](docs/testing.md) | Stubbing `PoliPageClient` with `@MockitoBean`, WireMock-backed integration tests, the gated develop-API smoke test. |
| [Specification](docs/spec/spring-boot-starter-specification.md) | The full design spec — authoritative source for all decisions. |
| [Implementation plan](docs/plan/2026-06-01-implementation.md) | Task-by-task plan that produces v0.1.0 from this spec. |

## Example app

A runnable Spring Boot app demonstrating every SDK method lives at [`example-app/mvc/`](example-app/mvc/) (MVC) and [`example-app/webflux/`](example-app/webflux/) (reactive). Each ships an interactive HTML dashboard at `GET /` covering the 10 demo steps from the SDK's own `examples/`.

```bash
export POLI_PAGE_API_KEY=pp_test_...
./gradlew :example-app:mvc:bootRun
open http://localhost:8080/
```

## Versioning

This project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). v0.x while the API stabilises; v1.0.0 when we ship the explicit API stability promise.

## License

[MIT](LICENSE)
