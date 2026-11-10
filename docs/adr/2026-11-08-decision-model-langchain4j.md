# 2026-11-08: Decision-модель — библиотечный `DecisionModel` из `quarkus-langchain4j-ollama`

- **Статус:** accepted
- **Дата:** 2026-11-08
- **Supersedes:** —

## Контекст

Нужен клиент decision-модели: по тексту сообщения получить вероятность спама и класс сообщения
(`spam`/`command`/`ham`). Сервер — System One API, `POST {DECISION_URL}/v1/systemone`; у него
экспериментальный decision-endpoint, которого нет у OpenAI-совместимого провайдера, а второй
провайдер уже стоит в деплое как chat-модель ([ADR 2026-11-05-llm-provider-vllm-openai](2026-11-05-llm-provider-vllm-openai.md)).
Своего HTTP-клиента, DTO и JSON-маппинга заводить не нужно: LangChain4j 1.23.0 содержит
`dev.langchain4j.model.decision.DecisionModel`, а Quarkus-расширение собирает его из конфига.

Клиент — заготовка: в этой задаче он не вызывается из runtime, политика «отвечать/не отвечать» и
пороги — следующая задача (решение «без вызова из runtime» заменено [ADR 2026-11-09-decision-runtime-spam-policy](2026-11-09-decision-runtime-spam-policy.md)).
Картинки/vision тоже позже.

## Решение

- **Транспорт — `quarkus-langchain4j-ollama:1.15.0`** (LangChain4j 1.23.0) в `:src:processor` как
  production-зависимость и в `:src:app`; собственного HTTP-клиента нет. Адаптер
  `DecisionModelOutputAdapter` (`src/processor/src/main/kotlin/com/simarel/vkbot/processor/adapter/output/decision/DecisionModelOutputAdapter.kt`)
  вызывает библиотечный `DecisionModel`, переводит `DecisionRequest`/`DecisionResponse` в доменный
  `DecisionAnalysis` и прячет LangChain4j за портом `AnalyzeMessageOutputPort`. Смена библиотеки или
  апгрейд — правка адаптера и `gradle.properties` ([ADR 2026-10-28-stack-kotlin-quarkus-gradle](2026-10-28-stack-kotlin-quarkus-gradle.md)).
- **Почему не `-openai`.** Decision-endpoint OpenAI-расширения — `/v1/decisions`; у System One API путь
  `/v1/systemone`, и тот же запрос OpenAI-провайдером не обслуживается. **Почему не `-typesafe`.**
  Typesafe-расширение — text-only и не даёт decision-модели с noul/choice-вопросами.
- **Два провайдера и два build-time ключа.** На classpath `-openai` и `-ollama`, поэтому обязательны
  `quarkus.langchain4j.chat-model.provider: openai` и
  `quarkus.langchain4j.decision-model.provider: ollama` — иначе сборка падает на «multiple available
  providers». «Один провайдер» из [ADR 2026-11-05-llm-provider-vllm-openai](2026-11-05-llm-provider-vllm-openai.md) относится к chat-модели
  (ответ, саммаризация, OCR); второй провайдер добавлен **только для decision** и выбирается
  build-time пином, а не рантайм-флагом. Решение ADR 2026-11-05-llm-provider-vllm-openai не пересматривается.
- **`DECISION_URL` — голый корень System One-сервера** (без `/v1`, без `/v1/systemone`, без хвостового
  `/`), обязателен и без дефолта ([ADR 2026-10-28-configuration-and-secrets](2026-10-28-configuration-and-secrets.md)); ключ —
  `quarkus.langchain4j.ollama.base-url`, расширение дописывает `v1/systemone`, иначе оно ушло бы на
  `http://localhost:11434`. **`DECISION_URL` объявлен обязательным без дефолта, но `fail-fast на старте не
  enforced`** — принятый named gap AC-5: decision-бин библиотеки — ленивый `@ApplicationScoped` synthetic
  bean, а клиент не вызывается из runtime, поэтому `${DECISION_URL}` разрешается только при создании бина
  и старт без переменной не падает. Деплой выставляет переменную вручную; первый деплой проверяет
  позитивную половину; проверка fail-fast переносится в задачу, которая подключит клиент к runtime.
  Наблюдение M-1 — `impl.md` board-task `decision-integration`.
- **`model-id: decision`** — переопределение дефолта расширения `nimble`; сервер поле `model` игнорирует.
  Ключ `BUILD_AND_RUN_TIME_FIXED`, поэтому тестовые профили задают `test-decision` конфигом, а не
  test-resource.
- **`timeout: 5s`** вместо дефолтных 10s — бюджет вызова; `api-key` не нужен.
- **До двух попыток транспорта.** `OllamaRecorder.decisionModel` жёстко ставит `maxRetries(1)`, то есть
  ретраится только HTTP-вызов (не маппинг и не валидатор); своих повторов, кэша и батчинга нет.
  Другое число попыток — отдельное решение (`ModelBuilderCustomizer` или своя сборка модели).
- **Ошибки.** Любой отказ контракта — `DecisionModelException` с `status() == EXTERNAL_FAILURE` и
  **фиксированным литеральным** сообщением: `Decision model call failed` для исключения `decide`/отказа
  модели (`ContentFilteredException` из `RefusalAnswer`) и `Decision model response is invalid` для
  неполной карты вероятностей (валидатор расширения её пропускает). Сообщение `cause` не
  интерполируется: у `HttpException` это тело HTTP-ответа. Частичный результат не возвращается.
