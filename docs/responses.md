# PDF and preview responses

> Return PDFs and HTML previews from your `@RestController` with `Content-Type`, RFC 5987-encoded `Content-Disposition`, `Cache-Control`, and `X-Content-Type-Options` headers all set correctly.

## Why

Returning a PDF from a Spring MVC controller is two lines if you let the framework set the wrong headers and four lines if you set them yourself. Set them wrong and browsers either render an unreadable octet stream, trigger a download with a mangled non-ASCII filename, or let an intermediary cache an invoice containing personalised data. `PoliPageResponses` is the stateless helper the starter ships so you write one line and the headers are right by construction.

The class lives in `page.poli.sdk.spring.web.PoliPageResponses` (autowireable as a bean once the MVC starter is on the classpath) and exposes four methods, mirroring the SDK's `Render` / `Documents` facades.

## How

Inject `PoliPageClient` and `PoliPageResponses`, call the SDK method, hand the result to the matching factory method. Spring serialises the returned `ResponseEntity` like any other.

### Returning bytes (small PDFs)

```java
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final PoliPageClient client;
    private final PoliPageResponses responses;

    public InvoiceController(PoliPageClient client, PoliPageResponses responses) {
        this.client = client;
        this.responses = responses;
    }

    @GetMapping(value = "/{id}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> invoice(@PathVariable String id) {
        byte[] pdf = client.render().pdf(ProjectModeInput.builder()
            .project("billing")
            .template("invoice")
            .version("1.0.0")
            .data(Map.of("invoice_id", id))
            .build());
        return responses.bytes(pdf, "invoice-" + id + ".pdf");
    }
}
```

Response headers (verified by the unit test in `PoliPageResponsesTest`):

```
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Length: 12345
Content-Disposition: attachment; filename="invoice-123.pdf"
Cache-Control: private, no-store
X-Content-Type-Options: nosniff
```

### Inline display (open in the browser tab)

Pass `inline=true` to flip the disposition:

```java
return responses.bytes(pdf, "preview.pdf", true);
```

→ `Content-Disposition: inline; filename="preview.pdf"`. The browser renders the PDF in-tab rather than triggering a download.

### Streaming large PDFs

For multi-megabyte PDFs, use `pdfStream()` (the SDK's `InputStream` variant) and the `stream(...)` helper, which returns a `ResponseEntity<StreamingResponseBody>`:

```java
@GetMapping("/{id}/statement.pdf")
public ResponseEntity<StreamingResponseBody> statement(@PathVariable String id) {
    InputStream stream = client.render().pdfStream(ProjectModeInput.builder()
        .project("statements")
        .template("monthly")
        .version("2.3.0")
        .data(Map.of("account_id", id))
        .build());
    return responses.stream(stream, "statement-" + id + ".pdf");
}
```

The helper wraps the stream copy in a `try-with-resources`, so the SDK connection is released even if the client disconnects mid-download. `Content-Length` is omitted unless the upstream stream reports a known size (it usually doesn't for chunked transfers — fine; the response uses transfer-encoding chunked).

### HTML preview (no PDF)

`Render.preview(...)` returns the HTML body without storing a document. Wrap it for the browser:

```java
@GetMapping("/{id}/preview")
public ResponseEntity<String> preview(@PathVariable String id) {
    PreviewResult preview = client.render().preview(ProjectModeInput.builder()
        .project("billing")
        .template("invoice")
        .version("1.0.0")
        .data(Map.of("invoice_id", id))
        .build());
    return responses.preview(preview);
}
```

Headers:

```
Content-Type: text/html; charset=utf-8
Cache-Control: private, no-store
```

The same helper accepts `DocumentPreviewResult` from `client.documents().preview(documentId)` — preview a stored document by id.

### 302 redirect to the presigned URL

When you've stored a document with `Render.document(...)` (returning a `DocumentDescriptor` with a presigned PDF URL), redirect the user there instead of streaming through your app:

```java
@GetMapping("/{id}/download")
public ResponseEntity<Void> download(@PathVariable String id) {
    DocumentDescriptor doc = client.documents().get(id);
    return responses.documentRedirect(doc);
}
```

Response:

```
HTTP/1.1 302 Found
Location: https://documents.poli.page/abc/...?signature=...
Cache-Control: private, no-store
```

The presigned URL has its own expiry (15 minutes by default in the platform), but the redirect itself should never be cached — `Cache-Control: private, no-store` ensures intermediaries don't hand a stale URL to a different user.

## Non-ASCII filenames

`Content-Disposition` is the part developers most often get wrong. The helper uses Spring's `ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build()` which emits RFC 5987-compliant headers:

```java
return responses.bytes(pdf, "facture-été.pdf");
```

→

```
Content-Disposition: attachment; filename="facture-?.pdf"; filename*=UTF-8''facture-%C3%A9t%C3%A9.pdf
```

Browsers that understand RFC 5987 (every modern browser) use `filename*`; older clients fall back to the ASCII `filename`. You don't have to think about it.

## Gotchas

- **Don't manually set headers on top of the helper's output.** Calling `.headers(h -> h.setContentDisposition(...))` on the returned `ResponseEntity` overwrites the helper's encoding and can produce conflicting headers. If you need additional headers (e.g. `X-Request-Id`), add them on the response without touching the four the helper sets.
- **`stream(...)` requires an `InputStream` you OWN.** The helper closes the stream once the body has been written. Don't reuse the same `InputStream` across multiple responses, and don't call `stream.close()` yourself after calling the helper — it's already closed by the time control returns to your controller method.
- **`StreamingResponseBody` runs on a separate request worker.** Spring MVC dispatches the body lambda on a `TaskExecutor` so the servlet container thread is released. If your `SecurityContext`-aware code reads `SecurityContextHolder` inside the lambda, configure `DelegatingSecurityContextRunnable` propagation (Spring Security handles this automatically when `securityContextHolderStrategy=MODE_INHERITABLETHREADLOCAL`).
- **`PoliPageResponses` is the MVC variant only.** WebFlux apps use `PoliPageReactiveResponses` (different class, same shape). See [webflux.md](webflux.md).
- **Cache-Control is `private, no-store` by design.** PDFs from Poli Page typically contain personalised data — names, account numbers, totals. Caching them anywhere outside the user's own browser is a leak risk. If you genuinely need a public, cacheable PDF (a generic terms-of-service document, say), build the `ResponseEntity` by hand instead of going through the helper.

## Related

- [Streaming](streaming.md) — deep-dive into `StreamingResponseBody` semantics and connection ownership.
- [WebFlux](webflux.md) — the reactive equivalent (`Mono<ResponseEntity<byte[]>>`, `Flux<DataBuffer>`).
- [spec/spring-boot-starter-specification.md §8](spec/spring-boot-starter-specification.md#8-polipageresponses-contract-mvc) — full signature reference and header table.
