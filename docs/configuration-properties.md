# Configuration properties

> Every property the starter reads from `application.yml` / `application.properties` / environment variables — what it binds to, how it's validated, and what happens when you leave it unset.

## Why

The starter is configured exclusively through Spring Boot's `@ConfigurationProperties` mechanism. There's no `init()` method to call, no static `PoliPage.configure(...)` to invoke, no Java-side builder you have to construct. You set `poli-page.api-key` and the autoconfig produces a `PoliPageClient` bean. Anything else you set tunes that bean; anything you leave unset falls through to the SDK's own defaults — meaning a future SDK release that bumps the default `requestTimeout` from 60 s to 90 s takes effect for your app without a starter upgrade.

The properties record is validated at startup via Jakarta Bean Validation. Bad input — a missing or blank `api-key`, a zero-duration `requestTimeout`, a 12-retry `maxAttempts` — fails the boot with a clear `BindValidationException`, not at first call. The key's format is not validated: the API is the only authority on keys, so a key that does not look like one gets one startup warning and the app starts anyway.

## How

### The full property surface

```yaml
poli-page:
  api-key: ${POLI_PAGE_API_KEY}                # REQUIRED, not blank — e.g. pp_sandbox_…
  base-url:                                    # optional URI, e.g. https://api.poli.page
  request-timeout:                             # optional Duration, e.g. 30s, PT1M
  retries:
    max-attempts:                              # optional integer in [0, 10]
    delay:                                     # optional Duration in [0s, 30s]
  health:
    enabled: true                              # default true — toggles HealthIndicator
  metrics:
    enabled: true                              # default true — toggles MeterBinder
  events:
    enabled: true                              # default true — toggles ApplicationEvent bridge
```

Bound to the `PoliPageProperties` record:

```java
@ConfigurationProperties("poli-page")
@Validated
public record PoliPageProperties(
    @NotBlank String apiKey,
    @Nullable URI baseUrl,
    @Nullable @DurationRange(minMillis = 1_000L, maxMillis = 600_000L) Duration requestTimeout,
    @NotNull Retries retries,
    @NotNull Health health,
    @NotNull Metrics metrics,
    @NotNull Events events
) {
    public record Retries(
        @Nullable @Min(0) @Max(10) Integer maxAttempts,
        @Nullable @DurationRange(minMillis = 0L, maxMillis = 30_000L) Duration delay
    ) {}
    public record Health(boolean enabled) {}
    public record Metrics(boolean enabled) {}
    public record Events(boolean enabled) {}
}
```

### Per-property reference

