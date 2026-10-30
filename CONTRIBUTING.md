# Contributing to Poli Page for Spring Boot

Thanks for your interest in improving this starter. This guide covers setup, tests, and conventions.

## Setup

The build resolves every dependency, the SDK `page.poli:sdk:0.11.0` included, from Maven Central.

Set `POLI_PAGE_API_KEY` to a sandbox key before running integration tests:

```bash
export POLI_PAGE_API_KEY=pp_sandbox_…
```

## Tests

Unit tests across all modules:

```bash
./gradlew --no-daemon test
```

Per-module:

```bash
./gradlew --no-daemon :poli-page-spring-boot-autoconfigure:test
./gradlew --no-daemon :poli-page-spring-boot-starter:test
./gradlew --no-daemon :poli-page-spring-boot-starter-webflux:test
```

### Contract tests

`ContractTest` in each example app runs the `impl` cases of the [Poli Page API contract](https://github.com/poli-page/api-contract) against that app: a local server plays the API, the app's SDK points at it through `poli-page.base-url`, and the test compares the app's answer and the SDK's requests with each case. CI runs them against the contract version pinned in `.github/workflows/ci.yml`. Locally, point `POLI_CONTRACT` at a `contract.json`:

```bash
POLI_CONTRACT=<path to contract.json> ./gradlew --no-daemon :example-app:mvc:test :example-app:webflux:test
```

Without `POLI_CONTRACT` they are skipped locally and fail under CI. A case that may fail is listed, with its reason, in the app's `src/test/resources/page/poli/example/<app>/contract-skip.json`; a listed case that passes fails the run.

### Integration tests

Integration tests run only when `POLI_PAGE_API_KEY` is a sandbox key (`pp_sandbox_…`). They are skipped when it is unset, so PR contributors without a key still get a green local run, and when it belongs to any other environment, so they never render into a counted one. Set `POLI_PAGE_TEST_BASE_URL` to target an API deployment other than the SDK default.

## Lint, format, build

```bash
./gradlew --no-daemon spotlessCheck    # google-java-format compliance
./gradlew --no-daemon spotlessApply    # auto-format
./gradlew --no-daemon build            # spotless + compile + test + assemble
```

CI matrix: Java 17 / 21 × Spring Boot 3.2.+ / 3.3.+ / 3.4.+ (6 cells). All must be green before merge.

## Pull requests

- [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`.
- One concern per PR, reviewable in under 30 minutes.
- PR description: what changed, why, how it was tested.
- CI must be green on all 6 matrix cells before merge.

## Reporting issues

Open an issue at [github.com/poli-page/spring-boot/issues](https://github.com/poli-page/spring-boot/issues) with:

- The Spring Boot version and Java version you're on
- A minimal reproducer (a snippet of `application.yml` + a stripped-down controller is usually enough)
- For runtime failures, the relevant stack trace and the `requestId` from the `PoliPageException` if one was thrown

For security-sensitive reports, email security@poli.page instead of opening a public issue.

## License

By contributing, you agree your contributions will be licensed under the [MIT License](LICENSE).
