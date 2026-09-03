# KGE Restructure — Macro Roadmap

**Date:** 2026-08-31. Replaces `2026-08-30-kge-restructure-phase-0-1.md`, the
revision backlog of 2026-08-30, the design spec and the toolchain research —
all deleted; the git history of this branch is the archive. This doc +
`docs/decisions/phase-1.md` (append-only log of verified facts and per-concept
decisions) are the only active documents.

## Purpose and operating model

The previous plan tried to specify the whole migration up front. It froze detail
months ahead of production and split concepts at the wrong boundaries (the buffer
shipped without allocation service, close semantics or leak detection). That is
why this is a **macro roadmap**: a short, ordered list of concepts, each with its
*macro requirement*, its *invariants* and its *definition of done*, and nothing
else. Detail is produced **just-in-time**: each concept gets its own short
implementation plan (TDD steps) written when the concept starts, after a design
touch-point. When a seam turns out wrong, the fix happens inside the same
concept before it closes — never by shipping a provisional API that a later
concept must break ("no throwaway commits", at concept granularity).

**What this document commits and does NOT commit:**

- Commits: the macro requirement per concept; decisions already made; ordering
  invariants; the per-concept definition of done.
- Does NOT commit: contract shapes, signatures, test inventories, implementation
  choices, or outcomes of undecided questions. Those are decided at each
  concept's touch-point. Predictions are not facts.
- Depth decays with distance: near concepts (C4–C5) carry invariants; far
  concepts (C6+) carry only the macro requirement and their place in the order.

**What this document is for:** future sessions read it to know what the engine
is, where it came from and what comes next. It does not repeat detail that will
be decided in the concepts.

## Strategy

- Greenfield incremental rewrite on a separate long-lived work branch; `main`
  kept intact and is the reference/inspiration source (`git show main:<path>`).
