# `kge-text-ttf` — micro-plan, round B (scaffold)

**Date:** 2026-09-16. Round **B** of the text work: scaffold the opt-in
`kge-text-ttf` module with its targets, the HarfBuzz + FreeType dependencies on
every target, and a smoke test proving the native/wasm libraries load. Rounds:
A bitmap (core) → **B scaffold** → C face + shaping + layout → D raster +
atlas/cache → E blit + addons + decal. Context:
`docs/plans/2026-09-16-r6-text-touchpoint.md` (Revision section) and the round A
micro-plan.

## Scope

A new KMP module `kge-text-ttf` (jvm/js/wasmJs, browser-only web targets),
`settings.gradle.kts` inclusion, the version catalog entries for the four
libraries, the LWJGL native-classifier wiring, the web npm dependencies and the
webpack `resolve.fallback` config, and one smoke test per target. **No
production API** — the seam (open/close face, shape, rasterize) and the public
layout arrive in rounds C/D/E; shipping a provisional seam now would violate the
"no throwaway commits" rule. The module's own package root
`dev.staticsanches.kge.text.ttf` is fixed here; the module does **not** depend on
`kge-core` yet — that dependency lands in round C, when the code first consumes
`Sprite`/`Pixel`.

## Dependencies (decided at the touch-point: wired in this scaffold round)

The catalog carries the version centralization (the catalog has no npm type);
the JVM artifacts ride the existing `lwjgl = "3.4.3"` BOM.

- **JVM:** `org.lwjgl:lwjgl-harfbuzz` and `org.lwjgl:lwjgl-freetype` under
  `libs.lwjgl.bom` (new catalog aliases `lwjgl-harfbuzz`, `lwjgl-freetype`).
  Non-obvious transitive fact: the old `fontDevelopment` branch declared
  `lwjgl-harfbuzz` but never called it; it is reference only.
  `lwjgl-core` is required on the test runtime classpath for LWJGL's shared
  loader. Natives use the shared `lwjglNativesClassifier()` utility in
  `buildSrc` (also consumed by `kge-core`).
- **web (js + wasmJs, shared `webMain`):** npm `harfbuzzjs` `1.6.1` (HarfBuzz
  `14.4.0`) and `@zkl2333/freetype-wasm` `2.14.3` (FreeType `2.14.3`). Versions
  centralized as catalog `[versions]` entries (`harfbuzzjs`, `freetype-wasm`),
  referenced via `libs.versions.<name>.get()`.
- Webpack: the Emscripten glue references Node builtins inside an
  `ENVIRONMENT_IS_NODE` branch, and the `harfbuzzjs` entry module has a
  top-level `await`; both break the browser build without a
  `webpack.config.d/*.js` config (see "Interop findings").

## Interop findings (from the package tarballs + Kotlin docs; the round B contract)

Recorded because the round B implementation depends on them (do not re-research):

- `harfbuzzjs@1.6.1`: `package.json` `main`/`exports` `"." → dist/index.mjs`
  (ESM), `type: module`. `index.mjs` has **named exports** (`Blob`, `Face`,
  `Font`, `Buffer`, `shape`, `versionString`, …) and **no default export**; its
  last line is `init(await createHarfBuzz())` (top-level await). The `.wasm` is
  located by the glue via `new URL("harfbuzz.wasm", import.meta.url)`.
  `versionString()` is **synchronous** and returns `"14.4.0"`.
- `@zkl2333/freetype-wasm@2.14.3`: ESM; `index.mjs` **default-exports**
  `async function initFreeType(opts) → Promise<FreeType>` and **named-exports**
  `FT`, `FreeType`, `Face`; no top-level await. `ft.version()` returns
  `[major, minor, patch]`. The `.wasm` is located the same `new URL(…)` way.
- Kotlin/JS `@JsModule`: named exports become top-level `external` members of a
  `@file:JsModule("pkg")` file; a default export is an `external fun` carrying
  `@JsName("default")`.
- Kotlin/wasmJs: every `external class` must extend `JsAny`; no `dynamic`
  (use `JsAny`); ES modules only. A `webTest` written with `: JsAny` supertypes
  compiles for both `js` and `wasmJs`.
- Risk (flagged UNVERIFIED in the research): the `harfbuzzjs` top-level await
  under the wasmJs webpack bundle, and whether `webpack.config.d/*.js` is merged
  for the wasmJs target the way it is for js; the emitted `.wasm` URL
  (`import.meta.url` sibling) must resolve in the Karma/ChromeHeadless runs. If
  the wasmJs `harfbuzzjs` entry proves unloadable, the fallback is a per-target
  interop file (a `@JsFun` bootstrap or driving the Emscripten factory
  directly); **do not** change the dependency or the public surface — this is a
  test-only interop detail. Report the blocker if no clean form is found.

