package page.poli.example.webflux;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.ProjectModeInput;
import page.poli.sdk.model.PreviewResult;
import page.poli.sdk.spring.reactive.PoliPageReactiveResponses;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Demo steps 1, 2, 4 — reactive variants of the MVC sibling. */
@RestController
@RequestMapping("/render")
public class RenderController {

  private final PoliPageClient client;
  private final PoliPageReactiveResponses responses;

  public RenderController(PoliPageClient client, PoliPageReactiveResponses responses) {
    this.client = client;
    this.responses = responses;
  }

  @GetMapping("/pdf")
  public Mono<ResponseEntity<byte[]>> pdf() {
    CompletableFuture<byte[]> f = client.renderAsync().pdf(welcomeInput());
    return responses.bytes(f, "welcome.pdf", true);
  }

  @GetMapping("/stream")
  public Mono<ResponseEntity<Flux<DataBuffer>>> stream() {
    CompletableFuture<InputStream> f = client.renderAsync().pdfStream(welcomeInput());
    return responses.stream(f, "welcome.pdf", true);
  }

  @GetMapping("/preview")
  public Mono<ResponseEntity<String>> preview() {
    CompletableFuture<PreviewResult> f = client.renderAsync().preview(welcomeInput());
    return responses.previewFromRender(f);
  }

  private static ProjectModeInput welcomeInput() {
    return ProjectModeInput.builder()
        .project("getting-started")
        .template("welcome")
        .version("1.0.0")
        .data(Map.of("name", "Spring Boot WebFlux Demo"))
        .build();
  }
}
