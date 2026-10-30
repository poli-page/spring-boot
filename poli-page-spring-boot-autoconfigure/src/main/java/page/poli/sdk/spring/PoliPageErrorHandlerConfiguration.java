package page.poli.sdk.spring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link PoliPageExceptionHandler} and {@link PoliPageErrorPayloadModule}. Imported by
 * the MVC and WebFlux auto-configurations, so the handler is on by default in both stacks; {@code
 * poli-page.error-handler.enabled=false} turns it off.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "poli-page.error-handler", name = "enabled", matchIfMissing = true)
public class PoliPageErrorHandlerConfiguration {

  /**
   * The error handler.
   *
   * @return the handler
   */
  @Bean
  @ConditionalOnMissingBean
  public PoliPageExceptionHandler poliPageExceptionHandler() {
    return new PoliPageExceptionHandler();
  }

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
