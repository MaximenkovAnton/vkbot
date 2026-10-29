# review-summary.md — docs-rule (round 2)

- Board task: `docs-rule` (stage `review`, round 2), task `0544056b-a6ef-4bd9-ac77-fba232fffd5f`, board `quick-fix`.
- Spec: `.agile/tasks/0544056b-a6ef-4bd9-ac77-fba232fffd5f/spec.md` (§4 rule, §6 guard, §7 AC-5/AC-6, §9 documents).
- Artifact under review: `impl.md` (sha256 `ca16485b3a902521b64238596bda380a9cced81f0bd4188924a0ee251029b388`, 402 lines), unchanged for the whole round — every verdict below is bound to these bytes, re-verified at close.
- Date: 2026-10-29. Workspace: `/Users/asmaksime1/IdeaProjects/test/vk`.
- Diff reviewed: exactly 6 files — `docs/development/testing.md`, `AGENTS.md` (§6/§8 only), `docs/architecture/patterns.md` (Factory section), `docs/capabilities/message-storage.md`, `vk-profiles.md`, `vk-message-send.md`. The shared worktree's `src/**` (factories-and-gate), `detekt-config.yml` + `AGENTS.md` §10 + `docs/adr/0005` + `DetektExcludesPathTest.kt` (parallel task `c40b0d2a`) are not attributable to this task.

## 1. Verdict of the review team (round 2)

| Reviewer | Decision | Blocking | Nits |
|---|---|---|---|
| test-reviewer | **approve** | 0 | 0 (6 evidence-only «checked clean» records) |
| code-reviewer | **approve** | 0 | 2 |
| security-auditor | **approve** | 0 | 0 (1 evidence-only record of checked classes) |
| spec-critic | **approve** | 0 | 2 |

Consensus reached: all four reviewers approve the same bytes. The round-1 blockers are fixed and no new blocking defect was found. No rework round is needed.

## 2. Round-1 blocking findings — both fixed (verified in the bytes, not the claim)

### B1 — factory family naming (`Fake*Provider` vs `PersistenceData*Provider`): FIXED

- `docs/development/testing.md:104-105` — rule is «фабриками `*Provider`»; naming convention `Fake*Provider`, persistence `PersistenceData*Provider`.
- `AGENTS.md:169-170` — «Test data comes only from `*Provider` factories (naming convention `Fake*Provider`; persistence factories are `PersistenceData*Provider`)».
- `docs/architecture/patterns.md:71-72` — same statement; `PersistenceDataRequestProvider.kt` is no longer called a `Fake*Provider`.
- The only remaining `Fake*Provider`-only sentence is `testing.md:165`, the verbatim quote of the real guard message (`TestDataFactoriesTest.kt:108-109`), kept as round 1 required; it is a quotation, not a rule.
- Matches delivered code: `PersistenceDataRequestProvider.kt` (9 `create*`) and `PersistenceDataResponseProvider.kt` (7) exist in `src/persistence/src/testFixtures` and are inside the guard's `factorySourceFiles()` scan; guard XML `tests=2 failures=0`.

### B2 — stale test line ranges: FIXED

- `docs/development/testing.md:187` → `PersistenceDataInProcessAdapterTest.kt:24-41`, `VkProfileInProcessAdapterTest.kt:16-34`.
- `AGENTS.md:186` → `PersistenceDataInProcessAdapterTest.kt:24-41`.
- `sed` confirms 24/41 and 16/34 are `@Test` / closing `}`. Capability files agree (`24-64`, `119-133`, `16-34`, `36-52`, `12-34`, `14-27`, `29-45`); stale-range grep over the six files (`38-49|17-33|38-62|111-123|14-62|35-51|15-28|30-44`) is empty, exit 1.

### Round-1 nits 1-5: all taken

1. «Ограничение» added: `limit > 0` default corrupting angle depth, annotation-after-modifier declaration not collected, `project.root` fail-open, strings/comments false positives (`testing.md:171-178`).
2. `patterns.md:76-78` now names the type scope of the ban.
3. Nested-contract exception `<Owner>Test.kt` present in all three documents.
4. Placement classifier explicit: `share` + `vk-facade` → `:src:testing:test-fixtures`; `persistence`/`receiver`/`processor` → `<module>/src/testFixtures` (matches the real dirs — only those three modules have `testFixtures`).
5. Placement duplication removed from `patterns.md` (factory file list only in «Где»).

## 3. Nits carried (non-blocking; no rework round)

1. **`patterns.md:76-78` states a narrower exception set** than `testing.md:116-119`/`AGENTS.md:177-178`: it names own-type/nested-contract only, not error exceptions, raw JSON bodies and directly-created dependency fakes. The ban there is type-scoped so it is deniable, not contradictory; polish for the next touch. (spec-critic)
2. **`testing.md:118-119` «фейки-зависимости (`Fake*`, `Recording*`) создаются напрямую»** collides with the guard: `FakeCommandRequest`/`FakeCommandResponse` live under `**/command/**` and end in `Request`/`Response`, so the guard forbids direct creation of them (correct path `FakeCommandProvider`, already listed at `testing.md:139`). Note the sentence mirrors spec §4.5 verbatim — the ambiguity is inherited from the specification, not introduced by this task; fixing the wording and §4.5 together is the clean follow-up. (spec-critic)
3. **`impl.md` §0 nit 1 cites `testing.md:169-176`** for the limits paragraph; actual span is `171-178`. Defect in the implementation report only, not in a delivered document. (code-reviewer)
4. **`AGENTS.md:129` / `testing.md:12` «… plus the `TestDataFactoriesTest` guard (18 tests)»** accounts for 17 of the 18: the 18 includes `DetektExcludesPathTest` (`testing.md:36` table breaks it down; AGENTS.md names it only in §10). Cheap reword: name both guards or use class-agnostic wording. (code-reviewer)

