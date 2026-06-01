package page.poli.sdk.spring.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** Backs {@link DurationRange} — null durations pass through (combine with {@code @NotNull}). */
public final class DurationRangeValidator implements ConstraintValidator<DurationRange, Duration> {

  private long minMillis;
  private long maxMillis;

  @Override
  public void initialize(DurationRange constraint) {
    this.minMillis = constraint.minMillis();
    this.maxMillis = constraint.maxMillis();
  }

  @Override
  public boolean isValid(@Nullable Duration value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    long millis = value.toMillis();
    return millis >= minMillis && millis <= maxMillis;
  }
}
