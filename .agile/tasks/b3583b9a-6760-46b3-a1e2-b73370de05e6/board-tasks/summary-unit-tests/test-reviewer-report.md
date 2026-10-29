# test-reviewer report — `summary-unit-tests`

- **Board task:** `summary-unit-tests` (board `test-authoring`), task `b3583b9a-6760-46b3-a1e2-b73370de05e6`
- **Reviewer:** `test-reviewer`, round 1
- **Worktree/branch:** `.worktrees/fix/ai-tests` / `fix/ai-tests` (branch never switched)
- **Tree reviewed:** `8ff1c9d1bb1a8d8fdd3a3dfe19a099910500afaf2144ccd2e50775fb65edf82e`
- **Verdict:** `changes` — 1 blocking finding, 1 nit

## 1. Evidence used (fresh gates on the reviewed tree)

Not re-run: every claim below is backed by fresh entries in the ledger for this tree.

| Gate | Ledger id | Result |
|---|---|---|
| `./gradlew :src:ai:test` | `5c489da4-f4c1-432b-94a3-80fb9066ade8` | exit 0; 25 task tests / 62 module tests, 0 failures |
| `./gradlew :src:ai:test --tests "*ProcessSummaryCommandImplTest*"` | `754a10cd-…` | exit 0; 11 tests, 0 failures |
| `./gradlew :src:ai:test --tests "*GenerateSummaryCommandImplTest*"` | `c5fb23da-…` | exit 0; 7 tests, 0 failures |
| `./gradlew :src:ai:test --tests "*SummarizationAiAdapterTest*"` | `8072bff1-…` | exit 0; 3 tests, 0 failures |
| `./gradlew :src:ai:test --tests "*SummaryRequestedEventProcessorTest*"` | `315a0317-…` | exit 0; 2 tests, 0 failures |
| `./gradlew :src:ai:test --tests "*SummaryUsecaseTest*"` | `6fea3942-…` | exit 0; 1 + 1 tests, 0 failures |
| `./gradlew :src:testing:arch-tests:test` | `70514ae9-…` | exit 0; `TestDataFactoriesTest` 2/0, `GradleDependenciesTest` 3/0, `LayerDependenciesTest` 5/0, `ModuleDependenciesTest` 7/0, `DetektExcludesPathTest` 1/0 |
| `./gradlew detekt ktlintCheck` | `f7936e57-…` | exit 0 |
| `./gradlew :src:ai:build` | `704ba78e-…` | exit 0 |

The JUnit XMLs read at review time (before the last single-class run replaced them) confirmed the same
25 executions: `ProcessSummaryCommandImplTest` 11 (9 `@Test` + 2 `@ParameterizedTest` cases
`[1] 10, 10`, `[2] null, 0`), `GenerateSummaryCommandImplTest` 7 (5 + 2 cases `[1] "5", "Пользователь 5"`,
`[2] "-8", "Группа -8"`), `SummarizationAiAdapterTest` 3, `SummaryRequestedEventProcessorTest` 2,
`ProcessSummaryUsecaseTest` 1, `GenerateSummaryUsecaseTest` 1.

## 2. Checklist coverage

### A. AC coverage (AC-6 … AC-12)

