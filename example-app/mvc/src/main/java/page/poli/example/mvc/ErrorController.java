package page.poli.example.mvc;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.RenderInput;

/**
 * Demo step 9 — renders the {@code getting-started} project's {@code does-not-exist} template so
 * the API answers 404 {@code TEMPLATE_NOT_FOUND}. There is no try/catch here: the starter's default
 * {@link page.poli.sdk.spring.PoliPageExceptionHandler} answers the error's {@code toPayload()}
 * ({@code code}, {@code message}, {@code status}, {@code requestId}) with status 404.
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
