package page.poli.sdk.spring.actuator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import page.poli.sdk.spring.PoliPageProperties;

/**
 * Spring Boot Actuator {@link HealthIndicator} probing the Poli Page platform's lightweight {@code
 * GET /v1/health} endpoint, under the configured base URL (a path prefix such as {@code
 * https://proxy.example.com/poli/} is kept, as the SDK keeps it).
 *
 * <p>Reports {@code UP} on HTTP 200, {@code DOWN} on any other status (with the status code in
 * details) or transport failure (with the exception captured).
 *
 * <p>The probe never invokes {@code render(...)} — that would consume a real API-quota call for
 * every Kubernetes readiness check. {@code /v1/health} is unauthenticated and intentionally cheap.
 */
public final class PoliPageHealthIndicator implements HealthIndicator {

  private static final URI DEFAULT_BASE_URL = URI.create("https://api.poli.page");
  private static final String HEALTH_PATH = "/v1/health";
  private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

  private final URI healthUrl;
  private final HttpClient httpClient;

  public PoliPageHealthIndicator(PoliPageProperties props) {
    this(props, defaultHttpClient());
  }

  /** Constructor exposed for tests that need to inject a stubbed HttpClient. */
  PoliPageHealthIndicator(PoliPageProperties props, HttpClient httpClient) {
    URI baseUrl = props.baseUrl() != null ? props.baseUrl() : DEFAULT_BASE_URL;
    this.healthUrl = join(baseUrl, HEALTH_PATH);
    this.httpClient = httpClient;
  }

  // Appends the path to the base URL as strings, with exactly one slash between them, as the SDK
  // builds its request URLs (sdk-java Urls.join). URI.resolve would not do: resolving an absolute
  // path replaces the base's whole path (RFC 3986), so https://proxy.example.com/poli/ would probe
  // https://proxy.example.com/v1/health instead of the API behind the /poli/ prefix.
  private static URI join(URI base, String path) {
    String b = base.toString();
    if (b.endsWith("/")) {
      b = b.substring(0, b.length() - 1);
    }
    return URI.create(b + path);
  }

  @Override
  public Health health() {
    HttpRequest request = HttpRequest.newBuilder(healthUrl).timeout(PROBE_TIMEOUT).GET().build();
    try {
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() == 200) {
        return Health.up().withDetail("baseUrl", healthUrl.getHost()).build();
      }
      return Health.down().withDetail("status", response.statusCode()).build();
    } catch (IOException e) {
      return Health.down(e).build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Health.down(e).build();
    }
  }

  private static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }
}