| Property | Type | Required | Default | Notes |
|---|---|---|---|---|
| `poli-page.api-key` | `String` | yes | — | `pp_{env}_{32 hex}` (`pp_sandbox_…`, `pp_live_…`, `pp_staging_…`): the key selects the environment. A missing or blank key fails startup; any other value starts the app, with one warning when it does not look like a key (see [Key format warning](#key-format-warning)). |
| `poli-page.base-url` | `URI` | no | SDK default (`https://api.poli.page`) | Must be absolute. Used for staging / self-hosted deployments. |
| `poli-page.request-timeout` | `Duration` | no | SDK default (60 s) | Per-attempt request timeout. Spring Boot relaxed binding accepts `30s`, `1m`, `PT45S`. |
| `poli-page.retries.max-attempts` | `Integer` | no | SDK default (2) | Number of retries on top of the initial request. `0` disables retries. |
| `poli-page.retries.delay` | `Duration` | no | SDK default (500 ms) | Base delay for exponential backoff. Jitter and `Retry-After` capping happen inside the SDK. |
| `poli-page.health.enabled` | `boolean` | no | `true` | Set to `false` to skip registering the `HealthIndicator`. |
| `poli-page.metrics.enabled` | `boolean` | no | `true` | Set to `false` to skip Micrometer meters. |
| `poli-page.events.enabled` | `boolean` | no | `true` | Set to `false` to drop the `ApplicationEventPublisher` bridge. |

### Relaxed binding & env-var mapping

Spring Boot's relaxed binding lets the same property surface as several spellings:

- `POLI_PAGE_API_KEY` (env var) → `poli-page.api-key` (yaml) → `poliPage.apiKey` (java property).
- `POLI_PAGE_RETRIES_MAX_ATTEMPTS` → `poli-page.retries.max-attempts` → `poliPage.retries.maxAttempts`.

You don't pick one — use whichever is idiomatic for the source. Deployment platforms (Heroku, Cloud Run, ECS) typically inject env vars; `application.yml` lives in source.

### A minimal `application.yml`

```yaml
poli-page:
  api-key: ${POLI_PAGE_API_KEY}
```

That's all you need. With this file and a `POLI_PAGE_API_KEY=pp_sandbox_...` in the environment, the autoconfig produces a fully-functional `PoliPageClient` bean.

### A staging-vs-production split

```yaml
# application.yml (defaults)
poli-page:
  api-key: ${POLI_PAGE_API_KEY}

# application-staging.yml (activated by SPRING_PROFILES_ACTIVE=staging)
poli-page:
  api-key: ${POLI_PAGE_STAGING_API_KEY}   # a pp_staging_… key: the key selects the environment
  request-timeout: 30s

# application-prod.yml
poli-page:
  request-timeout: 90s
  retries:
    max-attempts: 4
```

Spring Boot's profile mechanism handles the merge — properties from `application-prod.yml` overlay the defaults.

## Validation behaviour

Bad properties surface as a `BindValidationException` at `SpringApplication.run(...)` time:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target [Bindable@... type = page.poli.sdk.spring.PoliPageProperties] failed:

    Property: poli-page.api-key
    Value: ""
    Origin: System Environment Property "POLI_PAGE_API_KEY"
    Reason: must not be blank

Action:

Update your application's configuration
```

Other validations:

- `request-timeout: 0s` → `DurationRange` rejection ("must be a Duration between 1000 ms and 600000 ms").
- `request-timeout: 700s` → same `DurationRange` rejection (700000 ms exceeds the 600000 ms upper bound).
- `retries.max-attempts: -1` → `Min` rejection.
- `retries.max-attempts: 11` → `Max` rejection.

### Key format warning

The API is the only authority on keys, so the starter never rejects a non-blank key. When the client bean is built, it compares the key with the SDK contract's full-key regex:

```text
^pp_(?=[a-z][a-z0-9-]{0,19}_)[a-z][a-z0-9]*(?:-[a-z0-9]+)*_[0-9a-f]{32}$
```

A key that does not match (for example `pp_sandbox_x`, `pp_Sandbox_…`, a 21-character environment name, or a dashboard token such as `sk_live_…`) logs exactly one warning and the app starts anyway:

```
WARN  page.poli.sdk.spring.PoliPageAutoConfiguration : Poli Page API key "pp_sandbox_…" does not look like pp_{environment}_{32 hex characters}; starting anyway, the API decides whether it is valid.
```

The warning shows at most the `pp_{env}_` part (only for an environment segment of 1 to 20 characters), never the secret. The first call then fails with the API's answer: `401 INVALID_API_KEY`, or `401 MISSING_API_KEY` for a value without `pp_`.

## IDE auto-completion

The starter ships `META-INF/spring-configuration-metadata.json` so IntelliJ IDEA, VS Code (with the Spring Tools extension), and Eclipse STS suggest `poli-page.*` keys as you type, with type hints, default values, and descriptions sourced from the record's Javadoc. No extra plugin needed — Spring Boot's tooling reads this file by convention.

```yaml
poli-page:
  api  # IDE shows: api-key (String, REQUIRED) — Your Poli Page API key, for example pp_sandbox_... or pp_live_.... (…)
```

## Externalising secrets

`api-key` is sensitive — never commit it to a repository. Three options, in increasing operational sophistication:

### 1. Environment variable

```bash
export POLI_PAGE_API_KEY=pp_live_...
./gradlew bootRun
```

`application.yml` references it: `api-key: ${POLI_PAGE_API_KEY}`. The 12-factor default.

### 2. `.env` loaded by Spring Boot's profile

A `application-local.yml` not under version control:

```yaml
poli-page:
  api-key: pp_sandbox_...
```

Activate with `SPRING_PROFILES_ACTIVE=local` during development. Add `application-local.yml` to `.gitignore`.

### 3. Spring Cloud Config / Vault / AWS Secrets Manager

For production, use the bootstrap configuration mechanism your platform already provides. Spring Cloud Vault, AWS Secrets Manager, GCP Secret Manager, HashiCorp Vault — any of them resolves `poli-page.api-key` from a remote secret store before binding happens.

## Gotchas

- **`base-url` must be absolute.** A relative URI like `/v1` is silently invalid; the autoconfig throws `IllegalArgumentException` from the SDK builder. Set the full origin: `https://api.poli.page` (no trailing path).
- **Boolean toggles default to `true`.** `poli-page.health.enabled`, `poli-page.metrics.enabled`, `poli-page.events.enabled` are all opt-out, not opt-in. Omitting them is equivalent to `true` — flip to `false` to disable the corresponding bean.
- **Relaxed binding case is environment-specific.** Linux env vars are case-sensitive (`POLI_PAGE_API_KEY` only); Windows is not. Stick with UPPER_SNAKE_CASE for env vars to keep platforms aligned.
- **`@DurationRange` is shipped by the starter, not by Spring Boot.** Spring Boot offers no Duration-range constraint. The starter declares `page.poli.sdk.spring.validation.DurationRange` (~40 lines) so the properties record can validate duration bounds at startup. The annotation accepts `minMillis` / `maxMillis` parameters so any Duration unit in `application.yml` (`s`, `m`, `h`) is honoured consistently.
- **Properties aren't refreshable.** The autoconfig reads `PoliPageProperties` once at startup. Spring Cloud Config's `@RefreshScope` is not wired in v0.1 (deferred to a future release); changing `api-key` at runtime requires an application restart.

## Related

- [Auto-configuration](auto-configuration.md) — how the properties drive the bean wiring.
- [spec/spring-boot-starter-specification.md §6](spec/spring-boot-starter-specification.md#6-configuration-properties) — full property tree with rationale for each validation rule.
- [README → Quick start](../README.md#quick-start) — the minimal-config snippet this deep-dive expands.
