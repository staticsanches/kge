# KGE Restructure — Phases 0 + 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring up the new KGE project skeleton (Kotlin 2.4.10 / Gradle 9.5.0 / Koin 4.2.x, single `kge-core` KMP module on JVM+JS+wasmJs, CI) and then build + test the platform-pure kernel (buffers, pixels, PixelMap/Sprite, raster primitives, time/input state, text metrics) on the `restructure` branch.

**Architecture:** Greenfield incremental rewrite. Old code was wiped (`main` history is the reference/inspiration source). Phase 0 = skeleton + CI. Phase 1 = Layer 0 of the design, platform-free kernel over a minimal `Buffer` interface (JVM: LWJGL MemoryUtil; web: TypedArray), TDD'd with Kotest (exact-pixel cases + differential/property tests against a reference rasterizer).

**Tech Stack:** Kotlin 2.4.10 (KGP), Gradle gateway: wrapper pinned **9.5.0**, system Gradle (sdkman 9.5.1) only to generate the wrapper; daemon JDK 21; compile toolchain/bytecode 11; Koin 4.2.2; Kotest 6.2.4 + KSP **2.3.11** (KSP2 is decoupled from Kotlin version since 2.3.0 — the old `<kotlin>-<patch>` scheme no longer exists) + `io.kotest` plugin; Ktlint 12.3.0; kotlin-wrappers catalog **2026.8.5**; kotlinx-coroutines 1.10.2; kotlin-logging 7.0.6; LWJGL BOM 3.3.6; foojay-resolver-convention 1.0.0 (provisions JDK 11 for the toolchain).

**Spec:** `docs/specs/2026-08-30-kge-restructure-design.md` — this plan argues from it; executors read both.

## Global Constraints

- Repo docs/commits in **English**; commits end with `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- **TDD**: failing test → run (red) → implement → run (green) per feature; the plan's test code is the contract.
- `main` is the **inspiration/reference source**: for any content that existed there, read it (`git show main:<path>`) rather than re-inventing; the plan points to it.
- Old kernel tests were dropped on purpose — the tests in this plan are new; they are the only oracle besides olc (`/Users/felipesanches/workspace/olcPixelGameEngine/olcPixelGameEngine.h` v2.30).
- **Linter**: ktlint applied via `org.jlleitschuh.gradle.ktlint` (alias in catalog); config in `.editorconfig` (recreate from `git show main:.editorconfig`); `ktlintCheck` must pass every task.
- No `expect/actual` **classes** anywhere (design rule) — only `expect` functions (buffer allocation).
- Verification log lives in `docs/decisions/phase-1.md`, appended per task.
- **In-phase naming/semantics decisions are confirmed with the owner** at the task that touches them ("Step 0: Decision checkpoint" in Tasks 6, 7, 11) — defaults in the plan are proposals for that confirmation, not preset choices.

---

### Task 1: Bootstrap root build files (Phase 0)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.editorconfig`, `.gitignore`, `.sdkmanrc`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat` (all new — nothing exists)
- Reference: `.editorconfig` content ← `git show main:.editorconfig`; `.gitignore` ← Kotlin/Gradle standard (see step 4)

**Interfaces:**
- Produces: build with one included module `kge-core`; version catalog aliases consumed by Task 2 (`kotlin`, `ksp`, `kotest`, `ktlint`, `ktlint-plugin`, `koin`, `koin-bom`, `coroutines`, `kotlin-logging`, `kotlin-wrappers-catalog`, `lwjgl-bom`); plugin aliases `kotlin-multiplatform`, `ktlint`, `ksp-gradle`, `kotest-plugin`.

- [ ] **Step 1: Confirm wipe** — `git ls-files` shows only `docs/` + `LICENSE.md` (+ `.editorconfig`/`.gitignore`/wrapper/`gradlew*` from... no: only `docs/`, `LICENSE.md` — the first commit contains nothing else). If anything else lingers, stop.

- [ ] **Step 2: `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "kge"
include("kge-core")
```

- [ ] **Step 3: Generate the wrapper (system Gradle 9.5.1, pinned 9.5.0)**

```bash
gradle wrapper --gradle-version 9.5.0 --distribution-type bin
```

(Wrapper = machine independence for builders; `.sdkmanrc` below = deterministic dev machine.)

- [ ] **Step 4: `.editorconfig` + `.gitignore` + `.sdkmanrc`**

`.editorconfig`: content from `git show main:.editorconfig` (root=true; max_line_length=120; `ktlint_standard_argument-list-wrapping = disabled`).

`.gitignore` (Kotlin/Gradle project, tuned):

```gitignore
# Kotlin/Gradle build
.gradle/
build/
.kotlin/
# IDE
.idea/
*.iml
.run/
# macOS
.DS_Store
# Profiling/temp
*.jfr
*.log
node_modules/
```

Deliberately NOT ignored: `gradle/wrapper/` (wrapper jar is the bootstrap), `kotlin-js-store/yarn.lock` (JS test lockfile, should be tracked — as it was).

`.sdkmanrc`:

```
java=21.0.11-tem
gradle=9.5.1
```

(Matches your local default; `sdk env` activates it.)

- [ ] **Step 5: `gradle.properties`**

```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
```

- [ ] **Step 6: `gradle/libs.versions.toml`** (verify-none: versions pinned by the research; KSP/Kotest/wrappers confirmed via Maven metadata 2026-08)

```toml
[versions]
kotlin = "2.4.10"
ksp = "2.3.11"
kotest = "6.2.4"
koin = "4.2.2"
coroutines = "1.10.2"
kotlinLogging = "7.0.6"
ktlint = "12.3.0"
lwjgl = "3.3.6"
kotlinWrappersCatalog = "2026.8.5"

