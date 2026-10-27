package page.poli.example.webflux;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import page.poli.sdk.spring.NotFoundDemo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorControllerTest {

  private static final WireMockServer api = new WireMockServer(options().dynamicPort());

  @DynamicPropertySource
  static void poliPage(DynamicPropertyRegistry registry) {
    api.start();
    registry.add("poli-page.api-key", () -> SANDBOX_KEY);
    registry.add("poli-page.base-url", api::baseUrl);
  }

  @AfterAll
  static void stopApi() {
    api.stop();
  }

  @Autowired WebTestClient web;

  @Test
  void notFoundShowsTheTypedTemplateNotFoundError() {
    NotFoundDemo.stub(api);

    web.get()
        .uri("/errors/not-found")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody(String.class)
        .value(body -> assertStrictJson(NotFoundDemo.EXPECTED_RESPONSE, body));
  }

  // JSONAssert directly: BodyContentSpec#json(String, boolean) is deprecated from Spring 6.2 and
  // its
  // replacement is missing before it, while CI builds Spring Boot 3.2 to 3.4.
  private static void assertStrictJson(String expected, String actual) {
    try {
      JSONAssert.assertEquals(expected, actual, true);
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }
}
