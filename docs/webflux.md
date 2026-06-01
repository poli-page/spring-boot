# WebFlux: reactive responses

> Use `poli-page-spring-boot-starter-webflux` to return `Mono<ResponseEntity<byte[]>>` and `Flux<DataBuffer>` from a reactive controller, bridging the SDK's `renderAsync()` `CompletableFuture` results into Reactor types.

## Why

Spring WebFlux is the reactive web stack in the Spring Boot ecosystem — non-blocking I/O, end-to-end backpressure, ideal for high-fan-out services that talk to many slow upstreams. The SDK ships a `renderAsync()` / `documentsAsync()` facade that returns `CompletableFuture<T>` for every call. The WebFlux starter ships `PoliPageReactiveResponses`, a sibling of the MVC `PoliPageResponses`, that turns those futures into `Mono`/`Flux` types so your controller code is composable with the rest of your reactive pipeline.

A typical reactive use case: a controller that aggregates rendering with two other downstream calls and emits the combined PDF only when all three finish. With the WebFlux starter, that's `Mono.zip(...)` rather than blocking `Future.get()`.

## How

### Pick the WebFlux starter

```kotlin
// build.gradle.kts
dependencies {
    implementation("page.poli:poli-page-spring-boot-starter-webflux:0.1.0")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}
```

The autoconfig activates only in a reactive stack (`@ConditionalOnWebApplication(type = REACTIVE)`), so a hybrid app that pulls in both `spring-boot-starter-web` and `spring-boot-starter-webflux` ends up with the MVC variant active. To force reactive, omit `spring-boot-starter-web`.

### Returning bytes from a reactive controller

```java
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final PoliPageClient client;
    private final PoliPageReactiveResponses responses;

    public InvoiceController(PoliPageClient client, PoliPageReactiveResponses responses) {
        this.client = client;
        this.responses = responses;
    }

    @GetMapping("/{id}.pdf")
    public Mono<ResponseEntity<byte[]>> invoice(@PathVariable String id) {
        CompletableFuture<byte[]> future = client.renderAsync().pdf(ProjectModeInput.builder()
            .project("billing")
            .template("invoice")
            .version("1.0.0")
            .data(Map.of("invoice_id", id))
            .build());
        return responses.bytes(future, "invoice-" + id + ".pdf");
    }
}
```

The helper wraps the `CompletableFuture` in `Mono.fromFuture(future, false)` — the `suppressCancel=false` form means a `Mono` cancellation (e.g. the client disconnects) propagates back to the `CompletableFuture` via `.cancel(true)`, releasing the SDK's HTTP connection back to the pool.

Headers are identical to the MVC variant (see [responses.md](responses.md)) — `application/pdf`, RFC 5987 `Content-Disposition`, `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`.

### Streaming with `Flux<DataBuffer>`

For multi-megabyte PDFs, use `stream(...)`. The helper adapts the SDK's `InputStream` future into a `Flux<DataBuffer>` body via `DataBufferUtils.readInputStream(...)`:

```java
@GetMapping("/accounts/{id}/statement.pdf")
public Mono<ResponseEntity<Flux<DataBuffer>>> statement(
        @PathVariable String id,
        @Autowired DataBufferFactory factory) {

    CompletableFuture<InputStream> future = client.renderAsync().pdfStream(ProjectModeInput.builder()
        .project("statements")
        .template("monthly")
        .version("2.3.0")
        .data(Map.of("account_id", id))
        .build());

    return responses.stream(future, "statement-" + id + ".pdf", false, factory);
}
```

`DataBufferFactory` is bean-injected — reactive Spring apps already have one in the context (`NettyDataBufferFactory` for the default Netty server, `DefaultDataBufferFactory` for tests).

### Composing with other reactive sources

