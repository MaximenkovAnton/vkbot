# Task report: Подготовка проекта: vk

- Task: `8916d998-880d-48cf-9f56-d1ae067f17d7`
- Description: Человек нажал «Подготовить проект». Скан уже сделан и вложен ниже; карточка сразу ставит задачу на доску project-setup — в планировании проект не исследуется, это делает стадия аудита.
Проект: vk — /Users/asmaksime1/IdeaProjects/test/vk
Built-in defaults for a new project. There is no template directory and nothing to copy: the knowledge lives here, the files are produced from it.
- Principle: The framework is an adapter, never the architecture. In every kind of product the behaviour lives in plain code behind interfaces: the UI framework, the HTTP client, the database, the store, the device API and the platform are adapters a test can replace with a fake, and a linter or an architecture test fails the build when an import crosses a boundary. The contracts at the edges are typed or generated from the source of truth (an API schema, a domain model), so a change on one side breaks the build instead of the product. That is what keeps quality and reliability steady while the ecosystem moves.
- Ask the kind of product first, then the language, then the stack of that language. Kinds:
backend service or API:
- Shape: hexagonal monolith: one deployable, the horizontal layers as modules or packages, the behaviour behind ports
- Layers:
  - domain: entities, value objects, domain services, domain errors; imports no framework and no other layer
  - port.in: the use-case interfaces the outside world calls; the contract every incoming adapter depends on
  - port.out: the interfaces the business logic needs from outside — repositories, clients, messaging; no implementation
  - application: the use cases implementing port.in over domain and port.out; imports domain and port, nothing else
  - adapter.in: REST controllers, consumers, schedulers, CLI; depend on port.in, never on application internals or a repository
  - adapter.out: repository and client implementations of port.out; depend on port.out and the outside system, nothing else
  - config: the composition root: the only place that binds port.in to application and port.out to adapter.out
- Rules the build enforces:
  - The domain and the application never import an adapter, a framework beyond dependency injection, a database client or a REST type.
  - An incoming adapter never queries a database directly: it calls port.in, and the use case reaches storage through port.out.
  - No adapter imports another adapter; the only code that sees both sides is config.
  - Every dependency of the business logic on the outside world is an interface in port.out; every entry point is an interface in port.in.
- Boundaries:
  - Separate the layers into modules or source sets when the builder does it cheaply (Gradle, Maven, .NET projects): the compiler then makes a wrong import impossible.
  - Where modules are expensive, the layers stay packages and the architecture tests are the only thing between them.
  - Never a module or a deployable per feature: the layers are horizontal, the features are vertical and live in the same deployable until a requirement says otherwise.
- Gates that prove the project works:
  - build of the deployable
  - unit and integration tests
  - lint and format check
  - coverage report with a threshold that fails the build
  - architecture tests for the dependency direction between the layers
  - CI running all of the above on push and pull request
- Stacks by language (name the top ones, recommend the first, let the human pick or name another):
  Java (the current JDK LTS): builders Gradle with the Kotlin DSL | Maven; frameworks Spring Boot | Quarkus | Micronaut; tests JUnit 5 with Spring Boot Test | Testcontainers for real infrastructure; lint checkstyle | spotless | error-prone; coverage JaCoCo; architecture ArchUnit; conventions records for value types and DTOs, Lombok for constructors and builders, fake objects in tests
  Kotlin (the current JDK LTS): builders Gradle with the Kotlin DSL | Maven; frameworks Spring Boot | Ktor | Quarkus; tests JUnit 5 with Spring Boot Test | Kotest; lint detekt | ktlint; coverage JaCoCo; architecture Konsist | ArchUnit; conventions data classes for value types, sealed types for closed results, fake objects in tests
  TypeScript (the current Node.js LTS): builders pnpm | npm | yarn; frameworks NestJS | Fastify | Express; tests Vitest | Jest; lint ESLint with typescript-eslint | Prettier | Biome; coverage the v8 coverage of the test runner; architecture dependency-cruiser | eslint-plugin-boundaries; conventions strict typing, no any at a trust boundary, validation at the edge, fakes instead of module mocks
  Python (the current Python 3): builders uv | poetry | pip with a virtualenv; frameworks FastAPI | Django | Flask; tests pytest; lint ruff | mypy; coverage pytest-cov; architecture import-linter | pytest-archon; conventions type hints, dataclasses or pydantic at the boundary, fakes instead of Mock
  Go (the current Go release): builders go modules; frameworks net/http | Gin | Echo; tests go test | testify; lint golangci-lint | go vet; coverage go test -coverprofile; architecture depguard | go-arch-lint; conventions errors as values, interfaces only at the consumer, hand-written fakes
  C# (the current .NET LTS): builders the dotnet CLI | MSBuild; frameworks ASP.NET Core | Minimal APIs; tests xUnit | NUnit; lint dotnet format | Roslyn analyzers; coverage coverlet; architecture NetArchTest; conventions records for value types, nullable reference types enabled, fakes instead of a mocking framework

