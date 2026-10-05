package page.poli.sdk.spring.internal;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Builds {@code Content-Disposition} header values per RFC 6266 / RFC 8187 (ex-5987).
 *
 * <p>Internal to the Poli Page starters — not part of the public API.
 *
 * <p>Spring's {@code ContentDisposition} builder is deliberately not used: with a charset it emits
 * an RFC 2047 {@code =?UTF-8?Q?...?=} fallback in which {@code "} and {@code \} are not escaped (a
 * crafted name breaks out of the quoted-string), and without one it writes control characters,
 * including CR/LF, verbatim.
 *
 * <p>Contract (shared with the other Poli Page framework integrations): C0 controls (including TAB,
 * CR, LF), DEL and C1 controls are stripped; ASCII filenames use a quoted-string {@code
 * filename="..."} with {@code \} and {@code "} escaped as quoted-pairs (RFC 9110 §5.6.4); non-ASCII
 * filenames use the dual notation {@code filename="<ascii-fallback>";
 * filename*=UTF-8''<percent-encoded>}, the fallback replacing each non-ASCII code point by {@code
 * ?}.
 */
public final class ContentDispositionHeader {

  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F-\\x9F]");

  private ContentDispositionHeader() {}

  /**
   * Returns the header value for {@code filename}.
   *
   * @param filename the suggested filename — non-null
   * @param inline {@code true} for {@code inline}, {@code false} for {@code attachment}
   * @return the {@code Content-Disposition} header value
   */
  public static String build(String filename, boolean inline) {
    String disposition = inline ? "inline" : "attachment";
    String clean = CONTROL_CHARS.matcher(filename).replaceAll("");
    if (isAscii(clean)) {
      return disposition + "; filename=\"" + quotedStringContent(clean) + "\"";
    }
    return disposition
        + "; filename=\""
        + quotedStringContent(asciiFallback(clean))
        + "\"; filename*=UTF-8''"
        + percentEncode(clean);
  }

  private static boolean isAscii(String s) {
    return s.chars().allMatch(c -> c < 0x80);
  }

  private static String asciiFallback(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    s.codePoints().forEach(cp -> sb.append(cp < 0x80 ? (char) cp : '?'));
    return sb.toString();
  }

  /** Escapes {@code \} and {@code "} as quoted-pairs (RFC 9110 §5.6.4). */
  private static String quotedStringContent(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /** Percent-encodes the UTF-8 bytes of {@code s}, keeping only RFC 3986 unreserved bytes. */
  private static String percentEncode(String s) {
    StringBuilder sb = new StringBuilder();
    for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 0xFF;
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '.'
          || c == '_'
          || c == '~') {
        sb.append((char) c);
      } else {
        sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)));
        sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
      }
    }
    return sb.toString();
  }
}
