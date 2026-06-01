package page.poli.sdk.spring.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@link java.time.Duration} value falls within an inclusive range.
 *
 * <p>Spring Boot ships no built-in Duration range constraint, so the starter declares its own. The
 * range bounds are expressed in milliseconds for portability across {@code seconds}, {@code
 * minutes}, and {@code hours} units in {@code application.yml}.
 */
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DurationRangeValidator.class)
public @interface DurationRange {

  /** Inclusive lower bound, in milliseconds. Defaults to {@code 0} (zero is allowed). */
  long minMillis() default 0L;

  /** Inclusive upper bound, in milliseconds. Defaults to {@code Long.MAX_VALUE}. */
  long maxMillis() default Long.MAX_VALUE;

  String message() default "must be a Duration between {minMillis} ms and {maxMillis} ms";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
