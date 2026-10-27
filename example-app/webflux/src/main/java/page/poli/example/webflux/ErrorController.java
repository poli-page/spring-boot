package page.poli.example.webflux;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.RenderInput;
import reactor.core.publisher.Mono;

/**
 * Demo step 9 — renders the {@code getting-started} project's {@code does-not-exist} template so
 * the API answers 404 {@code TEMPLATE_NOT_FOUND}, reactive variant. {@link
 * PoliPageExceptionHandler} turns the SDK's typed exception into JSON ({@code code}, {@code
 * status}, {@code message}, {@code requestId}).
 */
@RestController
@RequestMapping("/errors")
public class ErrorController {

  private final PoliPageClient client;

  public ErrorController(PoliPageClient client) {
    this.client = client;
  }

  @GetMapping("/not-found")
  public Mono<Map<String, Object>> notFound() {
    return Mono.fromFuture(
            client
                .renderAsync()
                .pdf(
                    RenderInput.builder()
                        .project("getting-started")
                        .template("does-not-exist")
                        .data(Map.of())
                        .build()))
        .map(pdf -> Map.of("unexpected", "expected a 404 but the call succeeded"));
  }
}
