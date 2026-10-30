package page.poli.example.webflux;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import page.poli.sdk.spring.contract.ContractMockServer;
import page.poli.sdk.spring.contract.ContractSuite;

/**
 * The API contract's {@code impl} cases against this example app: its SDK points at a local server
 * that answers each case's responses, through the starter's {@code poli-page.base-url}, and the
 * app's answer and the SDK's requests are compared with the case. Run with {@code
 * POLI_CONTRACT=<path to contract.json>}; skip-listed cases are in {@code contract-skip.json}.
 */
@SpringBootTest
@AutoConfigureWebTestClient
class ContractTest {

  private static final ContractMockServer api = ContractMockServer.start();

  @DynamicPropertySource
  static void poliPage(DynamicPropertyRegistry registry) {
    registry.add("poli-page.api-key", () -> ContractSuite.API_KEY);
    registry.add("poli-page.base-url", api::origin);
  }

  @AfterAll
  static void stopApi() {
    api.close();
  }

  @Autowired WebTestClient web;

  static List<ContractSuite.Row> implCases() {
    return ContractSuite.rows(ContractTest.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("implCases")
  void implCase(ContractSuite.Row row) throws Exception {
    // The cases that fail every attempt wait out the SDK's default retry delays.
    WebTestClient client = web.mutate().responseTimeout(Duration.ofSeconds(60)).build();
    row.run(
        api,
        (method, path) -> {
          EntityExchangeResult<byte[]> result =
              client
                  .method(HttpMethod.valueOf(method))
                  .uri(path)
                  .exchange()
                  .expectBody(byte[].class)
                  .returnResult();
          HttpHeaders headers = result.getResponseHeaders();
          byte[] body = result.getResponseBody();
          return new ContractSuite.AppAnswer(
              result.getStatus().value(),
              headers.getFirst(HttpHeaders.LOCATION),
              headers.getFirst(HttpHeaders.CONTENT_TYPE),
              body == null ? new byte[0] : body);
        });
  }

  @Test
  void skipListNamesOnlyContractCases() {
    ContractSuite.checkSkipList(ContractTest.class);
  }
}
