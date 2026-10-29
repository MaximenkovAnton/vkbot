# Тестирование

Стратегия: **Fake Objects вместо моков**, unit-тесты без каркаса, интеграционные тесты через
`@QuarkusTest` с DevServices и WireMock, архитектурные правила как тесты. Всё, что ниже, — из кода и
прогонов этого прохода.

## Команды

```bash
./gradlew test                                  # все тесты (нужен Docker для app-тестов)
./gradlew :src:processor:test --tests "*MessageReceivedUsecaseInputTest*"  # один класс (в своём модуле)
./gradlew :src:testing:arch-tests:test          # архитектурные правила (15 тестов)
./gradlew coverageReport                        # агрегированный HTML+XML отчёт
./gradlew coverageVerification                  # порог покрытия (входит в ./gradlew build)
```

Фильтр `--tests` применяется ко всем test-задачам: корневой `./gradlew test --tests "*X*"` падает на
модуле, где совпадений нет (`No tests found for given includes` в `:src:app`), поэтому одиночный тест
запускается в своём модуле.

Проверено в этом проходе: `./gradlew test` → `BUILD SUCCESSFUL`, **59 тестов, 0 падений, 0 ошибок,
0 пропущенных**. Распределение по модулям: app — 7, receiver — 9, share — 8, vk-facade — 7, persistence — 9,
processor — 2, infrastructure — 2, arch-tests — 15; **в `ai` собственных тестов нет**.

## Где лежат тесты

| Модуль | Файлы |
|---|---|
| `app` | `InitTest` (контекст поднимается), `integration/VkWebhookIntegrationTest` (6 тестов), `WireMockTestResource`, `IntegrationTestProfile` |
| `receiver` | `VkMediatorRouterIntegrationTest`, `ReceiveMessageUsecaseTest`, `PublishVkEventCommandImplTest`, `ExceptionHandlerTest` |
| `persistence` | `PersistenceDataInProcessAdapterTest` (9 тестов — маппинг всех in-process портов) |
| `processor` | `MessageReceivedUsecaseInputTest` |
| `vk-facade` | `VkProfileInProcessAdapterTest`, `VkSendMessageOutputAdapterTest`, `PublishEventCommandImplTest`, `SendVkMessageCommandImplTest`, `MessageSendUsecaseTest` |
| `share` | `MessageTextTest`, `ConversationMessageIdTest` |
| `infrastructure` | `CommandLoggingDecoratorTest` |
| `testing/arch-tests` | `ModuleDependenciesTest` (7), `LayerDependenciesTest` (5), `GradleDependenciesTest` (3) |

Тестовые ресурсы: `src/app/src/test/resources/application.yaml` (базовый профиль `@QuarkusTest`),
`application-integration-test.yaml` (профиль `integration-test`), `application.yml` (`%test`-настройки),
`src/testing/test-common/src/main/resources/application.yaml` (общие значения для модульных тестов).

## Fake Objects

Ни одного mocking-фреймворка в сборке нет. Fake реализует тот же интерфейс, записывает вызовы в
`ConcurrentLinkedQueue` и возвращает реалистичный ответ.

```kotlin
class FakeVkClient(
    val vkResponseDto: VkResponseDto? = null,
    val vkUsersResponseDto: VkUsersResponseDto? = null,
    val vkGroupsResponseDto: VkGroupsResponseDto? = null,
) : VkClient {
    val sendMessageParameterCalls = ConcurrentLinkedQueue<SendMessageParameter>()
    override fun sendMessage(peerId: Long, message: String, rand: Int, forwardMessages: String?): VkResponseDto {
        sendMessageParameterCalls.add(SendMessageParameter(peerId, message, rand))
        return vkResponseDto ?: VkResponseDto(error = null)
    }
}
```

См. `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/adapter/output/vk/FakeVkClient.kt:9-41`,
`.../command/vkFacade/FakePublishEventCommand.kt:8-16`,
`.../adapter/output/mq/FakePublishEventOutputPort.kt:8-16`.

### Раскладка fake-объектов

```
src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/
├── adapter/
│   ├── input/FakeEventProcessor.kt
│   └── output/
│       ├── mq/FakePublishEventOutputPort.kt
│       └── vk/FakeVkClient.kt
├── command/
│   ├── FakeCommand.kt, FakeCommandRequest.kt, FakeCommandResponse.kt
│   └── vkFacade/
│       ├── FakePublishEventCommand.kt, FakePublishEventCommandProvider.kt
│       └── FakeSendVkMessageCommand.kt, FakeSendVkMessageCommandProvider.kt
├── domain/
│   ├── FakeMessageProvider.kt
│   └── FakeVoProvider.kt
├── objectProvider/ObjectMapperProvider.kt
└── port/output/vk/FakeVkSendMessageOutputProvider.kt

src/receiver/src/testFixtures/kotlin/com/simarel/vkbot/receiver/fixtures/   # FakeMessageMapper,
  # FakePublishVkEventCommand, FakeVkConfirmationInputPortProvider, FakeVkProvider, port/FakeReceiveMessageInputPort
src/processor/src/testFixtures/kotlin/com/simarel/vkbot/processor/testfixtures/  # FakeIsRequireAnswerCommand,
  # FakeMessageNewInputPortProvider, FakeMessageReceivedInputPort
```

Модульные `testFixtures` подключаются только в test-конфигурациях — это проверяет
`GradleDependenciesTest`. Общий набор подключается через `testFixtures(project(":src:testing:test-fixtures"))`.

### Провайдеры тестовых данных

