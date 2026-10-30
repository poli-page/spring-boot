package page.poli.sdk.spring;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * The example apps' not-found demo against a stubbed API: rendering the {@code getting-started}
 * project's {@code does-not-exist} template with data {@code {}} answers 404 {@code
 * TEMPLATE_NOT_FOUND}.
 */
public final class NotFoundDemo {

  /** The JSON body the demo's {@code GET /errors/not-found} answers with. */
  public static final String EXPECTED_RESPONSE =
      """
      {"code": "TEMPLATE_NOT_FOUND", "status": 404,
       "message": "Template does-not-exist not found", "requestId": "req_not_found"}
      """;

  /**
   * Stubs {@code POST /v1/render} so that only the demo's exact request body gets the API's 404;
   * any other body falls through to WireMock's own unmatched-request answer.
   */
  public static void stub(WireMockServer api) {
    api.stubFor(
        post(urlPathEqualTo("/v1/render"))
            .withRequestBody(
                equalToJson(
                    """
                    {"project": "getting-started", "template": "does-not-exist", "data": {}}
                    """))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("x-request-id", "req_not_found")
                    .withBody(
                        """
                        {"error": "TEMPLATE_NOT_FOUND",
                         "detail": "Template does-not-exist not found"}
                        """)));
  }

  private NotFoundDemo() {}
}
