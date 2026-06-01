package page.poli.sdk.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import page.poli.sdk.PoliPageClient;

class PoliPageAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PoliPageAutoConfiguration.class));

  @Test
  void registersClientBeanWithMinimalProperties() {
    runner
        .withPropertyValues("poli-page.api-key=pp_test_validkey")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(PoliPageClient.class);
            });
  }

  @Test
  void userDefinedClientBeanWins() {
    runner
        .withPropertyValues("poli-page.api-key=pp_test_x")
        .withUserConfiguration(UserClientConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PoliPageClient.class);
              PoliPageClient client = context.getBean(PoliPageClient.class);
              assertThat(client).isSameAs(UserClientConfig.STUB);
            });
  }

  @Test
  void doesNotFireWithoutApiKey() {
    runner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void explicitPropertiesReachTheBuilder() {
    runner
        .withPropertyValues(
            "poli-page.api-key=pp_test_x",
            "poli-page.base-url=https://api-develop.poli.page",
            "poli-page.request-timeout=15s",
            "poli-page.retries.max-attempts=4",
            "poli-page.retries.delay=750ms")
        .run(
            context -> {
              // Builder doesn't expose its values publicly; the assertion that matters here is
              // that the context boots cleanly with all properties set. Round-trip behaviour
              // is covered by the SDK's own builder tests.
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(PoliPageClient.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class UserClientConfig {
    static final PoliPageClient STUB =
        PoliPageClient.builder().apiKey("pp_test_userdefined").build();

    @Bean
    PoliPageClient userPoliPageClient() {
      return STUB;
    }
  }
}
