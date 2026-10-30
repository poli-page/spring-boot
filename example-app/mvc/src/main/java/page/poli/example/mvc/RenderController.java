package page.poli.example.mvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.RenderInput;
import page.poli.sdk.spring.web.PoliPageResponses;

/** Demo steps 1, 2, 3 — render PDF (bytes), streamed, and render-to-file. */
@RestController
@RequestMapping("/render")
public class RenderController {

  private static final Path OUTPUT_PATH = Path.of("example-app", "output", "welcome.pdf");

  private final PoliPageClient client;
  private final PoliPageResponses responses;

  public RenderController(PoliPageClient client, PoliPageResponses responses) {
    this.client = client;
    this.responses = responses;
  }

  @GetMapping("/pdf")
  public ResponseEntity<byte[]> pdf() {
    byte[] pdf = client.render().pdf(welcomeInput());
    return responses.bytes(pdf, "welcome.pdf", true);
  }

  @GetMapping("/stream")
  public ResponseEntity<StreamingResponseBody> stream() {
    InputStream s = client.render().pdfStream(welcomeInput());
    return responses.stream(s, "welcome.pdf", true);
  }

  /** Demo step 3 — {@code PoliPageClient.renderToFile} streams the PDF straight to disk. */
  @PostMapping("/file")
  public RenderFileResult file() throws IOException {
    Files.createDirectories(OUTPUT_PATH.getParent());
    client.renderToFile(
        RenderInput.builder()
            .project("getting-started")
            .template("welcome")
            .data(Map.of("name", "renderToFile demo"))
            .build(),
        OUTPUT_PATH);
    return new RenderFileResult(OUTPUT_PATH.toAbsolutePath().toString(), Files.size(OUTPUT_PATH));
  }

  /** Response payload for {@link #file()}. Serialized by Jackson as camelCase JSON. */
  public record RenderFileResult(String path, long sizeBytes) {}

  private static RenderInput welcomeInput() {
    return RenderInput.builder()
        .project("getting-started")
        .template("welcome")
        .data(Map.of("name", "Spring Boot Starter Demo"))
        .build();
  }
}
