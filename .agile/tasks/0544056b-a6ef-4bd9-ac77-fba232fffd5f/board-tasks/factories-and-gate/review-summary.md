# review-summary.md — factories-and-gate (round 2)

- Board task: `factories-and-gate` (stage `review`, round 2), task `0544056b-a6ef-4bd9-ac77-fba232fffd5f`.
- Spec: `.agile/tasks/0544056b-a6ef-4bd9-ac77-fba232fffd5f/spec.md` (§1, §4, §5, §6, §7, §10, §12).
- Implementation artifact under review: `impl.md` (sha256 `941d99dc…671c60d`, unchanged through the whole review).
- Round 2 is a re-review after the consolidated rework pass for the two blocking findings of round 1
  (`review-summary.md` §2, B1/B2). Date: 2026-10-29. Workspace: `/Users/asmaksime1/IdeaProjects/test/vk`.

## 1. Verdict of the review team

| Reviewer | Decision | Findings |
|---|---|---|
| test-reviewer | **approve** | 1 nit |
| code-reviewer | **approve** | 0 |
| security-auditor | **approve** | 3 nit |
| qa-expert | **approve** | 2 nit |

No blocking findings. All four reviewers posted a verdict bound to `impl.md` sha256 `941d99dc…671c60d`
and each stated the classes it checked. The stage delivers `review-summary.md`; the board task is ready
to be closed and `docs-rule` unblocked.

## 2. The rework: both round-1 blockers are fixed (reproduced)

Round 2 changed only `src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/TestDataFactoriesTest.kt`
(guard): annotation-tolerant `declarationPattern`, optional `<…>` type-parameter list in
`createFunctionPattern`, and separate bracket/angle depth with the `>` of `->` excluded in `parameters()`
(impl.md §0). Factories, the six refactored tests and the shared-fake reuse are unchanged.

- **B1 — annotated declaration (blocking in round 1).** Lead repro: temp
  `@Suppress("unused") data class ProbeLeadAnnotated(val value: Int)` in
  `share/domain/model` + `ProbeLeadAnnotationTest.kt` constructing it →
  `TestDataFactoriesTest > test sources create test data only through test data providers() FAILED`,
  `…ProbeLeadAnnotationTest.kt:4: ProbeLeadAnnotated создаётся напрямую; используй Fake*Provider…`.
  Independently reproduced by test-reviewer (`…ProbeTRDirectCreationTest.kt:8: ProbeTRAnnotated…`) and
  qa-expert (its own `ProbeQAAnnotated` repro). Fixed.
- **B2 — required parameter after a function-type default (blocking in round 1).** Lead repro:
  `fun createProbeLambda(a: Int = 1, callback: () -> Unit = {}, required: String)` plus
  `fun <T> createProbeGeneric(value: T)` → `FAILED` with
  `…ProbeLeadFactoryProvider.kt:3: параметр required функции createProbeLambda без дефолта` and
  `…:5: параметр value функции createProbeGeneric без дефолта`. Independently reproduced by
  test-reviewer (`required` after `() -> Unit = {}`, and generic form) and qa-expert (both). Fixed,
  including the type-parameter form that round 1 listed as the same class of silent miss.
- After deleting every probe file, the guard is green again:
  `./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --rerun-tasks --console=plain`
  → `BUILD SUCCESSFUL in 10s`, XML `tests="2" failures="0"`; `git status --short` has no probe paths.

## 3. What the lead verified on the delivered tree

