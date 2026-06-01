# Testing

> Stub the `PoliPageClient` bean in your tests, assert against the starter's helpers without hitting the network, and run one gated integration test against the develop API for end-to-end confidence.

## Why

A unit test that hits `api.poli.page` is brittle, slow, and consumes your quota. A unit test that ignores the SDK entirely misses the wiring layer the starter is responsible for. The right test pyramid for an app using this starter is: most tests stub `PoliPageClient` at the bean level (sub-second, no network), one or two integration tests use a WireMock-backed local server (medium, no quota), and one smoke test hits the real develop API (gated on a key).

The starter itself follows the same pyramid — see [spec §14](spec/spring-boot-starter-specification.md#14-testing-strategy). This page covers the techniques an *application* using the starter should adopt.

## How

### Stubbing `PoliPageClient` in unit tests

Spring Boot's `@MockitoBean` (Boot 3.4+) replaces a bean in the test context with a Mockito stub. This is the idiomatic way to test a `@RestController` that injects `PoliPageClient`:

```java
@WebMvcTest(InvoiceController.class)
@Import(PoliPageResponses.class)  // bring in the helper, skip the SDK autoconfig
class InvoiceControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PoliPageClient client;

    @Test
    void rendersInvoicePdf() throws Exception {
        Render render = mock(Render.class);
        when(client.render()).thenReturn(render);
        when(render.pdf(any(ProjectModeInput.class)))
            .thenReturn("%PDF-1.4 fake-bytes".getBytes(StandardCharsets.ISO_8859_1));

        mvc.perform(get("/invoices/123.pdf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition",
                containsString("filename=\"invoice-123.pdf\"")))
            .andExpect(content().bytes("%PDF-1.4 fake-bytes".getBytes(ISO_8859_1)));
    }
}
```

Three points worth noting:

1. **`@WebMvcTest` boots a slice of the context** — only the web layer plus the controllers under test. The full `PoliPageAutoConfiguration` is not active, so `PoliPageClient` isn't auto-wired. `@MockitoBean` provides the stub.
2. **`@Import(PoliPageResponses.class)`** brings in the real helper (it's stateless — no setup needed). The alternative — `@MockitoBean PoliPageResponses` — would force you to assert against the controller's invocations rather than the actual response headers, which defeats half the purpose of integration-style controller tests.
3. **The `Render` facade is mocked separately.** `client.render()` returns a cached `Render` instance; in tests we mock that chain. For multiple methods, mock each call.

### Stubbing on Spring Boot 3.2 / 3.3

`@MockitoBean` shipped in Spring Boot 3.4. Earlier versions use the now-deprecated `@MockBean`:

```java
@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PoliPageClient client;  // org.springframework.boot.test.mock.mockito.MockBean
    // ... same test body
}
```

Functionally equivalent for our purposes.

### Asserting events

To assert that the SDK's retry hook fires the right `ApplicationEvent`, capture publication with an `@RecordApplicationEvents`-tagged test:

```java
@SpringBootTest
@RecordApplicationEvents
class PoliPageEventsTest {

    @Autowired ApplicationEvents events;
    @Autowired PoliPageEventBridge bridge;

    @Test
    void retryEventPublishedFromBridge() {
        bridge.onRetry(new RetryEvent(1, Duration.ofMillis(500), 502, "5xx"));

        assertThat(events.stream(PoliPageRetryEvent.class))
            .singleElement()
            .satisfies(e -> {
                assertThat(e.sdkEvent().attempt()).isEqualTo(1);
                assertThat(e.sdkEvent().reason()).isEqualTo("5xx");
            });
    }
}
```

`@RecordApplicationEvents` is part of `spring-test`. It collects every event published inside the test method and lets you assert on the stream.

### Configuration validation tests

When you customise validation rules (e.g. a custom `Validator` bean) or want to assert the starter's validation triggers correctly on bad input:

```java
class PoliPagePropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PoliPageAutoConfiguration.class));

    @Test
    void rejectsKeyWithoutPrefix() {
        runner.withPropertyValues("poli-page.api-key=sk_live_dashboardtoken")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("pp_test_ or pp_live_");
            });
    }

    @Test
    void acceptsValidKey() {
        runner.withPropertyValues("poli-page.api-key=pp_test_validkey")
            .run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(PoliPageClient.class));
    }
}
```

`ApplicationContextRunner` is the recommended fixture for autoconfig-level tests — much faster than `@SpringBootTest`, gives precise control over property values.

### WireMock-backed integration tests

When you genuinely want to drive a full request through the SDK without hitting the real API, point the starter at a WireMock instance:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullRenderFlowTest {

    static WireMockServer wiremock;

    @DynamicPropertySource
    static void poliPageBaseUrl(DynamicPropertyRegistry registry) {
        wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wiremock.start();
        registry.add("poli-page.base-url", wiremock::baseUrl);
        registry.add("poli-page.api-key", () -> "pp_test_fake");
    }

    @AfterAll
    static void stopWiremock() { wiremock.stop(); }

    @Test
    void controllerReturnsPdfFromStubbedApi() throws Exception {
        wiremock.stubFor(post("/v1/render").willReturn(okJson("""
            {
              "documentId": "doc_x",
              "presignedPdfUrl": "%s/files/doc_x.pdf"
            }
            """.formatted(wiremock.baseUrl()))));
        wiremock.stubFor(get("/files/doc_x.pdf").willReturn(
            aResponse().withHeader("Content-Type", "application/pdf").withBody("%PDF-fake")));

        // ... exercise the controller via TestRestTemplate, assert the response
    }
}
```

Useful when you want to verify the full chain end-to-end (controller → SDK → HTTP → response) and you have specific failure modes to assert (e.g. how your controller reacts to a 429 from the Poli Page side). For most tests, the simpler `@MockitoBean PoliPageClient` approach is enough.

### Integration test against the develop API (gated)

The starter's own `RenderAgainstDevelopApiIT` is the template. Adopt it in your app for the smoke level:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "POLI_PAGE_API_KEY", matches = "pp_(test|live)_.+")
class PoliPageSmokeIT {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void rendersWelcomePdf() {
        ResponseEntity<byte[]> response = rest.getForEntity(
            "http://localhost:" + port + "/welcome.pdf", byte[].class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(response.getBody(), 0, 5, ISO_8859_1)).isEqualTo("%PDF-");
    }
}
```

`@EnabledIfEnvironmentVariable` (JUnit 5) skips the whole class when `POLI_PAGE_API_KEY` is missing — local contributor runs stay green without a key.

## What we explicitly DON'T test

These belong to the SDK and are covered exhaustively in `sdk-java/src/test`:

- HTTP transport behaviour (JDK `HttpClient` edge cases, connection pool sizing).
- Retry policy (exponential backoff, max attempts, `Retry-After`, never-retry-4xx).
- 4xx / 5xx → exception mapping.
- Idempotency-key generation.
- Stream handling, byte-range correctness, etc.

Re-testing these in your app — or in the starter — is wasted effort and creates a double-maintenance burden. If you find yourself writing a mock HTTP server to test "what happens when the API returns 429 three times in a row", **stop** — that's an SDK behaviour, already covered there. Trust the SDK's test suite the same way you trust Spring's.

## Gotchas

- **`@WebMvcTest` doesn't activate the starter's autoconfig.** Spring Boot's slice tests intentionally narrow the context to the controller layer. You must `@Import(PoliPageResponses.class)` (or `@Import(PoliPageAutoConfiguration.class)` for the full bean graph) if you want the helper bean available.
- **`@MockitoBean` is per-method semantics.** A mock declared at the class level is reset between methods — Mockito's `reset(client)` happens in `@AfterEach`. Stub inside `@BeforeEach` or per-test.
- **`PoliPageEventBridge` is a real, live bean.** If your test imports the full autoconfig and your code under test publishes a fake `RetryEvent`, downstream listeners (`@EventListener` methods on other beans in the test context) will fire. Use `@SpringBootTest` with a narrow `classes = ...` to limit which listeners load, or stub the publisher.
- **`@SpringBootTest` boots the full context, including the HealthIndicator.** That probe hits `https://api.poli.page/v1/health` by default — a network-flaky test. Stub the URL via `@DynamicPropertySource` (`poli-page.base-url=<wiremock>`) or disable the indicator (`poli-page.health.enabled=false`) in `@TestPropertySource`.
- **`TestRestTemplate` blocks for streamed responses.** It's a blocking client, so it reads the entire `StreamingResponseBody` into the response `byte[]` before returning. For end-to-end streaming verification, use Spring's `WebClient` with a `Flux<DataBuffer>` body — it preserves the streaming semantics.

## Related

- [Auto-configuration](auto-configuration.md) — what the starter wires that you may want to override in tests.
- [Events](events.md) — `@RecordApplicationEvents` usage for asserting on retry/error publication.
- [spec/spring-boot-starter-specification.md §14](spec/spring-boot-starter-specification.md#14-testing-strategy) — the starter's own testing strategy, mirrored here for application code.
