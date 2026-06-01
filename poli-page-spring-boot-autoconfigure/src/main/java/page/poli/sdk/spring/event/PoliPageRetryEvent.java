package page.poli.sdk.spring.event;

import java.util.Objects;
import org.springframework.context.ApplicationEvent;
import page.poli.sdk.RetryEvent;

/**
 * Spring {@link ApplicationEvent} published just before the SDK sleeps between retry attempts.
 *
 * <p>Wraps the SDK's own {@link RetryEvent} record verbatim — callers get the full payload ({@code
 * attempt}, {@code delay}, {@code statusCode}, {@code reason}) without translation. Subscribe via
 * {@code @EventListener public void onRetry(PoliPageRetryEvent event)}.
 */
public final class PoliPageRetryEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;

  private final RetryEvent sdkEvent;

  /**
   * @param source the bridge instance that published the event (per the {@link ApplicationEvent}
   *     contract)
   * @param sdkEvent the SDK's retry payload — must not be {@code null}
   */
  public PoliPageRetryEvent(Object source, RetryEvent sdkEvent) {
    super(source);
    this.sdkEvent = Objects.requireNonNull(sdkEvent, "sdkEvent");
  }

  /**
   * @return the underlying SDK {@link RetryEvent} record
   */
  public RetryEvent sdkEvent() {
    return sdkEvent;
  }
}