| Check (command) | Result |
|---|---|
| Guard (AC-1, AC-2, AC-3): `./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --rerun-tasks --console=plain` | `BUILD SUCCESSFUL in 10s`, exit 0; `TestDataFactoriesTest` XML `tests="2" failures="0"` |
| Full suite (AC-1, AC-6): `./gradlew test --rerun-tasks --console=plain` | `BUILD SUCCESSFUL in 2m 4s`, `65 actionable tasks: 65 executed`, exit 0; XML across 21 result files: `tests=62 failures=0 errors=0 skipped=0` (59 pre-existing + 2 guard + 1 parallel-task `DetektExcludesPathTest`) |
| Full build (AC-6): `./gradlew build --rerun-tasks --console=plain` | `BUILD SUCCESSFUL in 2m 10s`, `157 actionable tasks: 157 executed`, exit 0 |
| Capability map (AC-6): `./scripts/check-capabilities.sh` | `OK: capability map paths, test paths, statuses and ADR ids resolve`, exit 0 |
| Build files (AC-4): `git diff --stat -- '**/build.gradle.kts'` | one line only: `src/testing/arch-tests/build.gradle.kts \| 1 +` — the parallel board task `c40b0d2a`'s `inputs.file(rootDir.resolve("detekt-config.yml"))`; no line from this task (mtimes: that file 13:10, round-1 work 13:22–13:33, guard 14:10) |
| Round-2 delta scope | only `TestDataFactoriesTest.kt` has an mtime after the round-1 review summary (14:10 vs 14:08); the 7 tracked task files are 13:22–13:24 and the 10 factory/fixture files 13:22–13:33; `git diff --stat` of the tracked files is exactly impl.md §1 (7 files, 115+/94−) |
| Probe hygiene | `git status --short` has no `Probe*`/`Gate*`/temp files; `impl.md` sha256 still `941d99dc…671c60d` |

Reviewer evidence used above and in §4 (each reviewer quoted above/reproduced its own):

- test-reviewer: both negative runs reproduced; guard non-vacuous (a real production type was flagged);
  test 2 scans both factory roots (a probe in `src/receiver/src/testFixtures` was reported); each `@Test`
  can fail independently; own-type exemption exercised by `MessageTextTest`; the six refactored tests
  are behaviour-preserving, expected values remain explicit and the assertions still fail on a mapping
  change; no sleep/network/ordering added.
- qa-expert: adjacent shapes clean — annotation arguments with parentheses/commas, two annotations,
  own-line/multiline annotation, annotated nested class, `@JvmInline value class`, `sealed class`
  (included; spec excludes only `sealed interface`), DTO and nested port contract; generic/bounded
  `create`; multiline function-type default; comma-in-lambda default; annotated parameter.
  Exemption/scope clean — `<Type>Test.kt` and `<Owner>Test.kt` exempt only for their own type,
  `src/*/src/testFixtures` not scanned, a `*TestFixtures*`-suffixed file under `src/test` **is** scanned.
- code-reviewer: delta is minimal and load-bearing; non-capturing annotation prefix keeps the group
  indices used by `declarations()`; angle-depth tracking is required (otherwise `Map<K, V>` defaults
  split wrongly); `>`-after-`-` is the minimal `->` fix; no comments/TODO/dead code; no unrelated
  change to the round-1 delivery.
- security-auditor: no injection/network/deserialization; read-only walk bounded to `src/**/*.kt` (190
  main, 18 test, 30 factory files); no writes; failure text carries only relative path, line and
  identifiers; no new dependency.

## 4. Acceptance criteria on the delivered tree

| AC | Status | Evidence |
|---|---|---|
| AC-1 | met | guard green over the 18 `src/*/src/test/**/*.kt` files; the six refactored tests use providers only; the lead's annotated-declaration probe and test-reviewer's real-type probe show the scan is non-vacuous and can fail |
| AC-2 | met | all `create*` parameters in the 30 scanned factory files carry a default (guard green; round-1 enumeration of all 50 signatures); negative run for a required parameter fails with its name |
| AC-3 | met and reproduced | both required negative runs fail with `path:line` / parameter name and the tree is green after revert (impl.md §3.1–§3.4; lead + all three probing reviewers) |
| AC-4 | met | shared factories in `:src:testing:test-fixtures`; module factories in `src/persistence/src/testFixtures` and `src/receiver/src/testFixtures`; existing `java-test-fixtures` wiring used, no build file, dependency or module change from this task |
| AC-5 | out of scope | board task `docs-rule` (documents + doc-verify); not claimed by this task |
| AC-6 | met | `./gradlew test --rerun-tasks` 62/0, `./gradlew build --rerun-tasks` exit 0 (157 tasks), `check-capabilities.sh` OK |