`FakeVoProvider` (`testfixtures/domain/FakeVoProvider.kt:14-62`) — единая точка создания доменных значений:
`createDate`, `createHumanFromId`, `createGroupFromId`, `createGroupChatFromId`, `createGroupId`, `createPeerId`,
`createConversationMessageId`, `createMessageText`, `createMessage`, `createForwardedMessage`,
`createMessageWithForwarded`. `FakeMessageProvider` — фабрика `Message` поверх него.

Правило: в тесте задаются только значимые поля, остальное берётся из провайдера
(`FakeVoProvider.createMessage(messageText = ...)`).

## Как писать тесты

- **Именование:** backtick-предложение, описывающее проверку: ``fun `execute with confirmation event returns confirmation response`()``
  (`ReceiveMessageUsecaseTest.kt:14`). Стиль Given/When/Then — как в соседних тестах.
- **Unit-тест** — обычный JUnit 5 без Quarkus: собрать объект с fake-зависимостями, вызвать, проверить
  записи fake'а и результат (`ReceiveMessageUsecaseTest.kt:11-67`).
- **Адаптер in-process** — шаблон для новых адаптеров: записать запрос в `port.input` и проверить обратный
  маппинг (`PersistenceDataInProcessAdapterTest.kt:38-49`, `VkProfileInProcessAdapterTest.kt:17-33`).
- **Fake с настраиваемым поведением** — параметр конструктора вместо `when(...)`:
  `FakeVkClient(vkResponseDto = ...)`.
- **Thread safety** — `ConcurrentLinkedQueue` для записанных вызовов, не `mutableListOf`.
- **Реалистичные данные** — возвращать осмысленный объект из провайдера, не `""`/`null`.

## Интеграционные тесты

`VkWebhookIntegrationTest` (`src/app/src/test/kotlin/com/simarel/vkbot/integration/VkWebhookIntegrationTest.kt:27-177`):

- `@QuarkusTest` + `@TestProfile(IntegrationTestProfile::class)` (профиль `integration-test`) +
  `@QuarkusTestResource(WireMockTestResource::class)`;
- DevServices поднимают PostgreSQL и RabbitMQ, поэтому **нужен Docker**; Ollama DevServices отключён,
  LLM отвечает WireMock-заглушкой `/api/chat`; VK API (`/method/users.get`, `/method/groups.getById`,
  `/method/messages.send`) тоже заглушается;
- тест `full flow` отправляет `message_new` на `/vk/callback` и через Awaitility (до 120 с) проверяет, что
  `messages.send` дошёл до WireMock — это сквозное доказательство, что in-process порты, CDI-проводка и
  брокер работают вместе;
- остальные тесты: confirmation, неизвестный тип, неверный/отсутствующий секрет (403), ошибка VK API
  не роняет вебхук.

`WireMockTestResource` задаёт только те REST-клиенты, которые есть в коде: VK API (`quarkus.rest-client.vk.url`)
и Ollama (`quarkus.langchain4j.ollama.base-url`, `WireMockTestResource.kt:24-31`). Заглушек удалённых
loopback-эндпоинтов `/profiles/*` и `/persistence/*` в нём нет, поэтому e2e не пройдёт при возврате HTTP-контура
между модулями (ADR 0004). Vane в тестах смотрит на `http://localhost:8075` (`src/app/src/test/resources/application.yaml:22-25`).

## Архитектурные тесты

`src/testing/arch-tests`:

| Тест | Что проверяет |
|---|---|
| `ModuleDependenciesTest` | каждый бизнес-модуль зависит только от `share`; `share` — ни от кого |
| `LayerDependenciesTest` | L1 `domain`, L2 `port`, L3 `usecase`/`command`, L4 входные адаптеры, L5 выходные адаптеры |
| `GradleDependenciesTest` | найдено 12 build-файлов; production-рёбра соответствуют allowlist; testing-модули только в test-конфигурациях |

Негативная проверка (этот проход): класс пакета `receiver` в модуле `persistence`, зависящий от
`persistence` → `ModuleDependenciesTest > receiver module should only depend on share() FAILED`, exit 1;
ссылка use case на адаптер → `LayerDependenciesTest > L3 … FAILED`, exit 1. После откатов — зелёные.

## Покрытие

- Агрегат по 8 модулям (`ai, app, infrastructure, persistence, processor, receiver, share, vk-facade`),
  `src/testing/*` исключён (`build.gradle.kts:22-31`).
- Измерено: **line ratio 0.2796** (586 покрытых из 2096 строк), instruction 0.2862, branch 0.1527;
  `coverageMin = 0.25` (`build.gradle.kts:19-20`).
- Порог включён в `check` → `build` (`build.gradle.kts:130-133`) и в CI-джоб `coverage`.
- Политика: порог не понижается; ратчет 0.40, затем 0.60 — отдельные задачи с новыми тестами (ADR 0005).
  Покрытие меряется по бизнес-коду, а не по glue; `ai` и часть use case пока не покрыты.
- Негативная проверка: `coverageMin = 0.99` → `Rule violated for bundle vkbot: lines covered ratio is 0.27,
  but expected minimum is 0.99`, exit 1.

## Ограничения

- `@QuarkusTest` в `app` требует Docker (DevServices); в окружении без Docker эти тесты не пройдут.
- Юнит-тестов `ai` нет: `GenerateAnswerUsecase`, `ProcessSummaryCommand`, `GenerateSummaryCommand` и
  LangChain4j-адаптеры проверяются только сквозным интеграционным тестом.
- Для устойчивости к отсутствию тестов в модуле сборка ставит `failOnNoDiscoveredTests = false`
  (`build.gradle.kts:69-72`) — новый модуль без тестов не упадёт, но и не будет проверен.
