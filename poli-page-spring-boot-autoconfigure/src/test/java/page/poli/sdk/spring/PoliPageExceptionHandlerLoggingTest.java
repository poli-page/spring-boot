package page.poli.sdk.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static page.poli.sdk.spring.TestKeys.SANDBOX_KEY;
import static page.poli.sdk.spring.TestKeys.SECRET;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.exception.PoliPageNetworkException;

class PoliPageExceptionHandlerLoggingTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(PoliPageExceptionHandler.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final PoliPageExceptionHandler handler = new PoliPageExceptionHandler() {};

  @BeforeEach
  void capture() {
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void release() {
    logger.detachAppender(logs);
  }

  @Test
  void logsAClientErrorAtWarn() {
    handler.handlePoliPageException(
        new PoliPageException("TEMPLATE_NOT_FOUND", 404, "Template not found.", "req_1", null));

    assertThat(logs.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .isEqualTo(
                      "Poli Page SDK error answered with HTTP 404: code=TEMPLATE_NOT_FOUND status=404"
                          + " requestId=req_1 message=Template not found.");
            });
  }

  @Test
  void logsAServerErrorAtError() {
    handler.handlePoliPageException(
        new PoliPageException("INTERNAL_ERROR", 500, "Unexpected server error.", "req_2", null));

    assertThat(logs.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.ERROR);
  }

  @Test
  void logsANetworkErrorAndAnErrorWithNoStatusAtError() {
    handler.handlePoliPageException(
        new PoliPageNetworkException("network_error", "Network request failed", null));
    handler.handlePoliPageException(
        new PoliPageException("aborted", 0, "Request was aborted", null, null));

    assertThat(logs.list)
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.ERROR, Level.ERROR);
    assertThat(logs.list.get(1).getFormattedMessage())
        .isEqualTo(
            "Poli Page SDK error answered with HTTP 500: code=aborted status=null requestId=null"
                + " message=Request was aborted");
  }

  @Test
  void neverLogsTheApiKey() {
    handler.handlePoliPageException(
        new PoliPageException("INVALID_API_KEY", 401, "Invalid API key.", "req_3", null));
    handler.handlePoliPageException(
        new PoliPageNetworkException("timeout", "Request timed out after 60000ms", null));

    assertThat(logs.list)
        .allSatisfy(
            event ->
                assertThat(event.getFormattedMessage())
                    .doesNotContain(SANDBOX_KEY)
                    .doesNotContain(SECRET));
  }
}