web frontend (SPA or SSR):
- Shape: feature-sliced: layers from the entry point down to the shared kit, one slice per feature, a typed API client at the edge
- Layers:
  - app: the composition root: providers, routing, theme, global error handling, the API client wiring
  - pages: the route level: which widgets and features a screen shows and in what order; no business logic
  - widgets: self-contained blocks of the screen made of features and entities (header, feed, form shell)
  - features: one user action end to end: its model, its ui and the api calls of that action
  - entities: a business entity: its model, its ui, the api calls that read and change it
  - shared: framework glue, the ui kit, configuration, and the typed API client generated from the backend contract
- Rules the build enforces:
  - A layer imports only the layers below it — app, pages, widgets, features, entities, shared — and never a slice of its own layer.
  - A view never calls the network or storage directly: it calls the model of its feature or entity, and that model calls the typed API client.
  - The API client is generated or wrapped from the backend contract (OpenAPI, GraphQL) and is the only code that knows URLs, DTOs and transport; map DTOs to domain types at that edge.
  - Server state lives in the data-fetching cache, client state in one store; a component reads one of them and never mirrors the other.
  - Routes, permissions and feature flags are resolved in app or pages, not inside a feature ui.
- Boundaries:
  - One folder per slice with a public API (index.ts): a deep import into another slice is a build error.
  - The boundary linter runs in the build (Steiger for feature-sliced, eslint-plugin-boundaries or dependency-cruiser) and fails on a cross-import.
  - A slice may be a separate package in a monorepo when the build supports it; the public API of the slice stays the same.
- Gates that prove the project works:
  - typecheck (tsc --noEmit)
  - lint and format check
  - boundary lint for the layer and slice imports
  - unit and component tests of the models and the ui kit
  - end-to-end tests of the critical paths
  - visual check of the ui kit and the pages (screenshot tests)
  - accessibility check of the critical screens (axe or pa11y)
  - coverage threshold on the models, not on the glue
  - bundle size budget for the first load
  - CI running all of the above on push and pull request
- Stacks by language (name the top ones, recommend the first, let the human pick or name another):
  TypeScript (the current Node.js LTS): builders Vite | Next.js | Nuxt | SvelteKit | the Angular CLI; frameworks React | Vue | Svelte | Angular; tests Vitest with Testing Library | Playwright for end-to-end | Storybook for the ui kit; lint ESLint with typescript-eslint | Prettier | Biome; coverage the v8 coverage of the test runner; architecture Steiger (feature-sliced) | eslint-plugin-boundaries | dependency-cruiser; conventions strict typing, no any at a boundary, a schema validator at the edge, fakes instead of module mocks

mobile app (Android, iOS or cross-platform):
- Shape: clean architecture with unidirectional state: data and domain behind contracts, presentation above them, the platform as an adapter
- Layers:
  - domain: entities, use cases and repository contracts; no platform, UI, HTTP or database type
  - data: repositories implementing the domain contracts plus the data sources they use: HTTP, local database, device APIs
  - presentation: state holders (view model, store or bloc) and views; the state flows one way: event in, state out
  - platform: the platform adapter behind domain interfaces: navigation, permissions, push, background work, secure storage
  - di: the composition root: the only place that binds the contracts to the implementations
- Rules the build enforces:
  - A view never calls a repository, an HTTP client, a database or a device API: it observes its state holder and sends it events.
  - A state holder depends on use cases or domain contracts, never on a concrete data source or a framework type.
  - Only data implements the repository contracts; domain and presentation know only the interfaces.
  - Platform capabilities sit behind interfaces, so a test replaces them with a fake.
  - State is one immutable value per screen: the view derives from it and never mutates it.
- Boundaries:
  - Where the build supports modules cheaply (Gradle, SwiftPM, Dart packages), data, domain and presentation are modules, so a wrong import does not compile; otherwise packages with architecture tests.
  - Architecture tests per rule: Konsist or ArchUnit on the JVM, custom_lint and very_good_analysis for Dart, SwiftLint plus a dependency-direction test for Swift, NetArchTest for .NET MAUI.
- Gates that prove the project works:
  - build of a debug and a release artifact
  - lint and format check
  - architecture tests for the dependency direction
  - unit tests of the domain and the state holders
  - UI tests of the critical screens
  - screenshot or golden tests of the ui kit
  - coverage threshold on the domain and the state holders
  - CI that builds and runs all of the above on push and pull request
