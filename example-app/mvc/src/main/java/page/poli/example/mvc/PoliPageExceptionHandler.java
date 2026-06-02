package page.poli.example.mvc;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import page.poli.sdk.exception.PoliPageException;

/**
 * Surfaces SDK errors as their underlying HTTP status (e.g. 404 for a missing document) instead of
 * Spring's default 500. Mirrors the global error mapping shipped by the Next.js / NestJS / FastAPI
 * demos so the demo dashboard sees the real 4xx body.
 */
@RestControllerAdvice
public class PoliPageExceptionHandler {

  @ExceptionHandler(PoliPageException.class)
  public ResponseEntity<Map<String, Object>> handle(PoliPageException ex) {
    Map<String, Object> body = new HashMap<>();
    body.put("type", ex.getClass().getSimpleName());
    body.put("code", ex.code());
    body.put("status", ex.statusCode());
    body.put("requestId", ex.requestId());
    body.put("message", ex.getMessage());
    int status =
        ex.statusCode() >= 400 ? ex.statusCode() : HttpStatus.INTERNAL_SERVER_ERROR.value();
    return ResponseEntity.status(status).body(body);
  }
}
