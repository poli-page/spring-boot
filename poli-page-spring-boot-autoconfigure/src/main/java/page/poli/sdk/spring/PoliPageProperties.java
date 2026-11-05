package page.poli.sdk.spring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
 * @param apiKey the API key — required and not blank. A key has the form {@code
 *     pp_{environment}_{32 lowercase hex characters}} ({@code pp_sandbox_…}, {@code pp_live_…},
 *     {@code pp_staging_…}); any other value logs one warning at startup, which shows no part of
 *     the key, and is passed to the API, which decides whether it is valid
 * @param baseUrl optional base URL of the API; defaults to {@code https://api.poli.page}
 * @param requestTimeout optional per-attempt request timeout; defaults to 60 s
 * @param retries retry-policy nested record
 * @param health Actuator HealthIndicator toggle
 * @param metrics Micrometer MeterBinder toggle
 * @param events ApplicationEvent bridge toggle
 * @param errorHandler error-handler toggle
 */
@ConfigurationProperties("poli-page")
@Validated
public record PoliPageProperties(
    @NotBlank String apiKey,
    @Nullable URI baseUrl,
    @Nullable @DurationRange(minMillis = 1_000L, maxMillis = 600_000L) Duration requestTimeout,
    @Valid @DefaultValue Retries retries,
    @Valid @DefaultValue Health health,
    @Valid @DefaultValue Metrics metrics,
    @Valid @DefaultValue Events events,
    @Valid @DefaultValue ErrorHandler errorHandler) {

  /**
   * Retry-policy options.
   *
   * @param maxRetries maximum retries on top of the initial request, so a call makes at most {@code
   *     maxRetries + 1} attempts — {@code 0} disables retries
   * @param maxAttempts deprecated former name of {@code maxRetries}, with the same meaning (it
   *     never counted attempts); it fills {@code maxRetries} and logs a warning
   * @param delay base delay for the SDK's exponential backoff
   */
  public record Retries(
      @Nullable @Min(0) @Max(10) Integer maxRetries,
      @Nullable Integer maxAttempts,
      @Nullable @DurationRange(minMillis = 0L, maxMillis = 30_000L) Duration delay) {

    /** Without the deprecated {@code maxAttempts}; the shape this record had before it. */
    public Retries(@Nullable Integer maxRetries, @Nullable Duration delay) {
      this(maxRetries, null, delay);
    }

    private static final Logger LOG = LoggerFactory.getLogger(Retries.class);

    /**
     * Resolves the deprecated {@code maxAttempts} into {@code maxRetries}.
     *
     * @throws IllegalArgumentException when both are set to different values
     */
    @ConstructorBinding
    public Retries {
      if (maxAttempts != null) {
        if (maxRetries != null && !maxRetries.equals(maxAttempts)) {
          throw new IllegalArgumentException(
              "poli-page.retries.max-retries ("
                  + maxRetries
                  + ") and its deprecated alias poli-page.retries.max-attempts ("
                  + maxAttempts
                  + ") are both set, to different values. Keep poli-page.retries.max-retries only.");
        }
        LOG.warn(
            "poli-page.retries.max-attempts is deprecated: it is the number of retries after the"
                + " first attempt, not of attempts. Rename it to poli-page.retries.max-retries"
                + " (same value).");
        maxRetries = maxAttempts;
      }
    }
  }

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

  /**
   * Error-handler toggle. Defaults to {@code true}.
   *
   * @param enabled set to {@code false} to skip registering the {@code PoliPageExceptionHandler}
   *     that answers an SDK error with its {@code toPayload()} body
   */
  public record ErrorHandler(@DefaultValue("true") boolean enabled) {}
}