## 4. Checked and found clean (consolidated)

- **AC-5 content.** All three documents carry the §4 rule (forbidden direct `Type(...)`/`Type.of(...)` for `share` domain models/VOs/events, adapter DTOs under `**/adapter/output/**/dto/**`, port/command request/response incl. nested), factory placement, a realistic default on every `create*` parameter, exceptions (own-type `<Type>Test.kt`, nested `<Owner>Test.kt`, error exceptions, raw JSON integration bodies), the guard name `TestDataFactoriesTest`, and the order «добавили поле → дефолт в фабрику → тесты не меняются». AC-5 grep over the three files → exit 0, all three present.
- **Names, paths, ranges.** 50/50 `src/**` paths named in the six documents exist. Factory inventories match the files: `FakeVoProvider` 12 `create*` at `18-69`, `FakeModelProvider` 5, `FakeProfilePortProvider` 2, `FakePersistencePortProvider` 9, `FakeVkFacadeProvider` 5, `FakeCommandProvider` 2, `PersistenceDataRequestProvider` 9, `PersistenceDataResponseProvider` 7, `FakePublishVkEventCommandProvider` 1. The fixture tree in `testing.md:64-97` matches the real tree. Every cited range starts at `@Test` and ends at the test's `}`.
- **Test ledger.** `./gradlew test --console=plain` → `BUILD SUCCESSFUL`, exit 0 (re-run by test-reviewer, 1m 18s); sum of 21 `TEST-*.xml` = tests 62 / failures 0 / errors 0 / skipped 0; per module app 7, receiver 9, share 8, vk-facade 7, persistence 9, processor 2, infrastructure 2, arch-tests 18 (Module 7 + Layer 5 + Gradle 3 + TestDataFactories 2 + DetektExcludesPath 1). Matches `testing.md:21-23/36` and `AGENTS.md:122/129`; stale 59/15/61/17 absent (grep empty, exit 1).
- **Guard description truthfulness.** `testing.md:156-178` matches `TestDataFactoriesTest.kt`: both test names, scan scope (`src/<module>/src/test`; `testFixtures` not scanned for direct creation), failure formats `path:line: <Type> создаётся напрямую…` and `path:line: параметр <name> функции <fn> без дефолта…`, own-type/`<Owner>Test.kt` exemption, and only real limits (strings/comments false positives, `copy(...)`, split-line `Type`/`.of(`, expression default `<`/`>`, annotation-after-modifier, `project.root` fail-open, arch-tests `UP-TO-DATE` because scanned sources are not declared inputs; CI runs `./gradlew test` on a clean checkout). Negative-run claims are backed by `factories-and-gate/impl.md` §3.1-§3.4 with quoted `path:line` output and rollback PASSED. No document claims a catch the guard does not perform.
- **AC-6 capability part.** `./scripts/check-capabilities.sh` → `OK: capability map paths, test paths, statuses and ADR ids resolve`, exit 0 (run by the lead, test-reviewer, code-reviewer, security-auditor and spec-critic on the same bytes).
- **Capability rows.** Three capability diffs are line-range-only replacements (numstat 2/2, 3/3, 2/2); no AC row, entry point or test path removed; `index.md` untouched; new ranges resolve to `@Test`/`}`. No new capability row.
- **No test theatre / no false evidence.** Docs-only change; nothing advertises a check the guard does not perform; the negative-run evidence shows the actual executed FAILED/PASSED runs; no sleep/network/ordering introduced.
- **Scope discipline.** Exactly the six allowed files are attributable; `AGENTS.md` edits stay in §6/§8; no code, build file, ADR, detekt config or `.github/**` change belongs to this task (mtimes: round-2 docs 15:13-15:14, capability files 14:50, `src/**`/detekt/ADR ≤14:10).
- **Security (security-auditor).** No secrets, tokens, credentials, URLs, hosts, ports, env vars or commands introduced; no new operational instruction with security consequence; the guard-limits text is honest about the fail-open rather than claiming enforcement; no gate weakened; injection/deserialization/transport/dependency classes are not reachable from a docs-only diff (OWASP/CWE not applicable). The `detekt` excludes change in the shared tree belongs to `c40b0d2a` and narrows the glob (strengthens lint).

## 5. Open risks (not blocking)

- **Cross-task dependency.** 62/18 and the `DetektExcludesPathTest` rows describe the parallel, still-uncommitted `c40b0d2a` test in the shared tree. If that task is dropped or reworked, the numbers/rows go stale (61/17 fallback, documented in `impl.md` §4.1).
- **`docs/architecture/modules.md:164`** still cites `FakeVoProvider.kt:14-62` (file is now 18-69); outside this task's allowed files — follow-up.
- **Guard blind spots** (expression default with `<`/`>`, annotation-after-modifier, `project.root` fail-open) are now documented as accepted limits, not fixed; they remain available for a future code task.
- **`./gradlew build` was not run in this docs task.** AC-6 is closed here in its `capabilitiesCheck` part plus the full `./gradlew test`; the full build was proven in `factories-and-gate` on the same code. Docs-only edits cannot change the build outcome.
- **Shared worktree is uncommitted.** Completion of this board task does not commit `docs-rule` (nor `factories-and-gate`/`c40b0d2a`); the task-level decision should account for the commit.

## 6. Next step

No rework required. `docs-rule` meets AC-5 and the `capabilitiesCheck` part of AC-6 on the reworked bytes; the four nits are polish and do not gate done. Recommend completing the board task and carrying nits 1-4 (especially the `Fake*`/`FakeCommandRequest` wording, which needs spec §4.5 and §4 item 5 aligned with it) as a small follow-up.
