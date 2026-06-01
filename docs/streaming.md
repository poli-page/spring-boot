# Streaming large PDFs

> Stream a multi-megabyte PDF as a Spring MVC `StreamingResponseBody` (or a WebFlux `Flux<DataBuffer>`) instead of buffering the full document in heap memory before flushing it to the client.

## Why

`Render.pdf(...)` returns the PDF as a `byte[]` — fine for a one-page invoice, wasteful for a 50-page report or a 10 MB monthly statement. `Render.pdfStream(...)` returns a JDK `InputStream` that reads from the upstream presigned URL on demand. `PoliPageResponses.stream(...)` adapts that stream to Spring MVC's `StreamingResponseBody` with the correct PDF headers, so bytes flow controller → client in 8 KB chunks without sitting in the heap.

Use it whenever the rendered PDF is large enough that buffering it would risk OOM on the application instance, or when you want to start flushing bytes to the client before the upstream rendering completes (perceived latency wins).

## How

Inject `PoliPageClient` and `PoliPageResponses`, call `pdfStream()` instead of `pdf()`, hand the returned `InputStream` to `responses.stream(...)`. The helper wraps the read loop in a `try-with-resources` so the SDK's connection is released even if the client disconnects mid-stream. `Content-Type`, `Content-Disposition` (RFC 5987-encoded for non-ASCII filenames), `Cache-Control: private, no-store`, and `X-Content-Type-Options: nosniff` are set on the response; `Content-Length` is omitted (the body uses chunked transfer encoding).

```java
@RestController
public class StatementController {

    private final PoliPageClient client;
    private final PoliPageResponses responses;

    public StatementController(PoliPageClient client, PoliPageResponses responses) {
        this.client = client;
        this.responses = responses;
    }

    @GetMapping("/accounts/{id}/statement.pdf")
    public ResponseEntity<StreamingResponseBody> statement(@PathVariable String id) {
        InputStream stream = client.render().pdfStream(ProjectModeInput.builder()
            .project("statements")
            .template("monthly")
            .version("2.3.0")
            .data(Map.of("account_id", id))
            .build());

        return responses.stream(stream, "statement-" + id + ".pdf");
    }
}
```

Pass `inline=true` as the third argument to render the PDF in the browser tab instead of triggering a download:

```java
return responses.stream(stream, "statement-" + id + ".pdf", true);
```

### What `StreamingResponseBody` actually does

Spring MVC's `StreamingResponseBody` is a deferred body — the controller method returns immediately with the `ResponseEntity` headers, and Spring dispatches the body lambda on a `TaskExecutor` (default `SimpleAsyncTaskExecutor`). The lambda receives an `OutputStream` (the servlet response) and is expected to write the body in chunks. While the lambda runs, the request worker thread is back in the pool — large streaming responses do not pin a thread per concurrent client.

The starter's `stream(...)` lambda looks like this:

```java
StreamingResponseBody body = output -> {
    try (InputStream s = stream) {
        s.transferTo(output);
    }
};
```

`InputStream.transferTo(OutputStream)` reads in 8 KB chunks (JDK default) and forwards them. The `try-with-resources` closes the SDK stream after the last chunk, releasing the HTTP/2 connection back to the SDK's connection pool.

### Configuring the executor

By default Spring's `SimpleAsyncTaskExecutor` spawns a new thread per streaming response — fine for low traffic, expensive under high concurrency. Override with a pooled executor:

```java
@Configuration
public class AsyncConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(streamingExecutor());
    }

    @Bean
    ThreadPoolTaskExecutor streamingExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(0); // backpressure on accept rather than queueing
        ex.setThreadNamePrefix("poli-stream-");
        ex.initialize();
        return ex;
    }
}
```

`setQueueCapacity(0)` means the executor rejects new tasks when all 32 threads are busy — the request fails fast (HTTP 503) rather than queueing arbitrarily.

### Async timeout

Spring's default async request timeout is 30 s. A large PDF + slow client connection can exceed that. Override per-endpoint with a longer dispatch timeout:

```java
@GetMapping("/accounts/{id}/statement.pdf")
public ResponseEntity<StreamingResponseBody> statement(@PathVariable String id) {
    // ...
    return ResponseEntity.ok()
        .headers(responses.stream(stream, name).getHeaders())
        .body(output -> {
            try (InputStream s = stream) { s.transferTo(output); }
        });
}
```

Or globally:

```yaml
spring:
  mvc:
    async:
      request-timeout: 5m
```

## Gotchas

- **The SDK's `pdfStream(...)` owns the connection until the stream is closed.** The starter's `stream(...)` helper closes the stream in the `try-with-resources` — do not call `close()` on it yourself before the helper writes the response. Closing the stream prematurely raises `IOException` from inside `transferTo` and produces a truncated response.
- **Client disconnects propagate as `IOException` in the body lambda.** Spring catches it and marks the async request complete with an error. The SDK stream is still closed by the helper's `try-with-resources` even on the exceptional path — no connection leak, but your access logs will show truncated 200 entries.
- **`Content-Length` is intentionally omitted.** The upstream presigned URL emits `Transfer-Encoding: chunked`, and we propagate that. Don't try to compute `Content-Length` by reading the full stream into memory first — that defeats the entire purpose of streaming.
- **`SecurityContextHolder` does not propagate into the lambda by default.** Spring MVC's async dispatch runs the body lambda on a different thread; `SecurityContextHolderStrategy.MODE_INHERITABLETHREADLOCAL` plus Spring Security's `WebAsyncManagerIntegrationFilter` handle this correctly when both are configured. Read code that relies on `SecurityContextHolder.getContext()` before returning the `ResponseEntity`, not inside the lambda.
- **Reactive consumers should use `PoliPageReactiveResponses`.** This page covers Spring MVC. The WebFlux equivalent (`Flux<DataBuffer>` body, `Mono.fromFuture(...)` for the async render call) is documented in [webflux.md](webflux.md).
- **Logging the response body is impossible.** A `LoggingFilter` that buffers the response to log it (e.g. `ContentCachingResponseWrapper`) defeats streaming — the wrapper holds the entire body in memory before flushing. If you need response logging for streamed PDFs, log metadata (status, content-length-when-known, request id) instead.

## Related

- [Responses](responses.md) — the non-streaming `bytes()` helper for small PDFs.
- [WebFlux](webflux.md) — `Mono<ResponseEntity<Flux<DataBuffer>>>` reactive variant.
- [spec/spring-boot-starter-specification.md §8.4](spec/spring-boot-starter-specification.md#84-streamingresponsebody-ownership) — connection-ownership rationale.
