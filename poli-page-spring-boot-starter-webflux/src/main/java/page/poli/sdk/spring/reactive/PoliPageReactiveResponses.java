package page.poli.sdk.spring.reactive;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import page.poli.sdk.model.DocumentDescriptor;
import page.poli.sdk.model.DocumentPreviewResult;
import page.poli.sdk.model.PreviewResult;
import page.poli.sdk.spring.internal.ContentDispositionHeader;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive sibling of {@code PoliPageResponses} — bridges the SDK's {@code
 * java.util.concurrent.CompletableFuture}-returning async facade into Reactor types ({@link Mono},
 * {@link Flux}) so WebFlux controllers can compose them with the rest of their reactive pipeline.
 *
 * <p>The {@link #stream} family adapts an SDK {@link InputStream} future into a {@code Flux} of
 * {@link DataBuffer}s using {@link DataBufferUtils#readInputStream(java.util.concurrent.Callable,
 * DataBufferFactory, int)}. The injected {@link DataBufferFactory} is the one Spring WebFlux makes
 * available as a bean (Netty / Reactor-Netty / Tomcat-reactive each provide their own).
 *
 * <p>Cancellation: {@code Mono.fromFuture(future, false)} propagates a {@code Mono} cancellation
 * back to the underlying {@link CompletableFuture} via {@code cancel(true)}, releasing the SDK's
 * HTTP connection.
 */
public final class PoliPageReactiveResponses {

  private static final MediaType APPLICATION_PDF = MediaType.parseMediaType("application/pdf");
  private static final String CACHE_CONTROL = "private, no-store";
  private static final int STREAM_BUFFER_SIZE = 8 * 1024;

  private final DataBufferFactory bufferFactory;

  /**
   * @param bufferFactory the reactive runtime's DataBufferFactory bean — non-null
   */
  public PoliPageReactiveResponses(DataBufferFactory bufferFactory) {
    this.bufferFactory = bufferFactory;
  }

  public Mono<ResponseEntity<byte[]>> bytes(CompletableFuture<byte[]> future, String filename) {
    return bytes(future, filename, false);
  }

  public Mono<ResponseEntity<byte[]>> bytes(
      CompletableFuture<byte[]> future, String filename, boolean inline) {
    return Mono.fromFuture(future, false)
        .map(
            pdf -> {
              HttpHeaders headers = pdfHeaders(filename, inline);
              headers.setContentLength(pdf.length);
              return ResponseEntity.ok().headers(headers).body(pdf);
            });
  }

  public Mono<ResponseEntity<Flux<DataBuffer>>> stream(
      CompletableFuture<InputStream> future, String filename) {
    return stream(future, filename, false);
  }

  public Mono<ResponseEntity<Flux<DataBuffer>>> stream(
      CompletableFuture<InputStream> future, String filename, boolean inline) {
    return Mono.fromFuture(future, false)
        .map(
            stream -> {
              HttpHeaders headers = pdfHeaders(filename, inline);
              Flux<DataBuffer> body =
                  DataBufferUtils.readInputStream(() -> stream, bufferFactory, STREAM_BUFFER_SIZE);
              return ResponseEntity.ok().headers(headers).body(body);
            });
  }

  /**
   * Wrap an asynchronous {@link PreviewResult} (from {@code client.renderAsync().preview(...)}) in
   * a Mono of HTML response.
   */
  public Mono<ResponseEntity<String>> previewFromRender(CompletableFuture<PreviewResult> future) {
    return Mono.fromFuture(future, false).map(p -> htmlResponse(p.html()));
  }

  /**
   * Wrap an asynchronous {@link DocumentPreviewResult} (from {@code
   * client.documentsAsync().preview(id)}) in a Mono of HTML response.
   *
   * <p>(Two method names rather than overloaded {@code preview(...)} because Java type erasure
   * collapses {@code CompletableFuture<PreviewResult>} and {@code
   * CompletableFuture<DocumentPreviewResult>} to the same signature at compile time.)
   */
  public Mono<ResponseEntity<String>> previewFromDocument(
      CompletableFuture<DocumentPreviewResult> future) {
    return Mono.fromFuture(future, false).map(p -> htmlResponse(p.html()));
  }

  public Mono<ResponseEntity<Void>> documentRedirect(CompletableFuture<DocumentDescriptor> future) {
    return Mono.fromFuture(future, false)
        .map(
            descriptor -> {
              HttpHeaders headers = new HttpHeaders();
              headers.setLocation(URI.create(descriptor.presignedPdfUrl()));
              headers.setCacheControl(CACHE_CONTROL);
              return ResponseEntity.<Void>status(HttpStatus.FOUND).headers(headers).build();
            });
  }

  // -- internals -----------------------------------------------------

  private static HttpHeaders pdfHeaders(String filename, boolean inline) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_PDF);
    headers.set(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionHeader.build(filename, inline));
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
