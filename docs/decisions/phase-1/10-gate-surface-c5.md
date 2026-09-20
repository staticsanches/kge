## 2026-09-05 — Gate tooling: ktlint is part of `build`; gate/CI commands simplified

The C3-era gate spelled out `ktlintCheck` next to `build`, so keeping lint in
the gate depended on remembering an extra command. Investigation showed the
extra command was already redundant on the current toolchain.

- **Verified fact (2026-09-05): `./gradlew build` already runs ktlint.**
  `--dry-run` on `:kge-core:build` schedules all 11 per-source-set check tasks
  (`ktlint{CommonMain,CommonTest,JsMain,JsTest,JvmMain,JvmTest,KotlinScript,
  WasmJsMain,WasmJsTest,WebMain,WebTest}SourceSetCheck`) — the ktlint-gradle
  14.2.0 KMP integration wires them into `check`. The aggregate `ktlintCheck`
  and all Format tasks are *not* in that graph; only the source-set checks are.
- **Decision — commands simplified, coverage unchanged.** Gate becomes
  `./gradlew build --rerun-tasks` (CLAUDE.md + roadmap close step), and the CI
  command drops the explicit `ktlintCheck` (`.github/workflows/build.yaml`).
  The aggregate was composed of exactly those source-set tasks, so `build`
  enforces the same lint as the old two-command gate; the `/build/generated/`
  exclude filter applies unchanged.
- **`ktlintFormat` stays manual — deliberately not wired into `build`.** A
  format pass as a build step rewrites source files during `build`; on CI the
  auto-correction would make a commit of unformatted-but-fixable code pass
  silently instead of failing, which is the opposite of enforcement at this
  stage of the engine. Auto-formatting belongs to a dev-time tool (editor /
  pre-commit), not the gate — revisit if the owner wants a `check`-time format
  later.

## 2026-09-05 — C5 (surface — S3/S4): the 2D pixel surface (touch-point + implementation)

The touch-point (2026-09-05, roadmap S3/S4) got its detail per the micro-plan
(`docs/plans/2026-09-05-c5-surface-microplan.md`): the interface owns the
algorithms, the implementer supplies the raw accessors. `Pixmap` +
`MutablePixmap` (default bodies over abstract `unchecked` accessors),
`Sprite` as the single concrete surface, `SpriteCreationService : KGEOverridable`
with a platform-independent default — PNG is S5. The `name` parameter
identifies the surface (owner round 4): `Sprite.name` + `toString`, and the
wrapper representation (`byte buffer (N bytes) (name)`) — the leak/fail-fast
messages carry the label. `duplicate` preserves it.

- **Storage formula, verified at byte level**: pixel `(x, y)` at byte offset
  `(y * width + x) * INT`, value `pixel.nativeRGBA` — both platforms are LE
  (LWJGL `.order(LITTLE_ENDIAN)`, TypedArray explicit `littleEndian`), so the
  packed ints go straight through `putInt`/`getInt` (commonTest pins bytes
  `R,G,B,A` at the base offsets of both rows). Created content is
  unspecified — the draw path fills it.
- **OOB semantics per the touch-point**: `get` never throws (`NORMAL` →
  transparent; `PERIODIC` → flat index `abs(y%h)*w + abs(x%w)`, the formula
  being equivalent to `uncheckedGet(abs(x%w), abs(y%h))` — Kotlin's truncated
  `%` keeps both negatives positive and in range); `CLAMP` → coerce to the
  edges; `set` → Boolean, false outside without a write. `sample` =
  `get(min(int(u*w), w-1), min(int(v*h), h-1))` (truncation, high-clamp);
  `sampleBL` = floor of `u*w - 0.5`, four mode-aware `get` reads, RGB weighted
  and truncated to bytes, alpha forced 255.
- **Int→Pixel conversion**: `Pixel.fromNativeRGBA(...)` — owner review
  round 2: the internal `nativePixel` channel re-composition was rejected in
  favor of a real factory on `Pixel` itself (additive to C4, direct
  constructor, no channel round-trip).