- Stacks by language (name the top ones, recommend the first, let the human pick or name another):
  Kotlin (Android) (the current JDK and Android SDK): builders Gradle with the Kotlin DSL; frameworks Jetpack Compose | Views with a state holder; tests JUnit with the Compose UI test | Roborazzi or Paparazzi for screenshots | Maestro for end-to-end; lint detekt | ktlint | Android Lint; coverage Kover or JaCoCo; architecture Konsist | ArchUnit; conventions one view model per screen, immutable UI state, state in and events out, fake objects instead of mocks
  Swift (iOS) (the current Swift and SDK): builders Xcode with SwiftPM; frameworks SwiftUI with MVVM | The Composable Architecture; tests Swift Testing or XCTest | snapshot tests | XCUITest or Maestro; lint SwiftFormat | SwiftLint; coverage xccov; architecture SwiftLint rules with module boundaries | a test for the dependency direction; conventions immutable state, one store or view model per screen, typed navigation, fakes instead of mocks
  Dart (Flutter) (the current Flutter SDK): builders the flutter CLI | pub with melos for a monorepo; frameworks Flutter with Riverpod, Bloc or provider; tests flutter test with widget tests | golden tests | integration_test; lint flutter analyze | dart format | very_good_analysis or custom_lint; coverage flutter test --coverage with lcov; architecture custom_lint rules | package boundaries; conventions immutable state, one notifier or bloc per screen, repositories behind contracts, fakes instead of mocks
  TypeScript (React Native with Expo) (the current React Native and Node.js LTS): builders Metro with Expo | the React Native CLI; frameworks React Native with React Navigation; tests Jest or Vitest with React Native Testing Library | Maestro or Detox; lint ESLint | Prettier or Biome; coverage the coverage of the test runner; architecture eslint-plugin-boundaries | dependency-cruiser; conventions strict typing, typed navigation params, fake adapters instead of module mocks
- Versions: Ask the human, or take the current stable release of every chosen tool at the moment the project is created, then record what was resolved in the build file, the lock file and the ADR. Never copy a version out of this profile.
- Commands: Read them from the builder the design chose — the task list it exposes, the scripts it declares, the plugins it applies — and name the ones that exist: build, test, lint, format-check, coverage, and the extra gates of the kind. Prove each one runs and quote it.
- Documents: The set and the rules for it are the agent contracts, not a list here: AGENTS.md is the constitution every later session reads first, docs/adr/ holds the accepted decisions with successors that carry supersedes, docs/architecture/ describes the code as built, docs/capabilities/ is the product record the analyst writes from the code in its own stage against the analyst contract in its goal, docs/development/ says how to test, configure and deploy. The design stage names the exact files, one line each on what it answers; each document is written by the agent whose contract defines it, so a changed contract changes the document with no second copy to keep in step.
Найдено: settings.gradle.kts (gradle), build.gradle.kts (gradle); языки: kotlin; модули: src:ai, src:app, src:infrastructure, src:persistence, src:processor, src:share, src:testing:test-common, src:testing:test-fixtures.
Документы: AGENTS.md — да; docs/architecture — 5; docs/adr — 0; docs/capabilities — 0; docs/development — 3.
Гейты сейчас: build=./gradlew build, test=./gradlew test, lint=./gradlew detekt, format=./gradlew ktlintCheck, coverage=./gradlew jacocoTestReport, ci=.github/workflows/build-and-test.yml.
Не хватает: adr, capabilities; вердикт сканера: partial.
План: один таск доски project-setup — аудит → дизайн → реализация → архитектурные документы (архитектор) → карта возможностей (аналитик) → проверка → ревью; агент исполнения — по стеку из сканера (kotlin-dev / typescript-dev / react-dev / infra-dev / specialist).
Готово, когда: сборка проходит; тесты запускаются одной командой и дают отчёт покрытия, порог валит сборку; линтер валит сборку; CI запускает build + lint + test + coverage; AGENTS.md содержит выполненные команды и карту модулей; docs/architecture и docs/development описывают код со ссылками; docs/capabilities содержит строку на каждую существующую возможность, а её чек падает на пути и ADR id, которых в коде больше нет; каждый противоречащий ADR получил преемника, история решений сохранена.
- Workspace: /Users/asmaksime1/IdeaProjects/test/vk
- Status: running-tasks

## Board tasks

### Подготовка проекта: vk (`3f1f83e5-b597-40ed-a911-ca9c12ef77c6`) — done

