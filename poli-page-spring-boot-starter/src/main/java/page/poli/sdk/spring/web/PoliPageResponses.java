package page.poli.sdk.spring.web;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import page.poli.sdk.model.DocumentDescriptor;
import page.poli.sdk.model.DocumentPreviewResult;
import page.poli.sdk.model.PreviewResult;

/**
 * Stateless helper that adapts SDK return values into Spring MVC {@link ResponseEntity} objects
 * carrying the right PDF / HTML headers.
 *
 * <p>Methods set {@code Content-Type}, RFC 5987 {@code Content-Disposition} (so non-ASCII filenames
 * survive), {@code Cache-Control: private, no-store} (PDFs typically contain personalised data —
 * never let intermediaries cache them), and {@code X-Content-Type-Options: nosniff}.
 *
 * <p>Instances are immutable and stateless — one is registered as a bean by {@code
 * PoliPageWebMvcAutoConfiguration}; consumers may inject and reuse it freely.
 */
public final class PoliPageResponses {

  private static final MediaType APPLICATION_PDF = MediaType.parseMediaType("application/pdf");
  private static final String CACHE_CONTROL = "private, no-store";

  /**
   * Wrap an in-memory PDF byte array in a {@link ResponseEntity} as an {@code attachment}.
   *
   * @param pdf the rendered PDF bytes — non-null
   * @param filename suggested filename for the download dialog — non-null
   * @return a fully-headered response
   */
  public ResponseEntity<byte[]> bytes(byte[] pdf, String filename) {
    return bytes(pdf, filename, false);
  }

  /**
   * Wrap an in-memory PDF byte array in a {@link ResponseEntity}.
   *
   * @param pdf the rendered PDF bytes — non-null
   * @param filename suggested filename — non-null
   * @param inline if {@code true}, emit {@code Content-Disposition: inline} so the PDF renders in
   *     the browser tab instead of triggering a download
   * @return a fully-headered response
   */
  public ResponseEntity<byte[]> bytes(byte[] pdf, String filename, boolean inline) {
    HttpHeaders headers = pdfHeaders(filename, inline);
    headers.setContentLength(pdf.length);
    return ResponseEntity.ok().headers(headers).body(pdf);
  }

  /**
   * Wrap a streaming PDF body in a {@link ResponseEntity}. The underlying {@link InputStream} is
   * closed by the helper after the body is written (or on client disconnect) — callers must not
   * close it manually.
   *
   * @param stream the SDK-provided PDF stream — non-null, will be owned and closed by the helper
   * @param filename suggested filename — non-null
   * @return a streamed response
   */
  public ResponseEntity<StreamingResponseBody> stream(InputStream stream, String filename) {
    return stream(stream, filename, false);
  }

  /**
   * Wrap a streaming PDF body in a {@link ResponseEntity}.
   *
   * @param stream the SDK-provided PDF stream — non-null, will be owned and closed by the helper
   * @param filename suggested filename — non-null
   * @param inline if {@code true}, emit {@code Content-Disposition: inline}
   * @return a streamed response
   */
  public ResponseEntity<StreamingResponseBody> stream(
      InputStream stream, String filename, boolean inline) {
    HttpHeaders headers = pdfHeaders(filename, inline);
    StreamingResponseBody body =
        output -> {
          try (InputStream owned = stream) {
            owned.transferTo(output);
          }
        };
    return ResponseEntity.ok().headers(headers).body(body);
  }

  /**
   * Wrap a {@link PreviewResult} HTML body in a {@link ResponseEntity}.
   *
   * @param preview the SDK-rendered preview — non-null
   * @return a response with {@code Content-Type: text/html; charset=utf-8}
   */
  public ResponseEntity<String> preview(PreviewResult preview) {
    return htmlResponse(preview.html());
  }

  /**
   * Wrap a {@link DocumentPreviewResult} HTML body in a {@link ResponseEntity}.
   *
   * @param preview the SDK-fetched stored-document preview — non-null
   * @return a response with {@code Content-Type: text/html; charset=utf-8}
   */
  public ResponseEntity<String> preview(DocumentPreviewResult preview) {
    return htmlResponse(preview.html());
  }

  /**
   * Return an HTTP 302 redirect to the descriptor's {@code presignedPdfUrl}. The presigned URL has
   * its own expiry, but the redirect itself is never cacheable.
   *
   * @param descriptor the SDK-returned stored document — non-null
   * @return a 302 with the presigned URL in the {@code Location} header
   */
  public ResponseEntity<Void> documentRedirect(DocumentDescriptor descriptor) {
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(descriptor.presignedPdfUrl()));
    headers.setCacheControl(CACHE_CONTROL);
    return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
  }

  // -- internals -----------------------------------------------------

  private static HttpHeaders pdfHeaders(String filename, boolean inline) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_PDF);
    ContentDisposition disposition =
        (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(filename, StandardCharsets.UTF_8)
            .build();
    headers.setContentDisposition(disposition);
    headers.setCacheControl(CACHE_CONTROL);
    headers.set("X-Content-Type-Options", "nosniff");
    return headers;
  }

  private static ResponseEntity<String> htmlResponse(String html) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
    headers.setCacheControl(CACHE_CONTROL);
    return ResponseEntity.ok().headers(headers).body(html);
  }
}
