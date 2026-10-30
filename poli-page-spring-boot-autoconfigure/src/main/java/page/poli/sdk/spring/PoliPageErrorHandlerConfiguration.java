package page.poli.sdk.spring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link PoliPageErrorPayloadModule}, which the default error handler's body needs.
 * Imported by the MVC and WebFlux auto-configurations, which also register their stack's {@link
 * PoliPageExceptionHandler}; {@code poli-page.error-handler.enabled=false} turns both off.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "poli-page.error-handler", name = "enabled", matchIfMissing = true)
public class PoliPageErrorHandlerConfiguration {

  /**
   * Keeps the error body's {@code null} fields when the application omits nulls.
   *
   * @return the Jackson module
   */
  @Bean
  @ConditionalOnMissingBean
  public PoliPageErrorPayloadModule poliPageErrorPayloadModule() {
    return new PoliPageErrorPayloadModule();
  }
}
