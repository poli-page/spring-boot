package page.poli.sdk.spring.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
 * rethrows the error. Tomcat and Jetty then close the connection, so the client sees a truncated
 * download; Undertow ends the response normally, so the PDF is cut short without an error signal.
 * In no case is JSON appended to the PDF.
 *
 * <p>Both {@link #handlePoliPageException(PoliPageException)}, which finds the current response
 * through {@link RequestContextHolder}, and {@link #handlePoliPageException(PoliPageException,
 * HttpServletResponse)} do this, so an application advice that delegates to the injected {@code
 * PoliPageExceptionHandler} bean gets the same answer.
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
   * Answers the error's payload on this request's servlet response, after removing the content
   * headers set for another body; outside a servlet request, only builds the answer.
   *
   * @param ex the SDK error
   * @return the payload's status (or {@code 500}) with the payload as a JSON body
   */
  @Override
  public ResponseEntity<ErrorPayload> handlePoliPageException(PoliPageException ex) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet && servlet.getResponse() != null) {
      return handlePoliPageException(ex, servlet.getResponse());
    }
    return super.handlePoliPageException(ex);
  }

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
      // No stack trace: the container logs the rethrown error itself.
      log.warn(
          "Poli Page SDK error after the response was committed, so part of the body was sent:"
              + " code={} requestId={}; rethrowing so the response is not completed",
          ex.code(),
          ex.requestId());
      throw ex;
    }
    clearContentHeaders(response);
    return super.handlePoliPageException(ex);
  }

  // HttpServletResponse cannot remove a header (setHeader(name, null) leaves it on Tomcat), so keep
  // the others, reset, and put them back; WebFlux does the same with clearContentHeaders(). The
  // first
  // value goes back with setHeader, which replaces what a container re-adds on reset (Jetty: Date,
  // the session cookie), the others with addHeader.
  private static void clearContentHeaders(HttpServletResponse response) {
    Map<String, List<String>> kept = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (String name : response.getHeaderNames()) {
      if (!CONTENT_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        kept.putIfAbsent(name, new ArrayList<>(response.getHeaders(name)));
      }
    }
    response.reset();
    kept.forEach(
        (name, values) -> {
          for (int i = 0; i < values.size(); i++) {
            if (i == 0) {
              response.setHeader(name, values.get(i));
            } else {
              response.addHeader(name, values.get(i));
            }
          }
        });
  }
}