Человек нажал «Подготовить проект». Скан уже сделан и вложен ниже; карточка сразу ставит задачу на доску project-setup — в планировании проект не исследуется, это делает стадия аудита.
Проект: vk — /Users/asmaksime1/IdeaProjects/test/vk
Built-in defaults for a new project. There is no template directory and nothing to copy: the knowledge lives here, the files are produced from it.
- Principle: The framework is an adapter, never the architecture. In every kind of product the behaviour lives in plain code behind interfaces: the UI framework, the HTTP client, the database, the store, the device API and the platform are adapters a test can replace with a fake, and a linter or an architecture test fails the build when an import crosses a boundary. The contracts at the edges are typed or generated from the source of truth (an API schema, a domain model), so a change on one side breaks the build instead of the product. That is what keeps quality and reliability steady while the ecosystem moves.
- Ask the kind of product first, then the language, then the stack of that language. Kinds:
backend service or API:
- Shape: hexagonal monolith: one deployable, the horizontal layers as modules or packages, the behaviour behind ports
- Layers:
  - domain: entities, value objects, domain services, domain errors; imports no framework and no other layer
  - port.in: the use-case interfaces the outside world calls; the contract every incoming adapter depends on
  - port.out: the interfaces the business logic needs from outside — repositories, clients, messaging; no implementation
  - application: the use cases implementing port.in over domain and port.out; imports domain and port, nothing else
  - adapter.in: REST controllers, consumers, schedulers, CLI; depend on port.in, never on application internals or a repository
  - adapter.out: repository and client implementations of port.out; depend on port.out and the outside system, nothing else
  - config: the composition root: the only place that binds port.in to application and port.out to adapter.out
- Rules the build enforces:
  - The domain and the application never import an adapter, a framework beyond dependency injection, a database client or a REST type.
  - An incoming adapter never queries a database directly: it calls port.in, and the use case reaches storage through port.out.
  - No adapter imports another adapter; the only code that sees both sides is config.
  - Every dependency of the business logic on the outside world is an interface in port.out; every entry point is an interface in port.in.
- Boundaries:
  - Separate the layers into modules or source sets when the builder does it cheaply (Gradle, Maven, .NET projects): the compiler then makes a wrong import impossible.
  - Where modules are expensive, the layers stay packages and the architecture tests are the only thing between them.
  - Never a module or a deployable per feature: the layers are horizontal, the features are vertical and live in the same deployable until a requirement says otherwise.
- Gates that prove the project works:
  - build of the deployable
  - unit and integration tests
  - lint and format check
  - coverage report with a threshold that fails the build
  - architecture tests for the dependency direction between the layers
  - CI running all of the above on push and pull request
- Stacks by language (name the top ones, recommend the first, let the human pick or name another):
  Java (the current JDK LTS): builders Gradle with the Kotlin DSL | Maven; frameworks Spring Boot | Quarkus | Micronaut; tests JUnit 5 with Spring Boot Test | Testcontainers for real infrastructure; lint checkstyle | spotless | error-prone; coverage JaCoCo; architecture ArchUnit; conventions records for value types and DTOs, Lombok for constructors and builders, fake objects in tests
  Kotlin (the current JDK LTS): builders Gradle with the Kotlin DSL | Maven; frameworks Spring Boot | Ktor | Quarkus; tests JUnit 5 with Spring Boot Test | Kotest; lint detekt | ktlint; coverage JaCoCo; architecture Konsist | ArchUnit; conventions data classes for value types, sealed types for closed results, fake objects in tests
  TypeScript (the current Node.js LTS): builders pnpm | npm | yarn; frameworks NestJS | Fastify | Express; tests Vitest | Jest; lint ESLint with typescript-eslint | Prettier | Biome; coverage the v8 coverage of the test runner; architecture dependency-cruiser | eslint-plugin-boundaries; conventions strict typing, no any at a trust boundary, validation at the edge, fakes instead of module mocks
  Python (the current Python 3): builders uv | poetry | pip with a virtualenv; frameworks FastAPI | Django | Flask; tests pytest; lint ruff | mypy; coverage pytest-cov; architecture import-linter | pytest-archon; conventions type hints, dataclasses or pydantic at the boundary, fakes instead of Mock
  Go (the current Go release): builders go modules; frameworks net/http | Gin | Echo; tests go test | testify; lint golangci-lint | go vet; coverage go test -coverprofile; architecture depguard | go-arch-lint; conventions errors as values, interfaces only at the consumer, hand-written fakes
  C# (the current .NET LTS): builders the dotnet CLI | MSBuild; frameworks ASP.NET Core | Minimal APIs; tests xUnit | NUnit; lint dotnet format | Roslyn analyzers; coverage coverlet; architecture NetArchTest; conventions records for value types, nullable reference types enabled, fakes instead of a mocking framework