## Decisions (recorded)

1. **Module shape.** New KMP module `kge-text-ttf` mirroring `kge-core`'s
   library layout: `jvm()`, `js(IR) { browser { testTask { useKarma {
   useChromeHeadless() } } } }`, `wasmJs { browser { testTask … } }`; plugins
   `kotlin.multiplatform` + `ksp` + `ktlint` + `kotest`; `useJUnitPlatform()` on
   `Test`; the same ktlint `/build/generated/` exclusion as `kge-core`. It is a
   library (no `binaries.executable()`), unlike `kge-benchmark`.
2. **No production source in this round.** Only build config and tests. The
   module's first production code is round C.
3. **Dependencies live in the test source sets for now.** JVM LWJGL artifacts in
   `jvmTest` (the only consumer), web npm in `webMain` (so `webTest` inherits the
   bundle). When round C adds `jvmMain`/`webMain` production code, the
   production dependencies move with it.
4. **Smoke assertions are platform-local, not cross-platform.** JVM HarfBuzz and
   web HarfBuzz are different builds (`3.4.3` native vs `14.4.0` wasm), so the
   smoke asserts a **non-blank version / successful init**, never a cross-target
   equality. Parity between native and wasm is a round C/D concern with the
   version pinned there.

## Steps (TDD: red → green per feature)

1. **Scaffold the module (config, not TDD).** Add `include("kge-text-ttf")` to
   `settings.gradle.kts`; create `kge-text-ttf/build.gradle.kts` per decision 1;
   add catalog entries `lwjgl-harfbuzz`, `lwjgl-freetype`, and `[versions]`
   `harfbuzzjs = "1.6.1"`, `freetype-wasm = "2.14.3"`. Verify:
   `./gradlew :kge-text-ttf:build --rerun-tasks` succeeds (empty module compiles
   on the three targets).
2. **JVM smoke (red → green).** Add
   `src/jvmTest/kotlin/dev/staticsanches/kge/text/ttf/NativeStackSmokeTest.kt`
   (kotest `FunSpec`): `HarfBuzz.hb_version_string()` is non-blank;
   `MemoryStack.stackPush()` → `FT_Init_FreeType(PointerBuffer)` returns
   `FT_Err_Ok`, then `FT_Done_FreeType` returns `FT_Err_Ok`. Red: the imports do
   not resolve (no dependency). Green: add the `jvmTest` LWJGL deps + native
   classifiers (decision 3) and run
   `./gradlew :kge-text-ttf:jvmTest --rerun-tasks`.
   (`hb_version_string()` is the native call that forces `getLibrary()`; a
   compile-time `HB_VERSION_STRING` constant would prove nothing.)
3. **Web smoke (red → green).** Add a shared `webTest` smoke
   (`src/webTest/kotlin/.../WebNativeStackSmokeTest.kt`, kotest `FunSpec`,
   suspend tests) that reads a HarfBuzz version and initializes FreeType through
   `@JsModule` externals: `@file:JsModule("harfbuzzjs") external fun
   versionString(): String`; `@file:JsModule("@zkl2333/freetype-wasm")
   @JsName("default") external fun initFreeType(options: JsAny? =
   definedExternally): Promise<JsAny>` (plus the `FreeType`-returning member
   needed for a version, or a non-null init assertion). Assert the HarfBuzz
   version is non-blank and the FreeType init resolves to a non-null object.
   Red: unresolved externals. Green: add the `webMain` npm deps and the webpack
   config, then run `./gradlew :kge-text-ttf:jsBrowserTest --rerun-tasks` and
   `:kge-text-ttf:wasmJsBrowserTest --rerun-tasks`.
   - The npm externals carry `: JsAny` supertypes so one `webTest` file serves
     both web targets; split into `jsTest`/`wasmJsTest` only if the interop
     genuinely differs, and record why.

## Out of scope (round B)

- Face loading, HarfBuzz shaping, FreeType rasterization, glyph cache/atlas,
  layout, blit, addons, decal — rounds C/D/E.
- `kge-core` dependency, any public API, any `expect/actual` production seam.
- Cross-target parity goldens (the stack's byte-identity was proven at the
  touch-point spike; it is re-pinned per round where it matters).

## Gate

`./gradlew build --rerun-tasks` green (the new module included), then the
two-axis review, then the decisions-log entry, then one commit for the round.
