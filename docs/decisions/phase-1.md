# KGE Restructure — Phase 1 Decisions & Verification Log

Opened early (Task 2, 2026-08-30): plugin-toolchain findings needed recording before
the scheduled Task 4 opening. Task 4 will append the scaffold verification entries
(webMain presence, wasmJs test runner, framework combos).

## 2026-08-30 — Task 2: `kge-core` skeleton + smoke test

### 1. jvmTarget: `compilerOptions` DSL works on KGP 2.4.10 (no toolchain fallback)

`jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }` resolves and takes
effect. Verified on the compiled output: `SmokeTest.class` major version 55
(Java 11 bytecode). `jvmToolchain(11)` not needed.

### 2. KSP plugin required by the `io.kotest` plugin — added `com.google.devtools.ksp` 2.3.11

Without KSP, plugin application fails with: "KSP neither found in root project nor
kge-core, please add 'com.google.devtools.ksp' to the project's plugins." Kotest uses
KSP for test discovery on JS/wasmJs (no reflection there); the generated registry lives
in `build/generated/ksp/{js,wasmJs}/.../io/kotest/framework/runtime/kotest.kt`.
KSP 2.3.11 (current release; versioning decoupled from Kotlin since KSP 2.3.0) added to
the catalog as `ksp-gradle`, applied in `kge-core`.

### 3. Root build file must NOT declare plugins with `apply false` (classloader scope clash)

Root-declared plugins load in the root project's classloader scope; a subproject that
applies the same plugin reuses that scope, so classes are not shared with sibling
plugins declared only in the subproject. Two observed failures on Gradle 9.5.0:

- `io.kotest` in root + `kotlin-multiplatform` 2.4.10 in `kge-core` → KGP decoration
  crash (`KotlinJsCompilerTypeHolder.getBOTH()` abstract method) — the kotest plugin
  depends on kotlin-gradle-plugin-api 2.2.21, mixing with KGP 2.4.10.
- `org.jlleitschuh.gradle.ktlint` in root + KMP in `kge-core` →
  `NoClassDefFoundError: KotlinMultiplatformExtension` from
  `KtlintPlugin.applyKtlintMultiplatform` — ktlint has a compile-only KGP reference and
  cannot see KMP's classes from the root scope.

Fix: declare all plugins only in `kge-core/build.gradle.kts`; the root build file keeps
a warning comment. A clean repro project (same two plugins, no root declarations)
builds fine, confirming the scoping theory.

### 4. ktlint must exclude KSP-generated sources

ktlint lints `sourceSet.kotlin.sourceDirectories`, which includes
`build/generated/ksp/...`; kotest's generated discovery file fails
`standard:filename`/indent rules and cannot be auto-corrected. Ant-pattern excludes
match paths relative to each source directory, so they cannot reach `build/generated`.
Fix — path-based spec:

```kotlin
ktlint {
    filter {
        exclude { element -> element.file.path.contains("/build/generated/") }
    }
}
```

### 5. ktlint plugin version kept at 12.3.0

The `NoClassDefFoundError` initially looked like a ktlint/Gradle 9.5 incompatibility,
but the root cause was the classloader scope issue (item 3); 12.3.0 works on
Gradle 9.5.0. Newest on the Gradle Plugin Portal at check time (2026-08-30): 14.2.0 —
no bump needed, the plan's pin stays.

### 6. ktlint exclusion made Windows-portable (fix round 1)

