package page.poli.example.mvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import page.poli.sdk.spring.contract.ContractMockServer;
import page.poli.sdk.spring.contract.ContractSuite;

/**
 * The API contract's {@code impl} cases against this example app: its SDK points at a local server
 * that answers each case's responses, through the starter's {@code poli-page.base-url}, and the
 * app's answer and the SDK's requests are compared with the case. Run with {@code
 * POLI_CONTRACT=<path to contract.json>}; skip-listed cases are in {@code contract-skip.json}.
 */
@SpringBootTest
@AutoConfigureMockMvc
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

  @Autowired MockMvc mvc;

  static List<ContractSuite.Row> implCases() {
    return ContractSuite.rows(ContractTest.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("implCases")
  void implCase(ContractSuite.Row row) throws Exception {
    row.run(
        api,
        (method, path) -> {
          MockHttpServletResponse response =
              mvc.perform(request(HttpMethod.valueOf(method), path)).andReturn().getResponse();
          return new ContractSuite.AppAnswer(
              response.getStatus(),
              response.getHeader(HttpHeaders.LOCATION),
              response.getContentType(),
              response.getContentAsByteArray());
        });
  }

  @Test
  void skipListNamesOnlyContractCases() {
    ContractSuite.checkSkipList(ContractTest.class);
  }
}
