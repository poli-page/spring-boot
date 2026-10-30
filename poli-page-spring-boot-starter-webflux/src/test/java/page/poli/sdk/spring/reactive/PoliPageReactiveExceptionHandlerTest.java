package page.poli.sdk.spring.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;

import java.util.concurrent.CompletableFuture;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.exception.PoliPageNetworkException;
import page.poli.sdk.spring.PoliPageAutoConfiguration;
import page.poli.sdk.spring.PoliPageErrorPayloadModule;
import page.poli.sdk.spring.PoliPageExceptionHandler;
import reactor.core.publisher.Mono;

class PoliPageReactiveExceptionHandlerTest {

  private final ReactiveWebApplicationContextRunner runner =
      new ReactiveWebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  CodecsAutoConfiguration.class,
                  WebFluxAutoConfiguration.class,
                  PoliPageAutoConfiguration.class,
                  PoliPageWebFluxAutoConfiguration.class))
          .withUserConfiguration(FailingController.Config.class)
          .withPropertyValues("poli-page.api-key=" + SANDBOX_KEY);

  @Test
  void isOnByDefault() {
    runner.run(
        context ->
            assertThat(context)
                .hasSingleBean(PoliPageExceptionHandler.class)
                .hasSingleBean(PoliPageErrorPayloadModule.class));
  }

  @Test
  void isOffWhenDisabled() {
    runner
        .withPropertyValues("poli-page.error-handler.enabled=false")
        .run(
            context -> {
              assertThat(context)
                  .doesNotHaveBean(PoliPageExceptionHandler.class)
                  .doesNotHaveBean(PoliPageErrorPayloadModule.class);
              // Without the handler, WebFlux's own error handling answers, not the payload.
              String body =
                  WebTestClient.bindToApplicationContext(context)
                      .build()
                      .get()
                      .uri("/fail/api")
                      .exchange()
                      .expectStatus()
                      .is5xxServerError()
                      .expectBody(String.class)
                      .returnResult()
                      .getResponseBody();
              assertThat(body == null ? "" : body).doesNotContain("TEMPLATE_NOT_FOUND");
            });
  }

  @Test
  void answersAnApiErrorWithItsPayload() {
    runner.run(
        context ->
            assertAnswer(
                context,
                "/fail/api",
                404,
                """
                {"code": "TEMPLATE_NOT_FOUND", "message": "Template not found.",
                 "status": 404, "requestId": "req_123"}
                """));
  }

  @Test
  void answersAnErrorFromAFailedFuture() {
    runner.run(
        context ->
            assertAnswer(
                context,
                "/fail/future",
                404,
                """
                {"code": "TEMPLATE_NOT_FOUND", "message": "Template not found.",
                 "status": 404, "requestId": "req_123"}
                """));
  }

  @Test
  void answersANetworkErrorWith503() {
    runner.run(
        context ->
            assertAnswer(
                context,
                "/fail/network",
                503,
                """
                {"code": "network_error", "message": "Network request failed",
                 "status": 503, "requestId": null}
                """));
  }

  @Test
  void answersATimeoutWith504() {
    runner.run(
        context ->
            assertAnswer(
                context,
                "/fail/timeout",
                504,
                """
                {"code": "timeout", "message": "Request timed out after 60000ms",
                 "status": 504, "requestId": null}
                """));
  }

  @Test
  void answersAnErrorWithNoStatusWith500AndANullStatus() {
    runner.run(
        context ->
            assertAnswer(
                context,
                "/fail/no-status",
                500,
                """
                {"code": "aborted", "message": "Request was aborted",
                 "status": null, "requestId": null}
                """));
  }

  @Test
  void keepsNullFieldsWhenTheApplicationOmitsNulls() {
    runner
        .withPropertyValues("spring.jackson.default-property-inclusion=non_null")
        .run(
            context ->
                assertAnswer(
                    context,
                    "/fail/no-status",
                    500,
                    """
                    {"code": "aborted", "message": "Request was aborted",
                     "status": null, "requestId": null}
                    """));
  }

  private static void assertAnswer(
      ApplicationContext context, String path, int status, String json) {
    EntityExchangeResult<String> result =
        WebTestClient.bindToApplicationContext(context)
            .build()
            .get()
            .uri(path)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(status);
    MediaType contentType = result.getResponseHeaders().getContentType();
    assertThat(contentType).isNotNull();
    assertThat(contentType.getType() + "/" + contentType.getSubtype())
        .isEqualTo("application/json");
    assertStrictJson(json, result.getResponseBody());
  }

  private static void assertStrictJson(String expected, String actual) {
    try {
      JSONAssert.assertEquals(expected, actual, true);
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  @RestController
  static class FailingController {

    @GetMapping("/fail/{kind}")
    Mono<String> fail(@PathVariable("kind") String kind) {
      if (kind.equals("future")) {
        // An SDK async call fails its future; Mono.fromFuture surfaces the SDK error.
        return Mono.fromFuture(CompletableFuture.failedFuture(error("api")));
      }
      return Mono.error(error(kind));
    }

    static PoliPageException error(String kind) {
      return switch (kind) {
        case "api" ->
            new PoliPageException(
                "TEMPLATE_NOT_FOUND", 404, "Template not found.", "req_123", null);
        case "network" ->
            new PoliPageNetworkException("network_error", "Network request failed", null);
        case "timeout" ->
            new PoliPageNetworkException("timeout", "Request timed out after 60000ms", null);
        default -> new PoliPageException("aborted", 0, "Request was aborted", null, null);
      };
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
      @Bean
      FailingController failingController() {
        return new FailingController();
      }
    }
  }
}
