# KGE Restructure — Design Document

Date: 2026-08-30
Status: Approved design, pending implementation plan

## Context

KGE is a Kotlin Multiplatform game engine (a free adaptation of olcPixelGameEngine)
currently targeting JVM (LWJGL/GLFW/OpenGL) and JS (WebGL2). It was created with
Kotlin 2.1.20, uses an addon-based API over an SPI service mechanism
(`KGEExtensibleService` via `ServiceLoader` on JVM, manual registration on JS),
and has ~31 thin tests with no coverage tooling.

**Pains driving the restructure:**
- **Hard to extend**: the SPI mechanism has never been exercised (no
  `META-INF/services` provider exists in the repo; everything always falls back
  to the default implementations). Adding a new capability requires an addon +
  service + platform registration + state — wired three different ways per platform.
- **Outdated stack**: Kotlin 2.1.20 predates the 2026 KMP model (new default
  project structure, Kotlin 2.4, wasmJs as a first-class browser target, Koin as
  the de facto KMP DI).
- **Unreliable tests**: existing tests are thin, and the API as a whole is
  untested (no engine lifecycle tests, no extension-contract tests).

## Strategy (decided with the owner)

**Greenfield incremental rewrite** on a single long-lived branch:

1. The branch starts with a brand-new project skeleton and **zero inherited code**.
2. Concepts/services are brought back one at a time, each **tested first** with
   **new tests** (old tests are not ported — the reference for behavior is the
   olcPixelGameEngine semantics and exact, verifiable pixel math).
3. The platform-dependent engine loop is built **last**, once the pieces it
   wires together are tested and stable.
4. Library API may break freely (solo project; only examples consume it).
5. Modules, examples, natives artifacts, and publishing grow **by demand** —
   the initial skeleton contains nothing that has no consumer yet.

Working agreements:
- Conversation language is Portuguese; **all repo documentation and commit
  messages are in English**.
- Work happens on **one separate branch** (`restructure`), merged
  into `main` once at the end; `main` stays intact during the work.

## Section 1 — Target stack and module skeleton

| Item | Decision |
|---|---|
| Kotlin | **2.4.10** (UUID/Instant API now stable in common stdlib; `ExperimentalUuidApi` opt-in no longer needed) |
| Gradle | **9.5.0** (max of KGP 2.4.10 support range 7.6.3–9.5.0), daemon **JDK 21** |
| Compilation | **jvmToolchain 11**, bytecode target **11** — the real floor of the dependency set (Koin 4.2 JVM artifacts are published at JVM target 11; LWJGL 3.3.x requires Java 8; coroutines/SLF4J 8). JVM 8 cannot be chosen without fighting Koin 4.2. Consumer runtime perf is the consumer's JVM choice, not the bytecode target. |
| Modules (initial) | **`kge-core` only**, KMP library with targets `jvm()`, `js(IR)`, `wasmJs()` |
| Source sets | `commonMain`/`commonTest`, shared web group for `js`+`wasmJs` (`webMain`/`webTest` if provided by the default hierarchy template, else a custom `webMain` via `applyDefaultHierarchyTemplate()` + `by creating { dependsOn(commonMain) }` — the documented manual procedure), plus `jsMain`/`wasmJsMain`/`jvmMain` |
| Frameworks | Koin **4.2.x** (`koin-core`, `koin-test`; koin-bom), kotlinx-coroutines (+ `-test`), **Kotest 6.2.x** (+ KSP `io.kotest` plugin for wasmJs/JS test discovery), kotlin-logging, kotlin-wrappers **updated to latest** (repo is active/not deprecated; `web.gl` remains valid), ktlint (version current) |
| Left out initially | `kge-example-*`, `kge-natives/*`, active publishing — all added when the need appears (engine loop → examples; release → publishing) |
| Publishing (later, unchanged target) | Maven Central via **Central Portal**; vanniktech plugin **0.35.0** (OSSHR/Nexus is retired) |

The module layout mirrors the 2026 "new default KMP project structure" (shared
pure-KMP library + one app module per runnable platform): `kge-core` *is* the
`shared` module; when examples appear they become `kge-example-desktop`
(desktopApp) and `kge-example-web` (webApp, js+wasmJs).

## Section 2 — Internal architecture

Layered so the kernel is testable without platform dependencies and extension
goes through one homogeneous mechanism (Koin).

### Layer 0 — Common kernel (`commonMain`)

Pure logic, no platform knowledge:

- `Pixel` / colors / pixel modes (Normal/Mask/Alpha/Custom, blending math)
- `PixelMap` (2D view over a 1D buffer) / `Sprite` (buffer + sampling mode):
  semantics and naming are **decided in-phase** (see Open items) — the 2D-over-1D
  abstraction itself is considered valid and load-bearing for testability
- Raster primitives (line, circle, rect, triangle via scanline), clipping
  (Cohen–Sutherland), math/vectors
