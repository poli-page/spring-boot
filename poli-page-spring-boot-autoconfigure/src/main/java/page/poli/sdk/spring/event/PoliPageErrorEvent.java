package page.poli.sdk.spring.event;

import java.util.Objects;
import org.springframework.context.ApplicationEvent;
import page.poli.sdk.exception.PoliPageException;

/**
 * Spring {@link ApplicationEvent} published when the SDK gives up on a request after exhausting its
 * retry budget.
 *
 * <p>Wraps the SDK's own {@link PoliPageException} verbatim — callers get the full hierarchy
 * (status code, error code, request id, cause). Subscribe via {@code @EventListener public void
 * onError(PoliPageErrorEvent event)}. The exception still propagates from the call site, so this
 * event is for side effects (alerting, breadcrumbs, audit log), not for changing control flow.
 */
public final class PoliPageErrorEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;

  private final PoliPageException exception;

  /**
   * @param source the bridge instance that published the event (per the {@link ApplicationEvent}
   *     contract)
   * @param exception the SDK's terminal exception — must not be {@code null}
   */
  public PoliPageErrorEvent(Object source, PoliPageException exception) {
    super(source);
    this.exception = Objects.requireNonNull(exception, "exception");
  }

  /**
   * @return the underlying SDK {@link PoliPageException}
   */
  public PoliPageException exception() {
    return exception;
  }
}
