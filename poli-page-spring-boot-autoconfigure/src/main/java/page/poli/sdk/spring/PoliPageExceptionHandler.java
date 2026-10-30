package page.poli.sdk.spring;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;

/**
 * Turns a {@link PoliPageException} thrown by a controller into an HTTP answer, in Spring MVC and
 * in Spring WebFlux.
 *
 * <p>The body is the SDK's {@link PoliPageException#toPayload()}, written as JSON: {@code {"code",
 * "message", "status", "requestId"}}, with {@code null} for an unknown {@code status} or {@code
 * requestId}. The HTTP status is the payload's {@code status} ({@code 503} for {@code
 * network_error}, {@code 504} for {@code timeout}), or {@code 500} when the payload has none.
 *
 * <p>Registered by the MVC and WebFlux starters unless {@code poli-page.error-handler.enabled} is
 * {@code false} or the application declares its own {@code PoliPageExceptionHandler} bean. It has
 * the lowest precedence, so an application's own {@code @ExceptionHandler} for {@code
 * PoliPageException} in an ordered {@code @ControllerAdvice} wins.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class PoliPageExceptionHandler {

  /** The HTTP status for an error whose payload has no status. */
  static final int FALLBACK_STATUS = 500;

  /**
   * Answers the error's payload.
   *
   * @param ex the SDK error
   * @return the payload's status (or {@code 500}) with the payload as a JSON body
   */
  @ExceptionHandler(PoliPageException.class)
  public ResponseEntity<ErrorPayload> handlePoliPageException(PoliPageException ex) {
    ErrorPayload payload = ex.toPayload();
    Integer status = payload.status();
    return ResponseEntity.status(status != null ? status : FALLBACK_STATUS)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload);
  }
}
