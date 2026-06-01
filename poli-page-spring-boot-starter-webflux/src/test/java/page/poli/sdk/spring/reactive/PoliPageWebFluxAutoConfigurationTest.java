package page.poli.sdk.spring.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

class PoliPageWebFluxAutoConfigurationTest {

  @Test
  void registersResponsesInReactiveStack() {
    new ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PoliPageWebFluxAutoConfiguration.class))
        .run(context -> assertThat(context).hasSingleBean(PoliPageReactiveResponses.class));
  }

  @Test
  void doesNotRegisterInPlainContext() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PoliPageWebFluxAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(PoliPageReactiveResponses.class));
  }

  // The reciprocal "doesNotRegisterInServletStack" assertion is intentionally NOT here:
  // running a WebApplicationContextRunner from this module requires spring-boot-starter-web on
  // the classpath, which would force consumers to think they need both web stacks. The MVC
  // autoconfig's own test verifies the inverse (PoliPageWebMvcAutoConfigurationTest
  // .doesNotRegisterInReactiveStack), and @ConditionalOnWebApplication is itself exhaustively
  // tested by Spring Boot — that's the guarantee we lean on.
}
