package page.poli.sdk.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import page.poli.sdk.model.DocumentDescriptor;
import page.poli.sdk.model.DocumentPreviewResult;
import page.poli.sdk.model.PreviewResult;

class PoliPageResponsesTest {

  private final PoliPageResponses responses = new PoliPageResponses();

  @Test
  void bytesSetsHeadersForAsciiFilename() {
    byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.ISO_8859_1);

    ResponseEntity<byte[]> response = responses.bytes(pdf, "invoice.pdf");

    HttpHeaders headers = response.getHeaders();
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(headers.getContentType()).isEqualTo(MediaType.parseMediaType("application/pdf"));
    assertThat(headers.getContentLength()).isEqualTo(pdf.length);
    ContentDisposition disp = headers.getContentDisposition();
    assertThat(disp.isAttachment()).isTrue();
    assertThat(disp.getFilename()).isEqualTo("invoice.pdf");
    assertThat(headers.getCacheControl()).isEqualTo("private, no-store");
    assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getBody()).isEqualTo(pdf);
  }

  @Test
  void bytesEncodesNonAsciiFilenameViaRfc5987() {
    byte[] pdf = "data".getBytes(StandardCharsets.UTF_8);

    ResponseEntity<byte[]> response = responses.bytes(pdf, "facture-été.pdf");

    String raw = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(raw).contains("filename*=UTF-8''facture-%C3%A9t%C3%A9.pdf");
  }

  @Test
  void bytesInlineFlipsDisposition() {
    byte[] pdf = "data".getBytes(StandardCharsets.UTF_8);

    ResponseEntity<byte[]> response = responses.bytes(pdf, "preview.pdf", true);

    ContentDisposition disp = response.getHeaders().getContentDisposition();
    assertThat(disp.isInline()).isTrue();
    assertThat(disp.isAttachment()).isFalse();
  }

  @Test
  void streamSetsHeadersAndOwnsTheInputStream() throws IOException {
    CloseTrackingInputStream tracker =
        new CloseTrackingInputStream(
            new ByteArrayInputStream("%PDF-streamed".getBytes(StandardCharsets.ISO_8859_1)));

    ResponseEntity<StreamingResponseBody> response = responses.stream(tracker, "statement.pdf");

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/pdf"));
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    response.getBody().writeTo(out);

    assertThat(out.toString(StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-streamed");
    assertThat(tracker.closeCount).isEqualTo(1);
  }

  @Test
  void previewReturnsHtmlResponseFromPreviewResult() {
    PreviewResult preview = new PreviewResult("<h1>hi</h1>", 1, "sandbox");

    ResponseEntity<String> response = responses.preview(preview);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(new MediaType("text", "html", StandardCharsets.UTF_8));
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
    assertThat(response.getBody()).isEqualTo("<h1>hi</h1>");
  }

  @Test
  void previewReturnsHtmlResponseFromDocumentPreviewResult() {
    DocumentPreviewResult preview = new DocumentPreviewResult("<p>doc html</p>", 5);

    ResponseEntity<String> response = responses.preview(preview);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(new MediaType("text", "html", StandardCharsets.UTF_8));
    assertThat(response.getBody()).isEqualTo("<p>doc html</p>");
  }

  @Test
  void documentRedirectReturns302WithLocation() {
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

    ResponseEntity<Void> response = responses.documentRedirect(descriptor);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation())
        .hasToString("https://files.poli.page/doc_abc?sig=abc");
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
  }

  private static final class CloseTrackingInputStream extends InputStream {
    private final InputStream delegate;
    int closeCount;

    CloseTrackingInputStream(InputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
      return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return delegate.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
      closeCount++;
      delegate.close();
    }
  }
}