| AC clause | Covering test method (`ProcessSummaryCommandImplTest` unless noted) | Can it fail? |
|---|---|---|
| AC-6 chat not in `summary.enabled-chats` → skip, no generate | `execute skips a chat that is not enabled` | yes — asserts no `hasPending` and no generate call; neutralising `isSummarizationEnabled` fails it |
| AC-6 existing `PENDING` summary → skip | `execute skips when a pending summary already exists` | yes — reproduced mutation M1 |
| AC-6 last summary stuck in `PENDING` → skip | `execute skips when the last summary is still pending` | **NO — finding F1** |
| AC-6 config parsing with surrounding spaces | `execute recognises enabled chats listed with surrounding spaces` | yes — a parse/trim break throws or skips |
| AC-7 empty window → skip | `execute skips when the message window is empty` | yes — deleting the check reaches `messages.first()` |
| AC-7 short window + previous summary → skip | `execute skips a short window when a previous summary exists` | yes — mutation M2 |
| AC-7 short window, no previous summary → runs | `execute summarises a short window when there is no previous summary` | yes — forcing the `lastSummary != null` branch skips and fails |
| AC-7 no previous summary → `FindStoredMessagesBefore(peer, current+1, batchSize)`, command gets first/last id | `execute requests messages before the current one when there is no previous summary` | yes — falsifies request and command arguments |
| AC-7 previous summary → `FindStoredMessagesBetween(peer, last ?: 0, current, batchSize)`; 2 cases; `size == threshold` proceeds | `execute requests messages between the previous summary and the current one` (`[1] 10, 10`, `[2] null, 0`) | yes — falsifies request; also asserts the before-port was not used |
| AC-8 `PENDING` created first, `COMPLETED` saved, `summaryId` returned, limit 1000 | `execute creates the pending summary first and completes it with the llm result` (`GenerateSummaryCommandImplTest`) | yes for order/requests/limit; **the LLM pass-through part is weak — finding F2** |
| AC-8 empty range → `COMPLETED` «Нет сообщений» / «Нет сообщений для суммаризации», LLM not called | `execute completes with the no-messages texts and skips the llm when the range is empty` | yes |
| AC-8 LLM exception → `FAILED` + rethrow same exception | `execute marks the summary failed and rethrows when the llm fails` | yes — mutation M4 |
| AC-9 `[author]: text` joined by `\n\n`, user full name, group name (`id = -fromId`) | `execute formats messages as author and text separated by a blank line` | yes — mutation M5 |
| AC-9 profiles requested separately for users and groups | `execute asks for user and group profiles separately` | yes — merging/reversing the partition fails |
| AC-9 fallback «Пользователь N» / «Группа N» | `execute falls back to placeholders when a profile is missing` (`@CsvSource`) | yes — removing the fallback fails; quoting verified via XML display names |
| AC-10 first-attempt success, 1 call, mapped | `execute returns the summary on the first attempt` (`SummarizationAiAdapterTest`) | yes |
| AC-10 success on 2nd attempt → 2 calls, no third | `execute retries once after a failure and returns the second response` | yes — mutation M3 |
| AC-10 3 failures → 3 calls, last exception rethrown | `execute retries three times and rethrows the last failure` | yes — mutation M3 |
| AC-11 JSON `SUMMARY_REQUESTED` → identifiers passed to `ProcessSummaryInputPort` | `process reads the payload json and passes the identifiers to the input port` (`SummaryRequestedEventProcessorTest`) | yes — mutation M6 |
| AC-11 `event() == SUMMARY_REQUESTED` | `event is SUMMARY_REQUESTED` | yes |
| AC-12 `ProcessSummaryUsecase` passes ids, returns response | `execute passes the identifiers to the command and returns the response` (`ProcessSummaryUsecaseTest`) | yes — write artifact M8 (not re-derived, same shape as M2/M7) |
| AC-12 `GenerateSummaryUsecase` passes ids, returns `summaryId` | `execute passes the identifiers to the command and returns its summary id` (`GenerateSummaryUsecaseTest`) | yes — write artifact M7 (not re-derived) |

### B. Non-vacuity / test theatre

- No mocking framework; the classes under test (`ProcessSummaryCommandImpl`, `GenerateSummaryCommandImpl`,
  `SummarizationAiAdapter`, `SummaryRequestedEventProcessor`) are instantiated for real, only their
  `port`/service dependencies are fakes. No assertion is made on a mock of the thing under test.
- Fakes record into `ConcurrentLinkedQueue`; every recorded queue that matters is asserted in at least one
  test (`requests.single()`/`isEmpty()`), and the unused defaults are ordinary fixtures.
- `@MethodSource` `betweenWindows` passes `null` for `Long?`; JUnit reports `[2] null, 0` — parsed as intended.
  `@CsvSource` quoting is correct: XML display names are `[1] "5", "Пользователь 5"` and `[2] "-8", "Группа -8"`,
  no stray quotes/spaces.
- No sleeps, no network, no ordering between test methods, no shared mutable state between tests.
- The three "skip" tests assert the absence of the generate call, which is the observable requirement;
  two of the four skip tests also assert that the *later* ports were never touched, and the F1 test does not —
  that is the blocking hole.
- **F1**: the only test of the stuck-`PENDING` clause cannot fail when the clause is deleted.
- **F2**: the saved LLM values coincide with both fixtures' defaults, so hardcoding them keeps the test green.

### C. Mutation credibility (independently reproduced in this worktree)

Every mutation was applied with a scripted single-site edit, the named class run with
`./gradlew :src:ai:test --tests "*<Class>*"`, then reverted with `git checkout -- <file>`.
After each revert `git status --short -- src/ai/src/main` and `git diff --stat -- 'src/*/src/main'` were empty;
the final ledger tree remains `8ff1c9d1bb1a…` (unchanged).

