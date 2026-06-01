package page.poli.sdk.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import page.poli.sdk.PoliPageClient;

/**
 * Auto-configuration for the Poli Page SDK client.
 *
 * <p>Registers a singleton {@link PoliPageClient} built from {@link PoliPageProperties}. Wrapped in
 * {@link ConditionalOnMissingBean} so a consumer can override by declaring their own {@code @Bean
 * PoliPageClient}.
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
  public PoliPageClient poliPageClient(PoliPageProperties props) {
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
    return builder.build();
  }
}
