package page.poli.example.mvc;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.input.ProjectModeInput;

/**
 * Demo step 10 — deliberately trigger a 400 INVALID_VERSION_FORMAT and return the SDK's typed
 * exception as JSON, so a reader can see what the error surface looks like.
 */
@RestController
@RequestMapping("/errors")
public class ErrorController {

  private final PoliPageClient client;

  public ErrorController(PoliPageClient client) {
    this.client = client;
  }

  @GetMapping("/bad-version")
  public ResponseEntity<Map<String, Object>> badVersion() {
    try {
      client
          .render()
          .pdf(
              ProjectModeInput.builder()
                  .project("getting-started")
                  .template("welcome")
                  .version("not-a-semver")
                  .data(Map.of("name", "x"))
                  .build());
      return ResponseEntity.ok(Map.of("unexpected", "expected a 400 but the call succeeded"));
    } catch (PoliPageException ex) {
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
}