| # | File | Mutation | Result |
|---|---|---|---|
| M1 | `ProcessSummaryCommandImpl.kt` | `).hasPending` → `).hasPending && false` | exit 1; `ProcessSummaryCommandImplTest > execute skips when a pending summary already exists() FAILED`; `11 tests completed, 1 failed`; `BUILD FAILED in 7s` |
| M2 | `ProcessSummaryCommandImpl.kt` | `messages.size < summaryThreshold` → `>` | exit 1; `execute skips a short window when a previous summary exists() FAILED`; `11 tests completed, 1 failed`; `BUILD FAILED in 7s` |
| M3 | `SummarizationAiAdapter.kt` | `MAX_RETRIES = 3` → `1` | exit 1; `execute retries three times and rethrows the last failure() FAILED`, `execute retries once after a failure and returns the second response() FAILED`; `3 tests completed, 2 failed`; `BUILD FAILED in 7s` |
| M4 | `GenerateSummaryCommandImpl.kt` | `markSummaryAsFailedPort.execute(...)` → `Unit` | exit 1; `execute marks the summary failed and rethrows when the llm fails() FAILED`; `java.util.NoSuchElementException at GenerateSummaryCommandImplTest.kt:129`; `7 tests completed, 1 failed`; `BUILD FAILED in 7s` |
| M5 | `GenerateSummaryCommandImpl.kt` | `joinToString("\n\n")` → `joinToString("\n")` | exit 1; `execute formats messages as author and text separated by a blank line() FAILED`; `org.opentest4j.AssertionFailedError at GenerateSummaryCommandImplTest.kt:155`; `7 tests completed, 1 failed`; `BUILD FAILED in 7s` |
| M6 | `SummaryRequestedEventProcessor.kt` | swap `peerId` / `conversationMessageId` | exit 1; `process reads the payload json and passes the identifiers to the input port() FAILED`; `2 tests completed, 1 failed`; `BUILD FAILED in 7s` |
| F1 | `ProcessSummaryCommandImpl.kt` | delete the whole `if (lastSummary?.status == SummaryStatus.PENDING) { … }` block (lines 59-65) | exit **0**; `BUILD SUCCESSFUL in 6s`; JUnit XML `tests="11" skipped="0" failures="0" errors="0"` — the class stays green |
| F2 | `GenerateSummaryCommandImpl.kt` | save `"short summary", "full summary"` literals instead of `summaryResult.shortSummary/fullSummary` (line 57) | exit **0**; `BUILD SUCCESSFUL in 6s`; JUnit XML `tests="7" skipped="0" failures="0" errors="0"` — the class stays green |

Claimed mutations M7 (`GenerateSummaryUsecase.kt` firstMessageId) and M8 (`ProcessSummaryUsecase.kt` id → 0)
were not re-derived (only ≥5 were required); their shape is the same as M6 and they are consistent with
the delivered assertions.

### D. Fixtures rule

- `./gradlew :src:testing:arch-tests:test` is fresh (`70514ae9-…`): `TestDataFactoriesTest` 2/0 — every
  `create*` factory parameter has a default and no contract type is constructed in `src/test`.
- Manual check of the six test files: no direct `Type(...)` construction of `share` models/VOs/events,
  `port`/`command` request/response types (grep for each contract constructor: none found).
- Shared fixtures reused, not copied: `FakePersistencePortProvider` (requests), `FakeConversationContextPortProvider`
  (responses), `FakeModelProvider`/`FakeVoProvider` (models/VOs), `ObjectMapperProvider`.
- New factories are `create*` with realistic defaults: `FakeSummaryProvider` (8 request/response factories,
  all parameters defaulted) and `FakeEventProvider.createSummaryRequestedPayload` (peerId, conversationMessageId).
  No duplicate of an existing factory (`createSummaryRequestedPayload` exists once; response factories in
  `FakeSummaryProvider` do not overlap `FakePersistencePortProvider`/`FakeConversationContextPortProvider`).

### E. Scope

- `src/*/src/main`: no changes (`git diff --stat -- 'src/*/src/main'` empty, `git status --short -- src/ai/src/main` empty).
- `docs/**`: untouched.
- `src/app/**` and `src/ai/build.gradle.kts`: modified only by the parallel `quarkus-ai-test` /
  `ai-answer-unit-and-foundation` board tasks in this shared worktree (confirmed by Lead; not a finding here).
- This board task's delivered files: the six tests, `src/ai/src/testFixtures/.../FakeSummaryProvider.kt`,
  `src/testing/test-fixtures/.../domain/FakeEventProvider.kt` — exactly the declared may-touch list.

