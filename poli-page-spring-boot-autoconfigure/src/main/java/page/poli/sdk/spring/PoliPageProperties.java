package page.poli.sdk.spring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import page.poli.sdk.spring.validation.DurationRange;

/**
 * Configuration properties bound to {@code poli-page.*} keys in {@code application.yml} /
 * environment variables.
 *
 * <p>Every option except {@code api-key} is nullable. When omitted, the auto-configuration passes
 * {@code null} to the SDK's {@code PoliPageClient.Builder}, which then applies its own documented
 * defaults — so a future SDK release that bumps a default takes effect automatically without a
 * starter release.
 *
 * @param apiKey the API key — required, must start with {@code pp_test_} or {@code pp_live_}
 * @param baseUrl optional base URL of the API; defaults to {@code https://api.poli.page}
 * @param requestTimeout optional per-attempt request timeout; defaults to 60 s
 * @param retries retry-policy nested record
 * @param health Actuator HealthIndicator toggle
 * @param metrics Micrometer MeterBinder toggle
 * @param events ApplicationEvent bridge toggle
 */
@ConfigurationProperties("poli-page")
@Validated
public record PoliPageProperties(
    @NotBlank
        @Pattern(
            regexp = "^pp_(test|live)_.+$",
            message =
                "Poli Page API key must start with pp_test_ or pp_live_. "
                    + "Get one at https://poli.page/dashboard/keys.")
        String apiKey,
    @Nullable URI baseUrl,
    @Nullable @DurationRange(minMillis = 1_000L, maxMillis = 600_000L) Duration requestTimeout,
    @Valid @DefaultValue Retries retries,
    @Valid @DefaultValue Health health,
    @Valid @DefaultValue Metrics metrics,
    @Valid @DefaultValue Events events) {

  /**
   * Retry-policy options.
   *
   * @param maxAttempts maximum retries on top of the initial request — {@code 0} disables retries
   * @param delay base delay for the SDK's exponential backoff
   */
  public record Retries(
      @Nullable @Min(0) @Max(10) Integer maxAttempts,
      @Nullable @DurationRange(minMillis = 0L, maxMillis = 30_000L) Duration delay) {}

  /**
   * Actuator {@code HealthIndicator} toggle. Defaults to {@code true}.
   *
   * @param enabled set to {@code false} to skip registering the HealthIndicator
   */
  public record Health(@DefaultValue("true") boolean enabled) {}

  /**
   * Micrometer {@code MeterBinder} toggle. Defaults to {@code true}.
   *
   * @param enabled set to {@code false} to skip registering the metrics bean
   */
  public record Metrics(@DefaultValue("true") boolean enabled) {}

  /**
   * {@code ApplicationEvent} bridge toggle. Defaults to {@code true}.
   *
   * @param enabled set to {@code false} to skip publishing retry/error events
   */
  public record Events(@DefaultValue("true") boolean enabled) {}
}