- Single merge into `main` at the end (non-fast-forward if main moved).
- Concepts are brought back one at a time, tested first with new tests; behavior
  reference is olcPixelGameEngine v2.30
  (<https://github.com/OneLoneCoder/olcPixelGameEngine>) and exact pixel math.
  Old tests are not ported.
- Library API may break freely (solo project). Modules, examples, natives and
  publishing grow on demand — the skeleton contains nothing with no consumer yet.
- All written and committed documentation and commit messages are in **English**.
  Commits end with the co-author line.

## Guiding principles

These do not go through the decision lenses; no lens may eliminate one.

1. **Extensible by principle.** The project's core idea is that the engine is
   extensible/flexible — a fixed requirement, not an evidence-derived one. The old
   mechanism having never been exercised (no provider in `META-INF/services`) is
   the *pain* that motivated the rewrite ("Hard to extend" = pain #1), not a
   reason to cut the seam. Every capability with observable/user-replaceable
   behavior gets a service seam — exercised or not; the proof is an
   **extension-contract test** (override a service in a test module → behavior
   provably changes), not real-world usage.
2. **No resource leaks.** A resource is anything with an owner across the
   engine: native memory (JVM LWJGL), GPU objects (textures, programs, shaders,
   VAOs, vertex buffers), decoded image data, window and layer objects — the
   `KGEResource` concept from `main`, applied in many places. Every resource
   goes through the same contract: explicit `close()` (idempotent),
   use-after-close fail-fast, and detection of unclosed resources when a wrapper
   is collected (Cleaner / FinalizationRegistry). How detection reports — the
   previous implementation logged; that was `main`'s choice, not a requirement —
   is part of the C2 design and should be *testable* (e.g. hooks, assertions) so
   a leak fails a test rather than being observed in a log. Memory without GC is
   the *clearest* case, not the scope: the contract exists even where the
   platform GC would suffice (parity floor, principle 5).
3. **Pure, testable kernel.** No I/O and no dependency types in the kernel;
   pixel-exact parity tests. Pure math units have no service, no lifecycle.
4. **olc semantics as reference.** Where behavior is specified by
   olcPixelGameEngine v2.30 (<https://github.com/OneLoneCoder/olcPixelGameEngine>),
   the KGE behavior is what olc does (layout, blend math, draw rules), adapted to
   Kotlin.
5. **Parity floor.** JVM + web with the **minimum platform-specific code** (only
   the essential). Where one platform is more demanding, the common design adopts
   the full form in **both** — uniform contract, lean platform implementation
   (possible no-op where the platform does not need it); never an "exception for
   the less demanding platform". Uniform exceptions on both targets; one byte
   convention.
6. **LWJGL as a guiding principle.** On JVM, native memory via LWJGL (`MemoryUtil`)
   is the storage choice: scalable, zero-copy to the GPU. There is no serious
   alternative (`ByteBuffer.allocateDirect` returns cleanup to the GC/Cleaner —
   nondeterministic quota/timing). LWJGL counts as a reference with the same
   weight as olc. This is a Kotlin (JVM+web) engine: JVM-native paths are part of
   the design, not a C++ re-enactment.

## Decision path — three lenses

For every candidate concept, in this order:

1. **Real consumer.** Who uses it, in the concrete cycle engine → draw call → GL
   upload → user. Primary evidence: olc v2.30 semantics + our own loop. Code in
   `main` answers "did it work?", never "is it needed?".
2. **Seam lens.** Who allocates · who closes · who substitutes — formulated as
   "where must the design provide a seam **by principle**", not "did anyone
   substitute?". "Who substitutes" is answered by principle 1 (observable
   capability ⇒ seam, no evidence of use needed). What the lens measures: does
   the candidate need a transversal seam by principle, or is it a pure type with
   no entity in the roadmap?
3. **Minimal form.** The smallest shape that satisfies the consumer; proof =
   pixel-exact parity tests + hot-loop sanity; everything the consumer does not
   require is YAGNI.

Corollary: dependency pressure (LWJGL → java.nio; rAF vs GLFW) stays at the
**platform seam**; the dependency type does not propagate into the common kernel.

## Concept catalog (from `main`, evidence-based, no plan filter)

The catalog below is the evidence map (what `main` had, marked by boundary
strength). It is **not** a promise of what will ship; each concept's entry states
what is decided and what its touch-point decides. Markers: ● strong boundary
(own name, invariants, seams, consumed as a unit) · ◐ probable · ○ sub-concept /
internal helper.

### Transversals (belong to no consumer)
- **T1 ● Resource lifecycle** — the `KGEResource`/`ResourceWrapper`/
  `KGELeakDetector`/`KGECleanAction` semantics from `main` (close idempotent;
  use-after-close fail-fast; detection of unclosed resources on collection),
  uniform on both platforms (principles 2 and 5). The detection/reporting
  mechanism — and its testability (hooks/asserts) — is design work in C2, not a
  given from `main`. Def of done: contract + tests of the lifecycle semantics on
  both targets + decisions-log entry.
- **T2 ● Extensible service mechanism** — materializes principle 1. Redesigned
  at the 2026-09-02 touch-point (log #28): engine-fixed behaviors with
  consumer overrides — `KGEOverridable`/`Proxy` shape A, sensitive markers,
  `resetAll` internal; the C1 `KGEContext` contract is superseded. Proof =
  extension-contract test with per-platform defaults (expect/actual).
- **T3 ● Pixel display formats** — the hex/rgba string representation of `Pixel` as an
  extension capability (service seam per T2; default HEX; override proven by the
  extension-contract test). C4 ships a fixed `#RRGGBBAA` `toString()` (log #24); this
  concept — right after C1 — realizes the engine-level choice of representation, where
  main's mutable `defaultPixelFormat` var is the rejected anti-pattern. Macro only;
  detail at its touch-point.

### Representation / storage
- **S1 ● Native memory** (decision 2026-08-31: the buffer does NOT dissolve — it
  returns as a concept, with the seams that were missing).
  - Decided: direct/off-heap storage (JVM LWJGL — principle 6; web
    ArrayBuffer/TypedArray); allocation is a provider service (decisions 3-5,
    2026-08-30) — overridable (counting allocator, alternate backends);
    release/lifecycle follows the resource contract (T1); expect/actual allowed
    for this platform-boundary data type (decision 2026-08-31); little-endian
    convention.
  - Open (C3 touch-point): the contract shape — whether and where cursor
    semantics (position/limit/flip) are useful for common consumers, naming,
    typed views/bulk rules, provider API. Decided by the real consumers.
    Starting point: the rulings recorded in the decisions log (little-endian,
    memmove-safe overlap, uniform exceptions, byte-offset units) — candidates,
    re-decided here.
- **S2 ● Pixel/colours** — `Pixel` RGBA32 value class + ops + `Colors` (CSS Color 4
  named set + transparent) + fixed `#RRGGBBAA` toString. Decided at the C4 touch-point
  (log #24): value class over packed LE `Int` (memory bytes R,G,B,A); no endianness
  seam (all targets LE; `main` dropped BE in `61447d6`); pixel modes moved to R1.
- **S3 ● PixelMap 2D** — read/write surface contract (sample/clear/inv,
  row-major) over native memory. Open (C5 touch-point): name/semantics.
- **S4 ● Sprite** — surface + sample modes/Flip + ownership (surface owns its
  native memory resource) + creation service (create/duplicate; PNG at
  platform). Open: details at C5.

### Render (macro only — detail at each touch-point)
- **R1 ● Raster ops** — primitives over a surface; fast bulk paths; pixel modes
  (Normal/Mask/Alpha/Custom) + blend resolution math — moved here from S2 at the C4
  touch-point (log #24): the modes' only consumers are raster.
- **R2 ● Viewport/clipping** — pure clip math.
- **R3 ● Decal** — GPU-resident surface; modes/structures; instance batching.
- **R4 ● Renderer/pipeline** — Renderer service + platform backends; staging
  buffers platform-internal.
- **R5 ● GL** — facade + GLService (LWJGL GL33 / WebGL2 + multi-draw) + GL
  resource wrappers.
- **R6 ● Text** — font sheet + metrics + CPU and decal draw variants.

### Engine (macro only)
- **E1 ● Engine/lifecycle** — addon-based engine + platform engines; the known
  pain from `main` is the blocking/suspend asymmetry — the unified suspend loop
  is the approved design intent.
- **E2 ● Addons (user-facing API surface)** — mixins with defaults over
  facades/state. **E3 ● State** — `WithKGEState`, window composition, pure
  state machines; TimeState platform-coupled through a clock (form decided in
  C8). **E4 ◐ Input mapping** — common key enum + action types + platform
  mapping; open at C10 (KeyCode/InputAction touch-point). **E5 ○ Configuration**
  and **E6 ○ Window** — sub-concepts of E1/E3.

### Helpers (not domain concepts)
Int2D/Float2D, BytesSize, FormatUtils, InvokeUtils, PeekingIterator.

## Facade contract (decided 2026-08-30; redesigned 2026-09-02 — log #28)

- **Banned:** any `object`/companion holding a *resolved active service
  instance* (process-sticky) — the active implementation lives only in the
  mechanism's swappable registry (`AtomicReference` per service, no staleness
  by construction), never in a facade; any process-wide singleton cache
  outside the mechanism's own service registry. The engine-fixed default
  (`original`) is held by design — it is engine behavior, not the active
  service.
- **Allowed shape:** a **stateless facade** — the service's companion object —
  pure namespace delegating **per call** to the current implementation; no
  user-facing `getInstance()` anywhere. Immutable-data objects (`Colors`,
  constants) unaffected (principle 3).
- **`KGEOverridable` + `KGEOverridable.Proxy<O>`** — the T2 mechanism
  (replaces the C1 `KGEContext` contract): the facade companion extends the
  Proxy (`original` — `serviceType` is the private registry key); per-call
  forwarders to a `protected` `delegate`;
  `override(impl)` public, last-declared-wins; `resetAll()` internal (engine
  `onDestroy`; test teardown). The active implementation is a per-service
  `AtomicReference` (registry for `resetAll`; no hidden Koin container).
  Defaults register on first facade touch; the mechanism is available without
  an engine by design; the Proxy constructor is internal — services are
  engine-declared (consumers override, they do not declare new ones).
  No qualifiers, no per-service modules, no platform module — platform
  defaults are `internal expect`/`actual` implementation objects.
- **Original binding — identity by construction:** the default is a single
  instance, so `X.original` IS the un-overridden default; decorators delegate
  to the exact default object. The extension-contract test includes a
  delegating decorator.
- **Sensitive API:** `override` and `resetAll` carry `@KGESensitiveAPI`
  (`@RequiresOptIn(ERROR)`; project-wide opt-in inside `kge-core`) with risk
  docs — the deliberate "stop and think" callout; sensitive members are
  internal-ish per main's convention.
- **Verified (C1 #26, still in force):** the common atomics API
  (`kotlin.concurrent.atomics.AtomicReference`, `@OptIn(ExperimentalAtomicApi)`
  required). The Koin facts (4.2.2 semantics, koin-test, wasmJs run) were
  superseded at the Hunk round (log #28): the mechanism no longer uses Koin.
  At the design touch-point (log #28): kotlinx-collections-immutable 0.5.2
  for the service registry.

## Concept order

`C4` Pixel (warm-up; zero dependencies; validates the just-in-time flow at the
lowest risk)
→ `C1` extension mechanism (T2 materialization — closed 2026-09-01, then
redesigned at the 2026-09-02 T2 touch-point: `KGEOverridable` supersedes it)
→ `T3` pixel display formats (macro: extensible string representation; detail
at its touch-point)
→ `C2` resource lifecycle
→ `C3` native memory (S1 — Task-5 code fate decided here)
→ `C5` surface → `C6` raster ops → `C7` text → `C8` state → `C9`
renderer/GL/decals → `C10` engine (KeyCode/InputAction).

Ordering invariants (fixed): DI foundation before any service; provider +
lifecycle before the surface creation service; Pixel before raster; surface
before raster/sprite; pure math unconstrained. Surviving touch-points: C3
S1 contract, C5 surface naming/API, C6 tie rules, C10 KeyCode/InputAction;
T3 display-format detail at its touch-point (post-C1).

## Per-concept workflow

1. **Touch-point**: confirm the macro requirement + invariant reading, decide
   the open items, record the decisions.
2. **Micro-plan** (1–2 pages, TDD steps) written then, for that concept only.
   Future concepts stay unplanned.
3. **Implement** TDD: test → red → implement → green on all 3 targets; several
   commits per concept at meaningful green milestones; rework inside the concept
   before it closes is fine (the standing-rule granularity: never a provisional
   API a later concept must break).
4. **Close**: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green
   (`--rerun-tasks` mandatory — build cache produced a phantom green once, see
   decisions log items 9/11/15), review passed, entry in
   `docs/decisions/phase-1.md`, commit. **Review loop**: two-axis review →
   fixes → verify pass of the fix delta (the delta only, per round); at most 3
   rounds. Unresolved findings at the cap, or any escalation, mean the concept
   does **not** close — the owner decides (accept as known / different fix /
   abandon).

**Definition of done per concept:** its contract + seams (where applicable) +
tests + decisions-log entry. No concept closes with less.

## Verified stack (recorded in the decisions log)

Kotlin 2.4.10 (KGP) · Gradle wrapper 9.5.0 (daemon JDK 21; system 9.5.1 only to
generate the wrapper) · JVM bytecode 11 via `compilerOptions { jvmTarget }` DSL
(no toolchain fallback needed) · Koin 4.2.2 (koin-core, koin-bom; koin-test —
wasmJs support to verify at C1) · Kotest 6.2.4 + KSP 2.3.11 (KSP2 decoupled
from Kotlin; `io.kotest` plugin; kotest-runner-junit5 on jvmTest) · ktlint
12.3.0 (`org.jlleitschuh.gradle.ktlint`; exclude `/build/generated/`) ·
coroutines 1.10.2 · kotlin-logging 7.0.6 · kotlin-wrappers catalog 2026.8.5 ·
LWJGL BOM **3.4.3** (bumped at add-time by the latest-release rule;
`platform()` form; natives classifier on jvmTest) · foojay-resolver-convention
1.0.0 · kotlinx-browser 0.5.0 (wasmJsMain). Rules: pins in
`gradle/libs.versions.toml`; **at add-time always use the current release
unless a known problem exists**; record non-obvious findings in the log.

Build gotchas (in the log, do not relearn): root `build.gradle.kts` must NOT
declare these plugins with `apply false` (classloader scope clash on Gradle
9.5.0); ktlint filter excludes `build/generated/` via
`invariantSeparatorsPath`; KSP required by the kotest plugin.

## Testing strategy

- Oracle: olc v2.30 semantics + exact pixel-math cases; old tests not ported.
- Same `commonTest` suite on all targets (jvm, js-node, wasmJs-node) — the
  parity net.
- Kover on JVM as visibility, no percentage gate. Benchmark harness optional.
- CI gates node tests + lint (browser runs locally; actions currently
  disabled — workflow file lands when CI is discussed).

## Out of scope (grows by demand)

Full olc parity: rotated/warped decals, line patterns, `FillTexturedTriangle`,
ResourcePack, shaders/HW3D, user-shader API, PGEX/UTIL. Mouse input wiring,
audio. Android/iOS/Kotlin-Native targets. `kge-natives/*` consolidation,
example modules and publishing (Central Portal, vanniktech) wait until the
engine loop needs them or release time.

## Risks and notes

- `expect/actual` still flagged Beta in the Kotlin docs (possible future
  migration); the typealias-actual pattern is long-stable; risk recorded when
  S1 lands.
- JVM tests need LWJGL natives on the test classpath (OS/arch classifier logic).
- The branch diff will be large and is expected; mitigation: single merge,
  intact `main`.

## Current state

`kge-core` KMP module (jvm/js/wasmJs) + the CI workflow (scaffold described
above). **C4 (Pixel) closed on 2026-09-01** (log #24): `Pixel` value class + ops +
`Colors` (CSS Color 4, generated from the spec) + tests on all targets.
**C1 (extension mechanism) closed on 2026-09-01** (log #26/#27): `KGEContext` +
modules + extension-contract proof — **superseded on 2026-09-02** by the T2
redesign (log #28: `KGEOverridable` replaces the `KGEContext` contract, see
"Facade contract" above). **T3 (pixel display formats) closed on 2026-09-02**
(log #29): `PixelFormatService` — the first real T2 consumer.
**C2 (resource lifecycle, T1) closed on 2026-09-03** (log #30): the contract
(`KGEResource`/`ResourceWrapper`/`KGELeakDetector`/`KGECleanAction` + the
internal state machine), `LeakReporterService` (the second T2 consumer), the
thinnest possible expect/actual collection triggers (JVM Cleaner / web
FinalizationRegistry via `kotlin-js`), deterministic leak-path tests.
Next concept: C3 (native memory, S1) — the first consumer of the resource
contract.
