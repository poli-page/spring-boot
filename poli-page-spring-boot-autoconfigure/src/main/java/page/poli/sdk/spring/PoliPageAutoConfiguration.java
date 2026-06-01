package page.poli.sdk.spring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import page.poli.sdk.PoliPageClient;

/**
 * Auto-configuration for the Poli Page SDK client.
 *
 * <p>Registers a singleton {@link PoliPageClient} built from {@link PoliPageProperties}. Wrapped in
 * {@link ConditionalOnMissingBean} so a consumer can override by declaring their own {@code @Bean
 * PoliPageClient}.
 *
 * <p>Also registers a {@link PoliPageEventBridge} (opt-out via {@code
 * poli-page.events.enabled=false}) which translates the SDK's {@code Consumer<RetryEvent>} / {@code
 * Consumer<Throwable>} builder hooks into Spring {@link
 * org.springframework.context.ApplicationEvent}s.
 *
 * <p>Null-valued properties (everything except {@code apiKey}) are passed through to the SDK
 * builder's own defaults — a single source of truth that survives SDK version bumps without a
 * starter release.
 */
@AutoConfiguration
@EnableConfigurationProperties(PoliPageProperties.class)
@ConditionalOnClass(PoliPageClient.class)
public class PoliPageAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "poli-page.events", name = "enabled", matchIfMissing = true)
  public PoliPageEventBridge poliPageEventBridge(ApplicationEventPublisher publisher) {
    return new PoliPageEventBridge(publisher);
  }

  @Bean
  @ConditionalOnMissingBean
  public PoliPageClient poliPageClient(
      PoliPageProperties props, ObjectProvider<PoliPageEventBridge> bridge) {
    PoliPageClient.Builder builder = PoliPageClient.builder().apiKey(props.apiKey());
    if (props.baseUrl() != null) {
      builder.baseUrl(props.baseUrl());
    }
    if (props.requestTimeout() != null) {
      builder.requestTimeout(props.requestTimeout());
    }
    if (props.retries().maxAttempts() != null) {
      builder.maxRetries(props.retries().maxAttempts());
    }
    if (props.retries().delay() != null) {
      builder.retryDelay(props.retries().delay());
    }
    bridge.ifAvailable(
        eb -> {
          builder.onRetry(eb::onRetry);
          builder.onError(eb::onError);
        });
    return builder.build();
  }
}
