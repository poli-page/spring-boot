package page.poli.example.webflux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.ProjectModeInput;
import page.poli.sdk.model.DocumentDescriptor;
import page.poli.sdk.model.Thumbnail;
import page.poli.sdk.model.ThumbnailFormat;
import page.poli.sdk.model.ThumbnailOptions;
import page.poli.sdk.spring.reactive.PoliPageReactiveResponses;
import reactor.core.publisher.Mono;

/** Demo steps 5–9 — reactive document lifecycle. */
@RestController
@RequestMapping("/documents")
public class DocumentController {

  private final PoliPageClient client;
  private final PoliPageReactiveResponses responses;

  public DocumentController(PoliPageClient client, PoliPageReactiveResponses responses) {
    this.client = client;
    this.responses = responses;
  }

  @PostMapping
  public Mono<DocumentDescriptor> create() {
    return Mono.fromFuture(
        client
            .renderAsync()
            .document(
                ProjectModeInput.builder()
                    .project("getting-started")
                    .template("welcome")
                    .version("1.0.0")
                    .data(Map.of("name", "Document lifecycle demo (reactive)"))
                    .build()));
  }

  @GetMapping("/{id}")
  public Mono<ResponseEntity<Void>> redirect(@PathVariable String id) {
    CompletableFuture<DocumentDescriptor> f = client.documentsAsync().get(id);
    return responses.documentRedirect(f);
  }

  @GetMapping("/{id}/raw")
  public Mono<DocumentDescriptor> raw(@PathVariable String id) {
    return Mono.fromFuture(client.documentsAsync().get(id));
  }

  @GetMapping("/{id}/thumbnails")
  public Mono<List<Thumbnail>> thumbnails(@PathVariable String id) {
    return Mono.fromFuture(
        client
            .documentsAsync()
            .thumbnails(
                id, ThumbnailOptions.builder().width(240).format(ThumbnailFormat.PNG).build()));
  }

  @GetMapping("/{id}/preview")
  public Mono<ResponseEntity<String>> preview(@PathVariable String id) {
    return responses.previewFromDocument(client.documentsAsync().preview(id));
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
    return Mono.fromFuture(client.documentsAsync().delete(id))
        .thenReturn(ResponseEntity.<Void>noContent().build());
  }
}
