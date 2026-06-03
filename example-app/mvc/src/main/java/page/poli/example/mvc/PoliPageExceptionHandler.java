package page.poli.example.mvc;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;

/**
 * Surfaces SDK errors as their underlying HTTP status (e.g. 404 for a missing document) instead of
 * Spring's default 500. Sources every field from the SDK's canonical toPayload() so the wire shape
 * matches the other framework demos.
 */
@RestControllerAdvice
public class PoliPageExceptionHandler {

  @ExceptionHandler(PoliPageException.class)
  public ResponseEntity<Map<String, Object>> handle(PoliPageException ex) {
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
