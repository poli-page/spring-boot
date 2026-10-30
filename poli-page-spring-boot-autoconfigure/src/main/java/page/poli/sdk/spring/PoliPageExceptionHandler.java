package page.poli.sdk.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;

/**
 * Turns a {@link PoliPageException} into an HTTP answer: the body is the SDK's {@link
 * PoliPageException#toPayload()}, written as JSON ({@code {"code", "message", "status",
 * "requestId"}}, with {@code null} for an unknown {@code status} or {@code requestId}), and the
 * HTTP status is the payload's {@code status} ({@code 503} for {@code network_error}, {@code 504}
 * for {@code timeout}), or {@code 500} when the payload has none.
 *
 * <p>This class is not an advice itself. The starters register a stack-specific
 * {@code @RestControllerAdvice} subclass ({@code PoliPageWebMvcExceptionHandler} in Spring MVC,
 * which also removes PDF headers already on the response and never appends JSON to a response
 * already sent; {@code PoliPageWebFluxExceptionHandler} in Spring WebFlux) unless {@code
 * poli-page.error-handler.enabled} is {@code false} or the application declares its own {@code
 * PoliPageExceptionHandler} bean. Such a replacement must itself be a {@code @ControllerAdvice},
 * for example a subclass of the stack's handler; any other bean of this type leaves SDK errors
 * unhandled.
 *
 * <p>The advice has the lowest precedence: Spring answers an exception with the first
 * {@code @ControllerAdvice}, in order, that has any matching handler, so an application advice that
 * handles {@code PoliPageException} or a supertype such as {@code Exception}, ordered or not,
 * normally answers instead. An application with such a catch-all keeps the SDK answer by handling
 * {@code PoliPageException} in an advice ordered before it and delegating to {@link
 * #handlePoliPageException(PoliPageException)} of the injected {@code PoliPageExceptionHandler}
 * bean: in MVC that bean is the MVC handler, whose override does the header clean-up too.
 *
 * <p>Each answered error is logged on this class's logger: at {@code ERROR} for a 5xx or an error
 * with no status, at {@code WARN} otherwise. The log line names the code, status, request id and
 * message, never the API key.
 */
public abstract class PoliPageExceptionHandler {

  /** The HTTP status for an error whose payload has no status. */
  static final int FALLBACK_STATUS = 500;

  private static final Logger log = LoggerFactory.getLogger(PoliPageExceptionHandler.class);

  /** For the stack-specific subclasses. */
  protected PoliPageExceptionHandler() {}

  /**
   * Answers the error's payload. Subclasses may first adapt the response (see the MVC handler).
   *
   * @param ex the SDK error
   * @return the payload's status (or {@code 500}) with the payload as a JSON body
   */
  public ResponseEntity<ErrorPayload> handlePoliPageException(PoliPageException ex) {
    ErrorPayload payload = ex.toPayload();
    Integer payloadStatus = payload.status();
    int status = payloadStatus != null ? payloadStatus : FALLBACK_STATUS;
    if (payloadStatus == null || status >= 500) {
      log.error(
          "Poli Page SDK error answered with HTTP {}: code={} status={} requestId={} message={}",
          status,
          payload.code(),
          payloadStatus,
          payload.requestId(),
          payload.message(),
          ex);
    } else {
      log.warn(
          "Poli Page SDK error answered with HTTP {}: code={} status={} requestId={} message={}",
          status,
          payload.code(),
          payloadStatus,
          payload.requestId(),
          payload.message());
    }
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(payload);
  }
}
