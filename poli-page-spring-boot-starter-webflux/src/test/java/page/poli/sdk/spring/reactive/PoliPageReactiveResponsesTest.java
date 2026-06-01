package page.poli.sdk.spring.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import page.poli.sdk.model.DocumentDescriptor;
import page.poli.sdk.model.DocumentPreviewResult;
import page.poli.sdk.model.PreviewResult;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PoliPageReactiveResponsesTest {

  private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
  private final PoliPageReactiveResponses responses = new PoliPageReactiveResponses(bufferFactory);

  @Test
  void bytesEmitsResponseEntityWithPdfHeaders() {
    byte[] pdf = "%PDF-".getBytes(StandardCharsets.ISO_8859_1);
    CompletableFuture<byte[]> future = CompletableFuture.completedFuture(pdf);

    StepVerifier.create(responses.bytes(future, "invoice.pdf"))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode().value()).isEqualTo(200);
              assertThat(response.getHeaders().getContentType())
                  .isEqualTo(MediaType.parseMediaType("application/pdf"));
              assertThat(response.getHeaders().getContentLength()).isEqualTo(pdf.length);
              assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
              assertThat(response.getBody()).isEqualTo(pdf);
            })
        .verifyComplete();
  }

  @Test
  void bytesInlineFlipsDisposition() {
    byte[] pdf = "data".getBytes(StandardCharsets.UTF_8);

    StepVerifier.create(responses.bytes(CompletableFuture.completedFuture(pdf), "x.pdf", true))
        .assertNext(
            response ->
                assertThat(response.getHeaders().getContentDisposition().isInline()).isTrue())
        .verifyComplete();
  }

  @Test
  void streamEmitsFluxDataBufferBody() {
    byte[] payload = "%PDF-streamed".getBytes(StandardCharsets.ISO_8859_1);
    CompletableFuture<java.io.InputStream> future =
        CompletableFuture.completedFuture(new ByteArrayInputStream(payload));

    ResponseEntity<Flux<DataBuffer>> response = responses.stream(future, "doc.pdf").block();

    assertThat(response).isNotNull();
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/pdf"));
    StringBuilder collected = new StringBuilder();
    response
        .getBody()
        .doOnNext(
            buffer -> {
              byte[] tmp = new byte[buffer.readableByteCount()];
              buffer.read(tmp);
              collected.append(new String(tmp, StandardCharsets.ISO_8859_1));
              org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
            })
        .blockLast();
    assertThat(collected.toString()).isEqualTo("%PDF-streamed");
  }

  @Test
  void previewFromRenderReturnsHtmlResponse() {
    PreviewResult preview = new PreviewResult("<h1>rendered</h1>", 1, "sandbox");

    StepVerifier.create(responses.previewFromRender(CompletableFuture.completedFuture(preview)))
        .assertNext(
            response -> {
              assertThat(response.getHeaders().getContentType())
                  .isEqualTo(new MediaType("text", "html", StandardCharsets.UTF_8));
              assertThat(response.getBody()).isEqualTo("<h1>rendered</h1>");
            })
        .verifyComplete();
  }

  @Test
  void previewFromDocumentReturnsHtmlResponse() {
    DocumentPreviewResult preview = new DocumentPreviewResult("<p>stored</p>", 3);

    StepVerifier.create(responses.previewFromDocument(CompletableFuture.completedFuture(preview)))
        .assertNext(response -> assertThat(response.getBody()).isEqualTo("<p>stored</p>"))
        .verifyComplete();
  }

  @Test
  void documentRedirectEmits302() {
    DocumentDescriptor descriptor =
        new DocumentDescriptor(
            "doc_abc",
            "org_x",
            null,
            null,
            null,
            null,
            null,
            "sandbox",
            null,
            "pdf",
            null,
            null,
            1,
            123L,
            "2026-06-01T00:00:00Z",
            Map.of(),
            "https://files.poli.page/doc_abc?sig=abc",
            "2026-06-01T00:15:00Z");

    StepVerifier.create(responses.documentRedirect(CompletableFuture.completedFuture(descriptor)))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
              assertThat(response.getHeaders().getLocation())
                  .hasToString("https://files.poli.page/doc_abc?sig=abc");
              assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
            })
        .verifyComplete();
  }
}
