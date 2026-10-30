package page.poli.sdk.spring.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import page.poli.sdk.exception.ErrorPayload;
import page.poli.sdk.exception.PoliPageException;
import page.poli.sdk.spring.PoliPageExceptionHandler;

/**
 * The Spring MVC default error handler: answers a {@link PoliPageException} with its {@code
 * toPayload()} (see {@link PoliPageExceptionHandler}).
 *
 * <p>An SDK error can surface after a route or {@link PoliPageResponses#stream} has put PDF headers
 * on the servlet response, for example when the first read of a PDF stream fails. When nothing has
 * been sent yet, the handler removes the content headers ({@code Content-Disposition}, {@code
 * Content-Length}, {@code Content-Encoding}, {@code Content-Range}, {@code Content-Language},
 * {@code Content-Type}) and keeps the others, so the JSON error is not saved as a PDF. When the
 * response is already committed, part of the PDF has been sent: the handler writes nothing and
 * rethrows, and the servlet container closes the connection, so the client sees a truncated
 * download instead of a PDF with JSON appended.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class PoliPageWebMvcExceptionHandler extends PoliPageExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(PoliPageWebMvcExceptionHandler.class);

  private static final Set<String> CONTENT_HEADERS =
      Set.of(
          "content-disposition",
          "content-length",
          "content-encoding",
          "content-range",
          "content-language",
          "content-type");

  /** Creates the handler. */
  public PoliPageWebMvcExceptionHandler() {}

  /**
   * Answers the error's payload, after removing the content headers set for another body.
   *
   * @param ex the SDK error
   * @param response the servlet response
   * @return the payload's status (or {@code 500}) with the payload as a JSON body
   */
  @ExceptionHandler(PoliPageException.class)
  public ResponseEntity<ErrorPayload> handlePoliPageException(
      PoliPageException ex, HttpServletResponse response) {
    if (response.isCommitted()) {
      log.error(
          "Poli Page SDK error after the response was committed: code={} requestId={}; closing"
              + " the connection so the client sees a truncated response",
          ex.code(),
          ex.requestId(),
          ex);
      throw ex;
    }
    clearContentHeaders(response);
    return handlePoliPageException(ex);
  }

  // HttpServletResponse cannot remove a header (setHeader(name, null) leaves it on Tomcat), so keep
  // the others, reset, and put them back; WebFlux does the same with clearContentHeaders().
  private static void clearContentHeaders(HttpServletResponse response) {
    Map<String, List<String>> kept = new LinkedHashMap<>();
    for (String name : response.getHeaderNames()) {
      if (!CONTENT_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        kept.putIfAbsent(name, new ArrayList<>(response.getHeaders(name)));
      }
    }
    response.reset();
    kept.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
  }
}
