package page.poli.sdk.spring;

/** API keys shared by the tests, defined once. */
public final class TestKeys {

  /** The 32-hex secret of {@link #SANDBOX_KEY}. */
  public static final String SECRET = "0123456789abcdef0123456789abcdef";

  /** A well-formed sandbox key. */
  public static final String SANDBOX_KEY = "pp_sandbox_" + SECRET;

  private TestKeys() {}
}
