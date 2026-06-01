package page.poli.sdk.spring;

import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import page.poli.sdk.RetryEvent;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.spring.event.PoliPageErrorEvent;
import page.poli.sdk.spring.event.PoliPageRetryEvent;

/**
 * Adapter that turns the SDK's {@code Consumer<RetryEvent>} / {@code Consumer<Throwable>} builder
 * hooks into Spring {@link org.springframework.context.ApplicationEvent} publications.
 *
 * <p>The auto-configuration wires this bridge into the {@code PoliPageClient.Builder} via method
 * references ({@code bridge::onRetry}, {@code bridge::onError}) — see {@link
 * PoliPageAutoConfiguration}.
 */
public final class PoliPageEventBridge {

  private final ApplicationEventPublisher publisher;

  /**
   * @param publisher the Spring application context's event publisher — must not be {@code null}
   */
  public PoliPageEventBridge(ApplicationEventPublisher publisher) {
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }

  /**
   * SDK retry-hook callback. Publishes a {@link PoliPageRetryEvent} carrying the SDK payload
   * verbatim.
   *
   * @param event the SDK's retry payload — non-null
   */
  public void onRetry(RetryEvent event) {
    publisher.publishEvent(new PoliPageRetryEvent(this, event));
  }

  /**
   * SDK error-hook callback. Publishes a {@link PoliPageErrorEvent} when the cause is a {@link
   * PoliPageException}; other {@link Throwable}s are dropped silently (the SDK only invokes the
   * hook with terminal {@code PoliPageException}s, but the defensive guard keeps the bridge
   * crash-free if that invariant ever changes).
   *
   * @param cause the terminal failure that exhausted the SDK's retry budget
   */
  public void onError(Throwable cause) {
    if (cause instanceof PoliPageException ppe) {
      publisher.publishEvent(new PoliPageErrorEvent(this, ppe));
    }
  }
}
