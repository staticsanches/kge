# KGE

Guidance for AI agent work in this repository (opencode / Claude Code).

## Project

**KGE** — a Kotlin Multiplatform game engine, a free adaptation of
[olcPixelGameEngine](https://github.com/OneLoneCoder/olcPixelGameEngine),
targeting JVM (LWJGL/GLFW/OpenGL) and web (WebGL2 via kotlin-wrappers).

**Current state:** greenfield restructure in progress on the work branch. The
engine is being rebuilt concept by concept; the old engine lives in the git
history of `main` (evidence, not a mandate: `git show main:<path>`). So far:
`kge-core` KMP module (jvm/js/wasmJs) with scaffold smoke tests on all targets,
the CI workflow, C4 (Pixel) closed (log #24), the extension mechanism closed
then **redesigned** at the T2 touch-point (log #28) — `KGEOverridable`
supersedes the `KGEContext` contract — T3 (pixel display formats, log #29,
the first real T2 consumer), **C2 (resource lifecycle, T1, log #30)**:
resource contract + `LeakReporterService` + leak detection, and **C3 (native
memory, S1, log #31)**: `ByteBuffer` (JVM = `java.nio.ByteBuffer` via
typealias-actual; web TypedArray emulation) + `MemoryAllocatorService` — the
first platform-defaulted T2 service. C5 (surface — S3/S4, log #33) closed:
`Pixmap`/`MutablePixmap` (mode-aware `get`, nearest/bilinear sampling),
`Sprite` over native memory and the platform-independent
`SpriteService`. **S5 (PNG codec, log #34) closed, then generalized by S6 (log
#19, closed 2026-09-11)**: `ImageService` (generic `Decoder<T>`/`Encoder<T>`,
suspend `load`/`save`, JVM STB, web-native `createImageBitmap`/canvas), the
extension codecs (`BytesDecoder`/`Base64Decoder`/`UrlDecoder`/`FetchDecoder`,
`PngEncoder`/`JpegEncoder`/`Base64PngEncoder`), and a module-wide kotest
`ProjectConfig` that resets service overrides after every test. **C6 (raster ops, R1, log #35) closed**: four per-scope raster
sub-services (`DrawService`/`OutlineService`/`FillService`/`DrawSpriteService`)
aggregated by `Rasterizer`, pixel-mode blend via the draw seam, native bulk
buffer ops, and the `SpriteService` rename. **Vector/point concept (closed
2026-09-09)**: pure `Int2D`/`Float2D` `data class`es (`math/vector`) + typed
`Int2D` overloads on the raster sub-services (interface defaults + companion
`Proxy` analog forwarding). **Ordering revision (2026-09-10, owner):** the old
`C7` (simple text) is dropped — text becomes **elaborate text** (shaping +
rasterization + atlas + blit), shipped **last** as `R6`, after `R2`, `C9`,
`C10`; the `main` bitmap font is not ported. **R2 (viewport/clipping,
closed 2026-09-10)**: the pure `Viewport` sealed type, the `ClipService` seam
(fifth raster sub-service, olc `ClipLineToDrawTarget` Cohen–Sutherland),
`Pixmap : Viewport.Bounded`, and the clip-then-walk `drawLine` that clears the
C6 partial-OOB debt. **Window + partial blit (closed 2026-09-10, log #16)**:
the nested `Pixmap.Mutable`/`Pixmap.RawBacked`, the anonymous `Pixmap.window`
views, `BlitService`/`blit`/`blitRegion` over a `Pixmap` source (the
`DrawSpriteService` rename), `Flip` moved to `Pixmap`, and
`Sprite.byteBuffer` retired for `RawBacked.buffer`. **Circle octant masks
(closed 2026-09-11, log #17)**: the `CircleOctantMask` type and the required
`mask` on `drawCircle`/`fillCircle` (raw + `Int2D`, forwarded by `Rasterizer`)
— `ALL` is the untouched C6 behavior. **Line patterns (closed 2026-09-11, log
#18)**: the `LinePattern` sealed type and the required `pattern` on
`drawLine`/`drawRect`/`drawTriangle`, consumed per walked cell from the first
cell of the clipped walk — `Filled` is the untouched pre-change behavior. **Image service
`S6` (closed 2026-09-11, log #19)**: supersedes `S5` with a platform-generic
`ImageService` (generic `Decoder<T>`/`Encoder<T>`, suspend `load`/`save`,
`Sprite` RGBA-only, `PNG`/`JPEG` uniform encode, documented per-platform decode
divergence). The web targets are now **browser-only** (node dropped) and the
browser suites run in CI. `C9` (renderer/GL/decals) closed 2026-09-12 (log
#22): the GL layer, the renderer/pipeline and the decal exist. `C10` split at
the 2026-09-13 touch-point into `C10a` (loop/window/time) → `C10b` (input) →
`C10c` (addons), **all closed**: `C10a` (log #24) — the `Driver`/`DriverService`
seam (JVM GLFW + web canvas/WebGL2), `TimeService`/`FrameAccumulator`,
`WindowConfig` and the abstract `Engine` loop with suspend callbacks (fractional
letterbox, clear/present); `C10b` (log #25) — the entry-less `expect enum
KeyboardKey` + companion intersection vocabulary, `ButtonState`/`InputTracker`
(olc `HWButton`), mouse/focus/modifiers, `Driver.input` with JVM GLFW/web DOM
backends, and `Engine.input`; `C10c` (log #27) — the ISP roles (`HasWindow`/
`HasTime`/`HasInput`/`HasLayers`/…) and addons, `Layer`/`LayerStack` and the olc
layer render step. The `kge-benchmark` module (FPS sweep, log #26) and the
golden-image test harness (log #28) are also in. **Text is the last area.** The
2026-09-16 touch-point fixed the stack (HarfBuzz + FreeType, thin per-platform
seam) and split the work: `C7` bitmap text in `kge-core` (revived; zero new
deps) and `R6` elaborate text as the opt-in `kge-text-ttf` module (rounds B–E).
**`C7` round A closed 2026-09-16 (log #29)**: the stateless scope-parameterized
`DrawStringService` (private font holder), `HasResourceScope`, the engine wiring
and `DrawStringAddon` (CPU + decal). **`R6` round B closed 2026-09-16 (log
#30)**: the `kge-text-ttf` scaffold — a new jvm/js/wasmJs module with the
HarfBuzz/FreeType deps wired and smoke-tested on every target. **Bundled fonts
closed 2026-09-19 (log #31)**: the `buildSrc` embedder plus the data-only
`kge-font-roboto` (Roboto 3.015 / Roboto Mono 3.001 variable, OFL-1.1), which is
also round C's `commonTest` fixture. **Next: `R6` round C — face + HarfBuzz
shaping + public layout** (micro-plan revised 2026-09-19: shipped-font fixture,
base64 entry point + `BufferService`, contract re-measured on the default
instance; variable axes deferred).

**Text (R6) — touch-point decided 2026-09-16; research recorded.** The
font-library research (FreeType/HarfBuzz across JVM + js + wasmJs, candidate
stacks, UNVERIFIED items) is in `docs/decisions/phase-1/14-text-r6.md` — **do
not re-research**; the touch-point decisions and spike findings are in
`docs/plans/2026-09-16-r6-text-touchpoint.md`. The 2026-09-10 note that the
`main` bitmap font was not ported is **reversed** by `C7` (log #29). The
2026-09-19 touch-point revision amends the `Font` byte transport (base64 entry
point + `BufferService`, direct buffer retained on JVM, staging copy on web) and
records the deferred variable-axes shape; the measured axes/named instances and
the per-backend discovery matrix are in the font-bundle findings — **do not
re-verify**.

## Read first

- `docs/plans/2026-08-31-kge-restructure-roadmap.md` — the macro roadmap:
  concept list, guiding principles, decision lenses, ordering, per-concept
  workflow. **The plan is the roadmap; detail is not frozen ahead.**
- `docs/decisions/phase-1.md` — append-only log of verified facts and
  per-concept decisions. Future sessions use it, do not question without
  evidence. It is an **index**; entries live in `docs/decisions/phase-1/` split
  by concept era — read the index, then only the relevant chunk (see "How to
  read" there). Do not read every chunk.

These two are the only active documents; older plans/specs were deleted
(history is the archive).

## Working rules

- **Code review**: every code review (concept close / PR review) runs the
  two-axis review (Standards + Spec conformance) — dispatched through the
  project review sub-agents (`.opencode/agent/review-standards.md` and
  `.opencode/agent/review-spec.md`) when configured, or — when they are not —
  through two fresh general sub-agents run in parallel, one per axis (never a
  self-review by the working model that produced the diff). The close flow is
  **green gate → reviews ok → marker → commit**: the working agent runs the gate
  first (below), and the review sub-agents do **not** run the build, tests or
  gate — they review the diff statically, so the gate executes once, not once
  per axis. The Spec axis carries mandatory checks against the **three sources
  of truth** (olc behavior parity, no unjustified regression against `main`,
  Kotlin realization), because a plan-conformance review does not catch a defect
  of the plan itself: a divergence from olc, or a regression against a `main`
  solution, is a finding unless a rationale is recorded in the decisions log,
  the micro-plan, or KDoc, and recorded divergences are listed as accepted
  rather than suppressed. The olc reference is
  `~/workspace/olcPixelGameEngine/olcPixelGameEngine.h` (the upstream checkout);
  `main` is read via `git show main:<path>`. The review writes its report to
  `.opencode/reviews/<name>.md` (gitignored local state) and the marker
  `.opencode/review-passed`; the commit gate is enforced by the opencode
  plugin `.opencode/plugin/review-gate.ts` (blocks `git commit` touching
  `docs/decisions/` without a valid marker + `tree:` hash). Marker
  writes and the commit must be separate bash commands (the gate reads the
  marker before the command runs).
- **Concept flow**: touch-point (design confirmation, open items decided) →
  micro-plan (1-2 pages, TDD steps, just-in-time) → implement → gate → log
  entry. The touch-point and the micro-plan consult olc (behavior), the `main`
  implementation (its Kotlin-level solutions, not discarded) and the Kotlin
  constraints/facilities, and record why each divergence is taken. Never a slice
  of a concept; never a provisional API a later concept must break ("no
  throwaway commits" — restructure at the concept checkpoint).
- **TDD**: failing test → run (red) → implement → run (green), per feature; the
  micro-plan's test code is the contract. When dispatched, implementation runs
  through the project `tdd-developer` subagent
  (`.opencode/agent/tdd-developer.md`), which carries the olc-parity check too.
- **Resource discipline**: every failure path of engine code that allocated a
  resource must close it — allocate-then-construct call sites wrap the
  construction in `letClosingIfFailed` (the `main` engine's guard, ported to
  the new kernel); the concept review audits every allocate/close path,
  including construction failure branches.
- **API discipline**: every public parameter has an observable effect — a
  parameter with no behavior is a provisional API and a defect; its effect is
  pinned by a test. The concept review audits parameters too (the micro-plan
  can record a wrong "detail" — a plan-conformance review does not catch a
  defect of the plan itself).
- **Scope discipline**: prefer the narrowest visibility that compiles — a
  `private` top-level/class member over `internal`, and `internal` over
  `public`; a concrete implementation shared across files is a `private` type
  behind an `internal` factory, not an `internal` type. Public API exists on
  purpose — KGE is an **extensible engine**, so services, facades, role
  interfaces and the types an extender must name are legitimately public; the
  defect is *accidental* widening. **Test source sets**: `internal` is a no-op
  on unpublished code, so the ladder drops it — keep the same preference,
  `private` first, and widen to no modifier (the default `public`) only when the
  declaration is shared across test files (or cannot be `private`, as with
  `expect`/`actual`). The concept review audits visibility too.
- **KDoc discipline**: a KDoc never references the docs tree (`docs/...`) —
  rationale lives in the decisions log/plan, not in the code — and the KDoc of
  public API never names `internal`/`private` concepts, methods, or classes.
  Public API documentation must read on its own, without implementation
  references. Keep every comment and KDoc succinct and indispensable: the
  contract and the non-obvious only, never a narration of the code or a
  rationale essay. **At most two lines per comment or KDoc block**, and never
  restate the diff, the plan, or the decisions log.
- **Gate (every concept close, run by the working agent)**: `./gradlew build`;
  the review sub-agents do not re-run it — the close is gate
  green first, then reviews, then marker/commit. **Why `build`,
  not only `:kge-core:allTests`:** `build` is
  `check` + `assemble` — the tests of every target plus the `webMain`-class
  metadata/klib compilation of intermediate source sets, which only
  `assemble` exercises. `allTests` did not cover it once: the C3
  `org.khronos.webgl` imports resolved on every platform compilation but
  never in the webMain metadata compilation — `allTests` stayed green while
  `build` failed (decisions log item 14, correction). **Why no explicit
  `ktlintCheck`: ktlint-gradle 14.2.0 wires the ktlint source-set checks into
  `check` (decisions log #32); `ktlintFormat` stays a manual step.** **Why the
  test tasks always run:** the build config disables up-to-dateness and caching
  for every test task, so a plain `build` executes them — a cache can otherwise
  replay a stale green (`jvmTest` once reported "1 test" with the kotest engine
  never running, decisions log item 15); `--rerun-tasks` remains for a full
  forced re-run. JVM, JS (browser) and wasmJs
  (browser) run the same commonTest suite (the web targets are browser-only —
  node was dropped at S6, log #19).
- **Sources of truth — three roles**: **(1) olcPixelGameEngine v2.30** is the
  *behavior* reference (semantics, exact pixel math): this is a port, so a
  divergence from olc is a finding unless a rationale is recorded. **(2) `main`**
  is the previous Kotlin implementation — not a mandate (we are rewriting the
  port, its old tests are not ported) but **evidence that must not be discarded**:
  its Kotlin-level solutions (allocation, boxing, buffer strategy, structure,
  seam shape) are candidates, and **a regression against a `main` solution is a
  finding unless a recorded rationale justifies it**. **(3) Kotlin/KMP** is the
  realization medium: respect its constraints (value-class boxing in
  generic/nullable/supertype positions, web `Long` emulation, `expect`/`actual`,
  browser single-threading) and use its facilities — write idiomatic Kotlin, not
  a C++ re-enactment; olc parity is behavioral, the Kotlin form is ours to
  choose. All three are consulted at every touch-point and micro-plan, not only
  in review. (See the roadmap's three lenses.)
- **Dependencies**: at add-time always use the current release unless a known
  problem exists; record non-obvious findings in the decisions log.
- **Docs and commit messages in English**; committed documents carry no personal
  quotes — decisions are recorded by rationale, not by who said them.
- **Commit messages are succinct** (subject + non-obvious core only): a
  one-line subject, then **at most two short body lines** covering only what is
  not deducible from the diff — the "why", recorded decisions, non-obvious
  consequences. Every line — subject included — stays within 80 columns. No
  gate history, no test counts, no review narratives, no change-by-change recap
  (that is what the diff shows), no doc/location pointers. Long-form context
  lives in the decisions log and KDocs, never in the commit body.
- **One commit per round**: the round's work is committed as a single commit —
  never stack commits; squash before hand-off. The owner reviews and pushes;
  only then does the next round begin.
- **Delivery**: the agent commits the round's work (tests, gate and review
  green) and never pushes; the owner reviews, pushes, and may implement parts
  personally.

## Commands

```bash
./gradlew build                       # full gate: ktlint (wired into check) + all targets' tests + assemble/metadata
./gradlew :kge-core:allTests          # tests only (jvm + js browser + wasmJs browser)
./gradlew :kge-core:jvmTest           # JVM only
tools/gradle <args>                   # same arguments, for a sandbox that denies writes to ~/.gradle
```

`build` also covers `buildSrc` (through the `buildSrcCheck` task). Agents run
`tools/gradle` in place of `./gradlew`: it needs no escalation, and outside a
confining sandbox it is exactly the wrapper.

`jvmTest` runs through `kotest-runner-junit5` + `useJUnitPlatform()` in
`kge-core/build.gradle.kts` — kotest's Gradle plugin does not wire the JVM
target under KGP 2.4.10, and without that wiring `jvmTest` executes zero tests
(decisions log item 15; the phantom green the always-run test config exists
for).

A JDK 21 daemon and the Gradle wrapper 9.7.1 are pinned; bytecode target 11.