- `TimeState` / `InputState` as pure state machines (events arrive via listener
  seams; no platform type in the kernel)
- Font sheet generation and text layout

**Buffer seam (key testability decision).** The kernel operates on a minimal
common `Buffer` interface (position/limit/put/get semantics — the same shape
today's `expect class Buffer` already exposes). Implementations:

- `JvmNativeBuffer` — LWJGL `MemoryUtil` buffer; the real runtime buffer in the
  JVM (direct, GPU-uploadable). This is the LWJGL buffer itself, not a copy: the
  JVM core "knows LWJGL" in the sense that its runtime buffer is LWJGL's.
- `JsBuffer` / `WasmJsBuffer` — TypedArray-backed (as today).
- No pure-Kotlin heap buffer in the baseline (dropped from an earlier version of
  this design; JVM tests already run against LWJGL buffers with natives on the
  test classpath).

Consequence: all raster code is shared between JVM and web without duplication,
and the platform-producing side is reduced to buffer wrappers — exactly what the
existing spot already had, but expressed as a common interface + implementation
rather than `expect/actual classes` (aligning with the 2026 KMP guidance and with
the Koin-homogenization goal).

### Layer 1 — Services (`commonMain`)

- One **interface per capability** with a default implementation registered in
  `kgeDefaultModule` (Koin): drawing primitives, decals, layers, text, sprite I/O.
- **Static facades keep the "static access point" ergonomics**: `object
  Rasterizer`, `object Renderer`, `object GL` etc. become Koin-aware components —
  `val service: XService by inject()` — resolved **once at first use and cached**
  (same cost profile as today's `companion object : X by get()` delegation).
- **Engine-owned Koin context**: each engine `start()` builds its own local
  `KoinApplication` (not the global context); facades resolve from the active
  context. One active engine per process/web-context (as in olcPixelGameEngine).
- **Extension contract**: users provide a module; later `single<X>` declarations
  override the default (Koin last-declared-wins semantics replace
  `servicePriority`, which is dropped). This is a **tested contract**: a test
  overrides a service in a test module and proves the behavior changed.

### Layer 2 — Platform (`jvmMain` / `webMain`+`jsMain`+`wasmJsMain`)

Implementations registered in `kgePlatformModule` (per platform):

- Native buffers and GL/renderer: JVM → GLFW + OpenGL 3.3 (`GL33`) on LWJGL
  MemoryUtil buffers; web → WebGL2 (`web.gl`) with TypedArray buffers
- Time clocks (GLFW `glfwGetTime` vs rAF/`Date.now`)
- Resource lifecycle: `ResourceWrapper` + leak detection via `Cleaner` (JVM) /
  `FinalizationRegistry` (web)
- Input plumbing and callbacks mapping (GLFW key/drop callbacks; DOM listeners)

No `ServiceLoader`, no manual JS registration; no `META-INF/services`.

### Layer 3 — Engine (`commonMain`)

- **Unified suspend execution model** (approved): one `suspend` game loop in
  common code; user callbacks (`onUserCreate`/`onUserUpdate`/`onUserDestroy`,
  input callbacks) are `suspend` on **all** platforms
- Platform dock via `PlatformLoopDriver` (Koin-provided): JVM performs GLFW
  event polling inside the coroutine loop and swap buffers; web awaits
  `requestAnimationFrame` (JS `awaitAnimationFrame`); `start()` returns a `Job`
  and supports cancellation
- No more blocking-JVM vs suspend-JS API asymmetry — this removes the duplicated
  lifecycle/polling code and makes the loop testable with `coroutines-test`

### Cross-cutting

- `KGECleanable`, resource wrappers and leak detection stay (kernel-interface
  based; platform act as today)
- Logging stays kotlin-logging + slf4j/logback on JVM
- Build opt-ins that disappear: `-Xexpect-actual-classes` (no expect/actual
  classes left for buffers), `ExperimentalUuidApi` (stable), and `KGESensitiveAPI`
  if it no longer earns its cost — decided at implementation time
- Existing `CLAUDE.md` describes a stale API (documents `KeyboardCallbackAddon`
  which no longer exists). It gets rewritten during this work (in English)

## Section 3 — Testing strategy

Every concept/service lands in the new architecture **with tests written from
scratch**; behavior reference is olcPixelGameEngine (v2.30 in the workspace) and
exact pixel-math cases. Old tests are not ported.

Order = dependency order (each layer verified before it is consumed):

| Step | New tests | Target |
|---|---|---|
| Pixel/colors/modes | components, endianness, blend, mask | common |
| PixelMap/Sprite | read/write, edges, clipping, sample modes, create/duplicate | common |
| Primitives | line, circle, rect, triangle (clipping, alpha, spans), exact-result cases | common |
| TimeState/InputState | FPS calc, event queue, press/release/repeat | common |
| Font/text | widths, tab, wrapping, charset | common |
| XService contracts | behavior on a test `PixelMap` → exact pixels | common |
| Decals/layers | structures, UV, tint, order, offsets, `update` flag | common |
| Platform buffers | index/endianness, limits, flip | per target |
| Engine loop | coroutines-test: create→update→destroy sequence, tick, stop | JVM (web smoke tests) |
| Extension | override a `single<X>` via test module → behavior provably changes | JVM |

Stack: **Kotest 6.2.x** (`kotest-framework-engine`, `kotest-property`,
matchers) + `kotlinx-coroutines-test`. Kotest multi-target support (JVM, JS,
wasmJs) works through the **KSP-based `io.kotest` plugin** (no runtime classpath
scanning on web); the wasmJs engine is feature-limited (no annotation-based
config — keep common tests simple), so sophisticated property/differential
tests live in commonTest as long as `kotest-property` supports wasmJs, else in
`jvmTest` (decided at Phase 1 scaffolding). Everything shared lives in
`commonTest` and runs on **all three targets** (this is the cross-platform
parity net: same kernel must produce the same raster on JVM, JS and wasmJs).
`./gradlew allTests` is the gate. Kover reports JVM coverage as visibility,
**no percentage gate** (decision; a gate can be added later in one step).
Benchmark harness (rasterizer benchmark, task `benchmark`) is kept as an
optional tool.

## Section 4 — Phasing and branch strategy

Branch: `restructure` (created in Phase 0). `main` untouched.
Periodic rebases of main into the branch (cheap, repo is solo and slow-moving);
single merge commit at the end (non-fast-forward if main moved).

| Phase | Content | Exit gate |
|---|---|---|
| 0 — Bootstrap | branch; new `settings`/builds with only `kge-core` (Kotlin 2.4.10, Gradle 9.5.0/daemon 21, toolchain 11, Koin 4.2.x, ktlint); CI on push to the branch (`build` + `allTests`); research + this doc committed | green build on a clean checkout |
| 1 — Kernel | Buffer interface + impls; Pixel/colors; PixelMap/Sprite; primitives — each with tests | kernel tested on 3 targets |
| 2 — Services | Koin interfaces + defaults, `kgeDefaultModule`, static facades, decals/layers/font, extension-contract test | extension contract proven |
| 3 — Platform | JVM (GLFW/GL/LWJGL/clock/leak) and web (WebGL2/rAF/TypedArray) in `kgePlatformModule` | `allTests` green on 3 targets |
| 4 — Engine | unified suspend loop + `PlatformLoopDriver`, suspend callbacks, engine tests; **`kge-example-desktop` and `kge-example-web` appear here** to validate real use | engine tested; examples run |
| 5 — Consumption | when publishing: consolidated `kge-natives/*` + Central Portal publishing (vanniktech 0.35.0); CI/gh-pages updated | publishing verified |
| 6 — Merge | final rebase, merge into `main`, rewrite `CLAUDE.md` (English) | main green |

Conventions: commits in English, CI green per phase, docs in English.

## Open items (decided in-phase, recorded when decided)

- **Per-phrase concept decisions**: which concepts from the old code are brought
  back and under what names (e.g., `PixelMap` — value confirmed as valid, name
  and exact semantics decided at the start of its phase; supported by olc
  reference semantics and its tests). The spec intentionally does not freeze
  these; each phase opens with a small design session on candidates.
- `webMain` exact template name (`webMain`/`webTest`) — verify at scaffold;
  fallback is the documented manual custom source set.
- wasmJs test runner (Node vs browser) and Kover's scope on JS/wasmJs — verify
  during Phase 3 setup.
- Exact latest patch: Kotlin 2.4.x and Koin 4.2.x latest — verify at scaffold.
- `KGESensitiveAPI` fate — decided during the rewrite.

## Explicitly out of scope (grows by demand)

- Full olcPixelGameEngine parity: rotated/warped decals, line pattern, circle
  mask, `FillTexturedTriangle`, `ResourcePack`, `Renderable`, shaders/HW3D,
  user-shader API, extensions (PGEX/UTIL)
- Mouse input wiring (types already exist in the old code), audio (an extension
  in olc, not core)
- Android / iOS / Kotlin-Native targets
- New project structure beyond the initial files: `kge-natives/*` consolidation
  and example modules wait until the engine loop actually needs them

## Risks and notes

- Old-code references in the workspace (`kge-mp`, `old-kGE`, `test-kge`,
  `knes`, `olcNES`) are consultative only.
- JVM tests need LWJGL natives on the test classpath (as today); CI keeps the
  per-OS classifier logic for tests.
- The branch diff will be large and is expected; mitigation is the single merge
  and the stable `main`.
- Kotlin 2.4 warns: some warnings become errors and annotation use-site defaults
  changed — handle during Phase 0 bootstrap.

## Recommended follow-up skills

- Implementation plan via the writing-plans skill (next step)
- Within the plan: superpowers TDD for all new code; verification-before-completion
  at each phase gate
