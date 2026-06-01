package page.poli.sdk.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import page.poli.sdk.PoliPageErrorCode;
import page.poli.sdk.RetryEvent;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.exception.PoliPageRateLimitException;
import page.poli.sdk.spring.event.PoliPageErrorEvent;
import page.poli.sdk.spring.event.PoliPageRetryEvent;

class PoliPageEventBridgeTest {

  @Test
  void onRetryPublishesWrapperEventWithSdkPayload() {
    ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
    PoliPageEventBridge bridge = new PoliPageEventBridge(publisher);
    RetryEvent sdkEvent = new RetryEvent(2, Duration.ofMillis(750), 502, "5xx");

    bridge.onRetry(sdkEvent);

    ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
    verify(publisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(PoliPageRetryEvent.class);
    PoliPageRetryEvent wrapper = (PoliPageRetryEvent) captor.getValue();
    assertThat(wrapper.sdkEvent()).isSameAs(sdkEvent);
    assertThat(wrapper.sdkEvent().attempt()).isEqualTo(2);
    assertThat(wrapper.sdkEvent().delay()).isEqualTo(Duration.ofMillis(750));
    assertThat(wrapper.sdkEvent().statusCode()).isEqualTo(502);
    assertThat(wrapper.sdkEvent().reason()).isEqualTo("5xx");
    assertThat(wrapper.getSource()).isSameAs(bridge);
  }

  @Test
  void onErrorPublishesWrapperEventForPoliPageException() {
    ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
    PoliPageEventBridge bridge = new PoliPageEventBridge(publisher);
    PoliPageRateLimitException exception =
        new PoliPageRateLimitException(
            PoliPageErrorCode.QUOTA_EXCEEDED,
            429,
            "quota exceeded",
            "req_abc",
            Duration.ofSeconds(5));

    bridge.onError(exception);

    ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
    verify(publisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(PoliPageErrorEvent.class);
    PoliPageErrorEvent wrapper = (PoliPageErrorEvent) captor.getValue();
    assertThat(wrapper.exception()).isSameAs(exception);
    assertThat(wrapper.exception().statusCode()).isEqualTo(429);
    assertThat(wrapper.exception().requestId()).isEqualTo("req_abc");
  }

  @Test
  void onErrorDropsNonPoliPageException() {
    ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
    PoliPageEventBridge bridge = new PoliPageEventBridge(publisher);

    bridge.onError(new IllegalStateException("not from the SDK"));

    verify(publisher, never()).publishEvent(any(ApplicationEvent.class));
  }

  @Test
  void onErrorAcceptsAnyPoliPageExceptionSubclass() {
    ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
    PoliPageEventBridge bridge = new PoliPageEventBridge(publisher);
    PoliPageException base =
        new PoliPageException(PoliPageErrorCode.INTERNAL_ERROR, 500, "boom", "req_xyz", null);

    bridge.onError(base);

    ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
    verify(publisher).publishEvent(captor.capture());
    PoliPageErrorEvent wrapper = (PoliPageErrorEvent) captor.getValue();
    assertThat(wrapper.exception()).isSameAs(base);
  }
}
