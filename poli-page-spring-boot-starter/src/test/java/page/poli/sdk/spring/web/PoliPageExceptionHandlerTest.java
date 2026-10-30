package page.poli.sdk.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;

import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.exception.PoliPageNetworkException;
import page.poli.sdk.spring.PoliPageAutoConfiguration;
import page.poli.sdk.spring.PoliPageErrorPayloadModule;
import page.poli.sdk.spring.PoliPageExceptionHandler;

class PoliPageExceptionHandlerTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  PoliPageAutoConfiguration.class,
                  PoliPageWebMvcAutoConfiguration.class))
          .withUserConfiguration(ThrowingController.Config.class)
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
              MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
              assertThatThrownBy(() -> mvc.perform(get("/throw/api")))
                  .hasRootCauseInstanceOf(PoliPageException.class);
            });
  }

  @Test
  void backsOffForAnApplicationHandler() {
    runner
        .withBean("custom", CustomHandler.class, CustomHandler::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PoliPageExceptionHandler.class).hasBean("custom");
              MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
              MockHttpServletResponse response =
                  mvc.perform(get("/throw/api")).andReturn().getResponse();
              assertThat(response.getStatus()).isEqualTo(404);
              assertThat(response.getHeader("X-Custom")).isEqualTo("yes");
            });
  }

  @Test
  void answersAnApiErrorWithItsPayload() {
    runner.run(
        context ->
            assertAnswer(
                context,
                "/throw/api",
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
                "/throw/network",
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
                "/throw/timeout",
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
                "/throw/no-status",
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
                    "/throw/no-status",
                    500,
                    """
                    {"code": "aborted", "message": "Request was aborted",
                     "status": null, "requestId": null}
                    """));
  }

  private static void assertAnswer(
      WebApplicationContext context, String path, int status, String json) throws Exception {
    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
    MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();
    assertThat(response.getStatus()).isEqualTo(status);
    MediaType contentType = MediaType.parseMediaType(response.getContentType());
    assertThat(contentType.getType() + "/" + contentType.getSubtype())
        .isEqualTo("application/json");
    assertStrictJson(json, response.getContentAsString());
  }

  private static void assertStrictJson(String expected, String actual) {
    try {
      JSONAssert.assertEquals(expected, actual, true);
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  /** An application's replacement: a subclass of the MVC handler, so still an advice. */
  static class CustomHandler extends PoliPageWebMvcExceptionHandler {
    @Override
    public ResponseEntity<ErrorPayload> handlePoliPageException(
        PoliPageException ex, HttpServletResponse response) {
      response.setHeader("X-Custom", "yes");
      return super.handlePoliPageException(ex, response);
    }
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/throw/{kind}")
    String fail(@PathVariable("kind") String kind) {
      throw error(kind);
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
      ThrowingController throwingController() {
        return new ThrowingController();
      }
    }
  }
}
