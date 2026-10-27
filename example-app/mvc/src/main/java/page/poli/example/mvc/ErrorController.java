package page.poli.example.mvc;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.RenderInput;

/**
 * Demo step 9 — renders the {@code getting-started} project's {@code does-not-exist} template so
 * the API answers 404 {@code TEMPLATE_NOT_FOUND}. {@link PoliPageExceptionHandler} turns the SDK's
 * typed exception into JSON ({@code code}, {@code status}, {@code message}, {@code requestId}), so
 * a reader can see what the error surface looks like.
 */
@RestController
@RequestMapping("/errors")
public class ErrorController {

  private final PoliPageClient client;

  public ErrorController(PoliPageClient client) {
    this.client = client;
  }

  @GetMapping("/not-found")
  public Map<String, Object> notFound() {
    client
        .render()
        .pdf(
            RenderInput.builder()
                .project("getting-started")
                .template("does-not-exist")
                .data(Map.of())
                .build());
    return Map.of("unexpected", "expected a 404 but the call succeeded");
  }
}
