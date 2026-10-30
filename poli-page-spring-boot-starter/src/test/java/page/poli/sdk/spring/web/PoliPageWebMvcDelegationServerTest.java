package page.poli.sdk.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.spring.PoliPageExceptionHandler;

/**
 * The README's recipe for an application with a catch-all advice, on Tomcat: an advice ordered
 * first delegates SDK errors to the injected {@link PoliPageExceptionHandler}, and gets the same
 * answer as the default handler, PDF header clean-up and committed-response rethrow included.
 */
@SpringBootTest(
    classes = PoliPageWebMvcDelegationServerTest.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "poli-page.api-key=" + SANDBOX_KEY)
class PoliPageWebMvcDelegationServerTest {

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void theCatchAllAnswersOtherErrors() throws Exception {
    HttpResponse<String> response = get("/other-error");

    assertThat(response.statusCode()).isEqualTo(418);
    assertThat(response.body()).isEqualTo("catch-all");
  }

  @Test
  void aStreamWhoseFirstReadFailsAnswersJsonWithoutThePdfHeaders() throws Exception {
    HttpResponse<String> response = get("/stream/first-read-fails");

    assertThat(response.statusCode()).isEqualTo(504);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
    assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
    assertStrictJson(
        """
        {"code": "timeout", "message": "Request timed out after 60000ms",
         "status": 504, "requestId": null}
        """,
        response.body());
  }

  @Test
  void pdfHeadersSetBeforeTheErrorAreRemoved() throws Exception {
    HttpResponse<String> response = get("/headers-then-error");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
    assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
    assertThat(response.headers().allValues("X-Kept")).containsExactly("yes");
    assertStrictJson(
        """
        {"code": "TEMPLATE_NOT_FOUND", "message": "Template not found.",
         "status": 404, "requestId": "req_123"}
        """,
        response.body());
  }

  @Test
  void anErrorAfterPartOfThePdfWasSentTruncatesTheResponse() {
    assertThatThrownBy(() -> get("/stream/fails-after-bytes")).isInstanceOf(IOException.class);
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static void assertStrictJson(String expected, String actual) {
    try {
      JSONAssert.assertEquals(expected, actual, true);
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    PoliPageWebMvcExceptionHandlerServerTest.Routes.class,
    OtherRoutes.class,
    CatchAll.class,
    PoliPageErrors.class
  })
  static class App {}

  @RestController
  static class OtherRoutes {
    @GetMapping("/other-error")
    String otherError() {
      throw new IllegalStateException("not an SDK error");
    }
  }

  /** An application's catch-all advice, unordered. */
  @RestControllerAdvice
  static class CatchAll {
    @ExceptionHandler(Exception.class)
    ResponseEntity<String> any(Exception e) {
      return ResponseEntity.status(418).body("catch-all");
    }
  }

  /** The README's recipe, as written there. */
  @RestControllerAdvice
  @Order(Ordered.HIGHEST_PRECEDENCE)
  static class PoliPageErrors {
    private final PoliPageExceptionHandler poliPage;

    PoliPageErrors(PoliPageExceptionHandler poliPage) {
      this.poliPage = poliPage;
    }

    @ExceptionHandler(PoliPageException.class)
    ResponseEntity<ErrorPayload> handle(PoliPageException e) {
      return poliPage.handlePoliPageException(e);
    }
  }
}