### F. Capability record (noted, not blocking)

`docs/capabilities/conversation-summary.md` still says these classes are not covered (lines 88-90:
`ProcessSummaryCommandImpl … не покрыт тестом`, `GenerateSummaryCommandImpl …`, `SummarizationAiAdapter …`).
The docs row and the AC ids belong to the `docs-and-coverage-record` board task; this board task does not
block on it. `check-capabilities.sh` (in `./gradlew build`, fresh gate `a0445616-…`) is green because it does
not assert coverage wording.

## 3. Classes checked and clean

- `SummarizationAiAdapterTest` — clean (first-attempt success, retry-then-success, 3 failures with the last
  exception rethrown; fake throws if called more times than scripted, so over-retry is caught too).
- `SummaryRequestedEventProcessorTest` — clean (parsed identifiers asserted; `event()` asserted).
- `ProcessSummaryUsecaseTest`, `GenerateSummaryUsecaseTest` — clean (request mapping and returned value).
- `ProcessSummaryCommandImplTest` — clean except the stuck-`PENDING` test (F1).
- `GenerateSummaryCommandImplTest` — clean except the LLM-value coincidence in the success test (F2).

## 4. Findings

### F1 (blocking) — AC-6 stuck-`PENDING` clause has no test that can fail

`ProcessSummaryCommandImplTest.execute skips when the last summary is still pending` (lines 65-82) asserts
only `FindLastStoredSummaryPort` was called and `GenerateSummaryCommand` was not. With the
`lastSummary?.status == SummaryStatus.PENDING` block deleted (`ProcessSummaryCommandImpl.kt:59-65`), execution
falls through to the default empty `FindStoredMessagesBetweenPort` and returns at `messages.isEmpty()`
(`ProcessSummaryCommandImpl.kt:68-71`), so both assertions still hold: the class is green (11/11).

**Required change:** in that test, pass recording ports and assert the window lookup was skipped, e.g.
`processSummaryCommand(findLastSummaryPort = …, findMessagesBeforePort = before, findMessagesBetweenPort = between)`
then `assertTrue(before.requests.isEmpty())` and `assertTrue(between.requests.isEmpty())`; or make the between
fake return exactly `THRESHOLD` messages so deleting the PENDING skip reaches `GenerateSummaryCommand` and
fails the existing `requests.isEmpty()` assertion. Re-run the class and the deletion mutation must fail.

### F2 (nit) — saved LLM values are indistinguishable from constants

`GenerateSummaryCommandImplTest.execute creates the pending summary first and completes it with the llm result`
asserts `SaveStoredCompletedSummaryPort.Request` with `"short summary"` / `"full summary"` — the defaults of
both `FakeSummaryProvider.createSummarizationResponse()` and `FakePersistencePortProvider.createSaveCompletedSummaryRequest`.
Replacing `summaryResult.shortSummary/fullSummary` with those literals at `GenerateSummaryCommandImpl.kt:57`
keeps the class green (7/7). The saved value is therefore not bound to the LLM response.

**Suggested change:** construct the fake LLM port with distinct values, e.g.
`RecordingSummarizationOutputPort(FakeSummaryProvider.createSummarizationResponse(shortSummary = "llm short", fullSummary = "llm full"))`,
and assert `createSaveCompletedSummaryRequest(summaryId = SUMMARY_ID, shortSummary = "llm short", fullSummary = "llm full")`.
Worth applying in the same write-stage pass as F1, but it does not by itself justify rework.

## 5. Open risks

- AC-9's group fallback is pinned as «Группа -8» (`id = -fromId`). The Russian AC wording «Группа N» is
  ambiguous about the sign; the test locks the current production behaviour, which is consistent with
  spec §2 («тесты пишутся под текущее поведение»). Not a test defect; the docs/capability phrasing may
  want to say `id = -fromId`.
- `SummaryRequestedEventProcessorTest` round-trips JSON through the same shared `ObjectMapperProvider` it reads
  with. Field values are asserted, so a field mix-up is caught (M6), but the mapper is not the Quarkus one;
  low risk for a payload of two `Long` fields.
- The two command tests' skip paths assert "generate not called" rather than the full set of ports not queried;
  that is sufficient for most clauses, but the F1 case shows the pattern's weak spot when a downstream
  fallback also skips.
- JUnit XML outputs in `src/ai/build/test-results` are transient (each filtered run rewrites them); the
  durable evidence is the fresh gate ledger above.
