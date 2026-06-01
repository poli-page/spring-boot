# Auto-configuration

> How the starter wires a `PoliPageClient` bean into your application context the moment you add the dependency — and how to override every step of the way.

## Why

Spring Boot starters earn their keep by removing boilerplate. Adding `page.poli:poli-page-spring-boot-starter` to your build registers a fully-configured `PoliPageClient` bean, an `ApplicationEventPublisher` bridge for retry/error hooks, an Actuator `HealthIndicator`, and a Micrometer `MeterBinder` — all conditionally, so the starter never fights a configuration you've already declared.

The mechanism is the modern `@AutoConfiguration` annotation plus `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, the post-2.7 replacement for `spring.factories`. Every starter-provided bean is wrapped in `@ConditionalOnMissingBean`, so a `@Bean PoliPageClient` in your own configuration silently takes over.

## How

### What the imports file activates

`poli-page-spring-boot-autoconfigure` ships this single line:

```
page.poli.sdk.spring.PoliPageAutoConfiguration
```

The MVC starter adds:

```
page.poli.sdk.spring.web.PoliPageWebMvcAutoConfiguration
```

The WebFlux starter adds:

```
page.poli.sdk.spring.reactive.PoliPageWebFluxAutoConfiguration
```

Three classes, three imports files. Order is declared on the classes themselves, not in the file.

### The conditional chain

```java
@AutoConfiguration
@EnableConfigurationProperties(PoliPageProperties.class)
@ConditionalOnClass(PoliPageClient.class)
public class PoliPageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PoliPageClient poliPageClient(
            PoliPageProperties props,
            ObjectProvider<PoliPageEventBridge> bridge) {
        PoliPageClient.Builder b = PoliPageClient.builder().apiKey(props.apiKey());
        if (props.baseUrl() != null) b.baseUrl(props.baseUrl());
        if (props.requestTimeout() != null) b.requestTimeout(props.requestTimeout());
        if (props.retries().maxAttempts() != null) b.maxRetries(props.retries().maxAttempts());
        if (props.retries().delay() != null) b.retryDelay(props.retries().delay());
        bridge.ifAvailable(eb -> {
            b.onRetry(eb::onRetry);
            b.onError(eb::onError);
        });
        return b.build();
    }
}
```

Three conditionals fire in order:

1. **`@ConditionalOnClass(PoliPageClient.class)`** — short-circuits the entire autoconfig when the SDK is absent (e.g. someone added the starter without `page.poli:sdk` resolving). The autoconfig class is silently skipped; no bean failures.
2. **`@EnableConfigurationProperties`** — binds `poli-page.*` properties to the record and validates them. A bad `api-key` (missing the `pp_` prefix) fails at startup with a clear message.
3. **`@ConditionalOnMissingBean`** on the `@Bean` method — yields to your own `@Bean PoliPageClient` if you've declared one.

### The MVC and WebFlux autoconfigs

```java
@AutoConfiguration(after = PoliPageAutoConfiguration.class)
@ConditionalOnClass({ PoliPageClient.class, RestController.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PoliPageWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PoliPageResponses poliPageResponses() {
        return new PoliPageResponses();
    }
}
```

Two conditionals worth highlighting:

- **`@ConditionalOnWebApplication(type = SERVLET)`** — the MVC starter activates `PoliPageResponses` only when Spring MVC is the runtime web stack. A reactive-only app gets `PoliPageReactiveResponses` (from the WebFlux starter) instead, and neither autoconfig clashes with the other.
- **`@AutoConfiguration(after = ...)`** — the bean ordering means the MVC autoconfig wires its helper after `PoliPageClient` is already a singleton in the context, so a constructor-injected `PoliPageResponses` can keep working even if a future revision needs the client.

## Overriding the client

Declare your own `@Bean PoliPageClient` and the starter's bean recedes. Useful for:

- Custom retry callbacks with logic you don't want to express as an `@EventListener`.
- A custom `HttpClient` instance shared with another integration.
- Sentry breadcrumb instrumentation wrapped around the SDK builder.

```java
@Configuration
public class PoliPageConfig {

    @Bean
    PoliPageClient poliPageClient(PoliPageProperties props, Sentry sentry) {
        return PoliPageClient.builder()
            .apiKey(props.apiKey())
            .onError(t -> sentry.captureException(t))
            .build();
    }
}
```

The starter's `PoliPageProperties` bean is still on the context, so you can read configured values from `application.yml` while controlling the build steps yourself. The retry/error bridge is bypassed entirely — if you want both your callback and the Spring `ApplicationEvent`, call `publisher.publishEvent(...)` from inside your callback.

## Disabling the autoconfig

Two ways to silence the starter for a specific scope:

### Per-property toggles

```yaml
poli-page:
  events:
    enabled: false      # remove the ApplicationEventPublisher bridge
  health:
    enabled: false      # remove the HealthIndicator
  metrics:
    enabled: false      # remove the Micrometer MeterBinder
```

These flags read `@ConditionalOnProperty(value="...", matchIfMissing=true)`, so omitting them is equivalent to `=true`.

### Full opt-out via Spring's exclude list

```yaml
spring:
  autoconfigure:
    exclude:
      - page.poli.sdk.spring.PoliPageAutoConfiguration
      - page.poli.sdk.spring.web.PoliPageWebMvcAutoConfiguration
```

Or in code:

```java
@SpringBootApplication(exclude = {
    PoliPageAutoConfiguration.class,
    PoliPageWebMvcAutoConfiguration.class,
})
public class MyApplication { }
```

The starter then ships zero beans and `PoliPageProperties` is not bound. Use this when your app shares the JVM with the SDK but wires it manually (a `@Configuration` you ship yourself).

## Inspecting what fired

Spring Boot's `/actuator/conditions` endpoint shows every autoconfig the starter contributes and why it matched (or didn't). With actuator on the classpath:

```
curl localhost:8080/actuator/conditions | jq '.contexts.application.positiveMatches | with_entries(select(.key | startswith("PoliPage")))'
```

A match looks like:

```json
{
  "PoliPageAutoConfiguration": [
    { "condition": "OnClassCondition", "message": "@ConditionalOnClass found required class 'page.poli.sdk.PoliPageClient'" }
  ],
  "PoliPageAutoConfiguration#poliPageClient": [
    { "condition": "OnBeanCondition", "message": "@ConditionalOnMissingBean (types: page.poli.sdk.PoliPageClient; SearchStrategy: all) did not find any beans" }
  ]
}
```

If the starter isn't activating, this is the first place to look — usually a missing SDK jar (`OnClassCondition` reports the missing class) or a user-defined `@Bean PoliPageClient` already in scope (the `OnBeanCondition` message names the existing bean).

## Gotchas

- **The autoconfig respects the SDK's defaults.** Properties unset in `application.yml` resolve to `null` in the `PoliPageProperties` record, and the builder's `if (props.x() != null)` guard skips the corresponding setter. So unset properties pass through to the SDK's own `PoliPageClient.Builder` defaults — a single source of truth that survives SDK version bumps without a starter release.
- **Multiple starters in the same app pick the right web stack.** Including both `poli-page-spring-boot-starter` and `poli-page-spring-boot-starter-webflux` is supported (e.g. for a hybrid migration), but only one of `PoliPageWebMvcAutoConfiguration` / `PoliPageWebFluxAutoConfiguration` activates per application — gated on `@ConditionalOnWebApplication(type = ...)`. The other is silently inert.
- **`@ConditionalOnMissingBean` is type-strict.** A user-defined bean must have type `PoliPageClient` (or a subclass) to suppress the starter's bean. A `@Bean Object myPoliPageClient` would NOT count — register it with the right return type.
- **AOT / Spring Native is best-effort in v0.1.** The autoconfig uses reflection on Jackson types via the SDK; native-image users may need `@ImportRuntimeHints` to register additional reflection metadata. Tracked as v0.2 work in [spec §17](spec/spring-boot-starter-specification.md#17-deferred-to-v02-do-not-build-in-v010).

## Related

- [Configuration properties](configuration-properties.md) — how `application.yml` keys reach the autoconfig.
- [Events](events.md) — what the `PoliPageEventBridge` does when `poli-page.events.enabled=true`.
- [Actuator](actuator.md) — the health indicator and metrics that ride alongside the client bean.
- [spec/spring-boot-starter-specification.md §7](spec/spring-boot-starter-specification.md#7-di--bean-registration) — full bean map and conditional rules.
