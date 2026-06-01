# Actuator: HealthIndicator and Micrometer metrics

> Expose Poli Page reachability via `/actuator/health` and ship four Prometheus-ready Micrometer meters for retries, errors, and uptime.

## Why

Spring Boot Actuator is the industry-standard surface for "is this service healthy?" The starter contributes a `HealthIndicator` named `poliPage` and a `MeterBinder` registering four meters with bounded tag cardinality. Both are conditional — gated on Actuator/Micrometer being on the classpath and on opt-out properties — so they cost nothing if you don't use them.

The health indicator hits a dedicated lightweight `GET /v1/health` endpoint on the Poli Page platform. It never invokes `render(...)` for the probe — that would consume your API quota and a real render budget for every Kubernetes readiness check.

## How

### Enabling Actuator

If you haven't already, add the Actuator dependency:

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

The starter's `PoliPageHealthIndicator` activates automatically (`@ConditionalOnClass(HealthIndicator.class)`).

### Health endpoint output

`GET /actuator/health` returns:

```json
{
  "status": "UP",
  "components": {
    "poliPage": {
      "status": "UP",
      "details": {
        "baseUrl": "api.poli.page"
      }
    },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

When Poli Page is unreachable:

```json
{
  "status": "DOWN",
  "components": {
    "poliPage": {
      "status": "DOWN",
      "details": {
        "error": "java.net.ConnectException: Connection refused"
      }
    }
  }
}
```

### Health groups

Wire `poliPage` into the `liveness` group only — if Poli Page is down, your app is still alive (the dependency just degrades). Production deployments typically want this so a flaky Poli Page doesn't trigger Kubernetes pod restarts:

```yaml
management:
  endpoint:
    health:
      show-details: when_authorized
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState, poliPage
```

This config puts `poliPage` in `readiness` (a failing dependency removes the pod from the load balancer) but not `liveness` (the pod is not restarted). Adjust to your operational model.

### Disabling the health indicator

Two ways:

```yaml
# Disable the bean entirely
poli-page:
  health:
    enabled: false
```

```yaml
# Spring's standard mechanism — disable a specific HealthContributor
management:
  health:
    poliPage:
      enabled: false
```

Either silences the `poliPage` component. The starter's `@ConditionalOnProperty("poli-page.health.enabled", matchIfMissing=true)` handles the first; Spring Boot's `HealthContributorAutoConfiguration` handles the second.

## Metrics

Adding `spring-boot-starter-actuator` automatically pulls in `micrometer-core`. The starter's `PoliPageMetrics` `MeterBinder` registers four meters:

| Meter | Type | Tags | Source |
|---|---|---|---|
| `poli.page.retries.total` | Counter | `attempt`, `reason` | Incremented per `PoliPageRetryEvent`. |
| `poli.page.retries.delay` | Timer | `attempt`, `reason` | Records each retry's `delay` Duration. |
| `poli.page.errors.total` | Counter | `code`, `status_class` (`4xx`/`5xx`/`network`) | Incremented per `PoliPageErrorEvent`. |
| `poli.page.client.up` | Gauge | — | Mirrors the HealthIndicator (`1.0` UP, `0.0` DOWN). |

### Tag cardinality

`reason` is one of four SDK-defined values: `5xx`, `rate_limit`, `timeout`, `network_error`. `code` is one of the ~9 codes from the sealed `PoliPageException` hierarchy. `status_class` is one of three buckets. `attempt` is bounded by `maxRetries + 1` (default 3). The full cardinality is small and stable — Prometheus-safe.

### Prometheus scrape

Add the Prometheus registry:

```kotlin
implementation("io.micrometer:micrometer-registry-prometheus")
```

Spring Boot wires `/actuator/prometheus` automatically. Scraping it yields:

```
# HELP poli_page_retries_total
# TYPE poli_page_retries_total counter
poli_page_retries_total{application="my-app",attempt="1",reason="5xx"} 3.0
poli_page_retries_total{application="my-app",attempt="2",reason="5xx"} 1.0

# HELP poli_page_errors_total
# TYPE poli_page_errors_total counter
poli_page_errors_total{application="my-app",code="rate_limited",status_class="4xx"} 2.0

# HELP poli_page_client_up
# TYPE poli_page_client_up gauge
poli_page_client_up{application="my-app"} 1.0
```

### Disabling metrics

```yaml
poli-page:
  metrics:
    enabled: false
```

The `MeterBinder` is not registered and no `poli.page.*` meters are published.

## Grafana / alerting examples

Useful queries to put on a Poli Page dashboard:

```promql
# Retry rate by reason (last 5 minutes)
sum by (reason) (rate(poli_page_retries_total[5m]))

# Error rate by status class
sum by (status_class) (rate(poli_page_errors_total[5m]))

# 95th percentile retry delay (across all reasons)
histogram_quantile(0.95, sum by (le) (rate(poli_page_retries_delay_seconds_bucket[5m])))

# Alert: client unhealthy for more than 2 minutes
poli_page_client_up == 0
  for: 2m
```

A reasonable starting alerting policy: page on `poli_page_client_up == 0 for 5m` (the API has been unreachable long enough to be more than a transient blip) and on `rate(poli_page_errors_total{status_class="5xx"}[10m]) > 0.5` (sustained 5xx).

## Gotchas

- **The health endpoint hits a real network resource.** With a 2-second timeout per probe, a heavily-loaded Kubernetes cluster doing aggressive readiness checks can exert noticeable load on the Poli Page edge — and a flapping network between your app and `api.poli.page` will flip the status. Use the `readiness` group, not `liveness`, so Kubernetes removes the pod from the LB without restarting it.
- **`show-details` defaults to `never` outside dev.** Production endpoints typically run with `management.endpoint.health.show-details: when_authorized` — the `details.error` field showing the JDK exception class only surfaces for authenticated callers. Worth ensuring the operator dashboard authenticates.
- **`poli.page.client.up` is not the same as `/actuator/health`'s `UP`/`DOWN`.** The gauge is sampled on the Micrometer schedule (typically 60 s), the HealthIndicator on demand at each `/actuator/health` probe. For tight alerting fidelity, scrape `/actuator/health` directly via Prometheus's HTTP probe rather than relying on the gauge.
- **Tag cardinality stays low only if you don't add tags downstream.** Resist the temptation to add `documentId` or `templateSlug` as tags via a custom `@EventListener` — that explodes Prometheus's storage.
- **Health and metrics are independent toggles.** `poli-page.health.enabled=false` does not disable metrics, and `poli-page.metrics.enabled=false` does not disable health. Each subsystem has its own switch.

## Related

- [Events](events.md) — the event types that drive the retry and error counters.
- [Configuration properties](configuration-properties.md) — the `poli-page.health.enabled` and `poli-page.metrics.enabled` toggles.
- [spec/spring-boot-starter-specification.md §11](spec/spring-boot-starter-specification.md#11-actuator-healthindicator) — HealthIndicator implementation rationale.
- [spec/spring-boot-starter-specification.md §12](spec/spring-boot-starter-specification.md#12-micrometer-metrics) — meter definitions and tag cardinality discussion.