- **Пустой вход.** Пустой нормализованный текст (в том числе из невидимых/пробельных символов) даёт
  `HAM`/`null`/пустую карту/`0.0` без вызова модели: вероятности не выдумываются.
- **Нормализация — наш слой** (`MessageTextNormalizer`, чистый Kotlin): NFKC; удаление `U+200B`,
  `U+200C`, `U+200D`, `U+2060`, `U+FEFF`, `U+00AD`; ветка разряженной обфускации (`>= 8` частей со
  средней длиной `< 2.0`); гомоглифы только в словах, где есть строчная `[а-яё]` (`b→ь`, `B→В`);
  leet `0/3/4/6` при соседе-кириллице и `0 < i < len-1`; `ё→е`, `Ё→Е`; `(?U)\s+` → пробел и `trim()`.
  Известные границы: идемпотентность — **среди 27 строк §10 неидемпотентны 17 и 24**; шире не верно:
  контрпример `"К  а  з  и  н  о  о  н"` (первый проход `"К а з и н о о н"`, второй `"Казиноон"`);
  гомоглифы в верхнем регистре не детектируются. **Два известных отклонения от Python-эталона:**
  (1) `U+001C–U+001F` — для Python-`\s` пробельные, для `(?U)\s` — нет (в VK-тексте невозможны);
  (2) астральные символы — Python считает длину в code points, порт — в UTF-16 `String.length`, поэтому
  средняя длина частей шага 3 может отличаться; свидетель — 8 эмодзи через пробел, 14 расхождений на
  102 377 входах. Вход в верхнем регистре целиком (`ЗAРАБОТОК`) остаётся как есть — это поведение
  эталона, не дефект.
- **PII: decision-логи включены.** Провайдерские ключи `quarkus.langchain4j.ollama.log-requests`/
  `log-responses` **не добавляются**. Глобальные `quarkus.langchain4j.log-requests/log-responses: true`
  наследуются: у ollama-конфига дефолт `@WithDefault("${quarkus.langchain4j.log-requests}")`/
  `...log-responses`, а рекордер читает `firstOrDefault(false, decision-model.*, ollama.*)`. Поэтому
  нормализованный текст и ответ модели логируются на INFO и **экспортируются по OTLP**
  (`quarkus.otel.logs.enabled: true` — PII покидает процесс) — **принятый осознанный риск PII** (решение
  человека Q1=B), а не «false». Выключение — провайдерские `log-requests: false` + `log-responses:
  false`, отдельное решение. Второй PII-путь — `cause.message` для non-2xx (тело HTTP-ответа): его
  нельзя логировать выше DEBUG. Трейсинг decision-текст не получает: decision-слушателей в дереве нет,
  слушатели `quarkus-langchain4j-core` — chat-only.
- **Тестовая стратегия.** Живой сервер не нужен: `:src:processor` — unit-тесты на рукописном
  `FakeDecisionModel` (он реализует `doDecide`, default `decide` сам вызывает валидатор расширения),
  `:src:app` — `@QuarkusTest` с WireMock на HTTP-границе (`POST /v1/systemone`), настоящим
  `OllamaDecisionModel` и без Ollama DevServices.
- **Без вызова из runtime** (решение заменено [ADR 2026-11-09-decision-runtime-spam-policy](2026-11-09-decision-runtime-spam-policy.md)). Порт
  не вызывает ни один use case, входного адаптера нет; в продуктовой карте — строка «Не отображено в
  карте и почему». Поведение delivered-возможностей не меняется.

## Последствия

- Смена адреса или модели decision-сервера — правка env ([ADR 2026-10-28-configuration-and-secrets](2026-10-28-configuration-and-secrets.md)),
  а не кода; апгрейд LangChain4j — `gradle.properties` + адаптер.
- Контракт деплоя требует `DECISION_URL`; пока fail-fast на старте не enforced (AC-5 — named gap,
  см. выше), поэтому переменную нужно выставлять до раскатки, а не надеяться на отказ старта.
  Позитивная половина (старт с переменной) проверяется первым деплоем.
- Деплой обязан выставить `DECISION_URL` до раскатки нового образа; откат — предыдущий образ и удаление
  переменной, данных и схемы задача не меняет.
- Экспериментальная природа decision-API LangChain4j 1.23.0 — принятый риск: контракт задаёт библиотека,
  своей версии внешнего контракта нет.
- Второй провайдер стоит в сборке из-за decision-модели; chat-модель остаётся на OpenAI-совместимом
  endpoint. Второй decision-провайдер и выбор модели конфигом — вне задачи.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Свой HTTP-клиент на JAX-RS/RESTEasy с DTO к `/v1/systemone` | Дублирует библиотечный `DecisionModel`, требует своего JSON-маппинга, разбора ошибок и повторов; расходится с решением человека «не придумывать свои клиенты» |
| Decision-модель через `-openai` | Путь `/v1/decisions` не совпадает с System One API; поддержки noul/choice в OpenAI-провайдере нет |
| `-typesafe` | Text-only, decision-модели с вопросами не даёт |
| Выключить decision у `-openai` и chat/embedding у `-ollama` вместо двух `provider`-ключей | Три ключа вместо двух и менее явное выражение выбора |
| Провайдерские `ollama.log-requests/log-responses: false` | Выключило бы decision-логи, но это отдельное решение о PII, а не часть подключения клиента |
| Свои повторы/кэш/батчинг | Клиент недостижим из runtime; политика и бюджет — следующая задача |
| Дефолт `DECISION_URL` | Молчаливый запрос в неизвестный хост вместо отказа на старте ([ADR 2026-10-28-configuration-and-secrets](2026-10-28-configuration-and-secrets.md)) |
