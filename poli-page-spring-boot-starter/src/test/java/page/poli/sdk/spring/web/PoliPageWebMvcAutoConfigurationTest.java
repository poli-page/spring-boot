package page.poli.sdk.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class PoliPageWebMvcAutoConfigurationTest {

  @Test
  void registersResponsesInServletStack() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PoliPageWebMvcAutoConfiguration.class))
        .run(context -> assertThat(context).hasSingleBean(PoliPageResponses.class));
  }

  @Test
  void doesNotRegisterInPlainContext() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PoliPageWebMvcAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(PoliPageResponses.class));
  }

  @Test
  void doesNotRegisterInReactiveStack() {
    new ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PoliPageWebMvcAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(PoliPageResponses.class));
  }
}