[libraries]
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-bom = { module = "io.insert-koin:koin-bom", version.ref = "koin" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlin-logging = { module = "io.github.microutils:kotlin-logging", version.ref = "kotlinLogging" }
kotest-framework = { module = "io.kotest:kotest-framework-engine", version.ref = "kotest" }
kotest-property = { module = "io.kotest:kotest-property", version.ref = "kotest" }
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-datatest = { module = "io.kotest:kotest-datatest", version.ref = "kotest" }
lwjgl-bom = { module = "org.lwjgl:lwjgl-bom", version.ref = "lwjgl" }
lwjgl-core = { module = "org.lwjgl:lwjgl" }
kotlin-wrappers-catalog = { module = "org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog", version.ref = "kotlinWrappersCatalog" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
ksp-gradle = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kotest-plugin = { id = "io.kotest", version.ref = "kotest" }
```

- [ ] **Step 7: root `build.gradle.kts`** (YAGNI: no vanniktech yet — publishing grows on demand)

```kotlin
plugins {
    alias(libs.plugins.kotest.plugin) apply false
    alias(libs.plugins.ktlint) apply false
}
```

- [ ] **Step 8: Sanity** — `./gradlew projects` → evaluates, one subproject (`kge-core`), no errors.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "build: bootstrap restructure skeleton (Kotlin 2.4.10, Gradle 9.5.0, kge-core only)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: `kge-core` module skeleton + smoke test (Phase 0)

**Files:**
- Create: `kge-core/build.gradle.kts`
- Create: `kge-core/src/commonTest/kotlin/dev/staticsanches/kge/SmokeTest.kt`

**Interfaces:**
- Produces: compiling KMP module; test tasks `jvmTest`, `jsTest`, `wasmJsTest`; `allTests` aggregate.

- [ ] **Step 1: Smoke test** (kotest runs everywhere)

```kotlin
package dev.staticsanches.kge

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SmokeTest : FunSpec({
    test("kotest runs on this target") { (2 + 2).shouldBe(4) }
})
```

- [ ] **Step 2: `kge-core/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp.gradle)
    alias(libs.plugins.kotest.plugin)
}

