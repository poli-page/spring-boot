# Example apps

Two runnable Spring Boot apps that demonstrate every Poli Page SDK method through the starter:

| App | Stack | Default port | Run |
|---|---|---|---|
| [`mvc/`](mvc/) | Spring MVC (servlet) | `8080` | `./gradlew :example-app:mvc:bootRun` |
| [`webflux/`](webflux/) | Spring WebFlux (reactive) | `8081` | `./gradlew :example-app:webflux:bootRun` |

Both expose the same surface — the only difference is whether controllers return `ResponseEntity<…>` (MVC) or `Mono<ResponseEntity<…>>` / `Flux<DataBuffer>` (WebFlux). Read them side-by-side to see how the two stacks line up.

## Prerequisites

- Java 17+
- A Poli Page test API key (`pp_test_…`) — get one at [poli.page/dashboard/keys](https://poli.page/dashboard/keys)
- The local SDK installed via Maven (until `page.poli:sdk` publishes to Central):

```bash
cd ../sdk-java && ./mvnw install -DskipTests
```

## Running

```bash
export POLI_PAGE_API_KEY=pp_test_…
./gradlew :example-app:mvc:bootRun         # MVC, http://localhost:8080
./gradlew :example-app:webflux:bootRun     # WebFlux, http://localhost:8081
```

Open the URL in a browser. You'll get an interactive dashboard with one button per SDK feature:

1. **Render PDF (bytes)** — `GET /render/pdf`
2. **Render PDF (streamed)** — `GET /render/stream`
3. **Render to file** — invoked by `--render-to-file=PATH` at app start (see below)
4. **HTML preview** — `GET /render/preview`
5. **Store a document** — `POST /documents` returns the descriptor
6. **Fetch descriptor** — `GET /documents/{id}/raw` (and `GET /documents/{id}` for a 302 redirect to the presigned PDF)
7. **Thumbnails** — `GET /documents/{id}/thumbnails` returns base64 page thumbnails
8. **Stored preview** — `GET /documents/{id}/preview`
9. **Delete** — `DELETE /documents/{id}` returns 204
10. **Error surface** — `GET /errors/bad-version` deliberately triggers a 400 and returns the typed exception JSON

## Render-to-file demo (step 3)

The `RenderToFileRunner` (`ApplicationRunner`) writes a PDF on startup when the app is launched with `--render-to-file=PATH`:

```bash
./gradlew :example-app:mvc:bootRun --args='--render-to-file=./welcome.pdf'
```

The MVC variant calls `client.renderToFile(input, path)` (blocking); the WebFlux variant calls `client.renderToFileAsync(input, path).join()`.

## Direct curl examples

```bash
# Stream a PDF
curl -o welcome.pdf http://localhost:8080/render/pdf

# Document lifecycle
DOC=$(curl -s -X POST http://localhost:8080/documents | jq -r .documentId)
curl -s http://localhost:8080/documents/$DOC/raw | jq
curl -s http://localhost:8080/documents/$DOC/thumbnails | jq '.[0] | {page, width, contentType}'
curl -i http://localhost:8080/documents/$DOC                # 302 to presigned URL
curl -X DELETE -i http://localhost:8080/documents/$DOC      # 204
```

## What's not here (and why)

- **No tests**: this is a demo app, not a tested artifact. Test coverage lives in the starter modules.
- **No deployment manifest** (Dockerfile, Kubernetes YAML): the example is meant for local exploration.
- **No security**: the endpoints are wide open — production apps would gate them behind their normal auth layer.
