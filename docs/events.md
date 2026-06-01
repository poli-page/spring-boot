# Event bridges

> Subscribe `@EventListener` methods to `PoliPageRetryEvent` and `PoliPageErrorEvent` so SDK retry attempts and terminal failures flow through Spring's `ApplicationEventPublisher` like any other application event.

## Why

The SDK exposes `onRetry: Consumer<RetryEvent>` and `onError: Consumer<Throwable>` as builder hooks. The starter wraps those `Consumer`s and publishes them as two Spring `ApplicationEvent`s — `page.poli.sdk.spring.event.PoliPageRetryEvent` and `page.poli.sdk.spring.event.PoliPageErrorEvent` — so you observe retries and terminal errors with the same `@EventListener` methods, `@TransactionalEventListener` semantics, and `ApplicationEventMulticaster` plumbing you already use elsewhere. You do not write `Consumer<RetryEvent>` lambdas in a `@Bean PoliPageClient`; you write `@EventListener` methods.

## How

The two event classes carry the SDK payload verbatim. `PoliPageRetryEvent` exposes `sdkEvent()` returning the SDK `RetryEvent` record (`attempt`, `delay`, `statusCode`, `reason`). `PoliPageErrorEvent` exposes `exception()` returning the SDK `PoliPageException` (with `code()`, `statusCode()`, `requestId()`). Both extend `org.springframework.context.ApplicationEvent`, so the multicaster routes them to any `@EventListener` method whose parameter type matches.

### Logging retry attempts

```java
package com.example.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import page.poli.sdk.spring.event.PoliPageRetryEvent;

@Component
public class LogPoliPageRetries {

    private static final Logger log = LoggerFactory.getLogger(LogPoliPageRetries.class);

    @EventListener
    public void onRetry(PoliPageRetryEvent event) {
        log.warn("Poli Page retry attempt={} delay={} reason={} status={}",
            event.sdkEvent().attempt(),
            event.sdkEvent().delay(),
            event.sdkEvent().reason(),
            event.sdkEvent().statusCode());
    }
}
```

`event.sdkEvent().reason()` is one of four stable values (`"5xx"`, `"rate_limit"`, `"timeout"`, `"network_error"`) — safe to use as a metric tag.

### Reporting terminal failures

```java
@Component
public class ReportPoliPageFailures {

    private final ErrorReporter sentry;

    public ReportPoliPageFailures(ErrorReporter sentry) {
        this.sentry = sentry;
    }

    @EventListener
    public void onError(PoliPageErrorEvent event) {
        PoliPageException ex = event.exception();
        sentry.captureException(ex, builder -> builder
            .tag("poli.code", ex.code())
            .tag("poli.status", String.valueOf(ex.statusCode()))
            .tag("poli.request_id", ex.requestId()));
    }
}
```

`PoliPageErrorEvent` fires for **terminal** failures — failures the SDK gave up on after exhausting its retry budget. Transient 5xx that the SDK retries past do not surface here; they surface as `PoliPageRetryEvent`. The exception still propagates from the call site to your controller, so the listener is for side effects (Sentry breadcrumbs, alerting, audit log), not for changing control flow.

### Async listeners

By default `@EventListener` methods run on the publisher's thread — the SDK's retry/error hook thread, which sits on the hot path of your render call. For listeners doing I/O (Slack, Sentry HTTP, Postgres insert), make them async:

```java
@EnableAsync
@Configuration
public class AsyncConfig { }

@Component
public class SlackAlerts {
    @Async
    @EventListener
    public void onError(PoliPageErrorEvent event) {
        slackClient.post("#alerts", "Poli Page failed: " + event.exception().code());
    }
}
```

`@Async` dispatches the listener on Spring's default `taskExecutor`. Override the executor per-listener with `@Async("name")` if you want isolation from the rest of the async pool.

### Transactional listeners

`@TransactionalEventListener` (phase `AFTER_COMMIT`, the default) fires only after the surrounding transaction commits. Useful when the listener writes to a database and you want it to skip if the rendering call was inside a transaction that rolled back:

```java
@Component
public class PoliPageAudit {

    @TransactionalEventListener
    public void onError(PoliPageErrorEvent event) {
        auditRepository.save(new AuditEntry(event.exception()));
    }
}
```

Note that retry/error events fire from inside the SDK's internal threads, **outside** any user-side `@Transactional` boundary. `@TransactionalEventListener` will skip them with a warning ("No transaction is active") unless the calling controller code explicitly republishes the event under its own transaction — usually not what you want. Use plain `@EventListener` for retry/error events.

## Disabling the bridge

Set `poli-page.events.enabled=false` in `application.yml` and the autoconfig drops `PoliPageEventBridge` from the context. The SDK client is built without `.onRetry(...)` / `.onError(...)` hooks, and no events fire. Useful for production deployments that don't want the eventing overhead.

```yaml
poli-page:
  events:
    enabled: false
```

## Custom client + manual publishing

If you declare your own `@Bean PoliPageClient`, you take full control of the builder — the starter's bridge does not run. To keep the Spring event behaviour, wire it yourself:

```java
@Configuration
public class PoliPageConfig {

    @Bean
    PoliPageClient poliPageClient(PoliPageProperties props, ApplicationEventPublisher publisher) {
        return PoliPageClient.builder()
            .apiKey(props.apiKey())
            .onRetry(event -> publisher.publishEvent(new PoliPageRetryEvent(this, event)))
            .onError(t -> {
                if (t instanceof PoliPageException ppe) {
                    publisher.publishEvent(new PoliPageErrorEvent(this, ppe));
                }
            })
            .build();
    }
}
```

This is what the autoconfig does under the hood; copy the pattern if you also need additional callbacks alongside the event publication.

## Gotchas

- **The event bridge is replaced, not augmented, when you declare your own `@Bean PoliPageClient`.** `@ConditionalOnMissingBean` lets your bean win, but with it goes the autoconfig's wiring. If you want both your callable and the Spring event, publish the event manually inside your callback as shown above.
- **`PoliPageErrorEvent` fires only for terminal failures.** Transient 5xx that the SDK successfully retries past do not surface here — they show up as `PoliPageRetryEvent` entries. The exception itself still propagates back from the call site, so wrap the SDK call in a `try/catch` if you need to react to the exception in the caller.
- **SDK hooks must not throw.** The SDK wraps each `Consumer` in a swallowing `try/catch`, so an exception thrown from a listener is logged at SLF4J `WARN` and dropped. Make listeners log-and-return, not raise. The SDK's invariant: hooks must never break the request path.
- **`@EventListener` runs on the SDK's retry thread by default.** Slow listeners block the SDK's retry loop and inflate your perceived latency. Annotate listeners that do I/O with `@Async` and configure a sensible `taskExecutor`. Logging is fast enough not to need `@Async`.
- **Spring AOT (native image) needs reflection hints for the event classes.** `@EventListener` discovery uses reflection. Native-image users may need `@ImportRuntimeHints(PoliPageEventsRuntimeHints.class)` — tracked as v0.2 work in [spec §17](spec/spring-boot-starter-specification.md#17-deferred-to-v02-do-not-build-in-v010).

## Related

- [Auto-configuration](auto-configuration.md) — how the bridge is registered and conditionally enabled.
- [Configuration properties](configuration-properties.md#per-property-reference) — the `poli-page.events.enabled` toggle.
- [Actuator](actuator.md) — Micrometer counters that consume these events for retry/error rate dashboards.
- [spec/spring-boot-starter-specification.md §10](spec/spring-boot-starter-specification.md#10-eventdispatcher-integration) — event-class shape and bridge implementation.
