package page.poli.sdk.spring.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The stand-in for the Poli Page API in the contract tests: a local HTTP/1.1 server on 127.0.0.1
 * that answers a case's {@code responses} in arrival order and records every request.
 *
 * <p>A {@code json}, {@code text} or {@code base64} response is sent with its {@code status} and
 * {@code headers}, then the connection is closed. The fault {@code drop} closes the connection
 * without answering; {@code hang} never answers. A request past the last response gets {@code 599}
 * {@code MOCK_EXHAUSTED}, which the request count of the case then reports.
 */
public final class ContractMockServer implements AutoCloseable {

  /**
   * One request as it arrived.
   *
   * @param method the method
   * @param target the request target, query included, as sent
   * @param headers the headers, names lowercased
   */
  public record Recorded(String method, String target, Map<String, List<String>> headers) {

    /**
     * @param name a header name, any case
     * @return the header's values joined with {@code ", "}, or {@code null} when absent
     */
    public String header(String name) {
      List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
      return values == null ? null : String.join(", ", values);
    }
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerSocket listener;
  private final String origin;
  private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());
  private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
  private volatile List<JsonNode> responses = List.of();
  private volatile boolean closed;

  private ContractMockServer() throws IOException {
    this.listener = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
    this.origin = "http://127.0.0.1:" + listener.getLocalPort();
  }

  /**
   * @return a started server with no responses scripted yet
   */
  public static ContractMockServer start() {
    ContractMockServer server;
    try {
      server = new ContractMockServer();
    } catch (IOException e) {
      throw new IllegalStateException("cannot start the contract mock server", e);
    }
    Thread acceptor = new Thread(server::acceptLoop, "contract-mock-accept");
    acceptor.setDaemon(true);
    acceptor.start();
    return server;
  }

  /**
   * @return the origin, e.g. {@code http://127.0.0.1:41234}
   */
  public String origin() {
    return origin;
  }

  /**
   * Forgets the recorded requests and answers the next requests with these responses, in order.
   *
   * @param caseResponses a case's {@code responses}, placeholders already replaced
   */
  public void script(List<JsonNode> caseResponses) {
    synchronized (requests) {
      requests.clear();
      responses = List.copyOf(caseResponses);
    }
  }

  /**
   * @return the requests received since the last {@link #script}, in arrival order
   */
  public List<Recorded> requests() {
    synchronized (requests) {
      return List.copyOf(requests);
    }
  }

  @Override
  public void close() {
    closed = true;
    try {
      listener.close();
    } catch (IOException ignored) {
      // closing anyway
    }
    for (Socket socket : sockets) {
      closeQuietly(socket);
    }
  }

  private void acceptLoop() {
    while (!closed) {
      Socket socket;
      try {
        socket = listener.accept();
      } catch (IOException e) {
        return;
      }
      sockets.add(socket);
      Thread worker = new Thread(() -> serve(socket), "contract-mock-connection");
      worker.setDaemon(true);
      worker.start();
    }
  }

  private void serve(Socket socket) {
    try {
      InputStream in = socket.getInputStream();
      String requestLine = readLine(in);
      while (requestLine != null && requestLine.isEmpty()) {
        requestLine = readLine(in);
      }
      if (requestLine == null) {
        return;
      }
      String[] parts = requestLine.split(" ", 3);
      if (parts.length != 3) {
        return;
      }
      Map<String, List<String>> headers = readHeaders(in);
      readBody(in, headers);
      JsonNode response;
      synchronized (requests) {
        int index = requests.size();
        requests.add(new Recorded(parts[0], parts[1], headers));
        response = index < responses.size() ? responses.get(index) : null;
      }
      if (response == null) {
        write(socket.getOutputStream(), exhausted());
        return;
      }
      String fault = response.path("fault").asText("");
      if (fault.equals("drop")) {
        return;
      }
      if (fault.equals("hang")) {
        while (in.read() != -1) {
          // never answer; wait for the client or close() to end the connection
        }
        return;
      }
      if (!fault.isEmpty()) {
        throw new IllegalStateException("unknown fault: " + fault);
      }
      write(socket.getOutputStream(), response);
    } catch (IOException e) {
      // the client or close() ended the connection
    } finally {
      closeQuietly(socket);
      sockets.remove(socket);
    }
  }

  private static JsonNode exhausted() {
    return JSON.createObjectNode()
        .put("status", 599)
        .set("json", JSON.createObjectNode().put("error", "MOCK_EXHAUSTED"));
  }

  private static void write(OutputStream out, JsonNode response) throws IOException {
    int status = response.path("status").asInt();
    Map<String, String> headers = new LinkedHashMap<>();
    byte[] body;
    String defaultType;
    if (response.has("json")) {
      body = JSON.writeValueAsBytes(response.get("json"));
      defaultType = "application/json";
    } else if (response.has("base64")) {
      body = Base64.getDecoder().decode(response.get("base64").asText());
      defaultType = "application/octet-stream";
    } else if (response.has("text")) {
      body = response.get("text").asText().getBytes(StandardCharsets.UTF_8);
      defaultType = "text/plain; charset=utf-8";
    } else {
      body = new byte[0];
      defaultType = null;
    }
    for (Iterator<Map.Entry<String, JsonNode>> it = response.path("headers").fields();
        it.hasNext(); ) {
      Map.Entry<String, JsonNode> header = it.next();
      headers.put(header.getKey(), header.getValue().asText());
    }
    boolean typed =
        headers.keySet().stream().anyMatch(name -> name.equalsIgnoreCase("content-type"));
    if (!typed && defaultType != null && body.length > 0) {
      headers.put("Content-Type", defaultType);
    }
    StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(" Status\r\n");
    headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
    head.append("Content-Length: ").append(body.length).append("\r\n");
    head.append("Connection: close\r\n\r\n");
    out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
    out.write(body);
    out.flush();
  }

  private static String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != -1) {
      if (b == '\n') {
        String text = line.toString(StandardCharsets.ISO_8859_1);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
      }
      line.write(b);
    }
    return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
  }

  private static Map<String, List<String>> readHeaders(InputStream in) throws IOException {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    String line;
    while ((line = readLine(in)) != null && !line.isEmpty()) {
      int colon = line.indexOf(':');
      if (colon > 0) {
        headers
            .computeIfAbsent(
                line.substring(0, colon).trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
            .add(line.substring(colon + 1).trim());
      }
    }
    return headers;
  }

  private static void readBody(InputStream in, Map<String, List<String>> headers)
      throws IOException {
    List<String> length = headers.get("content-length");
    if (length != null) {
      in.readNBytes(Integer.parseInt(length.get(0)));
      return;
    }
    List<String> encoding = headers.get("transfer-encoding");
    if (encoding != null && encoding.get(0).toLowerCase(Locale.ROOT).contains("chunked")) {
      while (true) {
        String size = readLine(in);
        if (size == null) {
          return;
        }
        int chunk = Integer.parseInt(size.split(";", 2)[0].trim(), 16);
        if (chunk == 0) {
          readHeaders(in); // trailers
          return;
        }
        in.readNBytes(chunk);
        readLine(in);
      }
    }
  }

  private static void closeQuietly(Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) {
      // closing anyway
    }
  }
}