web frontend (SPA or SSR):
- Shape: feature-sliced: layers from the entry point down to the shared kit, one slice per feature, a typed API client at the edge
- Layers:
  - app: the composition root: providers, routing, theme, global error handling, the API client wiring
  - pages: the route level: which widgets and features a screen shows and in what order; no business logic
  - widgets: self-contained blocks of the screen made of features and entities (header, feed, form shell)
  - features: one user action end to end: its model, its ui and the api calls of that action
  - entities: a business entity: its model, its ui, the api calls that read and change it
  - shared: framework glue, the ui kit, configuration, and the typed API client generated from the backend contract
- Rules the build enforces:
  - A layer imports only the layers below it — app, pages, widgets, features, entities, shared — and never a slice of its own layer.
  - A view never calls the network or storage directly: it calls the model of its feature or entity, and that model calls the typed API client.
  - The API client is generated or wrapped from the backend contract (OpenAPI, GraphQL) and is the only code that knows URLs, DTOs and transport; map DTOs to domain types at that edge.
  - Server state lives in the data-fetching cache, client state in one store; a component reads one of them and never mirrors the other.
  - Routes, permissions and feature flags are resolved in app or pages, not inside a feature ui.
- Boundaries:
  - One folder per slice with a public API (index.ts): a deep import into another slice is a build error.
  - The boundary linter runs in the build (Steiger for feature-sliced, eslint-plugin-boundaries or dependency-cruiser) and fails on a cross-import.
  - A slice may be a separate package in a monorepo when the build supports it; the public API of the slice stays the same.
- Gates that prove the project works:
  - typecheck (tsc --noEmit)
  - lint and format check
  - boundary lint for the layer and slice imports
  - unit and component tests of the models and the ui kit
  - end-to-end tests of the critical paths
  - visual check of the ui kit and the pages (screenshot tests)
  - accessibility check of the critical screens (axe or pa11y)
  - coverage threshold on the models, not on the glue
  - bundle size budget for the first load
  - CI running all of the above on push and pull request
- Stacks by language (name the top ones, recommend the first, let the human pick or name another):
  TypeScript (the current Node.js LTS): builders Vite | Next.js | Nuxt | SvelteKit | the Angular CLI; frameworks React | Vue | Svelte | Angular; tests Vitest with Testing Library | Playwright for end-to-end | Storybook for the ui kit; lint ESLint with typescript-eslint | Prettier | Biome; coverage the v8 coverage of the test runner; architecture Steiger (feature-sliced) | eslint-plugin-boundaries | dependency-cruiser; conventions strict typing, no any at a boundary, a schema validator at the edge, fakes instead of module mocks

mobile app (Android, iOS or cross-platform):
- Shape: clean architecture with unidirectional state: data and domain behind contracts, presentation above them, the platform as an adapter
- Layers:
  - domain: entities, use cases and repository contracts; no platform, UI, HTTP or database type
  - data: repositories implementing the domain contracts plus the data sources they use: HTTP, local database, device APIs
  - presentation: state holders (view model, store or bloc) and views; the state flows one way: event in, state out
  - platform: the platform adapter behind domain interfaces: navigation, permissions, push, background work, secure storage
  - di: the composition root: the only place that binds the contracts to the implementations
- Rules the build enforces:
  - A view never calls a repository, an HTTP client, a database or a device API: it observes its state holder and sends it events.
  - A state holder depends on use cases or domain contracts, never on a concrete data source or a framework type.
  - Only data implements the repository contracts; domain and presentation know only the interfaces.
  - Platform capabilities sit behind interfaces, so a test replaces them with a fake.
  - State is one immutable value per screen: the view derives from it and never mutates it.
- Boundaries:
  - Where the build supports modules cheaply (Gradle, SwiftPM, Dart packages), data, domain and presentation are modules, so a wrong import does not compile; otherwise packages with architecture tests.
  - Architecture tests per rule: Konsist or ArchUnit on the JVM, custom_lint and very_good_analysis for Dart, SwiftLint plus a dependency-direction test for Swift, NetArchTest for .NET MAUI.
- Gates that prove the project works:
  - build of a debug and a release artifact
  - lint and format check
  - architecture tests for the dependency direction
  - unit tests of the domain and the state holders
  - UI tests of the critical screens
  - screenshot or golden tests of the ui kit
  - coverage threshold on the domain and the state holders
  - CI that builds and runs all of the above on push and pull request
