package page.poli.example.webflux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;

/**
 * Surfaces SDK errors as their underlying HTTP status (e.g. 404 for a missing document) instead of
 * Spring's default 500. Sources every field from the SDK's canonical toPayload() so the wire shape
 * matches the other framework demos.
 *
 * <p>Reactor wraps async errors in {@link CompletionException}, so the handler unwraps one level
 * before checking the type.
 */
@RestControllerAdvice
public class PoliPageExceptionHandler {

  @ExceptionHandler(PoliPageException.class)
  public ResponseEntity<Map<String, Object>> handlePoliPage(PoliPageException ex) {
    return toResponse(ex);
  }

  @ExceptionHandler(CompletionException.class)
  public ResponseEntity<Map<String, Object>> handleWrapped(CompletionException ex) {
    Throwable cause = ex.getCause();
    if (cause instanceof PoliPageException ppe) {
      return toResponse(ppe);
    }
    throw ex;
  }

  private ResponseEntity<Map<String, Object>> toResponse(PoliPageException ex) {
    ErrorPayload payload = ex.toPayload();
    int status = payload.status() != null ? payload.status() : 500;
    Map<String, Object> body = new HashMap<>();
    body.put("code", payload.code());
    body.put("message", payload.message());
    body.put("status", status);
    body.put("requestId", payload.requestId());
    return ResponseEntity.status(status).body(body);
  }
}
