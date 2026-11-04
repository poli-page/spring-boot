package page.poli.sdk.spring.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.event.EventListener;
import page.poli.sdk.PoliPageErrorCode;
import page.poli.sdk.exception.PoliPageException;
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
 * <p>{@code status_class} says where the error comes from:
 *
 * <ul>
 *   <li>{@code 4xx} / {@code 5xx}: the API answered with that status. {@code invalid_response} (a
 *       2xx answer whose body is not JSON, answered {@code 502} by the error handler) is {@code
 *       5xx};
 *   <li>{@code network}: the API or the PDF storage could not be reached or did not answer in time
 *       ({@code network_error}, {@code timeout}, and {@code DOWNLOAD_FAILED} whatever the storage
 *       status);
 *   <li>{@code local}: any other error without an API status, raised in the application's process,
 *       such as {@code io_error} (the PDF file could not be written), {@code aborted} or {@code
 *       invalid_options}.
 * </ul>
 *
 * <p>Tag cardinality is bounded: {@code reason} is one of four SDK-defined values, {@code code} is
 * one of the SDK's error codes ({@code PoliPageErrorCode}, or a code the API sends), {@code
 * status_class} is one of the four buckets above, {@code attempt} is bounded by {@code maxRetries +
 * 1} (max 11). Safe for Prometheus.
 *
 * <p>There is no {@code poli.page.client.up} gauge: it would force a network probe on every
 * Prometheus scrape. Operators get the same signal by scraping {@code /actuator/health} directly
 * via the Prometheus HTTP probe.
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
            "status_class", statusClass(event.exception()));
    registry.counter("poli.page.errors.total", tags).increment();
  }

  private static String statusClass(PoliPageException ex) {
    String code = ex.code();
    if (PoliPageErrorCode.NETWORK_ERROR.equals(code)
        || PoliPageErrorCode.TIMEOUT.equals(code)
        || PoliPageErrorCode.DOWNLOAD_FAILED.equals(code)) {
      return "network";
    }
    if (PoliPageErrorCode.INVALID_RESPONSE.equals(code)) {
      return "5xx";
    }
    int status = ex.statusCode();
    if (status >= 500 && status < 600) {
      return "5xx";
    }
    if (status >= 400 && status < 500) {
      return "4xx";
    }
    return "local";
  }
}
