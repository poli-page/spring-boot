package page.poli.sdk.spring.reactive;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.spring.PoliPageExceptionHandler;

/**
 * The Spring WebFlux default error handler: answers a {@link PoliPageException} with its {@code
 * toPayload()} (see {@link PoliPageExceptionHandler}). WebFlux clears the content headers before it
 * runs an {@code @ExceptionHandler}, so PDF headers set before the error do not reach the answer.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class PoliPageWebFluxExceptionHandler extends PoliPageExceptionHandler {

  /** Creates the handler. */
  public PoliPageWebFluxExceptionHandler() {}

  /**
   * Answers the error's payload.
   *
   * @param ex the SDK error
   * @return the payload's status (or {@code 500}) with the payload as a JSON body
   */
  @Override
  @ExceptionHandler(PoliPageException.class)
  public ResponseEntity<ErrorPayload> handlePoliPageException(PoliPageException ex) {
    return super.handlePoliPageException(ex);
  }
}