The path-based spec in item 4 used `File.path`, whose separator is `\` on Windows —
the filter would silently stop excluding KSP-generated sources and `ktlintCheck` would
fail on `windows-latest` CI. Changed to `File.invariantSeparatorsPath` (always `/`).

## 2026-08-30 — Task 4: Scaffold verification + decisions log

Phase 0 gate: `./gradlew :kge-core:allTests ktlintCheck --stacktrace` green (fresh
run with `--rerun-tasks`: BUILD SUCCESSFUL, all test targets executed + ktlintCheck).

### 7. Default hierarchy template provides `webMain`/`webTest` — webMain present (y), no source-set changes

The plan's inspection command (`:kge-core:sourceSets`) is not a Kotlin Gradle Plugin
task; the equivalent evidence came from two real tasks:

- `:kge-core:tasks --all`: ktlint emits one task pair per registered source set —
  `ktlintWebMainSourceSetCheck/Format` and `ktlintWebTestSourceSetCheck/Format` exist.
- `:kge-core:dependencies`: `webMainApi`, `webMainImplementation`, `webTestApi`,
  `webTestImplementation` (and friends) exist.

`kge-core/build.gradle.kts` declares no `webMain`/`webTest` and never calls
`applyDefaultHierarchyTemplate()`, so the web group comes from the default hierarchy
template that KGP 2.4.10 applies automatically (web group added to the template in
Kotlin 2.2). Verdict: template-provided, not custom — `build.gradle.kts` untouched.

### 8. wasmJs test runner: node

Confirmed in Task 2 and at the gate: the `wasmJs` target is configured with
`nodejs()` only, and `wasmJsNodeTest` runs the kotest smoke test. Node is the
supported runner.

### 9. kotest works on js + wasmJs — yes

Fresh gate run (2026-08-30), one SmokeTest per target, all green:

- `jvmTest`: 1 test, 0 failures (HTML report counters 1/0/0)
- `jsNodeTest`: 1 test, 0 failures
- `jsBrowserTest`: 1 test, 0 failures (Chrome)
- `wasmJsNodeTest`: 1 test, 0 failures

### 10. jvmTarget DSL over jvmToolchain — bytecode evidence re-confirmed at the gate

The `compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }` choice (item 1) stands:
`SmokeTest.class` from the gate's fresh compile reads major version 55 (Java 11
bytecode). No `jvmToolchain` fallback needed.

### 11. wasmJs test runner: node + browser (owner option a)

Owner decision on the open scaffold item: **option a — enable the browser, CI stays
node-only.** The `wasmJs` block now mirrors the `js` block:

```kotlin
wasmJs {
    browser {
        testTask {}
    }
    nodejs()
}
```

`wasmJsBrowserTest` runs the kotest smoke test locally (Chrome); CI keeps the
node-only runner per plan.

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

## 2026-09-01 — C4 (Pixel) closed (touch-point decisions + implementation)

### 24. C4 (Pixel) — touch-point decisions (owner) + close facts

The open items of #22 were decided at the C4 touch-point (this session):

- **No `PixelService`, no endianness seam.** `Pixel` stores the packed value directly —
  `R | G shl 8 | B shl 16 | A shl 24` (memory bytes R, G, B, A). Zero byte conversion on
  any planned target; no `reverseBytes` anywhere. Factual corrections to #22's reasoning
  (verified at the touch-point):
  - wasm linear memory is little-endian **by spec**.
  - JS `TypedArray` byte order is the **host's native order per spec** — little-endian
    only by practice (no BE hosts shipped). #22's "by spec" for JS was wrong.
  - Every LWJGL target (x86_64, x86, arm64) is LE; Java's "big-endian" reputation is API
    convention (`ByteBuffer` default order = `BIG_ENDIAN`, network-order streams, value
    packings such as ImageIO's AARRGGBB int), not host memory layout.
  - `main` itself dropped BE support deliberately: `a60b5ca` (2025-05-03) chose the
    per-endianness service via `ByteOrder.BIG_ENDIAN.isNative`; `61447d6` (2025-05-31)
    removed it and hardcoded a LE default ("KGE only supports, by default, little endian
    systems"). Remaining BE "market" = JVM-on-s390x (LWJGL does not support it) and retro
    consoles (no KMP targets).
- **Representation.** `@JvmInline value class Pixel private constructor(nativeRGBA: Int)`
  — the constructor is PRIVATE (owner review, 2026-09-01): a `Pixel` is created through
  the factories or `Colors`, never from a raw packed value; the little-endian packing is
  an engine invariant and an internal path that needs it gets a factory, not a leak. The
  packed value is PUBLIC, named `nativeRGBA` (little-endian bytes R,G,B,A) with
  `rgba: UInt` exposing the canonical `0xRRGGBBAA` value convention — the `main` naming
  split is carried over deliberately: the two conventions are different things and must
  be distinguishable. No `Pixel()` default constructor (owner review): explicitness via
  `Colors` or a factory — the olc-style opaque-black default was dropped. Factories:
  `Pixel.rgba(r, g, b, a = 0xFF)` (saturating) and `Pixel.rgba(0xRRGGBBAAu)` (value
  convention, alpha in the low byte).
- **Arithmetic — olc clamp semantics** (decided: stay close to the reference engine):
  per-channel saturation to [0, 255] on plus/minus/times/div; minus clamps at 0 (main
  masked with `& 0xFF` — 10-20 wraps to 246; rejected because saturation is the reference
  behavior); times/div convert via Float→Int (truncation toward zero for finite
  fractions; NaN → 0; +Inf saturates to Int.MAX); `inv` inverts RGB only;
  `lerp = this * (1-t) + end * t` (truncating); alpha always kept from the receiver.
  Domain pinned at the review pass (2026-09-01), per channel: `x / 0f` → 255 for x > 0
  (positive value over zero = +Inf → Int.MAX, then saturate) and → 0 for x == 0
  (0/0 = NaN); `x / NaN` and `x * NaN` → 0; `x * 0f` → 0; negative factor/divisor → 0
  (incl. `-0f` = −Inf; converted value below 0 saturates to 0); exact tests cover every case, including the
  channel split of `rgba(0, 100, 50) / 0 → rgba(0, 255, 255)`.
- **Colors — CSS Color 4, regenerated from the spec, not from main.** Decision: the
  CSS palette is the reference of interest (web parity — canvas/WebGL), the olc palette
  is not; values are regenerated from the specification rather than carried over from
  main. The 148 §6.1 named colors (the spec table is the oracle: fetched, extracted, 18
  anchors verified — `green` #008000, `lime` #00FF00, `gray`/`grey` #808080,
  `rebeccapurple` #663399, …) + `TRANSPARENT` (fully transparent black — the value is
  CSS `transparent`, which the spec treats as a special value, not a named color; the
  constant carries the CSS name — owner rename from the working name `BLANK`,
  second Hunk review round, 2026-09-01).
  Named colors are opaque (`A = 0xFF`); the 9 alias pairs keep both spellings (gray/grey,
  aqua/cyan, magenta/fuchsia, darkgray/darkgrey, dimgray/dimgrey,
  darkslategray/darkslategrey, lightgray/lightgrey, lightslategray/lightslategrey,
  slategray/slategrey).
- **Pixel modes moved to the Raster concept (R1).** No consumer in C4 — on `main` every
  `Mode` consumer was raster (draw slow path + FillRect/FillTriangle/DrawSprite tests).
  `Mode` (Normal/Mask/Alpha/Custom) + the blend resolution math
  (`r = a*p.r + (1-a)*d.r`, floor) are in R1's macro requirement now; no blend formula in
  the kernel before that consumer. Roadmap S2/R1 updated accordingly.
- **Display format → new transversal concept T3 ("pixel display formats"), ordered right
  after C1.** C4 ships `toString()` fixed `#RRGGBBAA` (uppercase, 8 digits); NO
  `Format` abstraction ships in C4 (decided — display formats are T3's job). The
  engine-level choice of representation (main's `defaultPixelFormat` var — process-sticky,
  breaks tests — is the rejected anti-pattern) becomes a service seam per principle 1,
  realized by the T2 mechanism: default HEX; override exercised by the extension-contract
  test. `toString()`'s signature is stable in C4 — T3 re-resolves through the mechanism
  without breaking it.

Close facts: TDD (test → red → implement → green) with the micro-plan in this session's
session plan; full gate green — `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks`
(jvmTest 30, wasmJs node 30, js node 35 — the js figure adds kotest's context containers
(4) and its Kotest discovery suite (1); the executable test set is the same 30 on all
targets — the 6 domain-semantics tests included; 0 failures; `--rerun-tasks` mandatory
per items 9/11/15). The
ktlint function-signature rule wraps only 2+ parameter declarations (item 20) —
single-parameter signatures are single-line (the gate failed once on this; `ktlintFormat`
applied).
One commit for the whole concept instead of green-milestone commits — owner decision
(2026-09-01): one session's work in one commit keeps CI to a single run; a deliberate
deviation from the workflow's "several commits at meaningful green milestones".
Close review pass: two-axis review (Standards + Spec) of the commit — findings fixed in
the same commit: the alias test extended to all 9 pairs, the div/times domain (0/NaN/
negative) pinned by exact tests, doc/impl semantic contradiction resolved. The
`rgba`-property ruling made in that pass was later SUPERSEDED by the owner review below
(`nativeRGBA` is public). Committed docs carry no personal quotes (standing rule since
this close: decisions are recorded by rationale).
**Owner review (Hunk, 2026-09-01 — the human layer of the loop; five comments in the
first round, plus one in the second: `BLANK` renamed `TRANSPARENT` — the value is the
CSS special value `transparent`, so it carries the CSS name, not a KGE one):**
KDocs no longer reference decision logs (such files may disappear or rot — facts only);
the constructor is private (factory-only construction; raw packing stays an engine
invariant); `nativeRGBA` is public again and `rgba: UInt` exposes the canonical value —
the `main` naming split keeps the two conventions distinguishable (for extensibility,
such values must be available); the `Pixel()` opaque-black default was dropped
(explicitness via `Colors`/factory); `Colors` aliases reuse the canonical constant's
reference (`val GREY = GRAY`, `CYAN = AQUA`, `MAGENTA = FUCHSIA`, … all 9 pairs — one
constant per value, aliases point at it, with the hex equality cross-checked by the
generator).
Tooling find (same pass): `data object Colors` produced order-dependent zero-value
failures in full jsNodeTest runs under kotest 6.2.4 — the identical access passed in
isolation, in pairs and with a warm-up access, so the parity net caught it as a JS-only
failure with `expected #RRGGBBFF but was #00000000`; bisecting to `object Colors` fixed
it and the combination (object + aliases) is green on 3 consecutive full runs.

## 2026-09-01 — CI Windows yarn.lock flake (diagnosed + fixed)

### 25. `kotlinWasmStoreYarnLock` — input `build/wasm/yarn.lock` missing on Windows

Consecutive Windows-only CI failures (both retrigger-fixable) with **different
root causes**:

- 2026-08-31 (roadmap docs run, attempt 1): `foojay-resolver-convention 1.0.0`
  plugin resolution failure ("was not found in any of the following sources") —
  transient Plugin Portal/resolution failure, no repo change; attempt 2 green.
- 2026-09-01 (C4 run): `:kotlinWasmStoreYarnLock` (`YarnLockStoreTask`) failed
  at input validation — `D:\a\kge\kge\build\wasm\yarn.lock` does not exist. Only
  `windows-latest` failed; ubuntu/macos green.

Evidence chain (the wasm target has **no npm dependencies**):

- Local gate artifacts: `build/wasm/package.json` is the workspaces umbrella
  with empty dependencies; the generated `build/wasm/yarn.lock` is a
  header-only 86-byte file versus the JS 98 KiB lock (webpack). The wasm store
  dir `kotlin-js-store/wasm/` is empty on macOS too.
- Failing Windows log: `kotlinWasmNpmInstall` started while `kotlinNpmInstall`
  (node pid 8360) still held Yarn's global instance mutex — "warning Waiting for
  the other yarn instance to finish (8360)" — and after that run the trivial
  wasm lock was absent, while the same build elsewhere produced it.
- Mechanism (inferred — no local Windows repro): under that contention the
  zero-dependency wasm `yarn install` can finish without writing the lock,
  consistent with the Kotlin 2.2.20+ documented behavior for wasm projects
  without npm dependencies; the store task then aborts on its required input.
  Windows timing makes the race window real; a retrigger changes the install
  ordering.

Fix: serialization in the root `build.gradle.kts` —
`tasks.matching { it.name == "kotlinWasmNpmInstall" }.configureEach { dependsOn(tasks.matching { it.name == "kotlinNpmInstall" }) }`.
The root yarn tasks are registered later in configuration, so the eager
`tasks.named` lookup fails at script evaluation ("Task with name
'kotlinWasmNpmInstall' not found"); the matching/`configureEach` form is lazy.
Verified by the `:kotlinWasmNpmInstall` dry-run ordering (install after
install) and the local full gate; Windows confirmation pending the owner push.

## 2026-09-01 — C1 (extension mechanism) closed (touch-point decisions + add-time verifications)

### 26. C1 (extension mechanism) — touch-point decisions + close facts

The open item of the facade contract (activation policy) was decided at the
C1 touch-point (this session):

- **Activation policy — explicit** (owner). The engine activates the context
  on create (`kgeDefaultModule` + `kgePlatformModule`) and deactivates on
  destroy; double activation and any access before activation fail fast
  (`IllegalStateException`); deactivate is idempotent (safe for unconditional
  engine-destroy calls).
- **Naming — `KGEContext`, not `ActiveContext`** (owner): KGE prefix keeps
  `main`'s convention; "active" is redundant since there is no user-visible
  "inactive" context. Roadmap "Facade contract" text updated to the name and
  the decided policy.
- **Proof — mechanism + test-defined service** (owner): C1 ships the machinery
  only; the extension-contract test (`KGEContextExtensionTest`) defines its own
  exemplar (`Translator`: default impl, internal `kgeOriginal` qualifier
  binding, delegating `DecoratorTranslator`, stateless per-call facade with
  `original`). The first real service lands with its consumer (T3 pixel display
  formats → C8 clock). No provisional API in `commonMain` beyond `KGEContext`
  + modules + qualifier.
- **koin-test not added in C1** — tests drive `KGEContext` directly (the
  roadmap's "scope helper" option); the wasmJs koin-test question is resolved
  as deferred to the first consumer that needs `loadKoinModules` semantics.
  `koin-core` lands and **runs on wasmJs** (verified by the wasmJs node test
  run of the new suites — koin-core-wasm-js artifact present in resolution).

Add-time verifications (Koin 4.2.2, this session):

- **`kotlin.concurrent.AtomicReference` does not exist in KMP common on KGP
  2.4.10.** The `kotlin.concurrent` package in the stdlib 2.4.10 jar holds
  only Locks/Threads/Timers/Volatile; the common atomics API lives in
  `kotlin.concurrent.atomics`: `AtomicReference` with `load()`/`store()`/
  `exchange()` (no `get`/`set`/`getAndSet`/`value` in common — `value` is
  JVM/native-only) and `compareAndSet`/`compareAndExchange`, all still
  `@ExperimentalAtomicApi` (opt-in required; extension fns `update`/
  `updateAndFetch`/`fetchAndUpdate` since Kotlin 2.2). Used with one
  `@OptIn` on the object. Sources: the stdlib jar listing + the public API
  reference.
- **Koin 4.2.2 last-declared-wins without an override flag — yes.** Verified
  from the koin-core sources jar: `KoinApplication.allowOverride = true` (new
  applications), `Koin.loadModules(..., allowOverride = true, ...)` default;
  resolution semantics covered by tests (module-order override + runtime
  `loadModules` override both exercise it).
- **Koin current release at add-time: 4.2.2** — repo1.maven.org
  `koin-bom`/`koin-core` metadata (`<release>4.2.2</release>`, checked
  2026-09-01); the planned pin stays (no known problem).
- **`koinApplication {}`/`KoinApplication.close()`/`koin.get(qualifier)`/
  `named(String)`** confirmed in the common API (koin-core sources jar), used
  exactly as shaped. No `GlobalContext`/`startKoin` anywhere.

Close facts: TDD with the micro-plan (test → red → implement → green);
jvmTest red was the expected missing-API compile failure, then the full cycle;
the extension-contract test passed on first run by design (its subject — the
pattern — was built in the previous cycle; it guards the proof, it is not a
new-red cycle). **Close review pass** — two-axis review (Standards: no hard
violations; 3 actionable judgement calls — duplicated module fixtures,
repeated `load()?.koin ?: error` shape, e.g. — plus Spec: 2 findings —
`activate` was check-then-store (non-atomic, concurrent activation leaks the
first application) and the 5th extension test contradicted itself). All
findings fixed in the same commit: `activate` is now build-then-
`compareAndSet` (on CAS failure the new application is closed and
`IllegalStateException` is thrown), the contradictory test removed, the
`requireKoin()` helper unifies the error shape, fixtures extracted.
Adversarial verify pass of the fix delta: CLEAN (verdict + evidence in
`~/.claude/kge/reviews/c1-round1-verify.md`).
Gate: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green —
allTests executed (jvmTest 41, wasmJs node 41, js node 48 — the js figure
again adds kotest container/discovery suites; 0 failures; counts read from
the XML reports, see item 15; 42 → 41 with the removed test). ktlintFormat
applied twice (multiline-expression-wrapping + no-unused-imports, then
function-signature on `requireKoin`). One commit for the concept, per the
owner's single-commit decision (see #24); the CI yarn fix landed as its own
commit before it (#25).

### 27. C1 (extension mechanism) — identity-semantics amendment

Reviewing the unpushed C1 commit surfaced a contract gap in the proven
`original` pattern: the exemplar module bound the default twice (unqualified
`single` + `single` under `kgeOriginal`), so the container held **two
singletons of the same default implementation** — the facade's `translate()`
and `original` never referred to the same object. The contract is identity
**semantics**: the default and `X.original` must be the same instance,
so stateful services need no statelessness exception and a decorator delegates
to the exact default object the engine would have used.

Fix (same concept; per the single-commit decision #24 it lands in the C1
commit, amended while unpushed): the canonical instance is bound under
`kgeOriginal` and the unqualified binding is an alias to it
(`single<X> { get(kgeOriginal) }`). The mirror shape (canonical unqualified,
qualifier as alias) was rejected: after decoration the qualifier alias would
resolve `get()` to the decorator itself, forming a
decorate→original→decorate cycle. `KGEContextExtensionTest` gains the missing
identity guard ("the default binding and the original are the same instance",
`shouldBeSameInstanceAs`): first red was a type-inference accident (reified `T`
of the right-hand `resolve(kgeOriginal)` inferred as `Any` →
`NoDefinitionFoundException`); with the explicit type argument the true red
followed (`AssertionFailedError` — two distinct instances), green after the
flip. `kgeOriginal` KDoc and the roadmap "Original binding" wording updated
(the contract no longer implies stateless implementations).

## 2026-09-02 — T2 (extensible service mechanism) redesign: `KGEOverridable` supersedes `KGEContext`

At the first T2 touch-point the extension mechanism was redesigned (owner
decision). The engine's services are **fixed engine behaviors** — defaults
defined by the engine — and the mechanism exists for one purpose: letting a
consumer replace one at runtime. `KGEContext` is **superseded**, not extended
(the two do not coexist — owner decision); the C1 contract of #26/#27 is
replaced.

### Decided

- **Concept — no generic DI container.** Engine wiring is plain object
  composition; consumers who want DI manage their own Koin. The mechanism's
  internal Koin (process-global, hidden — the defaults catalog + current
  implementations) is machinery, not a user-facing registry.
- **Shape.** `KGEOverridable` (marker interface) + `KGEOverridable.Proxy<O>`:
  the service facade companion extends it (`type`, `original`); per-call
  resolution via a `protected` `delegate` getter; `override(impl)` public —
  last-declared-wins; `resetAll()` **internal** — engine `onDestroy` and test
  teardown restore the engine defaults from the catalog; no per-service undo,
  no scoping. Package `dev.staticsanches.kge.overridable`.
- **Facade delegation — shape A** (owner choice after examining the options):
  hand-written forwarders on the facade companion (`override fun m() =
  delegate.m()`) — compile-safe, no magic. Rejected: `by` with a service
  instance (captured once — the main-era freeze; `by` is dynamic only when the
  captured object is a re-resolving gateway, i.e. the C1 proxy-object pattern
  — rejected as a relocation of the same one-liners); interface-default
  delegation (a missing override would silently inherit the facade delegation
  → runtime recursion instead of a compile-time error); codegen (KSP forces
  every consumer module to apply a processor; an IR compiler plugin adds a
  version-locked artifact; `Proxy<O> : O` is illegal — a type parameter
  cannot be a supertype — and KMP common has no reflective dynamic proxy).
- **Original — identity by construction.** The default is one instance passed
  to the Proxy: `original` IS the default, so #27's double-binding problem
  cannot occur (no qualifiers, no per-service default module + alias, no
  `kgePlatformModule`). The `X.original` facade name survives.
- **Activation policy — implicit (supersedes #26's explicit + fail-fast).**
  Defaults register on first facade touch; the mechanism works without the
  engine — deliberate: engine-fixed behavior is always present (T3's
  `Pixel.toString()` in logs/tests without an engine; #26's fail-fast use
  outside activation is replaced by always-available). Lifecycle: engine
  `onDestroy` → `resetAll`; no leakage to the next engine instance.
- **Platform defaults — `internal expect`/`actual`** implementation objects
  per target (first real need: S1/R4-style platform-backend services). The
  extension-contract proof exercises the actuals on all targets — one actual
  per target declared in test source sets, asserted from commonTest.
- **kotlinx-collections-immutable 0.5.2** — incoming dependency: the defaults
  catalog becomes an `ImmutableList` in the common `AtomicReference` (the
  sketch's mutability TODO dies). Add-time check: repo1.maven.org
  `<release>0.5.2</release>` (checked 2026-09-02).
- **Sensitive API.** `@KGESensitiveAPI` reintroduced (main's pattern —
  `dev.staticsanches.kge.annotations`, `@RequiresOptIn(Level.ERROR)`,
  BINARY; project-wide opt-in inside `kge-core`) on `override` and
  `resetAll`, each with risk docs; the public `override` doc does not
  reference the internal `resetAll`.
- **Proof.** `TranslatorService` returns as the contract exemplar in
  `commonTest` — `KGEOverridableExtensionTest` (supersedes
  `KGEContextExtensionTest`); `KGEContext`/`KgeModules` sources and
  `KGEContextTest` are removed at the concept close (history is the archive;
  the public-surface change is accepted at the checkpoint per the
  no-throwaway rule).
- Tooling notes: `org.koin.dsl.bind` is the idiomatic import (the
  `org.koin.plugin.module.dsl.bind` variant also resolves in koin-core 4.2.2 —
  non-infix, compiler-plugin support); ktlint function-signature wraps 2+
  parameter declarations (the forwarder style follows it as usual).

### Close (2026-09-02)

TDD per concept flow, one red→green cycle per feature (default resolution,
override, original/decorator, last-wins guard, resetAll guard) with `jvmTest`
per cycle, then the full gate. Tooling findings during the cycles (correct the
add-time expectations above):

- Koin 4.2.2 `single<T>` is reified-only: a binding for a `KClass` held in a
  type parameter uses `single<Any> { }.bind(type)` with the plugin-module
  non-infix `bind` (`org.koin.plugin.module.dsl.bind`) — the infix
  `org.koin.dsl.bind` is same-type-only (`KoinDefinition<out S>.bind(KClass<S>)`)
  and cannot take a secondary type.
- kotlinx-collections-immutable 0.5.2: `ImmutableList` is the read-only
  interface and the persistent operations (`add`) are on `PersistentList` — the
  defaults catalog is typed `PersistentList<Module>`. Implements the design's
  "immutable catalog" (the `ImmutableList` word in the design above is the 0.4
  naming; the design intent is unchanged). In 0.5.2 the `MutableCollection`-
  styled ops are `@Deprecated` — the persistent variants are `adding`/
  `addingAll` and `putting`/`puttingAll` (`ReplaceWith(...)`); the registry
  uses them. After the second Hunk comment the registry is a `PersistentMap`
  keyed by service type (`KClass<*>`): the duplicate-service guard is O(1)
  (`containsKey` — was an O(n) scan over the list) and `resetAll` iterates
  the values.
- `AtomicReference.update` requires the explicit
  `import kotlin.concurrent.atomics.update` (extension function, not a member).
- ktlint: `no-empty-first-line-in-class-body` (no blank after `{`) and
  `no-blank-line-in-list` (no blank lines inside value parameter lists — the
  `original` KDoc sits directly above the parameter).
- ktlint-gradle 12.3.0 wiring gap: the `ktlint { filter { } }` exclusion reaches
  only the check tasks; the format tasks (`ktlintFormat`, run by the pre-commit
  hook) see the KSP-generated discovery files under `build/generated/` and fail
  on them (observed with the new `jsTest`/`wasmJsTest` KSP outputs; upstream
  issues #751/#743). Fix: the same exclusion is wired per task —
  `tasks.withType<BaseKtLintCheckTask>().configureEach { exclude { … } }`
  (both task types implement `PatternFilterable`). The format tasks are
  incremental: stale snapshots (`build/intermediates/ktLint/*-snapshot.bin`)
  recorded before the fix replay the generated file — clearing them made the
  exclusion effective; the check gate stays the authority.

Gate: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green (fresh
run per item 15). Counts verified from the XML reports (before the Hunk
round below) — jvmTest 35 tests (`KGEOverridableExtensionTest` 5, failures
0), jsNodeTest 40 (extension 5/0/0 plus kotest discovery 1),
wasmJsNodeTest 35 (extension 5/0/0); `SmokeTest` still 1/0/0 on every
target. The C1 suites are gone with the C1 sources — the
count drop is the supersession, not a regression. The last-declared-wins and
resetAll guard tests passed on first run by design (their subjects were proven
by the earlier cycles); they guard the proof, like the C1 identity guard.

**Close review pass** (2026-09-02) — two-axis review (Standards + Spec) of the
staged diff vs HEAD; findings fixed in the same changeset: the binding idiom
extracted (`moduleFor` — duplication between `init` and `override`), the
`FunSpec` blank line removed (house style), `type` renamed `serviceType`
(private — registry machinery; the facade API exposes `original` only; owner
decision at the round-4 review), and the per-platform proof assertion
de-anchored — each target declares the expected default output as its own
literal (`translatorExpectedDefault` actuals, declared independently of the
implementation), so a wrong or copy-pasted literal fails that target's test
(the original pairing made the assertion self-referential: expected and
default came from the same actual, resting any wrong literal). Spec
axis verified the close facts from the XML reports (counts, supersession,
0.5.2 resolution); no missing/crept/wrong requirement. Adversarial verify
pass of the fix delta: CLEAN (verdict + evidence in
`.claude/kge/reviews/t2-round1-verify.md`).

### Owner Hunk review round (2026-09-02 — folded into the same concept commit)

Two review comments on `KGEOverridable.kt`; refined before any approval:

- **Active implementation held directly.** Each Proxy holds its own
  `AtomicReference<O>` of the active implementation: `delegate` is a single
  volatile read, `override(impl)` a store (last-declared-wins naturally),
  `resetAll` one `clearOverride()` per registered proxy — no container
  rebuild, no module catalog. The hidden Koin machinery is gone: the per-call
  path drops ~2 hashmap lookups to 1 field read (matters in hot-loop
  services — the S1 allocator provider, raster/renderer seams), and
  `koin-core`/`koin-bom` leave the project (the Koin add-time and `bind`
  findings above are history; the extension contract and its proof are
  unchanged).
- **Engine-declared services only.** The Proxy constructor is `internal` —
  consumers override engine services, they do not declare new ones. The
  `register` guard runs in one atomic step: the `require` lives inside the
  `AtomicReference.update` lambda, so the check-and-add linearizes as a
  single CAS (`IllegalArgumentException` on a duplicate service type); the
  extension-contract proof gains that case (5 → 6 tests; final counts below).
- The facade-contract "banned held active instance" wording is adjusted: the
  active implementation lives in the mechanism's swappable registry (atomic —
  no staleness by construction), it is never held by a facade.

Gate after the refinement: `./gradlew :kge-core:allTests ktlintCheck
--rerun-tasks` green — counts verified from the fresh XML reports: jvmTest 36
(extension 6/0/0), jsNodeTest 41 (extension 6/0/0 + kotest discovery 1),
wasmJsNodeTest 36 (extension 6/0/0); `SmokeTest` 1/0/0 everywhere. One
commit for the concept (owner decision #24), amended to fold the refinement.

### Round-4 review (post-commit 2026-09-02) — the committed tree vs the agents

The Hunk refinement delta (100+/47-) merged via the 19:15-19:22 amends had
never been re-reviewed by the review agents: round-1 verify (18:41) predates
it and the review gate could not tell (the marker was hand-refreshed 16s
before the final amend; nothing tied it to an agent report of that tree).
Post-commit two-axis review of `06ad04b` — evidence
[`t2-round4-twoaxis.md`] (report carries the reviewed `tree:` hash so the
gate can prove coverage):

- **Standards** — no hard violations. One record-vs-code finding,
  self-confirmed in the commit: the de-anchor sentence in the Close review
  pass above overstated the code — the assertion interpolated
  `$translatorPlatformMark` from the same actual that produced the default
  (self-referential; a copy-pasted actual still passed that target's test).
  Fixed for real: each target declares `translatorExpectedDefault` — the
  expected default output, as its own literal in its own file
  (`TranslatorExpected.kt`) — and the test asserts it verbatim
  (`shouldBe translatorExpectedDefault`). Proven by red proof: a wrong
  literal fails only that target (wasmJs 3 fails, restored); gate counts
  unchanged (36/41/36; `jsBrowserTest`/`wasmJsBrowserTest` 41 x2 in the same
  run, 0 failures).
- **Spec** — nothing missing or crept; one surface wording loose end: the
  `type, original` parenthetical read as a public `type`. Owner decision:
  `serviceType` stays private (registry machinery; the facade API exposes
  `original` only) — the roadmap wording above carries the precision.
- Fix-delta adversarial verify: CLEAN (verdict + evidence in
  `t2-round4-twoaxis.md` — fix delta = `git diff 06ad04b`).

## 2026-09-02 — T3 (pixel display formats): first real T2 consumer (touch-point + implementation)

### 29. T3 (pixel display formats) — touch-point decisions + close facts

The open item of the T3 macro requirement (the service shape and default
representation) was decided at the touch-point (this session):

- **Service name — `PixelFormatService`** (owner): the facade-contract
  convention of the exemplar (`TranslatorService`), applied to the first real
  engine service. The capability is "render a `Pixel` as a display string":
  `fun format(pixel: Pixel): String`. Package `dev.staticsanches.kge.image`,
  beside `Pixel` — no new subpackage for one type.
- **Default — `HexPixelFormat`** (engine-fixed): the same uppercase
  `#RRGGBBAA` (8 digits) the C4 `Pixel.toString()` shipped, **moved** from
  `Pixel`'s companion into the service default (private object, single
  instance — identity by construction per #28: `PixelFormatService.original`
  IS the un-overridden default; file-private is the minimum — only the
  companion uses it, and the public KDoc does not reference the shell
  implementation). `Pixel.toString()` now delegates through the
  facade (`PixelFormatService.format(this)`) — the C4 signature is untouched;
  the default output is bit-identical, so the existing `PixelTest` cases pass
  on both sides of the move (description renamed: "fixed" → "default
  uppercase hex").
- **RGBA representation — test-local only** (owner): the proof's override is a
  private test object (`RgbaPixelFormat`, `rgba(r, g, b, a)` form); nothing
  beyond the HEX default ships. The macro's "hex/rgba" delimits the capability
  domain, not a shipped format catalog (minimal form: no consumer asks for
  RGBA output).
- **No expect/actual** in this service: the HEX default is language-agnostic —
  `HexFormat`/`toHexString` are stdlib **common** (already used by C4 in
  `commonMain`). The #28 "platform defaults via `internal expect`/`actual`"
  line is for platform-backend services (S1/R4 style), not T3.
- **Duplicate-registration proof not repeated**: the register guard is proven
  by `KGEOverridableExtensionTest` (#28, 6th test); T3's suite proves the real
  service's contract instead.
- KDoc facts-only rule (#24): no references to the decisions log in code docs.

Implementation per the micro-plan, one red→green cycle per feature
(`jvmTest` per cycle; full gate at the end):

- **F1** service + default HEX — red (compile: type missing), green.
- **F2** `Pixel.toString()` delegates — red (assertion: still the C4 fixed
  formatter), green (delegation + `HexFormat` moved into `HexPixelFormat`).
- **F3–F5** decorator-on-`original`, last-declared-wins, `resetAll` — guards,
  passed on first run by design (their subjects were proven by the earlier
  cycles and by #28).
- ktlint caught in the gate, fixed: `max-line-length` on the Proxy supertype
  listing (wrapped like `TranslatorService`'s) and `function-signature` —
  body expression fits the signature line (single-line).
- **Teardown invariance: none needed in `PixelTest`** — the `afterTest`
  `resetAll()` pattern lives in `PixelFormatServiceTest` only; every other
  suite sees the engine default.
- Semantics note: `override` is process-wide by design (#28); with an override
  active, **every** `Pixel.toString()` in the process changes — the capability
  is observable in logs/tests, which is the point; `resetAll` on destroy
  restores the engine default. Test suites that override must reset (the
  suite-level `afterTest` covers it).

Gate: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green (fresh
run per item 15). Counts from the XML reports: jvmTest 41
(`PixelFormatServiceTest` 5, 0 failures), wasmJsNodeTest 41 (same),
jsNodeTest 46 (the usual kotest discovery + context-container overhead:
41 + 5). Baseline shift vs the #28 gate (36/41/36): exactly +5 per target —
the new T3 suite; `SmokeTest` 1/0/0 on every target.

### Close review pass (2026-09-02)

Owner review round(s) in Hunk: one comment — the engine default should be
private and not referenced from the public interface KDoc. Fixed in the same
changeset (`HexPixelFormat` is `private`, the `[HexPixelFormat]` reference
removed from the interface KDoc; wording above adjusted). Gate re-run after
the fix (green, counts unchanged).

Two-axis review of the tree (round 1): **Standards CLEAN · Spec CLEAN** — no
defects, no fix delta (adversarial verify pass vacuous); evidence + verdict in
`.claude/kge/reviews/t3-round1-twoaxis.md`. Three nits, no action: the KDoc
"e.g. a debug build rendering rgba(...)" is a hypothetical illustration;
`RgbaPixelFormat` is declared after the spec class (cosmetic — the exemplar
declares the service first); `Pixel.toString()` pays one atomic load per call
(by design, #28 per-call resolution).

Not committed: the owner holds the commit (their review completes the loop;
push is theirs by standing rule).

## 2026-09-03 — C2 (resource lifecycle, T1): touch-point decisions + close facts

### Touch-point decisions (owner)

The open items of the T1 macro requirement were decided at the C2 touch-point
(this session):

- **Report mechanism — `LeakReporterService` (T2 service, owner).** The
  capability "where a leak is reported" is engine-fixed behavior with a
  consumer override, per principle 1 — the second real consumer of the T2
  mechanism after T3. `interface LeakReporterService : KGEOverridable` with
  `report(representation: String)`; companion object extends the Proxy (the T3
  facade shape); engine default is a **private** `LoggingLeakReporter` via
  kotlin-logging (error level) — not referenced from the interface KDoc (the
  T3 review nit); the log message is `"Resource $representation was not
  closed and is potentially leaking its resources"` — the T1 "unclosed"
  vocabulary, deliberately divergent from `main`'s "was not cleaned" wording
  (minimal-form: the exact log text is not a contract, the report payload is).
  Extension-contract test proves the override.
  Name: *Service suffix* pattern; package `dev.staticsanches.kge.resource`
  beside the contract (the T3 "no new subpackage for one type" rule).
- **Scope — the four types only (owner).** `KGEResource`, `ResourceWrapper`,
  `KGELeakDetector`/`KGECleanable`, `KGECleanAction` + essential factories.
  Deferred to their future consumers (evidence in `main`): `KGEInternalResource`
  (E1 window/layer), `letClosingIfFailed`/`applyClosingIfFailed` (E1 window),
  `use`/`andThen`/`invokeIfFailed`/`toCleanerProvider` (R9 GL wrappers),
  the `component1`/`component2` destructuring sugar (no consumer requires
  it) and the wrapper `toString()` state suffix (`"(released)"` — no consumer
  reads it). Full `main` parity rejected (minimal form, YAGNI).
- **Detection/testability split (owner).** The platform registration is a
  THIN seam: `internal expect fun registerCollectionTrigger(obj, onCollected)`
  returning a `KGECleanableHandle` (unregister). Everything testable lives in
  the common module: `KGEResourceCleanableState` (cleaned flag, action
  exactly-once, report exactly-once, race-safe via `AtomicReference.exchange`),
  and the deterministic entry `onCollectionObserved()` (internal) on the
  wrapper/cleanable — tests drive it instead of relying on GC timing.

A note on the review loop below: the close review pass belongs to the
concept close in the normal flow; the entry is written at close and the
owner runs the Hunk review (their standing loop — owner commits and pushes;
delivery is commit-ready work).

### Verified facts (add-time checks, this session)

- **`js.memory.FinalizationRegistry` no longer exists in the Kotlin/JS
  stdlib.** Verified: compile probe on js + wasmJs (`Unresolved reference
  'js'`), and klib/sources listing of kotlin-stdlib-js 2.1.21/2.4.10 and
  kotlin-stdlib-wasm-js 2.4.10 (no `package_js.memory`). `main` (Kotlin
  2.1.20) still had it. It is now provided by
  `org.jetbrains.kotlin-wrappers:kotlin-js` — declared in `webMain` for both
  js and wasmJs targets (module metadata: `kotlin-js-js` and
  `kotlin-js-wasm-js` variants; confirmed in sources of 2026.8.5 and
  2026.9.0). Add-time: current release **2026.9.0** (repo1 check) — built
  with Kotlin **2.4.10** (kotlin-tooling-metadata.json), matching the branch.
- **kotlin-logging: current release 8.0.4** (repo1 check; `main` pinned
  7.0.6). Published for wasmJs (`kotlin-logging-wasm-js`, checksum 200),
  built with 2.1.21 — fine as an older-metadata dependency.
- **kotlin-logging 8.0.4 drops the compile-scope slf4j-api dependency.**
  The jvm POM/module declare only kotlin-stdlib; `NoClassDefFoundError:
  org/slf4j/LoggerFactory` at runtime. Fix: the engine declares slf4j-api
  itself — current **2.0.18** (2.1.0-alpha1 is `latest`, alpha; the stable
  release line is 2.0.18), jvmMain scope. Recorded so the next add-time
  check does not re-discover it.
- **`kotlin.uuid.Uuid` is stable in 2.4.10** (`@WasExperimental`, no opt-in
  needed — the `@ExperimentalUuidApi` file in stdlib is history; first
  compile without the annotation succeeded on jvm, then full gate).
- **The wasmJs-observation question (spike verdict):** probes on all targets
  never observed a FinalizationRegistry callback within the poll window with
  heap-pressure churn; the pure-node control (with `--expose-gc` +
  heap-busting, `new ArrayBuffer(32MB)` rounds) fired the callback quickly.
  Conclusion: V8 does deliver finalizers; the *kotest/node harness* does not
  force GC reliably, so callback-assertions are not deterministic test
  material on ANY web target. Design consequence: the web actuals register
  the same way on js and wasmJs (one `webMain` file — uniformity per parity
  floor), with KDoc noting the best-effort nature; **no no-op on wasmJs** —
  the owner's fallback (no-op if the spike failed) applies only if the
  mechanism itself were unproven; the harness limitation is not platform
  proof, and the same code exists on js anyway (no extra wasmJs-unsafe code).

### Implementation (TDD per feature, jvmTest per cycle, full gate at close)

- **F1** KGEResource + KGECleanAction(+factory) — red: compile (type
  missing); green.
- **F2** LeakReporterService + LoggingLeakReporter — red: compile; green;
  then the slf4j-api runtime discovery (see facts) fixed the `jvmTest`.
  Service guards (decorator-on-original, last-wins, resetAll) passed on
  first run by design (T2/T3 precedent: their subjects proven by earlier
  cycles).
- **F3** KGEResourceCleanableState + leak path — 5 tests; the state machine
  races (`clean` wins / collection wins) both covered. `exchange(null)` is a
  member (imports of `kotlin.concurrent.atomics.exchange`/`fetchAndSet`
  failed — member, like `load()`/`store()`, per #26).
- **F4** registerCollectionTrigger expect/actual (JVM Cleaner; web
  FinalizationRegistry via kotlin-js) + KGELeakDetector + ResourceWrapper —
  tests: distinct uuid, close-once, fail-fast (message aligned with `main`:
  "has already been released and can not be used"), leak-vs-close both ways.
  Red cycles: `private` on a local fun (invalid) — fixed; `shouldBeTypeOf`
  on a null-suppressed value produced "Cannot infer type" + at 758 a matcher
  parse error, switched to the `shouldThrow` idiom used by the T2 suite;
  JVM-only `StringBuffer` in commonTest — switched to `"x"` (String) after
  the wasmJs compile red.
- **GC-integration test (jvmTest only, best effort)** — an unclosed wrapper
  reported within a 15s poll window (System.gc + 50ms sleeps;
  `System.runFinalization()` dropped — deprecated since Java 18, it was only
  a hint). Green in 73ms on the first run. Not part of the commonTest parity
  suite (GC timing is not deterministic by design); the commonTest proofs
  are the deterministic seam.

Close review pass: the round-1 two-axis review ran on the staged tree before
commit — one Standards judgement call (the `registerCollectionTrigger` KDoc
overstated the unregister guarantee vs the JVM `Cleaner.clean()` reality; fix:
one-sentence KDoc reword + the load-bearing-order comment at the `clean()`
site), verify pass CLEAN, no Spec findings, gate numbers corroborated.

Owner Hunk review round (post-commit, 2026-09-03): the `as
KGEResourceCleanable` cast in `ResourceWrapper` was questioned; **kept**
(owner decision). Rationale: `KGELeakDetector.register` is not consumer API —
its sole caller is the wrapper factory (`ResourceWrapper.kt:51`); the
invariant is documented at the cast site and was verified by the round-1
review (`register` is the only `KGECleanable` construction path in the
module). Exit if a second construction path ever appears: `register` becomes
internal and returns the internal concrete type, moving the invariant from a
runtime cast into a compile-checked signature — no change to any public
surface. Recorded so a future session does not re-open it without new
evidence (ex: a second construction path or a consumer-facing register).
A second note in the same round: the dead binding in
`KGEResourceCleanableState.onCollected` — `val action =
actionRef.exchange(null) ?: return` kept a value that is deliberately
discarded (the collection path never runs the action). Kotlin's compiler
does not flag unused locals by default (IDE inspection only), which is why
the gate logs stayed silent; fix applied (`if (actionRef.exchange(null) ==
null) return`), semantics unchanged, gate re-run green with zero compiler
warnings (58/62/57, 0 failures).

Gate: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green (fresh
run, XML counts: jvmTest **58**, jsNodeTest **62** (js adds kotest
discovery), wasmJsNodeTest **57**; zero failures on all targets). Baseline
shift vs the #29 gate (41/46/41): +17 on jvm (16 new commonTest — 2
contract + 4 service + 5 state machine + 5 wrapper — plus the 1 jvm-only
GC integration test), +16 on js/wasmJs. ktlintCheck clean (one format pass:
multiline-expression-wrapping on the override object expressions).

IDE inspection sweep (owner-run, 2026-09-03, Inspect Code over kge-core):
three real findings, all fixed — the KDoc link `[unregister]` unresolved,
now `[KGECleanableHandle.unregister]`; the redundant SAM constructor in
`JvmCollectionTrigger` (`Runnable { }` → trailing lambda); the
`@Incubating` `repositories(Action)` use in `settings.gradle.kts`,
suppressed with `@Suppress("UnstableApiUsage")`. The rest of the sweep is
IDE-side noise, not source defects: `kotest.kt` "redundant public" hits
(KSP-generated discovery code under `build/`, excluded from ktlint for the
same reason); the KMP "unnecessary module dependency" inspections (IDEA's
module model does not model the source-set dependency hierarchy — false
positives by design); the spelling dictionary hits for project terms
(Koin, kotest, Kover, wasm, klib, lwjgl, actuals, gitignored, unpushed,
externref, RRGGBBAA, Transversals, nulled, githooks, herdr, shasum, esac,
the `gradlew.bat` cmd words, the owner's own surname in LICENSE) — owner
adds these to the IDE custom dictionary; no action in-repo. Intentional,
no action: the `gray`/`grey` CSS alias pairs in the #24 colors record,
the "newer Gradle minor" hint (9.5.0 pinned by project rule) and the
LICENSE `©` suggestion.

## 2026-09-03 — Toolchain refresh: Gradle 9.7.1 + ktlint-gradle 14.2.0

- **Gradle wrapper 9.5.0 → 9.7.1.** Current at check time (services.gradle.org,
  build 2026-08-19). The CLAUDE.md pin updated. Same JDK 21 daemon, config
  cache unchanged; full gate fresh on the new wrapper.
- **ktlint-gradle 12.3.0 → 14.2.0** (latest/release on the Plugin Portal at
  check time). Supersedes item 5's "no bump needed, the plan's pin stays"
  (2026-08-30, Gradle-9.5-era pin — the owner decision today: refresh). The
  resolved engine is **ktlint-cli 1.5.0** (the 12.3.0 default was 1.0.1).
- **New-rule migration:** the 1.5.0 `standard:class-signature` (super type
  must start on a newline) flagged 10 Kotest classes (`FunSpec({ ... })`
  same-line supertypes — SmokeTest + the 5 C4/T2 era + 4 C2 era test suites).
  One `ktlintFormat` pass restyled them (`class X :` / `FunSpec({ ... })`
  with reindented bodies); ktlintCheck green after.
- **The explicit final-newline pin in `.editorconfig` earned its keep**: the
  ktlint 2.x-era engine drift to "final newline not enforced" is exactly the
  scenario the pin protects against in a bump.
- Gate on the new pair: `./gradlew :kge-core:allTests ktlintCheck
  --rerun-tasks` green — jvmTest **58**, jsNodeTest **62**, wasmJsNodeTest
  **57**, zero failures, zero `w:` lines (all targets compiled+ran fresh).

## 2026-09-04 — C3 (native memory, S1): first consumer of the resource contract (touch-point + implementation)

### Touch-point decisions (owner)

The open items of the S1 macro requirement were decided at the C3 touch-point
(this session). Evidence base: the real consumers on `main` and the discarded
buffer attempt (#23; rulings 16–19 as starting candidates).

- **Lean contract — absolute access only.** The surface consumers (PixelMap:
  `getInt((y*w+x)*INT)`; DrawService: `fillInts(index, endX-startX+1, color)`)
  use absolute indexed access + bulk ops; the cursor family has exactly one
  observable consumer on `main` — the renderer batch (QuadInfo/BaseRenderer
  IntBuffers with `position()`/`flip()`, staging flip-then-upload). Cursor
  semantics and typed views (IntBuffer/FloatBuffer) are deferred to the
  renderer concept (C9), whose shape that consumer decides. Growth is
  additive (members + extension functions), nothing ships for a consumer that
  does not exist.
- **Contract shape — `expect abstract class ByteBuffer` mirroring `java.nio`
  (owner redirect mid-session: use the java.nio buffer directly on JVM — no
  wrapper object, no per-access getter chain).** The main-era solution is
  exactly this: `actual typealias ByteBuffer = java.nio.ByteBuffer`
  (typealias-actual). Mechanics verified during the concept (compiler
  diagnostics as the oracle, below), including `-Xjdk-release=11` and the
  expect/actual mirror constraints.
- **KGE-specific ops as commonMain extension functions** (one implementation
  on all targets): `byteAt` (0..255 mask), `putByte` (validates 0..255 →
  IllegalArgumentException), `fillInts(fromByteOffset, count, value)` — count
  in int units, start in byte offsets, range `[from, from + count*4)` — the
  real-consumer form (DrawService's call shape), re-decided over the discarded
  Task-5 `(from, to)` frame; `copyInts(dstFromByteOffset, source,
  sourceFromByteOffset, count)` — memmove-safe same-buffer overlap in both
  directions.
- **No zeroing requirement** (owner: efficiency-first when it does not cost
  usability — an image buffer must define its pixels anyway; main parity):
  initial content is unspecified. JVM `memAlloc` (raw); the web backing store
  is zeroed by platform spec (ArrayBuffer) but that is not the KGE contract.
  The first variant (memCalloc + a "zeroed buffer" contract test) was
  discarded in the same session.
- **Little-endian fixed; no `ByteOrder` seam** (re-affirming 16/19 + the C4
  no-endianness decision): JVM `.order(ByteOrder.LITTLE_ENDIAN)` at
  allocation; web DataView `littleEndian = true` on every int access.
  Exceptions uniform: `IndexOutOfBoundsException` for out-of-range (the JDK's
  `BufferIndexOutOfBoundsException` is a subclass; web explicit check),
  `IllegalArgumentException` for `putByte` out of 0..255 and negative
  `sizeInBytes`.
- **Service — `MemoryAllocatorService`** (T2 facade, `PixelFormatService`
  pattern; package `dev.staticsanches.kge.buffer` beside the contract):
  `allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer>`; `internal expect
  val memoryAllocatorDefault` with per-platform actuals — JVM: LWJGL
  `memAlloc(...).order(LE)` + `memFree` as the wrapper clean action (the
  buffer IS the direct JDK buffer, so a GPU upload later is zero-copy
  by construction); web: `Uint8Array`+`DataView` emulation, no-op clean action
  (GC-managed memory; the wrapper owns lifetime + leak detection).
  Representation for leak reports: `"byte buffer (N bytes)"`. Facade validates
  `sizeInBytes >= 0` before delegating. Duplicate-registration guard not
  re-proven (#28 exemplar's 6th test).
- **Deferred (with the future consumer):** cursor + typed views (C9 renderer),
  buffer-level `duplicate` (C5 sprite — `copyInts` covers the copy), GL/unwrap
  accessors (R5 — nothing to unwrap on JVM), uninitialized-alloc option (no
  consumer; the allocator seam is the exit).

### Verified facts (this session)

- **JDK-21 sealing vs typealias-actual.** The diagnostics chain: class level
  `expect: public abstract expect class` vs `actual: ... typealias` →
  `reason: the modalities are different ('abstract' vs 'sealed')`; members:
  expect final vs JDK `abstract` → `('final' vs 'abstract')` (and the inverse:
  `capacity()` reported **final** by the metadata — the expect member stays
  non-abstract). Resolution: expect members `abstract` except `capacity()`;
  class `abstract`; `-Xjdk-release=11` to read the JDK-11 API surface, where
  the class is a plain abstract class. Verified pairs (Kotlin enforces
  `-Xjdk-release` ≥ `jvmTarget` — the pair moves together): **(11,11) and
  (17,17) compile; (21,21) fails** — sealing arrived in the 21 metadata.
  Runtime is the pinned JDK 21 daemon (Temurin 21.0.11) — tests exercise the
  real 21 classes; compile surface is the 11 API. Future JDK-API need: raise
  the pair by one line; a JDK-21+ compile surface is NOT reachable while the
  typealias stands (wrapper is the alternative, rejected at the touch-point on
  JDK-directness). Recorded in the roadmap risks.
- **Add-time re-verification:** lwjgl-bom 3.4.3 and kotlinx-browser 0.5.0 still
  current (`<release>` on repo1, checked 2026-09-04) — no bump; both were
  removed with the #23 discard and re-added now.
- **Test-hygiene finding (methodology):** the first `ByteBufferTest` left 4
  allocated wrappers unclosed → the C2 best-effort GC test
  (`LeakDetectionJvmTest`) in the same JVM saw 5 leak reports in its poll
  (`expected:<1> but was:<5>` — the 4 buffer wrappers + its own). Resource
  tests must close every wrapper they allocate except the intentional leak,
  which gets claimed via the deterministic seam. The net caught a defect in
  the test, not in the (correct) leak detector.
- **memmove direction (root-cause note):** the initial default `copyInts` had
  the overlap condition inverted (`` `src > dst` `` instead of `dst > src` for
  the backward pass), caught by the exact two-direction tests; the correct
  rule matches the discarded `copyIntsOverlapping` (`dstFrom <= srcFrom` →
  forward, else backward). The common default implementation is shared across
  targets; per-platform native bulk overrides remain for the consumer that
  proves the need (C6 hot loop).

### Owner Hunk review round (2026-09-04 — three comments, fixed in the same changeset)

- **KDocs succinct, external-consumer value only** (the #24 standing rule,
  re-applied): the ByteBuffer class KDoc dropped its project-decision phrasing
  ("the engine name IS the platform type", "the only consumers of cursor
  operations are the renderer batch paths, decided when they land", the
  web-zeroed non-contract note) and the member docs are terse; the service KDoc
  dropped the test-flavored example (kept "an alternate backend"). It also
  caught a stale inconsistency the discarded variant left behind: the
  `allocate` KDoc still said "zeroed" — now "unspecified content".
- **No KDoc on self-evident declarations** — the `internal expect val
  memoryAllocatorDefault` doc removed.
- **Favor private, close to use** — `WebByteBuffer` moved into
  `MemoryAllocatorWeb.kt` as a `private` class (its only instantiation site),
  `checkIndex` private there; the actual mirror exposes only a `protected`
  view. Removed: `internal val view`, `internal fun checkIndex`.

### Close review pass (round 1, two-axis)

Two-axis review of the tree (one lean agent per the owner's rule; verdict +
evidence in `.claude/kge/reviews/c3-round1-twoaxis.md`, tree fingerprint
`8c2d1852…`): **Spec CLEAN** on the touch-point items (verified literally —
including a jshell probe on Temurin 21.0.11 confirming the JDK's out-of-range
throws are `IndexOutOfBoundsException` subclasses and the web bound
equivalence; grep sweeps: zero hits for deferred names/ops, zero log
references); **Standards CLEAN** except 2 doc-only findings (the item-8 KDoc
rule): `MemoryAllocatorJvm`'s KDoc used the banned "IS the platform type"
phrasing + a forward-looking engine-internal example ("future GPU upload"),
and the public web actual's KDoc linked the private `WebByteBuffer` impl +
carried internal rationale (the #29 T3 pattern). Fixed in the same changeset —
both KDocs reworded to external facts. Fix-delta verified: full gate re-run
fresh — BUILD SUCCESSFUL, counts unchanged (the fixes are doc-only).

### Gate

`./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` — fresh run, XML
counts: jvmTest **80**, jsNodeTest **84** (js adds kotest
discovery/context-container overhead), wasmJsNodeTest **79** (the jvm-only C2
GC test excluded), **0 failures**. Baseline vs #30 (58/62/57): **+22 per
target** — `ByteBufferTest` 18 + `MemoryAllocatorServiceTest` 4. ktlint: one
format pass (class-signature wrapping) and the `standard:filename` rename of
the web actual file (`ByteBufferWeb.kt` → `ByteBuffer.kt`; no facade clash —
no top-level declarations there). One commit for the concept (#24
single-commit decision).
