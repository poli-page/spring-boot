package page.poli.sdk.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;
import static page.poli.sdk.spring.TestKeys.SECRET;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

class PoliPagePropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          // Drop systemEnvironment so POLI_PAGE_* vars set on the host (CI runners,
          // contributors' shells) can't bind to poli-page.* via relaxed binding and
          // leak into the assertions below.
          .withInitializer(
              ctx ->
                  ctx.getEnvironment()
                      .getPropertySources()
                      .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(TestConfig.class);

  @Test
  void bindsValidKey() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              PoliPageProperties props = context.getBean(PoliPageProperties.class);
              assertThat(props.apiKey()).isEqualTo(SANDBOX_KEY);
              assertThat(props.baseUrl()).isNull();
              assertThat(props.requestTimeout()).isNull();
              assertThat(props.retries().maxAttempts()).isNull();
              assertThat(props.retries().delay()).isNull();
              assertThat(props.health().enabled()).isTrue();
              assertThat(props.metrics().enabled()).isTrue();
              assertThat(props.events().enabled()).isTrue();
            });
  }

  // The API is the only authority on keys: binding accepts any non-blank value, malformed or
  // not (PoliPageAutoConfigurationTest covers the startup warning).
  @ParameterizedTest
  @ValueSource(strings = {"pp_live_" + SECRET, "pp_sandbox_x", "sk_live_token"})
  void bindsAnyNonBlankKey(String key) {
    runner
        .withPropertyValues("poli-page.api-key=" + key)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(PoliPageProperties.class).apiKey()).isEqualTo(key);
            });
  }

  @Test
  void bindsAllExplicitValues() {
    runner
        .withPropertyValues(
            "poli-page.api-key=" + SANDBOX_KEY,
            "poli-page.base-url=https://api-staging.poli.page",
            "poli-page.request-timeout=45s",
            "poli-page.retries.max-attempts=5",
            "poli-page.retries.delay=2s",
            "poli-page.health.enabled=false",
            "poli-page.metrics.enabled=false",
            "poli-page.events.enabled=false")
        .run(
            context -> {
              PoliPageProperties props = context.getBean(PoliPageProperties.class);
              assertThat(props.baseUrl()).isEqualTo(URI.create("https://api-staging.poli.page"));
              assertThat(props.requestTimeout()).isEqualTo(Duration.ofSeconds(45));
              assertThat(props.retries().maxAttempts()).isEqualTo(5);
              assertThat(props.retries().delay()).isEqualTo(Duration.ofSeconds(2));
              assertThat(props.health().enabled()).isFalse();
              assertThat(props.metrics().enabled()).isFalse();
              assertThat(props.events().enabled()).isFalse();
            });
  }

  @Test
  void rejectsMissingKey() {
    runner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("apiKey");
        });
  }

  @Test
  void rejectsBlankKey() {
    runner
        .withPropertyValues("poli-page.api-key=")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("apiKey");
            });
  }

  @Test
  void rejectsZeroTimeout() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, "poli-page.request-timeout=0s")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsTimeoutAboveCap() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, "poli-page.request-timeout=700s")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsNegativeMaxAttempts() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, "poli-page.retries.max-attempts=-1")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsMaxAttemptsAboveCap() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, "poli-page.retries.max-attempts=11")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsRetryDelayAboveCap() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, "poli-page.retries.delay=60s")
        .run(context -> assertThat(context).hasFailed());
  }

  @EnableConfigurationProperties(PoliPageProperties.class)
  static class TestConfig {}
}
