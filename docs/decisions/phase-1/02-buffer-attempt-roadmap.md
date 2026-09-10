## 2026-08-30 — Buffer concept attempt (partially discarded — see entry 23)

### 12. LWJGL add-time check: release 3.4.3 observed — owner directive bumps over the plan pin

The plan pin was 3.3.6 (research pick). The add-time check (`curl` of
`org.lwjgl/lwjgl-bom/maven-metadata.xml` on repo1.maven.org) observed release 3.4.3.
Per the owner's directive (at add-time always bump to the current release unless a
known problem exists), 3.4.3 is used. Resolution, compilation and tests are green —
no known problem (MemoryUtil/memAlloc is stable in 3.4.x).

### 13. LWJGL: BOM `platform()` form and natives on the test classpath

`implementation(project.dependencies.platform(libs.lwjgl.bom))` compiles in the KMP
source-set DSL (KGP 2.4.10 / Gradle 9.5.0); `lwjgl-core` is versionless (BOM-supplied).
`MemoryUtil.memAlloc` hits a native path in 3.4.3 (`UnsatisfiedLinkError` without
natives), so jvmTest carries `runtimeOnly(libs.lwjgl.core.get()) { artifact {
classifier = lwjglNatives } }` with the OS/arch `when` block from main's build
(verbatim, adapted to lwjgl-core only). JVM tests run against the LWJGL buffer with
natives on the test classpath, as the design spec anticipated.

### 14. wasmJs web types moved out of the stdlib — `kotlinx-browser` 0.5.0 on wasmJsMain

`js.buffer`/`js.typedarrays` do not exist in Kotlin 2.4.10, and `kotlin-stdlib-wasm-js`
contains no web types at all (Kotlin 2.1.10+ moved the wasmJs web types into
`org.jetbrains.kotlinx:kotlinx-browser`; verified by inspecting the klib package lists).
webMain uses `org.khronos.webgl.{DataView, Uint8Array}`: on js these resolve from the
stdlib, on wasmJs from kotlinx-browser, so the dependency is declared on `wasmJsMain`
only. Add-time release: 0.5.0 (used).

**Correction (post-close):** the `./gradlew build` path (never exercised by the
gate — `allTests`/`check` do not compile webMain metadata) failed at
`:kge-core:compileWebMainKotlinMetadata`: webMain metadata compiles against
webMain's own dependencies only, so neither the js stdlib web types nor the
wasmJsMain-scope kotlinx-browser were on its classpath
(`Unresolved reference 'org'`/`Uint8Array`/`DataView`). kotlinx-browser moved
from `wasmJsMain` to `webMain` (shared by both targets via the hierarchy; the
js target keeps its stdlib declarations with no conflict).

### 15. jvmTest was a false green — kotest plugin does not wire the JVM target under KGP 2.4.10

The kotest 6.2.4 Gradle plugin only wires the JVM target when `jvmTest` is an instance
of `org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest`; under KGP 2.4.10 that
check fails silently, no kotest engine ran, and `jvmTest` executed zero tests — the
HTML report's "1 tests" counter at the Task 4 gate was a phantom (binary results held
no test events). Fix: `kotest-runner-junit5` on jvmTest + `tasks.withType<Test>().
configureEach { useJUnitPlatform() }`. jvmTest now runs the real suite (19 tests,
0 failures). Note: the Task 4 gate's jvmTest "1 test" reading should be disregarded.

### 16. Index units: byte offsets everywhere; `copyInts` count = ints

The brief's parameter names do not pin the units down. Chosen and recorded: `byteAt`/
`putByte`/`getInt`/`putInt` take byte offsets (Task 7's contract calls `getInt(x*4)`;
the old bulk hooks used byte indices), `fillInts(from, to, value)` fills the int slots
whose byte offset lies in `[from, to)` (step 4, to exclusive), `copyInts(dst, srcFrom,
dstFrom, count)` copies `count` ints (4-byte units). Absolute access never moves the
cursor. `sizeInBytes` == `capacity` (bytes). Overlapping same-buffer copies are
memmove-safe (direction-aware iteration).

### 17. Covariant returns on `ByteBuffer` (owner-kept, 2026-08-30)

`mark`/`clear`/`reset`/`flip`/`rewind` are redeclared in `ByteBuffer` with covariant
`ByteBuffer` returns so chained calls keep the concrete type. First recorded as an
"owner directive"; the attribution was not traceable, and after the owner's review
"keep as implemented" the entry now states the factual status: implementation choice
by the Task-5 implementer, accepted and kept by the owner.

### 18. `JvmByteBuffer` stays a plain class — value class analyzed and rejected (owner asked)

