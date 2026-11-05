package page.poli.sdk.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

// The starter's test classpath is an app's: the starter and Spring Boot, with no Bean Validation
// dependency of its own. The documented startup validation of poli-page.* must still run.
class PoliPagePropertiesValidationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(
              ctx ->
                  ctx.getEnvironment()
                      .getPropertySources()
                      .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
          .withConfiguration(AutoConfigurations.of(PoliPageAutoConfiguration.class));

  @ParameterizedTest
  @ValueSource(
      strings = {
        "poli-page.api-key=",
        "poli-page.request-timeout=700s",
        "poli-page.retries.max-attempts=11",
        "poli-page.retries.delay=31s"
      })
  void rejectsOutOfRangeOptionsAtStartup(String property) {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, property)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(BindValidationException.class));
  }
}
