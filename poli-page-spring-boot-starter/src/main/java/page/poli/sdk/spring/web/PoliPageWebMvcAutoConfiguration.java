package page.poli.sdk.spring.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.spring.PoliPageAutoConfiguration;
import page.poli.sdk.spring.PoliPageErrorHandlerConfiguration;
import page.poli.sdk.spring.PoliPageExceptionHandler;

/**
 * MVC-flavoured auto-configuration that registers {@link PoliPageResponses} and, unless {@code
 * poli-page.error-handler.enabled=false}, the {@link PoliPageExceptionHandler}.
 *
 * <p>Activates only in a servlet stack ({@link ConditionalOnWebApplication.Type#SERVLET}), so a
 * project that — atypically — pulls in both the MVC and WebFlux starters ends up with only the
 * stack-appropriate helper active.
 */
@AutoConfiguration(after = PoliPageAutoConfiguration.class)
@ConditionalOnClass({PoliPageClient.class, RestController.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(PoliPageErrorHandlerConfiguration.class)
public class PoliPageWebMvcAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PoliPageResponses poliPageResponses() {
    return new PoliPageResponses();
  }
}
