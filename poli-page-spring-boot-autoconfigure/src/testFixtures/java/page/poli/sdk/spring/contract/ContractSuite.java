package page.poli.sdk.spring.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import page.poli.sdk.spring.TestKeys;

/**
 * Runs the {@code impl} cases of the Poli Page API contract against an example app.
 *
 * <p>The contract is the file at {@code POLI_CONTRACT} (CI sets it from the {@code
 * poli-page/api-contract} action). When it is unset, every row fails if {@code CI} is set and is
 * skipped otherwise. The skip list is the test class's {@code contract-skip.json} resource: {@code
 * {"<case id>": "<reason>"}}. A listed case must fail; a listed case that passes, or an id the
 * contract does not have, fails the run.
 */
public final class ContractSuite {

  /** The API key the app's SDK must be configured with: a well-formed {@code pp_sandbox_} key. */
  public static final String API_KEY = TestKeys.SANDBOX_KEY;

  /** The skip list's resource name, next to the test class. */
  public static final String SKIP_LIST = "contract-skip.json";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]*)}}");
  private static final Pattern HTTP_DATE = Pattern.compile("httpDate\\+(\\d+)");
  private static final DateTimeFormatter HTTP_DATE_FORMAT =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
  private static final Pattern UUID_V4 =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
  private static final Set<String> EXPECT_KEYS =
      Set.of("status", "location", "contentType", "json", "requests");
  private static final Set<String> REQUEST_KEYS =
      Set.of("method", "path", "auth", "idempotencyKey");

  /** The answer of the app under test to a case's {@code request}. */
  public record AppAnswer(int status, String location, String contentType, byte[] body) {}

  /** Sends a case's {@code request} to the app under test through the framework's test client. */
  @FunctionalInterface
  public interface App {
    /**
     * @param method the HTTP method
     * @param path the path, query included
     * @return the app's answer
     * @throws Exception when the client fails
     */
    AppAnswer send(String method, String path) throws Exception;
  }

  /** One parameterised row: an {@code impl} case, or the reason the contract cannot run. */
  public static final class Row {
    private final String name;
    private final JsonNode implCase;
    private final String skipReason;
    private final String problem;
    private final boolean abort;

    private Row(String name, JsonNode implCase, String skipReason, String problem, boolean abort) {
      this.name = name;
      this.implCase = implCase;
      this.skipReason = skipReason;
      this.problem = problem;
      this.abort = abort;
    }

    @Override
    public String toString() {
      return name;
    }

    /**
     * Runs the case: the server answers its responses, the app gets its request, and the app's
     * answer and the SDK's requests are compared with its {@code expect}.
     *
     * @param server the server the app's SDK points at
     * @param app the app under test
     * @throws Exception when the app cannot be called
     */
    public void run(ContractMockServer server, App app) throws Exception {
      reportProblem(problem, abort);
      try {
        runCase(implCase, server, app);
      } catch (AssertionError failure) {
        if (skipReason != null) {
          Assumptions.abort(
              "skip-listed in "
                  + SKIP_LIST
                  + ": "
                  + skipReason
                  + " (fails with: "
                  + failure.getMessage()
                  + ")");
        }
        throw failure;
      }
      if (skipReason != null) {
        throw new AssertionError(name + " passes: remove it from " + SKIP_LIST);
      }
    }
  }

  private ContractSuite() {}

  /**
   * @param testClass the test class, whose package holds {@code contract-skip.json}
   * @return one row per {@code impl} case, or one row that reports why the contract cannot run
   */
  public static List<Row> rows(Class<?> testClass) {
    Loaded loaded = load(testClass);
    if (loaded.problem != null) {
      return List.of(new Row("POLI_CONTRACT", null, null, loaded.problem, loaded.abort));
    }
    List<Row> rows = new ArrayList<>();
    for (JsonNode implCase : loaded.implCases) {
      String id = implCase.get("id").asText();
      rows.add(new Row(id, implCase, loaded.skipList.get(id), null, false));
    }
    return rows;
  }

  /**
   * Fails when {@code contract-skip.json} names a case the contract does not have.
   *
   * @param testClass the test class, whose package holds {@code contract-skip.json}
   */
  public static void checkSkipList(Class<?> testClass) {
    Loaded loaded = load(testClass);
    reportProblem(loaded.problem, loaded.abort);
    for (String id : loaded.skipList.keySet()) {
      if (!loaded.allIds.contains(id)) {
        throw new AssertionError(SKIP_LIST + " names " + id + ", which the contract does not have");
      }
    }
  }

  private static void reportProblem(String problem, boolean abort) {
    if (problem == null) {
      return;
    }
    if (abort) {
      Assumptions.abort(problem);
    }
    throw new AssertionError(problem);
  }

  private static final class Loaded {
    private String problem;
    private boolean abort;
    private final List<JsonNode> implCases = new ArrayList<>();
    private final List<String> allIds = new ArrayList<>();
    private Map<String, String> skipList = Map.of();
  }

  private static Loaded load(Class<?> testClass) {
    Loaded loaded = new Loaded();
    try {
      loaded.skipList = readSkipList(testClass);
    } catch (IOException | IllegalStateException e) {
      loaded.problem = "cannot read " + SKIP_LIST + ": " + e.getMessage();
      return loaded;
    }
    String path = System.getenv("POLI_CONTRACT");
    if (path == null || path.isBlank()) {
      boolean ci = System.getenv("CI") != null && !System.getenv("CI").isBlank();
      loaded.abort = !ci;
      loaded.problem =
          ci
              ? "POLI_CONTRACT is not set: CI must run the contract tests with POLI_CONTRACT"
                  + " pointing at contract.json (the poli-page/api-contract action's path output)"
              : "POLI_CONTRACT is not set: the contract tests are skipped. Run them with"
                  + " POLI_CONTRACT=<path to contract.json>";
      return loaded;
    }
    JsonNode contract;
    try {
      contract = JSON.readTree(Files.readAllBytes(Path.of(path)));
    } catch (IOException e) {
      loaded.problem = "cannot read the contract at POLI_CONTRACT=" + path + ": " + e;
      return loaded;
    }
    if (!contract.path("formatVersion").isInt() || contract.get("formatVersion").asInt() != 1) {
      loaded.problem =
          "unsupported contract formatVersion " + contract.path("formatVersion") + " (expected 1)";
      return loaded;
    }
    for (JsonNode c : contract.path("cases")) {
      String kind = c.path("kind").asText();
      loaded.allIds.add(c.path("id").asText());
      if (kind.equals("impl")) {
        loaded.implCases.add(c);
      } else if (!kind.equals("sdk")) {
        loaded.problem = "case " + c.path("id").asText() + " has unknown kind '" + kind + "'";
        return loaded;
      }
    }
    if (loaded.implCases.isEmpty()) {
      loaded.problem = "the contract at " + path + " has no impl case";
    }
    return loaded;
  }

  private static Map<String, String> readSkipList(Class<?> testClass) throws IOException {
    try (InputStream in = testClass.getResourceAsStream(SKIP_LIST)) {
      if (in == null) {
        throw new IllegalStateException(
            "missing resource " + testClass.getPackageName() + "/" + SKIP_LIST);
      }
      JsonNode node = JSON.readTree(in);
      if (!node.isObject()) {
        throw new IllegalStateException("expected a JSON object of case id to reason");
      }
      Map<String, String> skip = new LinkedHashMap<>();
      for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> entry = it.next();
        skip.put(entry.getKey(), entry.getValue().asText());
      }
      return skip;
    }
  }

  private static void runCase(JsonNode rawCase, ContractMockServer server, App app)
      throws Exception {
    JsonNode c = replacePlaceholders(rawCase, server.origin());
    String id = c.get("id").asText();
    List<JsonNode> responses = new ArrayList<>();
    c.path("responses").forEach(responses::add);
    server.script(responses);

    JsonNode request = c.get("request");
    AppAnswer answer = app.send(request.get("method").asText(), request.get("path").asText());
    List<ContractMockServer.Recorded> recorded = server.requests();

    JsonNode expect = c.get("expect");
    for (Iterator<String> it = expect.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      check(EXPECT_KEYS.contains(key), id, "unknown expect field '" + key + "'");
    }
    if (expect.has("status")) {
      check(
          answer.status() == expect.get("status").asInt(),
          id,
          "status " + answer.status() + ", expected " + expect.get("status"));
    }
    if (expect.has("location")) {
      String location = expect.get("location").asText();
      check(
          location.equals(answer.location()),
          id,
          "Location " + answer.location() + ", expected " + location);
    }
    if (expect.has("contentType")) {
      String expected = expect.get("contentType").asText();
      check(
          expected.equalsIgnoreCase(mediaType(answer.contentType())),
          id,
          "Content-Type " + answer.contentType() + ", expected " + expected);
    }
    if (expect.has("json")) {
      JsonNode body;
      try {
        body = JSON.readTree(answer.body());
      } catch (IOException e) {
        throw new AssertionError(id + ": the body is not JSON: " + e.getMessage(), e);
      }
      check(
          expect.get("json").equals(body), id, "body " + body + ", expected " + expect.get("json"));
    }
    if (expect.has("requests")) {
      checkRequests(id, (ArrayNode) expect.get("requests"), recorded);
    }
  }

  private static void checkRequests(
      String id, ArrayNode expected, List<ContractMockServer.Recorded> recorded) {
    check(
        recorded.size() == expected.size(),
        id,
        recorded.size()
            + " requests reached the server, expected "
            + expected.size()
            + ": "
            + recorded);
    Map<String, String> stableKeys = new HashMap<>();
    for (int i = 0; i < expected.size(); i++) {
      JsonNode want = expected.get(i);
      ContractMockServer.Recorded got = recorded.get(i);
      String where = id + ", request " + (i + 1);
      for (Iterator<String> it = want.fieldNames(); it.hasNext(); ) {
        String key = it.next();
        check(REQUEST_KEYS.contains(key), where, "unknown request field '" + key + "'");
      }
      if (want.has("method")) {
        check(
            want.get("method").asText().equals(got.method()),
            where,
            "method " + got.method() + ", expected " + want.get("method").asText());
      }
      if (want.has("path")) {
        check(
            want.get("path").asText().equals(got.target()),
            where,
            "path " + got.target() + ", expected " + want.get("path").asText());
      }
      if (want.has("auth")) {
        String auth = want.get("auth").asText();
        String header = got.header("Authorization");
        switch (auth) {
          case "bearer" ->
              check(
                  ("Bearer " + API_KEY).equals(header),
                  where,
                  "Authorization is " + (header == null ? "absent" : "not Bearer <test key>"));
          case "none" -> check(header == null, where, "Authorization is present");
          default -> throw new AssertionError(where + ": unknown auth '" + auth + "'");
        }
      }
      if (want.has("idempotencyKey")) {
        String rule = want.get("idempotencyKey").asText();
        String key = got.header("Idempotency-Key");
        switch (rule) {
          case "stable" -> {
            check(key != null, where, "Idempotency-Key is absent");
            check(UUID_V4.matcher(key).matches(), where, key + " is not a lowercase UUID v4");
            String call = got.method() + " " + got.target();
            String first = stableKeys.putIfAbsent(call, key);
            check(
                first == null || first.equals(key),
                where,
                "Idempotency-Key " + key + " differs from the call's first attempt, " + first);
          }
          case "absent" -> check(key == null, where, "Idempotency-Key is present: " + key);
          default -> throw new AssertionError(where + ": unknown idempotencyKey '" + rule + "'");
        }
      }
    }
  }

  private static String mediaType(String contentType) {
    if (contentType == null) {
      return null;
    }
    return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
  }

  private static void check(boolean ok, String where, String message) {
    if (!ok) {
      throw new AssertionError(where + ": " + message);
    }
  }

  private static JsonNode replacePlaceholders(JsonNode node, String origin) {
    if (node.isTextual()) {
      return new TextNode(replace(node.asText(), origin));
    }
    if (node.isArray()) {
      ArrayNode copy = JSON.createArrayNode();
      node.forEach(item -> copy.add(replacePlaceholders(item, origin)));
      return copy;
    }
    if (node.isObject()) {
      ObjectNode copy = JSON.createObjectNode();
      for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> field = it.next();
        copy.set(field.getKey(), replacePlaceholders(field.getValue(), origin));
      }
      return copy;
    }
    return node;
  }

  private static String replace(String text, String origin) {
    Matcher m = PLACEHOLDER.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String name = m.group(1);
      String value;
      Matcher date = HTTP_DATE.matcher(name);
      if (name.equals("mock")) {
        value = origin;
      } else if (date.matches()) {
        value =
            HTTP_DATE_FORMAT.format(
                ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(Long.parseLong(date.group(1))));
      } else {
        throw new IllegalStateException("unknown placeholder {{" + name + "}}");
      }
      m.appendReplacement(out, Matcher.quoteReplacement(Objects.requireNonNull(value)));
    }
    m.appendTail(out);
    return out.toString();
  }
}
