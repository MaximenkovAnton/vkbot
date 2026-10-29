# tests.md — vane-unit-tests (AC-13, AC-14, AC-15)

Board task: `vane-unit-tests`, board `test-authoring`, stage `write`.
Worktree: `/Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests` (branch `fix/ai-tests`).
Production code wasn't changed: tests are written from `spec.md` §5 and `dod.md`, not from the implementation.

## 1. What is delivered

| File | Tests |
|---|---|
| `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/vane/VaneSearchCommandImplTest.kt` | 8 (4 behaviours, 2 × 3 parameterized rows) |
| `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchOutputAdapterTest.kt` | 8 (4 tests + 4 parameterized rows) |
| `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapterTest.kt` | 3 parameterized rows |
| `src/ai/src/testFixtures/kotlin/com/simarel/vkbot/ai/testfixtures/FakeVaneProvider.kt` | +4 factories, defaults on every parameter |

Fixture additions (they are required: `TestDataFactoriesTest` forbids direct construction of `**/command/**`
and `*Request`/`*Response` contract types in `src/*/src/test/**`):

- `createVaneWebSearchCommandRequest(query = "kotlin coroutines")`
- `createVaneAcademicSearchCommandRequest(query = "kotlin coroutines")`
- `createVaneDiscussionSearchCommandRequest(query = "kotlin coroutines")`
- `createVaneSearchCommandResponse(result = "Vane answer")`

Existing factories reused as-is: `createVanePortSearchRequest`, `createVanePortSearchResponse`,
`createVanePortSource`, `createVaneClientSearchRequest`, `createVaneClientSearchResponse`,
`createVaneClientSource`, `createVaneClientSourceMetadata`, `createVaneProvidersResponse`,
`createVaneProvider`, `createVaneModel`, `createVaneModelConfig`. The port and REST-client types have the same
names, so the test uses import aliases (`VanePortSearchRequest` / client types from the same package).

No comments, no new dependencies, no new framework; plain JUnit 5 + local `Recording*` fakes, same style as
`GenerateAnswerCommandImplTest` / `ProcessSummaryCommandImplTest`.

## 2. AC → test → command

All commands are run from the worktree root. The whole suite is one command: `./gradlew :src:ai:test`.

| AC | Behaviour | Test (class › name) | Command |
|---|---|---|---|
| AC-13 | Web → sources `["web"]`, mode `balanced`; Academic → `["academic"]`, `quality`; Discussion → `["discussions"]`, `balanced`; query is forwarded | `VaneSearchCommandImplTest` › `execute sends the tool sources and optimization mode to the vane port` (rows `[1] WebSearch`, `[2] AcademicSearch`, `[3] DiscussionSearch`) | `./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"` |
| AC-13 | Labels `Sources` / `Academic Sources` / `Discussion Sources`; numbered `[n] title: url` block after `--- <label> ---` | `VaneSearchCommandImplTest` › `execute formats the numbered sources under the tool source label` (3 rows) | `./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"` |
| AC-13 | Empty `sources` → answer only, no sources block | `VaneSearchCommandImplTest` › `execute omits the sources block when vane returns no sources` | `./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"` |
| AC-13 | Port exception → `Search failed: <message>`, no rethrow | `VaneSearchCommandImplTest` › `execute returns a search failed line instead of throwing when the port fails` | `./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"` |
| AC-14 | First provider with chat **and** embedding models is selected (chat-only / embedding-only are filtered out) | `VaneSearchOutputAdapterTest` › `execute picks the first provider that has both a chat and an embedding model` | `./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"` |
| AC-14 | Keys of the **first** chat and embedding models go into the client request, together with mode/sources/query | `VaneSearchOutputAdapterTest` › `execute sends the first model keys of the selected provider with the search request` | `./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"` |
| AC-14 | Client response mapped to the port: `message`, source `content`/`metadata.title`/`metadata.url` | `VaneSearchOutputAdapterTest` › `execute maps the message and each source title and url to the port response` | `./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"` |
| AC-14 | `sources = null` → empty source list | `VaneSearchOutputAdapterTest` › `execute maps missing sources to an empty source list` | `./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"` |
| AC-14 | No provider with both model lists → `IllegalStateException("No providers configured in Vane")`, no search call | `VaneSearchOutputAdapterTest` › `execute throws when no provider has both a chat and an embedding model` (rows: empty list, `chatModels=null`, `embeddingModels=null`, `embeddingModels=[]`) | `./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"` |
| AC-15 | `search_web` / `search_academic` / `search_discussions` delegate with `WebSearch` / `AcademicSearch` / `DiscussionSearch(query)` and return the command's `result` | `VaneSearchToolAdapterTest` › `invoking a tool delegates to VaneSearchCommand with the matching request and returns its result` (3 rows) | `./gradlew :src:ai:test --tests "*VaneSearchToolAdapterTest*"` |