kotlin {
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
    js(IR) { browser { testTask {} }; nodejs() }
    wasmJs { nodejs() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.coroutines.core)
            implementation(libs.kotlin.logging)
        }
        commonTest.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.kotest.framework)
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.datatest)
            implementation(libs.coroutines.test)
        }
        jvmMain.dependencies { implementation(libs.lwjgl.core) } // MemoryUtil only; GL deps come with the GL service (Phase 3)
    }
}
```

(NOTE: if `compilerOptions { jvmTarget }` inside `jvm {}` doesn't resolve on KGP 2.4, use `jvmToolchain(11)` — record which in `docs/decisions/phase-1.md`.)

- [ ] **Step 3: Run**

```bash
./gradlew :kge-core:allTests --stacktrace
```

Expected: smoke green on JVM, JS (Node+Karma-Chrome), wasmJs (Node). Kotest-on-JS/wasmJs issues (if any: KSP discovery) get fixed here — record outcome in the decisions log.

- [ ] **Step 4: Lint** — `./gradlew ktlintCheck` clean.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "build: add kge-core KMP module (jvm/js/wasmJs, koin, kotest, ktlint)"` + co-author line per convention.

---

### Task 3: CI (Phase 0)

**Files:**
- Create: `.github/workflows/build.yaml` (nothing exists — old workflows were wiped)

- [ ] **Step 1: Workflow** — gate = node tests + lint (decided for stability: browser runs locally; Chrome/Karma can be added back on demand):

```yaml
name: Build
on:
  push: { branches: [restructure, main] }
  pull_request: { branches: [main] }
jobs:
  build:
    strategy:
      fail-fast: false
      matrix: { os: [ubuntu-latest, windows-latest, macos-latest] }
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: zulu, java-version: "21" }
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew :kge-core:jvmTest :kge-core:jsNodeTest :kge-core:wasmJsNodeTest ktlintCheck --stacktrace
```

NOTE: kernel tests touch no OpenGL → no Mesa/GL setup until Phase 3 (restore `ssciwr/setup-mesa-dist-win` then). Actions on the repo are currently disabled (owner decision) — the workflow file still lands now; it starts firing when Actions are re-enabled or CI is discussed next.

- [ ] **Step 2: Push branch** — `git push -u origin restructure`; workflow file present (not firing yet; Actions disabled).

- [ ] **Step 3: Commit** — `git add -A && git commit -m "ci: build gate on restructure/main (node tests + lint, zulu 21)"` + co-author.

---

### Task 4: Scaffold verification + decisions log (Phase 0)

