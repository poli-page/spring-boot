package page.poli.sdk.spring.reactive;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.spring.PoliPageAutoConfiguration;

/**
 * WebFlux-flavoured auto-configuration that registers {@link PoliPageReactiveResponses}.
 *
 * <p>Activates only in a reactive stack ({@link ConditionalOnWebApplication.Type#REACTIVE}).
 * Requires a {@link DataBufferFactory} bean — Spring WebFlux provides one out of the box; falls
 * back to {@link DefaultDataBufferFactory} only when no other is on the context (mainly relevant
 * for unit tests).
 */
@AutoConfiguration(after = PoliPageAutoConfiguration.class)
@ConditionalOnClass({PoliPageClient.class, WebFluxConfigurer.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class PoliPageWebFluxAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(DataBufferFactory.class)
  public DataBufferFactory poliPageDataBufferFactory() {
    return new DefaultDataBufferFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public PoliPageReactiveResponses poliPageReactiveResponses(DataBufferFactory bufferFactory) {
    return new PoliPageReactiveResponses(bufferFactory);
  }
}
