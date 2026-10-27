package page.poli.sdk.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;
import static page.poli.sdk.spring.TestKeys.SECRET;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.spring.actuator.PoliPageHealthIndicator;
import page.poli.sdk.spring.metrics.PoliPageMetrics;

@ExtendWith(OutputCaptureExtension.class)
class PoliPageAutoConfigurationTest {

  private static final String ENV_20 = "a1234567890123456789";
  private static final String ENV_21 = ENV_20 + "0";
  private static final String NOT_A_KEY_WARNING =
      "does not look like pp_{environment}_{32 hex characters}; "
          + "starting anyway, the API decides whether it is valid.";

  private final Logger logger = (Logger) LoggerFactory.getLogger(PoliPageAutoConfiguration.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private @Nullable Level savedLevel;

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          // Drop systemEnvironment so POLI_PAGE_* vars set on the host (CI runners,
          // contributors' shells) can't bind to poli-page.* via relaxed binding and
          // leak into the assertions below — notably doesNotFireWithoutApiKey().
          .withInitializer(
              ctx ->
                  ctx.getEnvironment()
                      .getPropertySources()
                      .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
          .withConfiguration(AutoConfigurations.of(PoliPageAutoConfiguration.class));

  // At TRACE, so that a key logged at any level reaches the assertions.
  @BeforeEach
  void captureLogs() {
    savedLevel = logger.getLevel();
    logger.setLevel(Level.TRACE);
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(logs);
    logger.setLevel(savedLevel);
  }

  @Test
  void registersClientBeanWithMinimalProperties() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(PoliPageClient.class);
            });
  }

  @Test
  void userDefinedClientBeanWins() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY)
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

  // Without a Bean Validation provider @NotBlank is not enforced: the SDK refuses the key.
  @Test
  void blankKeyFailsWithoutWarningEvenWithoutAValidator() {
    runner
        .withClassLoader(new FilteredClassLoader("jakarta.validation", "org.hibernate.validator"))
        .withPropertyValues("poli-page.api-key=")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(warnings()).isEmpty();
              assertThat(eventsAboveWarn()).isEmpty();
            });
  }

  static Stream<String> wellFormedKeys() {
    return Stream.of(
        SANDBOX_KEY,
        "pp_live_" + SECRET,
        "pp_eu-prod_" + SECRET,
        "pp_" + ENV_20 + "_" + SECRET,
        "pp_a_" + SECRET,
        "pp_a1-b2-c3_" + SECRET);
  }

  @ParameterizedTest
  @MethodSource("wellFormedKeys")
  void wellFormedKeyStartsWithoutWarning(String key, CapturedOutput output) {
    startWithKey(key);

    assertThat(warnings()).isEmpty();
    assertThat(eventsAboveWarn()).isEmpty();
    assertKeyNeverShown(key, output);
  }

  // Each malformed key with the only part of it the warning may show (null: none of it).
  static Stream<Arguments> malformedKeys() {
    return Stream.of(
        Arguments.of("pp_sandbox_x", "pp_sandbox_"),
        Arguments.of("pp_eu-prod_x", "pp_eu-prod_"),
        // An {env} outside the environment-name grammar is not shown: it may be part of the secret.
        Arguments.of("pp_Sandbox_" + SECRET, null),
        Arguments.of("pp_2fa_" + SECRET, null),
        Arguments.of("pp_a--b_" + SECRET, null),
        Arguments.of("pp_eu-_" + SECRET, null),
        Arguments.of("pp_-eu_" + SECRET, null),
        Arguments.of("pp_eu_prod_" + SECRET, "pp_eu_"),
        // The shown prefix's 20/21 boundary, from both sides.
        Arguments.of("pp_" + ENV_20 + "_" + SECRET.toUpperCase(Locale.ROOT), "pp_" + ENV_20 + "_"),
        Arguments.of("pp_" + ENV_21 + "_" + SECRET, null),
        Arguments.of("pp_sandbox_" + SECRET.toUpperCase(Locale.ROOT), "pp_sandbox_"),
        Arguments.of("pp_sandbox_" + SECRET.substring(1) + "g", "pp_sandbox_"),
        Arguments.of("pp_sandbox_" + SECRET.substring(1), "pp_sandbox_"),
        Arguments.of(SANDBOX_KEY + "0", "pp_sandbox_"),
        Arguments.of(SANDBOX_KEY + "x", "pp_sandbox_"),
        Arguments.of(SANDBOX_KEY + "\n", "pp_sandbox_"),
        Arguments.of("x" + SANDBOX_KEY, null),
        Arguments.of(" " + SANDBOX_KEY, null),
        Arguments.of("pp__" + SECRET, null),
        Arguments.of("pp_" + SECRET, null),
        Arguments.of(SECRET, null),
        Arguments.of("sk_live_token", null));
  }

  @ParameterizedTest
  @MethodSource("malformedKeys")
  void malformedKeyStartsWithOneWarningThatHidesTheSecret(
      String key, @Nullable String shownPrefix, CapturedOutput output) {
    startWithKey(key);

    String expected =
        "Poli Page API key "
            + (shownPrefix == null ? "" : "\"" + shownPrefix + "…\" ")
            + NOT_A_KEY_WARNING;
    assertThat(warnings()).containsExactly(expected);
    assertThat(eventsAboveWarn()).isEmpty();
    assertKeyNeverShown(key, output);
  }

  @Test
  void registersEventBridgeByDefault() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PoliPageEventBridge.class);
            });
  }

  @Test
  void omitsEventBridgeWhenEventsDisabled() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, "poli-page.events.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(PoliPageEventBridge.class);
              // Client still wires correctly without the bridge.
              assertThat(context).hasSingleBean(PoliPageClient.class);
            });
  }

  @Test
  void registersHealthIndicatorByDefault() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY)
        .run(context -> assertThat(context).hasSingleBean(PoliPageHealthIndicator.class));
  }

  @Test
  void omitsHealthIndicatorWhenDisabled() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, "poli-page.health.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(PoliPageHealthIndicator.class));
  }

  @Test
  void registersMetricsByDefault() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY)
        .run(context -> assertThat(context).hasSingleBean(PoliPageMetrics.class));
  }

  @Test
  void omitsMetricsWhenDisabled() {
    runner
        .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY, "poli-page.metrics.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(PoliPageMetrics.class));
  }

  @Test
  void explicitPropertiesReachTheBuilder() {
    runner
        .withPropertyValues(
            "poli-page.api-key=" + SANDBOX_KEY,
            "poli-page.base-url=https://api.example.com",
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
    static final PoliPageClient STUB = PoliPageClient.builder().apiKey(SANDBOX_KEY).build();

    @Bean
    PoliPageClient userPoliPageClient() {
      return STUB;
    }
  }

  private void startWithKey(String key) {
    // Validation on, as in an application: only a missing or blank key fails startup.
    runner
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        // A property source rather than withPropertyValues, which trims the value.
        .withInitializer(
            ctx ->
                ctx.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("key", Map.of("poli-page.api-key", key))))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(PoliPageClient.class);
            });
  }

  private List<String> warnings() {
    return logs.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private List<ILoggingEvent> eventsAboveWarn() {
    return logs.list.stream()
        .filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
        .toList();
  }

  private void assertKeyNeverShown(String key, CapturedOutput output) {
    assertThat(output).doesNotContain(key.strip()).doesNotContain(SECRET);
    assertThat(logs.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(key.strip()) || message.contains(SECRET));
  }
}
