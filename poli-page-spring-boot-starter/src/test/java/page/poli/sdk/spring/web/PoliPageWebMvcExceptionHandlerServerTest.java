package page.poli.sdk.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;

import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.exception.PoliPageNetworkException;

/**
 * The MVC error handler on a real servlet container (Tomcat), where PDF headers already on the
 * response must not reach the JSON error, and a response already partly sent must not get JSON
 * appended.
 */
@SpringBootTest(
    classes = PoliPageWebMvcExceptionHandlerServerTest.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "poli-page.api-key=" + SANDBOX_KEY)
class PoliPageWebMvcExceptionHandlerServerTest {

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();

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
  void pdfHeadersSetBeforeTheErrorAreRemovedAndOtherHeadersKept() throws Exception {
    HttpResponse<String> response = get("/headers-then-error");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
    assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
    assertThat(response.headers().firstValue("Content-Language")).isEmpty();
    assertThat(response.headers().firstValue("X-Kept")).hasValue("yes");
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
  @Import(Routes.class)
  static class App {}

  @RestController
  static class Routes {

    private final PoliPageResponses responses;

    Routes(PoliPageResponses responses) {
      this.responses = responses;
    }

    @GetMapping("/stream/first-read-fails")
    ResponseEntity<StreamingResponseBody> firstReadFails() {
      return responses.stream(failing(new byte[0]), "x.pdf");
    }

    @GetMapping("/stream/fails-after-bytes")
    ResponseEntity<StreamingResponseBody> failsAfterBytes() {
      // Well past the container's response buffer, so the response is committed first.
      return responses.stream(failing(new byte[256 * 1024]), "x.pdf");
    }

    @GetMapping("/headers-then-error")
    String headersThenError(HttpServletResponse response) {
      response.setContentType("application/pdf");
      response.setHeader("Content-Disposition", "attachment; filename=\"x.pdf\"");
      response.setHeader("Content-Language", "fr");
      response.setHeader("X-Kept", "yes");
      throw new PoliPageException(
          "TEMPLATE_NOT_FOUND", 404, "Template not found.", "req_123", null);
    }

    /** The bytes, then a read that fails the way the SDK's PDF stream does. */
    private static InputStream failing(byte[] first) {
      InputStream failure =
          new InputStream() {
            @Override
            public int read() {
              throw new PoliPageNetworkException(
                  "timeout", "Request timed out after 60000ms", null);
            }
          };
      return new SequenceInputStream(new ByteArrayInputStream(first), failure);
    }
  }
}
