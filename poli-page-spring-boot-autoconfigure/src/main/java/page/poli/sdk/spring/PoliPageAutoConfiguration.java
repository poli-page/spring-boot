package page.poli.sdk.spring;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.spring.actuator.PoliPageHealthIndicator;
import page.poli.sdk.spring.metrics.PoliPageMetrics;

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
 *
 * <p>The API is the only authority on keys: a key that does not look like {@code
 * pp_{environment}_{32 hex characters}} gets one startup warning, and the client is built anyway.
 */
@AutoConfiguration
@EnableConfigurationProperties(PoliPageProperties.class)
@ConditionalOnClass(PoliPageClient.class)
public class PoliPageAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(PoliPageAutoConfiguration.class);

  // pp_{env}_ with {env} a valid environment name, as in the SDK contract's full-key regex.
  private static final String ENV_PREFIX =
      "pp_(?=[a-z][a-z0-9-]{0,19}_)[a-z][a-z0-9]*(?:-[a-z0-9]+)*_";

  // The SDK contract's full-key regex without its ^…$ anchors: matches() anchors both ends and,
  // unlike $, also rejects a trailing newline.
  private static final Pattern KEY_FORMAT = Pattern.compile(ENV_PREFIX + "[0-9a-f]{32}");

  // The only part of a malformed key the warning may show. An {env} that is not a valid
  // environment name is not shown, since it may be part of the secret.
  private static final Pattern SHOWN_PREFIX = Pattern.compile(ENV_PREFIX);

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
    // After build(): the SDK refuses a missing or blank key before it gets here.
    PoliPageClient client = builder.build();
    warnIfMalformed(props.apiKey());
    return client;
  }

  private static void warnIfMalformed(String apiKey) {
    if (KEY_FORMAT.matcher(apiKey).matches()) {
      return;
    }
    Matcher prefix = SHOWN_PREFIX.matcher(apiKey);
    log.warn(
        "Poli Page API key {}does not look like pp_{environment}_{32 hex characters}; "
            + "starting anyway, the API decides whether it is valid.",
        prefix.lookingAt() ? "\"" + prefix.group() + "…\" " : "");
  }

  /**
   * Nested actuator wiring. Activates only when Spring Boot Actuator is on the classpath and the
   * health indicator is not explicitly disabled.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnProperty(prefix = "poli-page.health", name = "enabled", matchIfMissing = true)
  static class HealthIndicatorConfiguration {

    @Bean(name = "poliPage")
    @ConditionalOnMissingBean(name = "poliPage")
    public PoliPageHealthIndicator poliPageHealthIndicator(PoliPageProperties properties) {
      return new PoliPageHealthIndicator(properties);
    }
  }

  /**
   * Nested Micrometer wiring. Activates only when Micrometer is on the classpath and metrics are
   * not explicitly disabled.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnProperty(prefix = "poli-page.metrics", name = "enabled", matchIfMissing = true)
  static class MetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PoliPageMetrics poliPageMetrics() {
      return new PoliPageMetrics();
    }
  }
}
