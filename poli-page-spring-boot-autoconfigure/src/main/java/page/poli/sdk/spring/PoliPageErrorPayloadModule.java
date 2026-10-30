package page.poli.sdk.spring;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.module.SimpleModule;
import page.poli.sdk.exception.ErrorPayload;

/**
 * Jackson module that writes every field of the SDK's {@link ErrorPayload}, {@code null} ones
 * included, even when the application sets {@code spring.jackson.default-property-inclusion} to
 * {@code non_null}: the error body always has the keys {@code code}, {@code message}, {@code
 * status} and {@code requestId}.
 *
 * <p>Spring Boot adds every {@link com.fasterxml.jackson.databind.Module} bean to its {@code
 * ObjectMapper}; the starters register this one together with {@link PoliPageExceptionHandler}.
 */
public class PoliPageErrorPayloadModule extends SimpleModule {

  private static final long serialVersionUID = 1L;

  /** Creates the module. */
  public PoliPageErrorPayloadModule() {
    super(PoliPageErrorPayloadModule.class.getName());
    setMixInAnnotation(ErrorPayload.class, AlwaysIncluded.class);
  }

  @JsonInclude(JsonInclude.Include.ALWAYS)
  private abstract static class AlwaysIncluded {}
}