## 5. Non-blocking findings (not rework requirements)

New in round 2 (all reproduced, all nit — no current violation; they are textual-scan corners to be
documented by `docs-rule`, not defects of the delivered factories or tests):

1. A comparison operator in an expression default still corrupts angle depth, so a later required
   parameter is missed: `flag: Boolean = limit > 0, required: String` or `a: Int = if (1 < 2) 1 else 2,
   requiredTwo: String` pass silently (lead repro: only `required`/`value` reported, not
   `requiredTwo`). Same class as round-1 B2, but pre-existing and not worsened by the delta; no
   delivered factory uses expression defaults. (test-reviewer, qa-expert, lead.)
2. A legal declaration with the annotation after a modifier (`public @Suppress(...) data class`) or a
   class on the same line as `@file:` is not collected. Unconventional; absent from the tree. (qa-expert.)
3. The B1 annotation prefix makes `declarationPattern` quadratic on a synthetic single-line failure
   input (`"@A(x) "`×n → 8.6 s at 96 KB); no trust boundary — the input is the repo's own linted
   `src/**` (longest line 261 chars). (security-auditor.)

Carried from round 1 (deliberately left per the §7 rework instruction; all nit, all still nit):
fail-open when `project.root` is absent (`TestDataFactoriesTest.kt:10`); unbounded/symlink-following
`walkTopDown`; own-type exemption merged by simple name for nested contracts; hint says `Fake*Provider`
while `PersistenceDataRequestProvider`/`PersistenceDataResponseProvider` do not match it; `createFromId`
duplicates `createHumanFromId`; line-based `Type` + newline + `.of(` not caught; text-scan false
positives (type names in comments/strings, commented-out `fun create*`, comma+colon in a string
default); incremental `:src:testing:arch-tests:test` is `UP-TO-DATE` after a test elsewhere changes
(needs a build-file change outside this task's allowed set).

## 6. Open risks for the human

1. The shared working tree contains the parallel board task `c40b0d2a`'s uncommitted changes
   (`AGENTS.md`, `detekt-config.yml`, `docs/adr/0005`, `src/testing/arch-tests/build.gradle.kts`,
   `DetektExcludesPathTest.kt`, `.agile/…`). Therefore `git diff --stat -- '**/build.gradle.kts'` is
   not empty in this workspace; the single added line is not this task's and must be attributed/merged
   with `c40b0d2a`, not reverted here.
2. Gate freshness: a local incremental `./gradlew test` can skip the guard after a test change because
   the scanned sources are not task inputs. CI from a clean checkout re-executes it; the limitation and
   the build-file follow-up should be stated by `docs-rule` (`docs/development/testing.md`).
3. The guard is a textual scan: its accepted limits (§5 above) must be documented by `docs-rule` so a
   false positive is not mistaken for a broken gate and a residual silent miss is not mistaken for
   compliance. `docs-rule` also owns AC-5; this board task does not claim it.
4. AC-3's canonical evidence lives in `impl.md` §3, not in a committed test report; the negative runs
   were independently reproduced by the lead and three reviewers and fully reverted.

## 7. Exit criteria

- Every declared reviewer posted a verdict for round 2: test-reviewer, code-reviewer, security-auditor,
  qa-expert — all `approve`, no blocking findings.
- `impl.md` was not edited after any verdict (sha256 `941d99dc…671c60d` at verdict time and at closure).
- No probe or temporary file remains; the working tree is the delivered state plus the parallel task's
  changes.
- Next: close this board task; board task `docs-rule` (AC-5) becomes ready.