- Stacks by language (name the top ones, recommend the first, let the human pick or name another):
  Kotlin (Android) (the current JDK and Android SDK): builders Gradle with the Kotlin DSL; frameworks Jetpack Compose | Views with a state holder; tests JUnit with the Compose UI test | Roborazzi or Paparazzi for screenshots | Maestro for end-to-end; lint detekt | ktlint | Android Lint; coverage Kover or JaCoCo; architecture Konsist | ArchUnit; conventions one view model per screen, immutable UI state, state in and events out, fake objects instead of mocks
  Swift (iOS) (the current Swift and SDK): builders Xcode with SwiftPM; frameworks SwiftUI with MVVM | The Composable Architecture; tests Swift Testing or XCTest | snapshot tests | XCUITest or Maestro; lint SwiftFormat | SwiftLint; coverage xccov; architecture SwiftLint rules with module boundaries | a test for the dependency direction; conventions immutable state, one store or view model per screen, typed navigation, fakes instead of mocks
  Dart (Flutter) (the current Flutter SDK): builders the flutter CLI | pub with melos for a monorepo; frameworks Flutter with Riverpod, Bloc or provider; tests flutter test with widget tests | golden tests | integration_test; lint flutter analyze | dart format | very_good_analysis or custom_lint; coverage flutter test --coverage with lcov; architecture custom_lint rules | package boundaries; conventions immutable state, one notifier or bloc per screen, repositories behind contracts, fakes instead of mocks
  TypeScript (React Native with Expo) (the current React Native and Node.js LTS): builders Metro with Expo | the React Native CLI; frameworks React Native with React Navigation; tests Jest or Vitest with React Native Testing Library | Maestro or Detox; lint ESLint | Prettier or Biome; coverage the coverage of the test runner; architecture eslint-plugin-boundaries | dependency-cruiser; conventions strict typing, typed navigation params, fake adapters instead of module mocks
- Versions: Ask the human, or take the current stable release of every chosen tool at the moment the project is created, then record what was resolved in the build file, the lock file and the ADR. Never copy a version out of this profile.
- Commands: Read them from the builder the design chose — the task list it exposes, the scripts it declares, the plugins it applies — and name the ones that exist: build, test, lint, format-check, coverage, and the extra gates of the kind. Prove each one runs and quote it.
- Documents: The set and the rules for it are the agent contracts, not a list here: AGENTS.md is the constitution every later session reads first, docs/adr/ holds the accepted decisions with successors that carry supersedes, docs/architecture/ describes the code as built, docs/capabilities/ is the product record the analyst writes from the code in its own stage against the analyst contract in its goal, docs/development/ says how to test, configure and deploy. The design stage names the exact files, one line each on what it answers; each document is written by the agent whose contract defines it, so a changed contract changes the document with no second copy to keep in step.
Найдено: settings.gradle.kts (gradle), build.gradle.kts (gradle); языки: kotlin; модули: src:ai, src:app, src:infrastructure, src:persistence, src:processor, src:share, src:testing:test-common, src:testing:test-fixtures.
Документы: AGENTS.md — да; docs/architecture — 5; docs/adr — 0; docs/capabilities — 0; docs/development — 3.
Гейты сейчас: build=./gradlew build, test=./gradlew test, lint=./gradlew detekt, format=./gradlew ktlintCheck, coverage=./gradlew jacocoTestReport, ci=.github/workflows/build-and-test.yml.
Не хватает: adr, capabilities; вердикт сканера: partial.
План: один таск доски project-setup — аудит → дизайн → реализация → архитектурные документы (архитектор) → карта возможностей (аналитик) → проверка → ревью; агент исполнения — по стеку из сканера (kotlin-dev / typescript-dev / react-dev / infra-dev / specialist).
Готово, когда: сборка проходит; тесты запускаются одной командой и дают отчёт покрытия, порог валит сборку; линтер валит сборку; CI запускает build + lint + test + coverage; AGENTS.md содержит выполненные команды и карту модулей; docs/architecture и docs/development описывают код со ссылками; docs/capabilities содержит строку на каждую существующую возможность, а её чек падает на пути и ADR id, которых в коде больше нет; каждый противоречащий ADR получил преемника, история решений сохранена.

