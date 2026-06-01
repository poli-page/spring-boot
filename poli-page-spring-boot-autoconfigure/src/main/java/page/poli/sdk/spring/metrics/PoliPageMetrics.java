package page.poli.sdk.spring.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.event.EventListener;
import page.poli.sdk.spring.event.PoliPageErrorEvent;
import page.poli.sdk.spring.event.PoliPageRetryEvent;

/**
 * Micrometer {@link MeterBinder} that exposes three meters fed by the starter's {@code
 * ApplicationEvent} bridge:
 *
 * <ul>
 *   <li>{@code poli.page.retries.total} — counter, tagged {@code attempt} + {@code reason}
 *   <li>{@code poli.page.retries.delay} — timer, same tags, records {@code event.delay()}
 *   <li>{@code poli.page.errors.total} — counter, tagged {@code code} + {@code status_class}
 * </ul>
 *
 * <p>Tag cardinality is bounded: {@code reason} is one of four SDK-defined values, {@code code} is
 * one of the ~9 codes from the sealed exception hierarchy, {@code status_class} is one of three
 * buckets ({@code 4xx} / {@code 5xx} / {@code network}), {@code attempt} is bounded by {@code
 * maxRetries + 1} (max 11). Safe for Prometheus.
 *
 * <p>The {@code poli.page.client.up} gauge specified in v0.1 design has been deferred — it would
 * force a network probe on every Prometheus scrape. Operators get the same signal by scraping
 * {@code /actuator/health} directly via the Prometheus HTTP probe.
 */
public final class PoliPageMetrics implements MeterBinder {

  private MeterRegistry registry;

  @Override
  public void bindTo(MeterRegistry registry) {
    this.registry = registry;
  }

  @EventListener
  public void onRetry(PoliPageRetryEvent event) {
    if (registry == null) {
      return;
    }
    Tags tags =
        Tags.of(
            "attempt", String.valueOf(event.sdkEvent().attempt()),
            "reason", event.sdkEvent().reason());
    registry.counter("poli.page.retries.total", tags).increment();
    registry.timer("poli.page.retries.delay", tags).record(event.sdkEvent().delay());
  }

  @EventListener
  public void onError(PoliPageErrorEvent event) {
    if (registry == null) {
      return;
    }
    Tags tags =
        Tags.of(
            "code", event.exception().code(),
            "status_class", statusClass(event.exception().statusCode()));
    registry.counter("poli.page.errors.total", tags).increment();
  }

  private static String statusClass(int status) {
    if (status >= 500 && status < 600) {
      return "5xx";
    }
    if (status >= 400 && status < 500) {
      return "4xx";
    }
    return "network";
  }
}