```java
@GetMapping("/{id}/report.pdf")
public Mono<ResponseEntity<byte[]>> report(@PathVariable String id) {
    Mono<Account> account = accountService.findById(id);
    Mono<List<Transaction>> txns = transactionService.recent(id);

    return Mono.zip(account, txns)
        .flatMap(tuple -> {
            CompletableFuture<byte[]> pdf = client.renderAsync().pdf(ProjectModeInput.builder()
                .project("reports")
                .template("monthly")
                .version("1.0.0")
                .data(Map.of(
                    "account", tuple.getT1(),
                    "transactions", tuple.getT2()))
                .build());
            return responses.bytes(pdf, "report-" + id + ".pdf");
        });
}
```

Three concurrent I/Os; the response flushes when all three complete. No threads blocked.

### Documents preview / redirect

The reactive helper has the same four-method surface as MVC:

```java
@GetMapping("/{id}/preview")
public Mono<ResponseEntity<String>> preview(@PathVariable String id) {
    return responses.preview(client.documentsAsync().preview(id));
}

@GetMapping("/{id}/download")
public Mono<ResponseEntity<Void>> download(@PathVariable String id) {
    return responses.documentRedirect(client.documentsAsync().get(id));
}
```

## Functional routing (Router DSL)

WebFlux's functional routing works the same way — just return `Mono<ServerResponse>`:

```java
@Configuration
public class PoliPageRoutes {

    @Bean
    RouterFunction<ServerResponse> routes(
            PoliPageClient client,
            PoliPageReactiveResponses responses) {
        return route(GET("/welcome.pdf"), req -> {
            CompletableFuture<byte[]> pdf = client.renderAsync().pdf(ProjectModeInput.builder()
                .project("getting-started")
                .template("welcome")
                .version("1.0.0")
                .data(Map.of("name", req.queryParam("name").orElse("World")))
                .build());

            return responses.bytes(pdf, "welcome.pdf")
                .flatMap(entity -> ServerResponse.status(entity.getStatusCode())
                    .headers(h -> h.putAll(entity.getHeaders()))
                    .bodyValue(entity.getBody()));
        });
    }
}
```

`PoliPageReactiveResponses.bytes(...)` returns `Mono<ResponseEntity<byte[]>>`; the router adapts it to a `Mono<ServerResponse>`. Two lines of glue, no blocking.

## Gotchas

- **Cancellation does not refund the API quota.** A reactive client that cancels the `Mono` mid-render does release the local HTTP connection (the underlying `CompletableFuture` is `cancel(true)`'d), but the Poli Page render has already started server-side. The render counts against your quota. Reactive cancellation is for resource cleanup, not cost-avoidance.
- **`DataBufferFactory` cannot be hardcoded.** The factory varies by reactive server (Netty default, Jetty, Undertow, Tomcat-reactive). Inject it; don't construct one.
- **Don't mix `renderAsync()` with `Mono.fromCallable(() -> client.render()...)`.** Wrapping the blocking facade in `fromCallable` and `subscribeOn(boundedElastic)` works, but ties up a thread per concurrent render — the whole point of WebFlux is dodging that. Use the async facade directly.
- **Spring Security context propagation is opt-in in WebFlux.** `ReactorContextWebFilter` (registered by `spring-security-webflux`) propagates the security context through Reactor's `Context`. Without it, `ReactiveSecurityContextHolder.getContext()` returns empty inside the `flatMap`. Verify before relying on it for auditing.
- **No `@EventListener` on retry events in WebFlux apps.** The events still fire — the bridge runs on the SDK's internal threads, not on Reactor's `Schedulers.parallel()`. Your `@EventListener` methods execute as usual; do not subscribe to events from inside a reactive pipeline.
- **`stream(...)` uses 8 KB buffers by default.** That's right for most networks. If you have a measured reason to want larger buffers, write a custom helper using `DataBufferUtils.readInputStream(() -> stream, factory, 32_768)` — but bench first.

## Related

- [Responses](responses.md) — MVC equivalents for direct comparison.
- [Streaming](streaming.md) — `StreamingResponseBody` rationale (the MVC half of the story).
- [spec/spring-boot-starter-specification.md §9](spec/spring-boot-starter-specification.md#9-webflux-response-helpers) — full reactive-helper signatures and cancellation semantics.