Board: project-setup; stage: review; path: audit → design → implement → docs → capabilities → verify → docs → capabilities → verify → review → implement → verify → review
Reason: manual resume at verify after docs fixes; stage visit limits reset
Artifacts: /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0002-single-deployable-app-module.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/README.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/conversation-summary.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/vk-profiles.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/architecture/modules.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/ai-tools-vane.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/answer-decision.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/event-dispatch.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/vk-webhook.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/development/configuration.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0006-configuration-and-secrets.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/ai-tools-vane.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/capabilities.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/development/testing.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0006-configuration-and-secrets.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/message-storage.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/event-dispatch.md, /Users/asmaksime1/IdeaProjects/test/vk/AGENTS.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/architecture/overview.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/development/deployment.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/README.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/docs.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0005-quality-gates-and-ci.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/index.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/vk-profiles.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/architecture/data-flow.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0001-stack-kotlin-quarkus-gradle.md, /Users/asmaksime1/IdeaProjects/test/vk/README.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/capabilities.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/audit.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/scaffold.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/message-storage.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0005-quality-gates-and-ci.md, /Users/asmaksime1/IdeaProjects/test/vk/README.md, /Users/asmaksime1/IdeaProjects/test/vk/AGENTS.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/audit.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/ai-answer.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/scaffold.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/architecture/modules.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/architecture/data-flow.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/tooling.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0002-single-deployable-app-module.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/index.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/design.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/vk-message-send.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/development/deployment.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/tooling.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/vk-webhook.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0004-in-process-ports-instead-of-loopback-rest.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/review-summary.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0001-stack-kotlin-quarkus-gradle.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/docs.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0003-module-boundaries-and-layer-rules.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/vk-message-send.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/development/testing.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/answer-decision.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0003-module-boundaries-and-layer-rules.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/architecture/overview.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/conversation-summary.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/prepared-report.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/architecture/patterns.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/prepared-report.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/adr/0004-in-process-ports-instead-of-loopback-rest.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/architecture/patterns.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/capabilities/ai-answer.md, /Users/asmaksime1/IdeaProjects/test/vk/docs/development/configuration.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/prepared-report.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/review-summary.md, /Users/asmaksime1/IdeaProjects/test/vk/.agile/tasks/8916d998-880d-48cf-9f56-d1ae067f17d7/board-tasks/3f1f83e5-b597-40ed-a911-ca9c12ef77c6/review-summary.md
Verdicts:
- test-reviewer (round 1): approve — AC-строка в docs/capabilities/<capability>.md должна доказываться тестом, и проверка карты (scripts/check-capabilities.sh) должна падать, если AC ссылается не на тест, а на любой существующий src-путь. [nit]
- security-auditor (round 1): changes — Публичный HTTP-порт деплоя не должен давать анонимный доступ к сохранённой переписке и к отправке сообщений от имени VK-сообщества; граница аутентификации должна быть реализована или явно задеплоена (OWASP A01:2021, API1:2023 BOLA, API5:2023 BFLA; CWE-306). [blocking]; Секрет VK Callback сравнивается constant-time, а полное тело отклонённого вебхука не попадает в логи (CWE-208, CWE-532; правило `AGENTS.md:197` «Do not log … full webhook payloads»). [nit]; Ответы об ошибках не раскрывают внутренние сообщения исключений и являются корректным JSON (CWE-209, CWE-116). [nit]; Публичный `/vk/callback` ограничен по размеру и частоте тела, т.к. JSON парсится до проверки секрета (OWASP API4:2023, CWE-400). [nit]
- security-auditor (round 1): approve — Публичный HTTP-порт деплоя не должен давать анонимный доступ к сохранённой переписке и к отправке сообщений от имени VK-сообщества; граница аутентификации должна быть реализована или явно задеплоена (OWASP A01:2021, API1:2023 BOLA, API5:2023 BFLA; CWE-306). [nit]; Секрет VK Callback сравнивается constant-time, а полное тело отклонённого вебхука не попадает в логи (CWE-208, CWE-532; правило `AGENTS.md:197` «Do not log … full webhook payloads»). [nit]; Ответы об ошибках не раскрывают внутренние сообщения исключений и являются корректным JSON (CWE-209, CWE-116). [nit]; Публичный `/vk/callback` ограничен по размеру и частоте тела, т.к. JSON парсится до проверки секрета (OWASP API4:2023, CWE-400). [nit]
- code-reviewer (round 1): changes — ADR 0004 / in-process refactor: loopback-REST-контуры между модулями удалены полностью и в дереве не остаётся мёртвой REST-обвязки — ни адаптеров, ни конфига клиентов, ни заглушек удалённых эндпоинтов. Интеграционный тест должен отличать in-process проводку от возврата HTTP. [blocking]; Переход business-логирования с io.quarkus.logging.Log на java.lang.System.Logger (ADR 0003) не должен менять уровень сообщений: рефакторинг ради L3 сохраняет наблюдаемое поведение. [nit]; Документы должны называть тест, который реально ловит правило: L1–L5 живут в LayerDependenciesTest, L6 — в ModuleDependenciesTest. [nit]; Индекс ADR не должен утверждать больше, чем ADR: не все мёртвые ключи конфига удалены — часть зафиксирована как долг. [nit]; Ссылки на диапазоны строк в таблицах версий должны покрывать перечисленные артефакты. [nit]
- test-reviewer (round 2): approve — AC evidence must be what the cited test actually asserts (ledger honesty) [nit]; Coverage numbers in the test documentation must match the coverage artifact [nit]; Test name must describe the check it performs [nit]
- security-auditor (round 2): approve — Публичный HTTP-порт деплоя не должен давать анонимный доступ к сохранённой переписке и к отправке сообщений от имени VK-сообщества (OWASP A01:2021, API1:2023 BOLA, API5:2023 BFLA; CWE-306). [nit]; Секрет VK Callback сравнивается constant-time, а полное тело отклонённого вебхука не попадает в логи (CWE-208, CWE-532; `AGENTS.md` §9 «Do not log … full webhook payloads»). [nit]; Ответы об ошибках не раскрывают внутренние сообщения исключений и являются корректным JSON (CWE-209, CWE-116). [nit]; Публичный `/vk/callback` ограничен по размеру и частоте тела, т.к. JSON парсится до проверки секрета (OWASP API4:2023, CWE-400). [nit]; Текст переписки и ответов LLM (PII пользователей) не должен без нужды покидать деплой в наблюдаемость (CWE-532; OWASP A09:2021). [nit]; Служебные эндпоинты единственного деплоя (`/q/info`, `/q/health*`) не должны отдавать метаданные сборки анонимно на публичном порту (OWASP A05:2021 Security Misconfiguration, CWE-200). [nit]; CI-пайплайн не должен позволять подмену стороннего action или неограниченный по времени прогон (CWE-1357, supply chain). [nit]; Охват проверки (не находка): перечисление классов, проверенных в раунде 2 и признанных чистыми. [nit]
- code-reviewer (round 2): approve — Docs claims about gates must name where the gate actually runs (docs-claims-vs-code). [nit]; Doc line references must point at what the sentence claims (docs-claims-vs-code). [nit]
- test-reviewer (round 2): approve — AC evidence must be what the cited test actually asserts (ledger honesty) [nit]; Coverage numbers in the test documentation must match the coverage artifact [nit]; Test name must describe the check it performs [nit]
- code-reviewer (round 2): approve — Docs claims about gates must name where the gate actually runs (docs-claims-vs-code). [nit]; Doc line references must point at what the sentence claims (docs-claims-vs-code). [nit]
- security-auditor (round 2): approve — Публичный HTTP-порт деплоя не должен давать анонимный доступ к сохранённой переписке и к отправке сообщений от имени VK-сообщества (OWASP A01:2021, API1:2023 BOLA, API5:2023 BFLA; CWE-306). [nit]; Секрет VK Callback сравнивается constant-time, а полное тело отклонённого вебхука не попадает в логи (CWE-208, CWE-532; `AGENTS.md` §9 «Do not log … full webhook payloads»). [nit]; Ответы об ошибках не раскрывают внутренние сообщения исключений и являются корректным JSON (CWE-209, CWE-116). [nit]; Публичный `/vk/callback` ограничен по размеру и частоте тела, т.к. JSON парсится до проверки секрета (OWASP API4:2023, CWE-400). [nit]; Текст переписки и ответов LLM (PII пользователей) не должен без нужды покидать деплой в наблюдаемость (CWE-532; OWASP A09:2021). [nit]; Служебные эндпоинты единственного деплоя (`/q/info`, `/q/health*`) не должны отдавать метаданные сборки анонимно на публичном порту (OWASP A05:2021 Security Misconfiguration, CWE-200). [nit]; CI-пайплайн не должен позволять подмену стороннего action или неограниченный по времени прогон (CWE-1357, supply chain). [nit]; Охват проверки (не находка): перечисление классов, проверенных в раунде 2 и признанных чистыми. [nit]
Resolutions:
- round 1, undefined: security-auditor поставил blocking на неаутентифицированные REST-эндпоинты /persistence/* и /api/profiles/* в деплое (OWASP A01:2021/API1/API5:2023, CWE-306) с требованием удалить/закрыть их в этом проходе; лид возразил: уязвимость pre-existing (те же маршруты на HEAD 0f0eae2), diff не добавил маршрутов и не снял проверок, решение «публичный REST остаётся» принято в ADR 0004:58, а DoD доски — исполняемость среды; удаление/аутентификация — продуктовое решение человека. → Blocking понижен до открытого риска для MITM-решения человека: evidence (file:line, цепочки чтения переписки и отправки в VK, OWASP/CWE) и требуемое изменение (удалить или закрыть аутентификацией + регрессионный тест + обновить ADR 0004 и карту) сохранены в verdict как nit/open risk; стадия project-setup не блокируется. Документальная дельта (карта впервые кодифицирует REST /persistence/* как публичный journey без оговорки о сетевом периметре) включена в открытый риск. (accepted by security-auditor, lead)
