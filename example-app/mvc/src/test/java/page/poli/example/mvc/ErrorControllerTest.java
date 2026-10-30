package page.poli.example.mvc;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

  @Autowired TestRestTemplate rest;

  @Test
  void notFoundShowsTheTypedTemplateNotFoundError() throws Exception {
    NotFoundDemo.stub(api);

    ResponseEntity<String> response = rest.getForEntity("/errors/not-found", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JSONAssert.assertEquals(NotFoundDemo.EXPECTED_RESPONSE, response.getBody(), true);
  }
}
