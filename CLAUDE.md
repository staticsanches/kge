# CLAUDE.md

Guidance for Claude Code work in this repository.

## Project

**KGE** — a Kotlin Multiplatform game engine, a free adaptation of
[olcPixelGameEngine](https://github.com/OneLoneCoder/olcPixelGameEngine),
targeting JVM (LWJGL/GLFW/OpenGL) and web (WebGL2 via kotlin-wrappers).

**Current state:** greenfield restructure in progress on the work branch. The
engine is being rebuilt concept by concept; the old engine lives in the git
history of `main` (reference/inspiration only: `git show main:<path>`). So far:
`kge-core` KMP module (jvm/js/wasmJs) with scaffold smoke tests on all targets,
the CI workflow, C4 (Pixel) closed (log #24), the extension mechanism closed
then **redesigned** at the T2 touch-point (log #28) — `KGEOverridable`
supersedes the `KGEContext` contract — T3 (pixel display formats, log #29,
the first real T2 consumer), **C2 (resource lifecycle, T1, log #30)**:
resource contract + `LeakReporterService` + leak detection, and **C3 (native
memory, S1, log #31)**: `ByteBuffer` (JVM = `java.nio.ByteBuffer` via
typealias-actual; web TypedArray emulation) + `MemoryAllocatorService` — the
first platform-defaulted T2 service. Next concept: C5 (surface — S3/S4). No
renderer or engine loop yet.

## Read first

- `docs/plans/2026-08-31-kge-restructure-roadmap.md` — the macro roadmap:
  concept list, guiding principles, decision lenses, ordering, per-concept
  workflow. **The plan is the roadmap; detail is not frozen ahead.**
- `docs/decisions/phase-1.md` — append-only log of verified facts and
  per-concept decisions. Future sessions use it, do not question without evidence.

These two are the only active documents; older plans/specs were deleted
(history is the archive).

## Working rules

- **Concept flow**: touch-point (design confirmation, open items decided) →
  micro-plan (1-2 pages, TDD steps, just-in-time) → implement → gate → log
  entry. Never a slice of a concept; never a provisional API a later concept
  must break ("no throwaway commits" — restructure at the concept checkpoint).
- **TDD**: failing test → run (red) → implement → run (green), per feature; the
  micro-plan's test code is the contract.
- **Gate (every concept close)**: `./gradlew :kge-core:allTests ktlintCheck
  --rerun-tasks`. **Why `--rerun-tasks` is mandatory:** build cache and
  configuration cache (both enabled in gradle.properties) can return up-to-date
  results without executing — a "green" can be stale. Historical proof:
  `jvmTest` once reported "1 test" while the kotest engine never ran (decisions
  log item 15). Only force-executed green counts. JVM, JS (node) and wasmJs
  (node) run the same commonTest suite.
- **Behavior reference**: olcPixelGameEngine v2.30 semantics + exact pixel math;
  old tests are not ported, evidence of `main` is not a mandate (see the
  roadmap's three lenses).
- **Dependencies**: at add-time always use the current release unless a known
  problem exists; record non-obvious findings in the decisions log.
- **Docs and commit messages in English**; commits end with
  `Co-Authored-By: Claude Code <noreply@anthropic.com>`; committed documents carry
  no personal quotes — decisions are recorded by rationale, not by who said them.
  **Commit messages are succinct**: a subject plus the non-obvious core — no gate
  history, no test counts, no review narratives, nothing deducible from the diff
  (context lives in the decisions log).
- **Delivery**: commit-ready work; the owner reviews, pushes, and may implement
  parts personally. Do not push.

## Commands

```bash
./gradlew :kge-core:allTests              # all targets (jvm + js-node + wasmJs-node)
./gradlew ktlintCheck                     # lint (never accept --rerun-less gates)
./gradlew :kge-core:jvmTest               # JVM only
```

`jvmTest` runs through `kotest-runner-junit5` + `useJUnitPlatform()` in
`kge-core/build.gradle.kts` — kotest's Gradle plugin does not wire the JVM
target under KGP 2.4.10, and without that wiring `jvmTest` executes zero tests
(decisions log item 15; the phantom green that motivates the `--rerun-tasks`
rule).

A JDK 21 daemon and the Gradle wrapper 9.7.1 are pinned; bytecode target 11.
