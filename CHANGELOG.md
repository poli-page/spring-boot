# Changelog

All notable changes to `page.poli:poli-page-spring-boot-starter` (and the sibling `-webflux` and `-autoconfigure` artifacts) are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.11.0] - 2026-10-30

First release on Maven Central: `page.poli:poli-page-spring-boot-starter`, `page.poli:poli-page-spring-boot-starter-webflux` and the `page.poli:poli-page-spring-boot-autoconfigure` module they share. The version follows the Poli Page SDK it requires, `page.poli:sdk` 0.11.0. The 0.1.0 version was never published; its entries are folded in here, and the Breaking, Removed and Fixed entries below are relative to its development snapshots.

### Added
- `PoliPageAutoConfiguration` registering a `PoliPageClient` `@Bean` from `poli-page.*` properties, `@ConditionalOnMissingBean` so consumers override.
- `PoliPageProperties` record with Jakarta Bean Validation: `api-key` required and not blank, `request-timeout` range (1 s..600 s), `retries.max-attempts` range (0..10), `retries.delay` range (0..30 s). `BindValidationException` on bad input at startup.
- `PoliPageEventBridge` publishing the SDK's `onRetry` / `onError` `Consumer` hooks as `PoliPageRetryEvent` / `PoliPageErrorEvent` (Spring `ApplicationEvent`s). Opt-out via `poli-page.events.enabled=false`.
- `PoliPageHealthIndicator` (Spring Boot Actuator) probing `GET <baseUrl>/v1/health` with a 2 s timeout — never invokes `render(...)` for health. Opt-out via `poli-page.health.enabled=false`.
- `PoliPageMetrics` (Micrometer `MeterBinder`) with three meters: `poli.page.retries.total` (counter, tagged `attempt` + `reason`), `poli.page.retries.delay` (timer, same tags), `poli.page.errors.total` (counter, tagged `code` + `status_class`). Tag cardinality bounded by SDK enum values — safe for Prometheus. Opt-out via `poli-page.metrics.enabled=false`. A `poli.page.client.up` gauge specified in the design was deferred — scrape `/actuator/health` via Prometheus HTTP probe for that signal.
- `poli-page-spring-boot-starter` (MVC) artifact registering `PoliPageResponses` with `bytes` / `stream` / `preview` / `documentRedirect` builders. Correct PDF/HTML headers, an RFC 6266 `Content-Disposition` (see Fixed), `Cache-Control: private, no-store`. `stream(...)` returns `ResponseEntity<StreamingResponseBody>` and owns the SDK `InputStream` lifecycle.
- `poli-page-spring-boot-starter-webflux` artifact registering `PoliPageReactiveResponses` — `Mono<ResponseEntity<byte[]>>` and `Flux<DataBuffer>` helpers bridging `renderAsync()` `CompletableFuture` results to Reactor types. Cancellation propagates back via `Mono.fromFuture(..., suppressCancel=false)`.
- Default error handler, on in both starters (`PoliPageWebMvcExceptionHandler`, `PoliPageWebFluxExceptionHandler`, subclasses of `PoliPageExceptionHandler`): a `@RestControllerAdvice` that answers a `PoliPageException` with the SDK's `toPayload()` as JSON, `{ "code", "message", "status", "requestId" }`, and its status (`503` for `network_error`, `504` for `timeout`), or `500` with `"status": null` when the error has none, and logs it (`ERROR` for 5xx or no status, `WARN` otherwise). The `null` fields stay even with `spring.jackson.default-property-inclusion=non_null`. In MVC, PDF headers already on the response are removed first, and an error after part of the PDF was sent is rethrown instead of appending JSON (Tomcat and Jetty close the connection). An advice of your own can delegate to the injected `PoliPageExceptionHandler` and get the same answer. Turn it off with `poli-page.error-handler.enabled=false`, or declare your own `PoliPageExceptionHandler` advice bean.
- `META-INF/spring-configuration-metadata.json` + handcrafted overlay for IntelliJ / VS Code / Eclipse STS auto-completion on `poli-page.*` keys.
- Example apps at `example-app/mvc/` and `example-app/webflux/` demonstrating the SDK through the starters, with an interactive HTML dashboard at `GET /`. They are not published.
- Contract tests: CI runs the `impl` cases of the Poli Page API contract (v1.3.0, `poli-page/api-contract`) against both example apps (`ContractTest`), and Dependabot proposes each new contract version.
- CI matrix: Java 17 / 21 × Spring Boot 3.2 / 3.3 / 3.4 (6 cells). A live API job runs on `main` (and on demand) against the production API with the sandbox key from the `POLI_PAGE_API_KEY` secret, and skips with a notice when the key is absent or not a `pp_sandbox_` key.
- Release workflow: a `vX.Y.Z` tag runs the full build with the API contract, then, after approval on the `maven-central-release` environment, publishes the three modules to Maven Central through the Central Portal and creates the GitHub Release (see `RELEASING.md`).