**Files:**
- Modify: `kge-core/build.gradle.kts` (add `webMain`/`webTest` if the default hierarchy template doesn't provide it)
- Create: `docs/decisions/phase-1.md` (first entries — see open items below)

- [ ] **Step 1: Inspect source sets** — `./gradlew :kge-core:sourceSets` looking for `webMain`/`webTest` (template web group). If absent: `applyDefaultHierarchyTemplate()` then custom `webMain`/`webTest` via `by creating { dependsOn(commonMain.get()) }` + edges from `jsMain`/`jsTest`/`wasmJsMain`/`wasmJsTest` (the manual procedure the Kotlin docs prescribe; manual edges require reapplying the template first).

- [ ] **Step 2: Confirm framework combos** — `./gradlew :kge-core:allTests` green after the source-set change (Koin 4.2.2 + Kotest 6.2.4 + KSP 2.3.11 + Kotlin 2.4.10).

- [ ] **Step 3: Open `docs/decisions/phase-1.md`** with entries for each verified fact (webMain present y/n; wasmJs test runner node/browser; kotest works on js/wasmJs; jvmTarget DSL vs jvmToolchain choice).

- [ ] **Step 4: Commit** — `git add -A && git commit -m "build: verify scaffold — web source set, wasmJs/coroutines/kotest combos"` + co-author.

---

### Task 5: `Buffer` interface + platform implementations (kernel start)

**Files:**
- Create: `commonMain/.../buffer/Buffer.kt`, `.../buffer/ByteBuffer.kt`, `.../buffer/ByteBufferFactory.kt` (expect fun)
- Create: `jvmMain/.../buffer/JvmByteBuffer.kt`; `webMain/.../buffer/JsByteBuffer.kt`
- Test: `commonTest/.../buffer/ByteBufferTest.kt`
- Reference (inspiration only — adapt, don't copy): `git show main:kge-core/src/commonMain/kotlin/dev/staticsanches/kge/buffer/Buffer.kt` and the JVM/JS actives in `main:.../buffer/BufferJVM.kt` + `main:.../buffer/BufferJS.kt`

**Interfaces:**
- `interface Buffer { val capacity: Int; var position: Int; var limit: Int; fun mark(): Buffer; fun clear(): Buffer; fun remaining(): Int; fun reset(): Buffer; fun flip(): Buffer; fun rewind(): Buffer }`
- `interface ByteBuffer : Buffer { val sizeInBytes: Int; fun byteAt(i: Int): Int; fun putByte(i: Int, v: Int); fun getInt(i: Int): Int; fun putInt(i: Int, v: Int); fun fillInts(from: Int, to: Int, value: Int); fun copyInts(dst: ByteBuffer, srcFrom: Int, dstFrom: Int, count: Int) }` — ints little-endian (fixed)
- `expect fun allocateByteBuffer(sizeInBytes: Int): ByteBuffer`

- [ ] **Step 1: Tests** (contract — key cases: zeroed alloc, capacity/position/limit/flip/rewind/mark/reset, int roundtrip + little-endian byte order, fillInts range, copyInts overlapping; JVM impl = LWJGL `MemoryUtil.memAlloc` with LITTLE_ENDIAN order; web impl = TypedArray (little-endian by spec)).

- [ ] **Step 2: Run → red** (`:kge-core:jvmTest`: `allocateByteBuffer` unresolved)

- [ ] **Step 3: Implement** common interface + actives (per Interfaces; test contract drives details)

- [ ] **Step 4: Run → green** (`:kge-core:allTests` — same semantics on JVM/JS/wasmJs because TypedArray is little-endian)

- [ ] **Step 5: Commit** — `feat(kernel): buffer interface + JVM (LWJGL) and web (TypedArray) implementations`

---

### Task 6: Pixel + color codec

**Files:** Create `commonMain/.../image/Pixel.kt`, `.../image/Colors.kt`; Test `commonTest/.../image/PixelTest.kt`
Reference: `git show main:.../image/Pixel.kt` + `main:.../image/Colors.kt` (semantics; the old value-class layout carries over)

**Interfaces:**
- `@JvmInline value class Pixel(val rgba: Int)`; `enum class PixelMode { NORMAL, MASK, ALPHA }`
- `object PixelOps { infix fun rgb(r: Int, g: Int, b: Int, a: Int = 255): Pixel; fun red(p): Int; fun green(p): Int; fun blue(p): Int; fun alpha(p): Int; fun blendOver(base, overlay): Pixel; fun masked(base, overlay): Pixel; fun Pixel.withAlpha(a: Int): Pixel }`
- `object Colors { BLACK, WHITE, BLANK, RED, GREEN, BLUE, CYAN, MAGENTA, YELLOW, GREY }` (extended palette only when a consumer exists)

- [ ] **Step 0: Decision checkpoint (owner)** — palette scope: 9 constants now (BLACK/WHITE/BLANK/RED/GREEN/BLUE/CYAN/MAGENTA/YELLOW/GREY — 10), extended CSS palette only when a consumer exists. Confirm before implementing.

- [ ] **Step 1: Tests** — component extraction (RGBA bit layout), packing `0xFF0000FF`, constants rgba, blend: opaque wins / a=0 identity / 50% red-over-blue interpolation formula `(a*c + (255-a)*bc)/255` floor, masked transparent/opaque, idempotence `forAll` rows.

- [ ] **Step 2-4: red → implement (per Interfaces; blend/mask semantics exactly per tests) → green** (`allTests`)

- [ ] **Step 5: Commit** — `feat(kernel): pixel value class, RGBA ops and basic color set`

---

### Task 7: `PixelMap` — 2D pixel surface over a `ByteBuffer`

**Files:** Create `commonMain/.../image/PixelMap.kt`; Test `commonTest/.../image/PixelMapTest.kt`; append naming decision to `docs/decisions/phase-1.md`
Reference: `git show main:.../image/PixelMap.kt` (row-major layout contract is the old intent; the new `require` validation and `create` companion are new)

**Interfaces:**
- `open class PixelMap(val width: Int, val height: Int, val buffer: ByteBuffer)` — `init` requires positive dims and `buffer.capacity == width*height*4`
- `fun contains(x,y): Boolean; fun pixelIndex(x,y): Int; fun getPixel(x,y): Pixel; fun setPixel(x,y,pixel); fun fill(pixel); fun duplicate(): PixelMap; companion { fun create(w,h): PixelMap }`

- [ ] **Step 0: Decision checkpoint (owner)** — name and semantics of the 2D-over-1D pixel surface are **open** (per spec: decided when this phase reaches them). Proposal to discuss: keep `PixelMap` (the old concept) with row-major layout and caller-side clipping; alternatives on the table: `Canvas`/`RasterSurface`/`RGBABuffer`/2D-typed buffer. Nothing preset — discuss, decide, record, then continue.

- [ ] **Step 1: Tests** — zeroed create (capacity `w*h*4`), roundtrip, row-major `getInt(x*4)` layout, `fill`, `duplicate` deep copy (mutating copy leaves original), `contains` bounds.

- [ ] **Step 2-4: red → implement → green**

- [ ] **Step 5:** decision log: keep the name `PixelMap` (alternatives `RasterSurface`/`Canvas` rejected — GPU-vocabulary collision); semantics row-major, engine-side clamping stays with callers. Commit `feat(kernel): PixelMap — row-major 2D pixel surface over ByteBuffer`.

---

### Task 8: `Sprite` — `PixelMap` + sampling

**Files:** Create `commonMain/.../image/Sprite.kt`; Test `commonTest/.../image/SpriteTest.kt`
Reference: `git show main:.../image/Sprite.kt` (SampleMode/Flip semantics)

**Interfaces:**
- `enum class SampleMode { NORMAL, PERIODIC, CLAMP }`
- `class Sprite(width, height, buffer) : PixelMap(...)` — `fun sample(x, y, mode = NORMAL): Pixel` (NORMAL/CLAMP same clamp, PERIODIC wraps via floorMod); `companion { fun create(w, h): Sprite }`

- [ ] **Step 1: Tests** — NORMAL clamps (negative & overflow), PERIODIC wraps (x=w → 0; negatives), CLAMP border values.

- [ ] **Step 2-4: red → implement → green**

- [ ] **Step 5: Commit** — `feat(kernel): Sprite — PixelMap with NORMAL/PERIODIC/CLAMP sampling`

---

### Task 9: Raster primitives — line, rect, circle

**Files:** Create `commonMain/.../raster/RasterOps.kt`; Test `commonTest/.../raster/RasterOpsTest.kt`
Reference: **only the old algorithms** — `git show main:.../rasterizer/utils/BresenhamLine.kt` + `.../utils/BresenhamCircle.kt` (the 2026 semantics below are simpler and are the plan's)

**Interfaces:**
- `fun fillRect(pm, x, y, w, h, color, mode = NORMAL)` (clip negative/overflow)
- `fun drawRect(pm, x, y, w, h, color, mode = NORMAL)`
- `fun drawLine(pm, x0, y0, x1, y1, color, mode = NORMAL)` (Bresenham via Liang–Barsky parametric clip first)
- `fun fillCircle(pm, cx, cy, r, color, mode = NORMAL)`; `fun drawCircle(pm, cx, cy, r, color, mode = NORMAL)` (midpoint, 8-fold)
- `fun put(pm, x, y, color, mode)$` — the single write path: bounds check; NORMAL writes; MASK writes only if alpha==255; ALPHA writes `blendOver(base)`, skip if alpha==0

- [ ] **Step 1: Tests** — fillRect interior exact / negative clip; drawRect outline set; drawLine discrete cells (0,0)→(3,1) = (0,0)(1,0)(2,1)(3,1), neighbors BLANK; line clip through bounds (no crash, border lands); fillCircle r=2 exact interior (`dx²+dy²≤r²`); drawCircle r=2 cardinal 8 cells + outside `dist>r²+1` blank; put via MASK transparent no-op.

- [ ] **Step 2-4: red → implement → green**

- [ ] **Step 5: Commit** — `feat(kernel): raster primitives — line (clip+Bresenham), rect, circle (midpoint)`

---

### Task 10: `fillTriangle` — scanline + differential oracle

**Files:** Create `commonMain/.../raster/TriangleRasterizer.kt`; Test `commonTest/.../raster/TriangleRasterizerTest.kt`
Reference: `git show main:.../rasterizer/utils/SortedTriangleVertices.kt` (vertex sorting) — the scanline itself is below

**Interfaces:**
- `fun fillTriangle(pm, x0,y0, x1,y1, x2,y2, color, mode = NORMAL)` — scanline at pixel centers: half-open top/left, closed bottom/right (rule recorded in decisions log)
- `fun referenceFillTriangle(...)` — **independent** integer scanline built as the oracle (classic: sort vertices; per y row, interpolate both x edges; same half-open half-closed tie rules). Must not share code paths with `fillTriangle`.

- [ ] **Step 1: Tests** — single-pixel triangle; known (0,0)(0,2)(3,2) expected rows `y0:x==0; y1:x<=1; y2:x<=3`; **differential**: 500 seeded (Random(42)) random triangles (coords −2..w+2) must match reference cell-by-cell; **blended differential**: 200 seeded triangles with alpha 128 over a BLUE base must match reference in ALPHA mode; degenerate (zero-area) writes nothing.

- [ ] **Step 2-4: red → implement (rasterizer + oracle) → green** — `allTests` on the 3 targets.

- [ ] **Step 5: decisions + commit** — rule "pixel centers, half-open top/left, closed bottom/right"; `feat(kernel): triangle scanline rasterizer + differential property tests`

---

### Task 11: Time/input state machines

**Files:** Create `commonMain/.../state/TimeState.kt`, `.../state/input/KeyCode.kt`, `.../state/input/InputAction.kt`, `.../state/input/InputState.kt`; Tests `commonTest/.../state/TimeStateTest.kt`, `.../state/input/InputStateTest.kt`
Reference: `git show main:.../engine/state/TimeState.kt` (windowed FPS concept)

**Interfaces:**
- `class TimeState { var timeScale: Double = 1.0; val elapsedTime: Double = 0.0; val fps: Int = 0; fun tick(nowSeconds: Double); fun reset() }` — first tick sets reference (elapsed 0); fps = frames in a 1s window (window resets on ≥1.0s); elapsed scaled by timeScale
- `enum class KeyCode { UNKNOWN, SPACE, ENTER, TAB, ESCAPE, BACKSPACE, LEFT, RIGHT, UP, DOWN, A..Z, DIGIT0..DIGIT9, F1..F12 }` (common enum replaces the old expect enum — recorded in decisions)
- `sealed class InputAction { data class Press(key); Release(key); Repeat(key) }`
- `class InputState { fun press(key); release(key); repeat(key); isDown(key): Boolean; consumedPresses(key): Boolean }` — edge-triggered press queue

- [ ] **Step 0: Decision checkpoint (owner)** — `KeyCode` as common enum (replaces the old expect enum; platform maps GLFW/DOM codes to it) + `InputAction` sealed types. Confirm before implementing.

- [ ] **Step 1: Tests** — TimeState: accumulate 0.5 in 1.0→1.5; 60 ticks at 1/60 → fps 60; timeScale 2×; reset zeroes and next tick is ref again. InputState: press/release toggles; consumedPresses true then false; repeat no edge; release clears.

- [ ] **Step 2-4: red → implement → green**

- [ ] **Step 5: decisions + commit** — `feat(kernel): TimeState (windowed FPS, timeScale) and InputState (edge-triggered key state)` with KeyCode common-enum note.

---

### Task 12: Text metrics

**Files:** Create `commonMain/.../text/TextMetrics.kt`; Test `commonTest/.../text/TextMetricsTest.kt`
Reference: `git show main:.../rasterizer/service/DrawStringService.kt` (width/tab semantics of the old font sheet)

**Interfaces:**
- `class TextMetrics(charWidths: IntArray /*95 entries, ' '..'~'*/, tabSizeInSpaces: Int = 4)` — `fun widthOf(text: String): Int` (tab = space width × tabSize), `fun wrap(text: String, maxWidth: Int): List<String>` (word wrap; single overflow word stays intact)

- [ ] **Step 1: Tests** — uniform 8px widths: "abc"=24, ""=0; tab `a\tb` = 8+32+8; wrap "hello world" @40 → [hello, world], @64 → single line; long word @16 → intact.

- [ ] **Step 2-4: red → implement → green**

- [ ] **Step 5: Commit** — `feat(kernel): TextMetrics — tab-aware width and word wrapping`

---

### Task 13: Phase 1 wrap-up

- [ ] **Step 1: Gate** — `./gradlew :kge-core:allTests ktlintCheck` green on all targets.

- [ ] **Step 2: Kover (visibility only, no percentage gate)** — if the Kover plugin (latest, `org.jetbrains.kotlinx.kover`) resolves cleanly with the KMP setup: add to root `apply false`, apply in kge-core, generate `:kge-core:koverXmlReport` as a visibility artifact; if it fights the toolchain, defer (recorded decision; spec's "no gate" holds either way).

- [ ] **Step 3: Finalize `docs/decisions/phase-1.md`** — kernel complete: Buffer/ByteBuffer (+platform), Pixel/Colors, PixelMap, Sprite, RasterOps, TriangleRasterizer(+oracle), TimeState/InputState/KeyCode, TextMetrics; zero GL/audio deps in commonMain; parity = same commonTest across JVM/JS/wasmJs.

- [ ] **Step 4: Commit** — `docs: close Phase 1 kernel decisions log`

---

## Self-Review vs spec

- Section 1 (stack/skeleton/CI/toolchain) → Tasks 1-4; spec's `[verify]` items → Task 4 (explicit) — KSP/Kotest/wrappers pins already verified (2026-08 metadata).
- Section 2 Layer 0 entire → Tasks 5-12; buffer seam: JVM = LWJGL MemoryUtil real buffers, web = TypedArray; no heap buffer (per design).
- Section 2 Layers 1-3 → later plan (Phases 2-3) by design; Section 3 rows 1-7 → Tasks 6-12; rows 8-10 (platform buffers, engine, extension) → later.
- Section 4 Phase 0 gate → Task 4; Phase 1 gate → Task 13; user pauses after Phase 0 gate (decision).
- Type consistency: signatures in Interfaces blocks are single-sourced; test code references match them exactly (`pixelIndex`, `blendOver`, `withAlpha`, `consumedPresses`...). Note `blendOver` formula and `withAlpha` defined once (Task 6), consumed by Tasks 9-11.
- No placeholders beyond the deliberate 2: (a) Task 10 reference scanline = classic algorithm described by tie rules + oracle role (its behavior is fully pinned by the differential tests — implementation detail, not a spec gap); (b) Kover in Task 13 explicitly optional (owner's spec: visibility only, no gate).
