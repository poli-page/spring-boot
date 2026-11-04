package page.poli.sdk.spring.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import page.poli.sdk.PoliPageErrorCode;
import page.poli.sdk.RetryEvent;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.exception.PoliPageNetworkException;
import page.poli.sdk.exception.PoliPageRateLimitException;
import page.poli.sdk.spring.event.PoliPageErrorEvent;
import page.poli.sdk.spring.event.PoliPageRetryEvent;

class PoliPageMetricsTest {

  private SimpleMeterRegistry registry;
  private PoliPageMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new PoliPageMetrics();
    metrics.bindTo(registry);
  }

  @Test
  void retryEventIncrementsCounterAndRecordsTimer() {
    RetryEvent sdkEvent = new RetryEvent(2, Duration.ofMillis(750), 502, "5xx");

    metrics.onRetry(new PoliPageRetryEvent(this, sdkEvent));

    Counter counter =
        registry.find("poli.page.retries.total").tag("attempt", "2").tag("reason", "5xx").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);

    Timer timer =
        registry.find("poli.page.retries.delay").tag("attempt", "2").tag("reason", "5xx").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(750.0);
  }

  @Test
  void errorEvent4xxIncrementsCounterTaggedAs4xx() {
    PoliPageException ex =
        new PoliPageRateLimitException(
            PoliPageErrorCode.QUOTA_EXCEEDED, 429, "quota", "req_a", null);

    metrics.onError(new PoliPageErrorEvent(this, ex));

    Counter counter =
        registry
            .find("poli.page.errors.total")
            .tag("code", "QUOTA_EXCEEDED")
            .tag("status_class", "4xx")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void errorEvent5xxTaggedAs5xx() {
    PoliPageException ex =
        new PoliPageException(PoliPageErrorCode.INTERNAL_ERROR, 500, "boom", "req_b", null);

    metrics.onError(new PoliPageErrorEvent(this, ex));

    assertThat(registry.find("poli.page.errors.total").tag("status_class", "5xx").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void errorEventNetworkTaggedAsNetwork() {
    PoliPageException ex =
        new PoliPageNetworkException(PoliPageErrorCode.NETWORK_ERROR, "connection refused", null);

    metrics.onError(new PoliPageErrorEvent(this, ex));

    assertThat(
            registry
                .find("poli.page.errors.total")
                .tag("status_class", "network")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void errorEventTimeoutTaggedAsNetwork() {
    assertStatusClass(
        new PoliPageNetworkException(
            PoliPageErrorCode.TIMEOUT, "Request timed out after 60000ms", null),
        "network");
  }

  @Test
  void errorEventDownloadFailedTaggedAsNetworkWhateverTheStorageStatus() {
    assertStatusClass(
        new PoliPageException(
            PoliPageErrorCode.DOWNLOAD_FAILED, 0, "Failed to download PDF: timeout", null, null),
        "network");
    assertStatusClass(
        new PoliPageException(
            PoliPageErrorCode.DOWNLOAD_FAILED, 403, "Failed to download PDF: HTTP 403", null, null),
        "network");
  }

  @Test
  void errorEventInvalidResponseTaggedAs5xx() {
    // statusCode() keeps the 2xx; the error handler answers 502.
    assertStatusClass(
        new PoliPageException(
            PoliPageErrorCode.INVALID_RESPONSE,
            200,
            "Invalid response: HTTP 200 body is not JSON",
            "req_c",
            null),
        "5xx");
  }

  @Test
  void errorEventRedirectTaggedAs5xx() {
    // The SDK does not follow redirects: a 3xx is an unexpected answer from the server side.
    assertStatusClass(
        new PoliPageException(
            PoliPageErrorCode.INTERNAL_ERROR, 301, "Unexpected HTTP 301", "req_d", null),
        "5xx");
  }

  @Test
  void errorEventIoErrorTaggedAsLocal() {
    assertStatusClass(
        new PoliPageException(
            PoliPageErrorCode.IO_ERROR,
            0,
            "Failed to write PDF: /out/invoice.pdf: No space left on device",
            null,
            null),
        "local");
  }

  @Test
  void errorEventAbortedTaggedAsLocal() {
    assertStatusClass(
        new PoliPageException(PoliPageErrorCode.ABORTED, 0, "Request was aborted", null, null),
        "local");
  }

  private void assertStatusClass(PoliPageException ex, String statusClass) {
    metrics.onError(new PoliPageErrorEvent(this, ex));
    Counter counter =
        registry
            .find("poli.page.errors.total")
            .tag("code", ex.code())
            .tag("status_class", statusClass)
            .counter();
    assertThat(counter).as("%s tagged %s", ex.code(), statusClass).isNotNull();
    assertThat(registry.find("poli.page.errors.total").tag("code", ex.code()).counters())
        .as("%s has one status_class", ex.code())
        .hasSize(1);
  }

  @Test
  void multipleRetryEventsAccumulate() {
    metrics.onRetry(
        new PoliPageRetryEvent(this, new RetryEvent(1, Duration.ofMillis(500), 502, "5xx")));
    metrics.onRetry(
        new PoliPageRetryEvent(this, new RetryEvent(1, Duration.ofMillis(500), 502, "5xx")));
    metrics.onRetry(
        new PoliPageRetryEvent(this, new RetryEvent(2, Duration.ofMillis(1_000), 502, "5xx")));

    assertThat(
            registry
                .find("poli.page.retries.total")
                .tag("attempt", "1")
                .tag("reason", "5xx")
                .counter()
                .count())
        .isEqualTo(2.0);
    assertThat(
            registry
                .find("poli.page.retries.total")
                .tag("attempt", "2")
                .tag("reason", "5xx")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