### Changed
- **Breaking:** environment keys. A key is `pp_{env}_{32 hex}` and selects its environment (`pp_sandbox_…`, `pp_live_…`, `pp_staging_…`). `poli-page.api-key` is still required and must not be blank, but its `(test|live)` prefix check is gone: a key that does not match the SDK contract's full-key regex logs one startup warning that names the expected format and shows no part of the key, not even the `pp_{env}_` prefix, and the app starts anyway; the API decides whether it is valid.
- **Breaking:** requires `page.poli:sdk` 0.11.0 (was `1.0.0-SNAPSHOT`), from Maven Central: environment API keys (`pp_{env}_{32 hex}`), and render inputs are `RenderInput`, with no `version` (templates have no versions). The build no longer reads `mavenLocal()`.
- README: in MVC a subclass of `PoliPageWebMvcExceptionHandler` overrides the two-argument `handlePoliPageException(ex, response)`; the one-argument override is ignored.
- Example apps (MVC and WebFlux): SDK errors are answered by the starter's default error handler instead of each app's own, which built the body by hand and put `500` in its `status` for an error with no status. `GET /documents/{id}` still answers `302` to the presigned PDF URL.
- Example apps (MVC and WebFlux): render inputs carry no `version`, `POLI_PAGE_BASE_URL` optionally overrides the API URL, and the error demo is `GET /errors/not-found` (template `does-not-exist`, data `{}`, answering `404 TEMPLATE_NOT_FOUND` with its `code`, `status`, `message` and `requestId`), replacing `GET /errors/bad-version`. Each app now tests that contract against a stubbed API.
- `RenderAgainstDevelopApiIT` runs only with a sandbox key (`POLI_PAGE_API_KEY` matching `pp_sandbox_.+`).

### Removed
- **Breaking:** `PoliPageResponses.preview(PreviewResult)` and `PoliPageReactiveResponses.previewFromRender(...)`, with the SDK's render preview. `PoliPageResponses.preview(DocumentPreviewResult)` and `PoliPageReactiveResponses.previewFromDocument(...)` stay.
- The example apps' `GET /render/preview` route and its dashboard button.

### Fixed
- `PoliPageResponses` / `PoliPageReactiveResponses`: the `Content-Disposition` filename is now escaped per RFC 6266 / RFC 9110 (`\` and `"` as quoted-pairs, ASCII fallback included) and stripped of control characters (CR/LF, TAB, DEL, C1). Spring's `ContentDisposition` builder is no longer used: its UTF-8 mode emits an RFC 2047 `=?UTF-8?Q?…?=` fallback with `"` and `\` unescaped, so a crafted filename could break out of the quoted-string. ASCII filenames now get a plain `filename="…"` instead of a Q-encoded one.

### Notes
- Requires Java 17+ and Spring Boot 3.2 or newer (Jakarta namespace only; no `javax.*` fallback).
- WebFlux artifact is opt-in — a single application typically picks one of the two starters, not both.

[Unreleased]: https://github.com/poli-page/spring-boot/compare/v0.11.0...HEAD
[0.11.0]: https://github.com/poli-page/spring-boot/releases/tag/v0.11.0