No DoD row is left without a test. Gaps: none inside AC-13…AC-15. Behaviour the AC does not state was not
tested (for example, formatting of an empty `message`, or `history`/`stream` fields of the client request,
which the adapter never sets) — named as gaps, not passes.

## 3. Gate evidence

### 3.1 Module suite — `./gradlew :src:ai:test`

```
$ ./gradlew :src:ai:test
BUILD SUCCESSFUL in 1s
18 actionable tasks: 18 up-to-date
$ ./gradlew :src:ai:cleanTest :src:ai:test      # same tree, forced execution
BUILD SUCCESSFUL in 7s
```
Both exit code 0. Test results: 14 classes, 62 tests, 0 failures. The three classes of this board task:
`VaneSearchCommandImplTest tests=8 failures=0`, `VaneSearchOutputAdapterTest tests=8 failures=0`,
`VaneSearchToolAdapterTest tests=3 failures=0`.

### 3.2 Per class

```
$ ./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"
BUILD SUCCESSFUL in 6s          (exit 0, tests=8 failures=0)
$ ./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"
BUILD SUCCESSFUL in 6s          (exit 0, tests=8 failures=0)
$ ./gradlew :src:ai:test --tests "*VaneSearchToolAdapterTest*"
BUILD SUCCESSFUL in 6s          (exit 0, tests=3 failures=0)
```

### 3.3 Architecture — `./gradlew :src:testing:arch-tests:test`

The plain command is `UP-TO-DATE` (it does not watch the test sources it scans at runtime), so the factory
guard was forced to re-read the new fixture:

```
$ ./gradlew :src:testing:arch-tests:cleanTest :src:testing:arch-tests:test
BUILD SUCCESSFUL in 9s
```
Exit code 0; `TestDataFactoriesTest tests=2 failures=0`, `GradleDependenciesTest tests=3 failures=0`,
`LayerDependenciesTest tests=5 failures=0`, `ModuleDependenciesTest tests=7 failures=0` (18/18).

### 3.4 Lint — `./gradlew detekt ktlintCheck`

Scoped to the delivered module, exit 0:

```
$ ./gradlew detekt ktlintCheck
BUILD SUCCESSFUL in 688ms
89 actionable tasks: 89 up-to-date
$ ./gradlew :src:ai:detekt :src:ai:ktlintCheck
BUILD SUCCESSFUL in 605ms
```

Exit code 0. No finding names a file of this board task.

The shared worktree runs this board task and `quarkus-ai-test` / `summary-unit-tests` in parallel. While a
parallel board task temporarily mutates `src/*/src/main` for its own negative proof, or while two Gradle
builds write `src/ai/build/test-results/test/binary` at the same time, lint/build/test can transiently fail
with `Analysis failed …` or `java.nio.file.NoSuchFileException: …/in-progress-results-*.bin`. Those runs are
not evidence: every command quoted here was re-run after the parallel proof finished and the tree was clean
(`git status --porcelain -- 'src/*/src/main'` empty), and the gate ledger binds each one to the tree hash it
ran on.

### 3.5 Module build — `./gradlew :src:ai:build`

```
$ ./gradlew :src:ai:build --rerun-tasks
BUILD SUCCESSFUL in 10s
28 actionable tasks: 28 executed
$ ./gradlew :src:ai:build
BUILD SUCCESSFUL in 727ms
28 actionable tasks: 28 up-to-date
```
Exit code 0 (all tasks executed, not `UP-TO-DATE`).

