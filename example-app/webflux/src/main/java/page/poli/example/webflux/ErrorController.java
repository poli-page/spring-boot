package page.poli.example.webflux;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.input.ProjectModeInput;
import reactor.core.publisher.Mono;

/** Demo step 10 — deliberate INVALID_VERSION_FORMAT trigger, reactive variant. */
@RestController
@RequestMapping("/errors")
public class ErrorController {

  private final PoliPageClient client;

  public ErrorController(PoliPageClient client) {
    this.client = client;
  }

  @GetMapping("/bad-version")
  public Mono<ResponseEntity<Map<String, Object>>> badVersion() {
    return Mono.fromFuture(
            client
                .renderAsync()
                .pdf(
                    ProjectModeInput.builder()
                        .project("getting-started")
                        .template("welcome")
                        .version("not-a-semver")
                        .data(Map.of("name", "x"))
                        .build()))
        .map(pdf -> ResponseEntity.ok(Map.<String, Object>of("unexpected", "succeeded")))
        .onErrorResume(
            ex -> {
              Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
              if (cause instanceof PoliPageException ppe) {
                Map<String, Object> body = new HashMap<>();
                body.put("type", ppe.getClass().getSimpleName());
                body.put("code", ppe.code());
                body.put("status", ppe.statusCode());
                body.put("requestId", ppe.requestId());
                body.put("message", ppe.getMessage());
                int status = ppe.statusCode() > 0 ? ppe.statusCode() : 500;
                return Mono.just(ResponseEntity.status(status).body(body));
              }
              return Mono.error(ex);
            });
  }
}