- **Fail-fast after close covers the OOB paths (review round 1 catch)**: the
  NORMAL OOB branch of `get`/`set` never reaches the storage, so a closed
  `Sprite` would silently return transparent/false there, contradicting the
  C2 contract ("using the object after close fails fast"). `Sprite` now
  checks `buffer.cleaned` first on `get`/`set` (every other access goes
  through the buffer's fail-fast accessor anyway).
- **Test hygiene rule (review round 1)**: the caller must close a
  `ResourceWrapper` when the `Sprite` constructor rejects it — the JVM
  Cleaner delivers such wrappers during `LeakDetectionJvmTest`'s GC polling
  and reports them into the overridden `LeakReporterService`, breaking the
  C2 test's exact-count assertion.
- **Deferred**: the color-fill convenience (`clear` + close-on-failure) stays
  out — owner's call (2026-09-05): C6 raster is the first consumer, service
  API stays lean; `Flip` → C6; `Viewport.Bounded` → R2; PNG → S5.
- **Review**: two-axis round 1 (single lean agent, standards + micro-plan
  conformance): no blockers/majors; 5 minors fixed in round 1 (the fail-fast
  gap above, row-1 byte-layout test, set-no-write proof, forward-looking
  "GPU upload" KDoc claim removed, `spriteCreationDefault` made file-private,
  per #29); verify pass green; report
  `.claude/kge/reviews/c5-round1-twoaxis.md`.
- **Review round 2 (owner, Hunk on the commit)**: 5 comments applied —
  `Colors.TRANSPARENT` instead of a private transparent constant; the
  `Pixel.fromNativeRGBA` factory (above); `Sprite : KGEResource by buffer`
  (interface delegation replaces the manual `close()` override);
  `letClosingIfFailed` — the `main` engine's own resource guard brought over
  to the new kernel — wrapping `create`/`duplicate` so a failed construction
  never leaks the buffer. `applyClosingIfFailed` deliberately not ported: no
  consumer yet (the deferred color-fill extension at C6 would be its first).
  **Process lesson recorded:** the round-1 review had no leak audit in its
  axes and passed this; the owner caught it in their own review. The concept
  review now audits every allocate/close path and construction failure branch
  (roadmap close step), resource discipline is a CLAUDE.md working rule, and
  `Sprite`'s KDoc now states the rejected-constructor ownership rule (caller
  keeps and closes the wrapper) — a leak audit is part of the review loop
  from now on.
- **Review round 4 (owner, Hunk on the amended commit)**: two comments.
  (1) The `name` parameter did nothing: now `Sprite` carries `name`
  (`toString` carries it too) and `MemoryAllocatorService.allocate` gained
  the optional `name` label, so the wrapper's leak/fail-fast messages
  identify the surface; `duplicate` keeps the source name. (2) The
  `letClosingIfFailed` port was questioned: it was brought over verbatim —
  now evaluated: naming/convention stays (the engine keeps `main`'s
  vocabulary), semantics correct (release on `Throwable` + `addSuppressed`),
  but the nullable-receiver bound was narrowed to `AutoCloseable` — the new
  kernel has no nullable resources, the `if (this != null)` branch was dead
  weight. The create-site guard stays (owner's round-2 choice); the
  rejected-constructor ownership rule remains explicit in `Sprite`'s KDoc.
- **Review round 5 (owner, why did the name pass)**: the unused `name` was
  three stacked misses — the touch-point micro-plan recorded it as
  "logging-only" (a Claude-written resolution, not the owner's word), the TDD
  never exercised the parameter, and the review checked structure, not
  observability (a plan-conformance review cannot catch a defect of the
  plan). Institutionalized: "API discipline" is a CLAUDE.md working rule
  (every public parameter has an observable effect, pinned by a test) and the
  review loop audits it.
- **Review round 6 (owner, Hunk)**: byte sizes in messages must be
  human-readable — `formatBytes` utility (1024-based, one decimal below 10,
  integer at 10+) used by both allocators' representations and the `Sprite`
  capacity message; offsets stay raw (debug values, not sizes). **Process
  change (owner's rule, 2026-09-05): owner-driven review rounds have no
  cap** — the roadmap words it, the review-gate hook dropped its rounds cap
  (the `escalated` status still blocks), and the marker now counts truthfully
  (round 6).
- **Gate**: `./gradlew build --rerun-tasks` green (BUILD SUCCESSFUL; jvm 104 /
  jsNode+jsBrowser / wasmJsNode+wasmJsBrowser — 0 failures on all targets;
  ktlint clean via the `check` wiring of #32; `ktlintFormat` remains manual per
  the owner).

### Correction (2026-09-12) — `sampleBL` folds its bilinear corners

The C5 `sampleBL` read its four bilinear corners through `get(fx, …)` /
`get(fx + 1, …)` with no index fold, so the surface's default `NORMAL` mode
returned transparent for an edge corner (`fx == -1` at `u == 0`, `fx + 1 ==
width` at `u == 1`) and bilinear sampling faded toward transparent at the
borders. olc `Sprite::SampleBL` folds each axis (`x0 = max(x, 0)`,
`x1 = min(x + 1, width - 1)`, same for `y`) and reads through `GetPixel`, which
applies the sprite's sample mode; `main`'s `PixelMap.sampleBL` folded
identically.

- **Fix.** `sampleBL` folds the four indices as olc does and reads them through
  `get`, so for `u`/`v` in `[0, 1]` every read is in bounds; a coordinate still
  out of range after the fold is resolved by `get`, following `sampleMode`.
  KDoc updated.
- **Tests.** `PixmapTest` pins the edge texel at `u`/`v` 0 and 1 (previously the
  transparent blend) and the still-out-of-range corner at `u = ±10` through the
  `get` path (the unfolded read was out of bounds).

### Correction (2026-09-13) — `Pixmap` drops its `Sequence<Pixel>` supertype

C5 kept `Pixmap : Sequence<Pixel>` (a row-major iterator), but a value class in
a generic type argument is boxed on every element — the same non-optimized path
removed from the decal geometry (log #22 correction) — and no engine consumer
iterates a surface as a `Sequence` (the bulk operations and the tests read
explicit `get`/`uncheckedGet` loops). The supertype and its iterator are
removed; production reads through `get`/`uncheckedGet`, and the tests use a
test-only `asSequence` extension.

- **Supersedes** the C5 touch-point's "`Sequence<Pixel>` kept" decision (and the
  matching roadmap S3 line). An extender that iterated a surface uses an
  explicit `width`/`height` loop; a `Sequence<Pixel>` view stays one extension
  function away if a real consumer appears.

### Gate hole (2026-09-20) — a web browser suite can report zero tests and exit 0

`:kge-text-ttf:jsBrowserTest` reports **zero tests and still exits 0** when it
runs as part of `tools/gradle build`. With every `build/test-results/` directory
deleted first, one full gate run finished `BUILD SUCCESSFUL` with that suite at 0
tests — no XML, `binary/results-generic.bin` of 44 bytes — while the same run
reported its full counts everywhere else: `kge-core` js 750, `kge-font-roboto`
js 6, `kge-text-ttf` wasm 22. Invoked on its own with `--rerun-tasks` the suite
runs for real (22 tests, nine XMLs, 6260 bytes), so the trigger is not stale
state but running inside the multi-task build, where several browser suites start
Karma at once. A Karma port collision — the default 9876 shared by concurrent
servers — is the suspicion; no root cause was established.

This is the second phantom-green mechanism, and the gate rule does not cover it:
disabling up-to-dateness makes the test *task* execute, but a browser suite can
still report nothing, and a zero-test run is indistinguishable from a passing one
by exit code.

- **Consequence for a close:** a plain `tools/gradle build` is not by itself
  evidence for the web targets. Check that each suite reported tests
  (`build/test-results/<target>/*.xml`) and force `--rerun-tasks` on the web
  target when a count is missing or short.

