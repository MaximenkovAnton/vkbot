# AGENTS.md — constitution for this repository

<!-- bmad:context -->
<!-- Verified 2026-11-05 against 8b80e58. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## vkbot

VK community bot: one Quarkus/Kotlin deployable (`:src:app`) across 11 Gradle modules, hexagonal — plain Kotlin behind `share` ports, framework at the edge. Runtime path: `VK webhook → receiver → RabbitMQ → persistence → processor → ai → vk-facade → VK API`. Docs index: `docs/README.md`; design in `docs/architecture/`, decisions in `docs/adr/`, product record in `docs/capabilities/`; planning artifacts go to `_bmad-output/`. Code is the source of truth: when this block and the code disagree, fix the block in the same change.

## Policy

- Never commit secrets or `.env`; runtime values come from environment variables (ADR 0006).
- Never log secrets, tokens or full webhook payloads.
- Never delete an accepted ADR — add a successor with `supersedes` and update `docs/README.md`.
- Never edit an applied Liquibase changeset — add the next one.
- Never add a second deployable or Quarkus runner; the artifact is `:src:app` (ADR 0002).
- Never bump a version without updating its ADR (ADR 0001).
- Never add a Gradle module or a production dependency outside the allowlist without updating `GradleDependenciesTest`, `ModuleDependenciesTest` and ADR 0003.
- Never hand-edit `_bmad/scripts` — the `bmad` setup regenerates them.
- Never claim a gate passes without its command output, and never document an endpoint, command or config key the code does not have.

## Where things are

- Deployable and composition root: `src/app`; business modules and shared contracts: `src/*`, `src/share`.
- Before editing a module, read its section in `docs/architecture/modules.md` — responsibility, public API, dependencies.
- A behaviour change updates `docs/capabilities/` and the matching `docs/` page in the same change.
- Architecture rules live as tests in `src/testing/arch-tests`; fakes live in `src/testing/test-fixtures` and in `src/<module>/src/testFixtures` for `ai`, `persistence`, `processor` and `receiver`.

## Running and verifying

- `./gradlew build` is the full gate: compile, tests, detekt, ktlintCheck, coverage threshold, capability check, `:src:app:quarkusBuild`.
- One test class runs module-scoped: `./gradlew :src:<module>:test --tests "*ClassName*"`; a root-level `test --tests` fails where a module has no match.
- Docker is required — DevServices for PostgreSQL, RabbitMQ and Ollama, and every `@QuarkusTest`.
- There is no root `quarkusDev`/`quarkusBuild`; both are `:src:app:` tasks.
- `./gradlew jacocoTestReport` fails; use `coverageReport` and `coverageVerification`.

## Conventions that differ from defaults

- Flow is fixed: `adapter input → port input → usecase → command → port output → adapter output`. A business module imports only `share`; `:src:app` imports all. ArchUnit fails the build on a wrong import — fix the import, never the rule.
- One operation per port (`fun interface OutputPort<REQ, RESP>`), commands as `Command<REQ, RESP>`, consumers as `EventProcessor`. `@Tool` sits on an adapter class, never on a command.
- Cross-module synchronous calls go through in-process `share` ports; never a loopback HTTP call, never another module's use case.
- Tests use fakes, never mocks, and build data through `*Provider` factories with a default for every parameter — a direct `Type(...)` in a test fails `TestDataFactoriesTest`.
- Write no comments unless neighbouring code documents itself that way; the decision belongs in the ADR or the plan.

## Known pitfalls

- `share/domain/exception/ValidationException` is a plain `RuntimeException`, not a `VkBotAppException`: it maps to 503, not 400. Only `receiver`'s `ValidationException` returns 400.
- `SUMMARY_ENABLED_CHATS` is optional: an unset or empty value starts the app with summarization silently off — set the peer ids whose chats get summarized.
- `/q/health` reports DOWN because declared channels have no downstream consumer; the webhook itself works — treat health as informational.
- A behaviour change that skips the docs leaves the product record stale: `./scripts/check-capabilities.sh` catches broken paths and ADR ids, not stale prose.

<!-- /bmad:context -->
