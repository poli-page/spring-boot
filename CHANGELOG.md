# Changelog

All notable changes to `page.poli:poli-page-spring-boot-starter` (and the sibling `-webflux` and `-autoconfigure` artifacts) are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial release scaffolding.

## [0.1.0] — TBD

### Added
- `PoliPageAutoConfiguration` registering a `PoliPageClient` `@Bean` from `poli-page.*` properties, `@ConditionalOnMissingBean` so consumers override.
- `PoliPageProperties` record with Jakarta Bean Validation: `api-key` shape (`pp_test_*` / `pp_live_*`), `request-timeout` range (1 s..600 s), `retries.max-attempts` range (0..10), `retries.delay` range (0..30 s). `BindValidationException` on bad input at startup.
- `PoliPageEventBridge` publishing the SDK's `onRetry` / `onError` `Consumer` hooks as `PoliPageRetryEvent` / `PoliPageErrorEvent` (Spring `ApplicationEvent`s). Opt-out via `poli-page.events.enabled=false`.
- `PoliPageHealthIndicator` (Spring Boot Actuator) probing `GET <baseUrl>/v1/health` with a 2 s timeout — never invokes `render(...)` for health. Opt-out via `poli-page.health.enabled=false`.
- `PoliPageMetrics` (Micrometer `MeterBinder`) with four meters: `poli.page.retries.total` (counter), `poli.page.retries.delay` (timer), `poli.page.errors.total` (counter), `poli.page.client.up` (gauge). Tag cardinality bounded by SDK enum values. Opt-out via `poli-page.metrics.enabled=false`.
- `poli-page-spring-boot-starter` (MVC) artifact registering `PoliPageResponses` with `bytes` / `stream` / `preview` / `documentRedirect` builders. Correct PDF/HTML headers, RFC 5987 `Content-Disposition` encoding, `Cache-Control: private, no-store`. `stream(...)` returns `ResponseEntity<StreamingResponseBody>` and owns the SDK `InputStream` lifecycle.
- `poli-page-spring-boot-starter-webflux` artifact registering `PoliPageReactiveResponses` — `Mono<ResponseEntity<byte[]>>` and `Flux<DataBuffer>` helpers bridging `renderAsync()` `CompletableFuture` results to Reactor types. Cancellation propagates back via `Mono.fromFuture(..., suppressCancel=false)`.
- `META-INF/spring-configuration-metadata.json` + handcrafted overlay for IntelliJ / VS Code / Eclipse STS auto-completion on `poli-page.*` keys.
- Example apps at `example-app/mvc/` and `example-app/webflux/` demonstrating all 10 SDK demo steps with an interactive HTML dashboard at `GET /`.
- CI matrix: Java 17 / 21 × Spring Boot 3.2 / 3.3 / 3.4 (6 cells).

### Notes
- Depends on `page.poli:sdk` at `1.0.0` (Maven Central).
- Requires Java 17+ and Spring Boot 3.2 or newer (Jakarta namespace only; no `javax.*` fallback).
- WebFlux artifact is opt-in — a single application typically picks one of the two starters, not both.

[Unreleased]: https://github.com/poli-page/spring-boot/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/poli-page/spring-boot/releases/tag/v0.1.0
