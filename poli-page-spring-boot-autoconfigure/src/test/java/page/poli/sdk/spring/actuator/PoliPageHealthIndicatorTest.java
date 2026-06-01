package page.poli.sdk.spring.actuator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import page.poli.sdk.spring.PoliPageProperties;

class PoliPageHealthIndicatorTest {

  private WireMockServer wireMock;

  @BeforeEach
  void start() {
    wireMock = new WireMockServer(options().dynamicPort());
    wireMock.start();
  }

  @AfterEach
  void stop() {
    wireMock.stop();
  }

  @Test
  void upWhenEndpointReturns200() {
    wireMock.stubFor(get(urlEqualTo("/v1/health")).willReturn(aResponse().withStatus(200)));

    Health health = indicator().health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsKey("baseUrl");
  }

  @Test
  void downWhenEndpointReturns503() {
    wireMock.stubFor(get(urlEqualTo("/v1/health")).willReturn(aResponse().withStatus(503)));

    Health health = indicator().health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("status", 503);
  }

  @Test
  void downWhenConnectionRefused() {
    int port = wireMock.port();
    wireMock.stop(); // refuse connections at the configured port

    PoliPageHealthIndicator indicator = new PoliPageHealthIndicator(propertiesFor(port));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
  }

  private PoliPageHealthIndicator indicator() {
    return new PoliPageHealthIndicator(propertiesFor(wireMock.port()));
  }

  private PoliPageProperties propertiesFor(int port) {
    return new PoliPageProperties(
        "pp_test_x",
        URI.create("http://127.0.0.1:" + port),
        null,
        new PoliPageProperties.Retries(null, null),
        new PoliPageProperties.Health(true),
        new PoliPageProperties.Metrics(true),
        new PoliPageProperties.Events(true));
  }
}
