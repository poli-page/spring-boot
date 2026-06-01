package page.poli.example.mvc;

import java.util.List;
import java.util.Map;
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
import page.poli.sdk.model.DocumentPreviewResult;
import page.poli.sdk.model.Thumbnail;
import page.poli.sdk.model.ThumbnailFormat;
import page.poli.sdk.model.ThumbnailOptions;
import page.poli.sdk.spring.web.PoliPageResponses;

/** Demo steps 5, 6, 7, 8, 9 — store, get, thumbnails, preview, delete. */
@RestController
@RequestMapping("/documents")
public class DocumentController {

  private final PoliPageClient client;
  private final PoliPageResponses responses;

  public DocumentController(PoliPageClient client, PoliPageResponses responses) {
    this.client = client;
    this.responses = responses;
  }

  @PostMapping
  public DocumentDescriptor create() {
    return client
        .render()
        .document(
            ProjectModeInput.builder()
                .project("getting-started")
                .template("welcome")
                .version("1.0.0")
                .data(Map.of("name", "Document lifecycle demo"))
                .build());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Void> getRedirect(@PathVariable String id) {
    DocumentDescriptor doc = client.documents().get(id);
    return responses.documentRedirect(doc);
  }

  @GetMapping("/{id}/raw")
  public DocumentDescriptor getRaw(@PathVariable String id) {
    return client.documents().get(id);
  }

  @GetMapping("/{id}/thumbnails")
  public List<Thumbnail> thumbnails(@PathVariable String id) {
    return client
        .documents()
        .thumbnails(id, ThumbnailOptions.builder().width(240).format(ThumbnailFormat.PNG).build());
  }

  @GetMapping("/{id}/preview")
  public ResponseEntity<String> preview(@PathVariable String id) {
    DocumentPreviewResult preview = client.documents().preview(id);
    return responses.preview(preview);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    client.documents().delete(id);
    return ResponseEntity.noContent().build();
  }
}