`@JvmInline value class` is technically possible on JVM (one wrapped field, all computed
properties), but would buy nothing: the factory must return the `ByteBuffer` interface
(brief contract), so every allocation boxes the value class at the boundary anyway.
It would also forbid the `===` identity check used for same-buffer overlap detection,
and the web impl needs two backing views (Uint8Array + DataView), which a value class
cannot hold. Both actives are plain `internal` classes.

### 19. Endianness rulings applied

JVM: `MemoryUtil.memAlloc(...).order(ByteOrder.LITTLE_ENDIAN)` — memAlloc returns an
unordered direct buffer. Web: every DataView int access passes `littleEndian = true`
explicitly (the default is big-endian); byte access uses `getUint8`/`setUint8` on the
same view. `putInt`/`getInt` are fixed little-endian ints, not future ByteOrder hooks.

### 20. ktlint 12.3.0 `function-signature` forces multiline declarations for 2+ parameters

Multi-parameter function declarations (interface and impls) are wrapped one-parameter-
per-line by `ktlintFormat`; the rule is enabled by default and `.editorconfig` does not
opt out. Accepted as the project style (call-site argument wrapping remains disabled
per the owner's `.editorconfig`).

## 2026-08-31 — Macro roadmap landed; C4 touch-point material

### 21. Roadmap commit (1f3f7e7)

`docs/plans/2026-08-31-kge-restructure-roadmap.md` approved; superseded docs
deleted (phase-0-1 plan, revision backlog, design spec, research — history is the
archive); `CLAUDE.md` rewritten to point at the roadmap + this log; `.superpowers/`
session artifacts removed (were gitignored, unsupported by any flow). Working
model: macro roadmap + just-in-time touch-points; concept order C4 → C1 → C2 →
C3 → C5 → C6 → C7 → C8 → C9 → C10.

### 22. C4 (Pixel) — touch-point material, **undecided** (owner decides next session)

Open items (roadmap S2): the representation; whether an endianness seam is needed.
Evidence on the table:

- **All planned targets are little-endian** (x86/ARM JVM; WASM memory; JS
  TypedArray by spec) → an Int-packed RGBA with a little-endian convention needs
  no runtime conversion on any target; the old `PixelService` existed in `main`
  solely for the native-RGBA conversion.
- **olc v2.30 arithmetic**: `Pixel` is a union `{uint32 n; struct{uint8 r,g,b,a}}`
  — memory order R,G,B,A; straight alpha; default pixel `0xFF000000` (opaque
  black), default alpha 0xFF; arithmetic ops clamp per channel; the blend at the
  draw funnel is per channel `r = a*p.r + (1-a)*d.r` (floor semantics for the
  int path — verify at implementation by exact tests).
- **main's shape (for reference, `git show main:.../image/Pixel.kt`)**: value
  class over `nativeRGBA: Int`; components in [0,255]; `plus`/`minus` RGB-only
  (alpha kept from receiver); `times`/`div(Float)` RGB-only; `inv()` inverts RGB,
  keeps alpha; `lerp(end, t) = this * (1 - t) + end * t`; nested `sealed Mode`
  (Normal / Mask / Alpha(blendFactor clamped 0..1) / Custom(x, y, new, old));
  `Format` RGBA/HEX for toString; `Colors` object (148 CSS names + BLANK —
  the extended set was gated by demand there too).

Suggested direction (NOT decided): `@JvmInline value class Pixel(rgba: Int)`,
LE convention, no `PixelService`; ops as in main (minus-clamp nuance resolved by
tests); `Colors` baseline = 10 constants (BLACK, WHITE, BLANK, RED, GREEN, BLUE,
CYAN, MAGENTA, YELLOW, GREY), extended palette only when a consumer exists;
`Mode` types in `Pixel`; pure blend helpers computable here (consumed by the
raster concept later).

Next session starts at the C4 touch-point: confirm/adjust the above, then
micro-plan (TDD steps) and implement.

### 23. Buffer concept attempt — disposition (2026-08-31)

The buffer commit (`3c1e381`; never pushed, never approved beyond the
time-limited yes) and its API were discarded from the branch; the source files,
LWJGL/kotlinx-browser wiring and the provisional `expect fun` went with it.
**Kept:** the test-execution fix (item 15 — `kotest-runner-junit5` +
`useJUnitPlatform`, re-applied to the scaffold build; without it jvmTest runs
zero tests) and the durable facts above (LWJGL 3.4.3 add-time check, the
native-classifier pattern, kotlinx-browser 0.5.0, endianness and units rulings,
the value-class analysis, the ktlint style rule). The API-shape rulings (items
16-18) belonged to the discarded shape: the S1 contract is decided fresh at C3
(roadmap S1), with those rulings as starting candidates, not as inherited code.