### 3.6 Production tree untouched

```
$ git diff --stat -- 'src/*/src/main'
(empty)
```
Exit code 0. All temporary mutations below were reverted and byte-identical to their backups.

## 4. Negative proof — every test was made to fail

The tree is `fix/ai-tests`; each mutation was applied to `src/ai/src/main`, the class was run, and the file was
restored from a byte copy. None of the mutations is present in the final tree.

### 4.1 AC-13 — Academic sources changed to `["web"]`

Mutation: `VaneSearchCommand.kt:41` `sources = listOf("academic")` → `sources = listOf("web")`.

```
$ ./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"     (exit 1)
VaneSearchCommandImplTest > execute sends the tool sources and optimization mode to the vane port(...) > [2] AcademicSearch(query=kotlin coroutines), [academic], "quality" FAILED
    org.opentest4j.AssertionFailedError at VaneSearchCommandImplTest.kt:30
> Task :src:ai:test FAILED
BUILD FAILED in 7s
```

Reverted; `git diff --stat -- src/ai/src/main/.../VaneSearchCommand.kt` empty.

### 4.2 AC-13 — port exception no longer handled

Mutation: `catch (e: Exception)` → `catch (e: Error)`, so the port failure escapes instead of becoming
`Search failed: …`.

```
$ ./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"     (exit 1)
VaneSearchCommandImplTest > execute returns a search failed line instead of throwing when the port fails() FAILED
    java.lang.IllegalStateException at VaneSearchCommandImplTest.kt:80
BUILD FAILED in 7s
```

Reverted.

### 4.3 AC-14 — provider filter removed

Mutation: removed `.filter { it.chatModels.isNotEmpty() && it.embeddingModels.isNotEmpty() }` from
`VaneSearchOutputAdapter.getAvailableProviders`.

```
$ ./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"   (exit 1)
VaneSearchOutputAdapterTest > execute picks the first provider that has both a chat and an embedding model() FAILED
    java.util.NoSuchElementException at VaneSearchOutputAdapterTest.kt:28
VaneSearchOutputAdapterTest > execute throws when no provider has both a chat and an embedding model(List) > [2] ... FAILED
    Caused by: java.util.NoSuchElementException at VaneSearchOutputAdapterTest.kt:142
... [3], [4] same
BUILD FAILED in 8s
```

Reverted.

### 4.4 AC-14 — source mapping removed

Mutation: `sources = response.sources?.map { … } ?: emptyList()` → `sources = emptyList()`.

```
$ ./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"   (exit 1)
VaneSearchOutputAdapterTest > execute maps the message and each source title and url to the port response() FAILED
    org.opentest4j.AssertionFailedError at VaneSearchOutputAdapterTest.kt:100
BUILD FAILED in 9s
```

Reverted.

### 4.5 AC-15 — discussions tool delegates to the wrong request

Mutation: `VaneSearchToolAdapter.search_discussions` sends `AcademicSearch(query)` instead of
`DiscussionSearch(query)`.

```
$ ./gradlew :src:ai:test --tests "*VaneSearchToolAdapterTest*"     (exit 1)
VaneSearchToolAdapterTest > invoking a tool delegates to VaneSearchCommand with the matching request and returns its result(...) > [3] ..., DiscussionSearch(query=kotlin coroutines), "discussion result" FAILED
    org.opentest4j.AssertionFailedError at VaneSearchToolAdapterTest.kt:27
BUILD FAILED in 7s
```

Reverted.

## 5. Reproducibility on a clean checkout

On a tree without the other board tasks' in-flight files:

```
./gradlew :src:ai:test
./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"
./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"
./gradlew :src:ai:test --tests "*VaneSearchToolAdapterTest*"
./gradlew :src:testing:arch-tests:cleanTest :src:testing:arch-tests:test
./gradlew detekt ktlintCheck
./gradlew :src:ai:build
```

All seven exit 0; `git diff --stat -- 'src/*/src/main'` is empty.
