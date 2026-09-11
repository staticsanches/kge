# KGE

Guidance for AI agent work in this repository (opencode / Claude Code).

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
first platform-defaulted T2 service. C5 (surface — S3/S4, log #33) closed:
`Pixmap`/`MutablePixmap` (mode-aware `get`, nearest/bilinear sampling),
`Sprite` over native memory and the platform-independent
`SpriteService` (PNG to S5). **S5 (PNG codec, log #34) closed**:
`PngService` (decode/encode/load seam, JVM STB zero-copy, web pngjs `.sync`),
the typed `PngSource` load boundary (`base64`/`url`/`fetch` factories), and a
module-wide kotest `ProjectConfig` that resets service overrides after every
test. **C6 (raster ops, R1, log #35) closed**: four per-scope raster
sub-services (`DrawService`/`OutlineService`/`FillService`/`DrawSpriteService`)
aggregated by `Rasterizer`, pixel-mode blend via the draw seam, native bulk
buffer ops, and the `SpriteService` rename. **Vector/point concept (closed
2026-09-09)**: pure `Int2D`/`Float2D` `data class`es (`math/vector`) + typed
`Int2D` overloads on the raster sub-services (interface defaults + companion
`Proxy` analog forwarding). **Ordering revision (2026-09-10, owner):** the old
`C7` (simple text) is dropped — text becomes **elaborate text** (shaping +
rasterization + atlas + blit), shipped **last** as `R6`, after `R2`, `C8`,
`C9`, `C10`; the `main` bitmap font is not ported. **R2 (viewport/clipping,
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
cell of the clipped walk — `Filled` is the untouched pre-change behavior. Next:
`C8` (state), per the roadmap; no renderer or engine loop yet.

**Text (R6) — deferred to the end; research recorded.** Owner decision
(2026-09-10): do not invest in text during the `main` restructure; text is the
final concept with shaping + rasterization + atlas + blit. The font-library
research (FreeType/HarfBuzz across JVM + js + wasmJs, candidate stacks,
UNVERIFIED items) was done and is in
`docs/decisions/phase-1/14-text-r6.md` — **do not re-research**; consult that
chunk before any text work.

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
  project `review` subagent when it is configured (`.opencode/agent/`), or —
  when it is not — through two fresh general sub-agents run in parallel, one
  per axis (never a self-review by the working model that produced the diff).
  The review writes its report to
  `.opencode/reviews/<name>.md` (gitignored local state) and the marker
  `.opencode/review-passed`; the commit gate is enforced by the opencode
  plugin `.opencode/plugin/review-gate.ts` (blocks `git commit` touching
  `docs/decisions/` without a valid marker + `tree:` hash). Marker
  writes and the commit must be separate bash commands (the gate reads the
  marker before the command runs).
- **Concept flow**: touch-point (design confirmation, open items decided) →
  micro-plan (1-2 pages, TDD steps, just-in-time) → implement → gate → log
  entry. Never a slice of a concept; never a provisional API a later concept
  must break ("no throwaway commits" — restructure at the concept checkpoint).
- **TDD**: failing test → run (red) → implement → run (green), per feature; the
  micro-plan's test code is the contract.
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
- **Gate (every concept close)**: `./gradlew build --rerun-tasks`. **Why `build`,
  not only `:kge-core:allTests`:** `build` is
  `check` + `assemble` — the tests of every target plus the `webMain`-class
  metadata/klib compilation of intermediate source sets, which only
  `assemble` exercises. `allTests` did not cover it once: the C3
  `org.khronos.webgl` imports resolved on every platform compilation but
  never in the webMain metadata compilation — `allTests` stayed green while
  `build` failed (decisions log item 14, correction). **Why no explicit
  `ktlintCheck`: ktlint-gradle 14.2.0 wires the ktlint source-set checks into
  `check` (decisions log #32); `ktlintFormat` stays a manual step.** **Why
  `--rerun-tasks` is mandatory:** build cache and
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
- **Docs and commit messages in English**; committed documents carry no personal
  quotes — decisions are recorded by rationale, not by who said them.
- **Commit messages are succinct** (subject + non-obvious core only): a
  one-line subject, then at most a few body lines covering only what is not
  deducible from the diff — the "why", recorded decisions, non-obvious
  consequences. No gate history, no test counts, no review narratives, no
  change-by-change recap (that is what the diff shows), no doc/location
  pointers. Long-form context lives in the decisions log and KDocs, never in
  the commit body.
- **Delivery**: commit-ready work; the owner reviews, pushes, and may implement
  parts personally. Do not push.

## Commands

```bash
./gradlew build --rerun-tasks  # full gate: ktlint (wired into check) + all targets' tests + assemble/metadata
./gradlew :kge-core:allTests   # tests only (jvm + js + wasmJs, node + browser)
./gradlew :kge-core:jvmTest    # JVM only
```

`jvmTest` runs through `kotest-runner-junit5` + `useJUnitPlatform()` in
`kge-core/build.gradle.kts` — kotest's Gradle plugin does not wire the JVM
target under KGP 2.4.10, and without that wiring `jvmTest` executes zero tests
(decisions log item 15; the phantom green that motivates the `--rerun-tasks`
rule).

A JDK 21 daemon and the Gradle wrapper 9.7.1 are pinned; bytecode target 11.
