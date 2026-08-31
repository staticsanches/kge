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
