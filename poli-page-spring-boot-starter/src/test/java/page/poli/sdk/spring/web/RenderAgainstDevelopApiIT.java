package page.poli.sdk.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.ProjectModeInput;

/**
 * One happy-path integration test against {@code api-develop.poli.page}.
 *
 * <p>Skipped automatically when {@code POLI_PAGE_API_KEY} is unset, so contributor PRs without a
 * key still get a green local run. Boots a minimal Spring Boot app with the MVC starter and a
 * single controller; hits it via {@link TestRestTemplate}; asserts the response is a real PDF.
 *
 * <p>To run locally: {@code POLI_PAGE_API_KEY=pp_test_… ./gradlew
 * :poli-page-spring-boot-starter:test}.
 */
@SpringBootTest(
    classes = RenderAgainstDevelopApiIT.IntegrationApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "poli-page.base-url=https://api-develop.poli.page",
      "spring.main.banner-mode=off",
      "logging.level.root=WARN"
    })
@EnabledIfEnvironmentVariable(named = "POLI_PAGE_API_KEY", matches = "pp_(test|live)_.+")
class RenderAgainstDevelopApiIT {

  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;

  @Test
  void rendersWelcomeTemplateAsPdf() {
    ResponseEntity<byte[]> response =
        rest.getForEntity("http://localhost:" + port + "/welcome.pdf", byte[].class);

    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("expected 2xx, got %s", response.getStatusCode())
        .isTrue();
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    byte[] body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.length).isGreaterThan(100);
    assertThat(new String(body, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
  }

  @SpringBootApplication
  @Import(WelcomeController.class)
  static class IntegrationApp {
    public static void main(String[] args) {
      SpringApplication.run(IntegrationApp.class, args);
    }
  }

  @RestController
  static class WelcomeController {
    private final PoliPageClient client;
    private final PoliPageResponses responses;

    WelcomeController(PoliPageClient client, PoliPageResponses responses) {
      this.client = client;
      this.responses = responses;
    }

    @GetMapping("/welcome.pdf")
    ResponseEntity<byte[]> welcome() {
      byte[] pdf =
          client
              .render()
              .pdf(
                  ProjectModeInput.builder()
                      .project("getting-started")
                      .template("welcome")
                      .version("1.0.0")
                      .data(Map.of("name", "spring-boot-starter integration test"))
                      .build());
      return responses.bytes(pdf, "welcome.pdf");
    }
  }
}
