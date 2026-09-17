## 2026-09-16 — `kge-text-ttf` scaffold, round B: module, deps + smoke test

Round B of the text work. Context:
`docs/plans/2026-09-16-r6-text-touchpoint.md` (Revision) and the round B
micro-plan `docs/plans/2026-09-16-kge-text-ttf-round-b-microplan.md`. This is a
**scaffold** round: the module, its targets, the HarfBuzz + FreeType dependencies
on every target, and a smoke test proving the native/wasm libraries load. **No
production source and no public API** — the seam and the layout land in rounds
C/D/E, so nothing provisional is shipped.

### Shape (decided at the touch-point, wired here)

- New KMP module `kge-text-ttf` (jvm/js/wasmJs, web browser-only) included in
  `settings.gradle.kts`, mirroring `kge-core`'s library layout (kotlin
  multiplatform + ksp + ktlint + kotest, `useJUnitPlatform`, the same ktlint
  `/build/generated/` exclusion). No `kge-core` dependency yet: it lands in
  round C, when the code first consumes `Sprite`/`Pixel`.
- Dependencies, consumed for the first time in this round: JVM
  `org.lwjgl:lwjgl-harfbuzz` and `org.lwjgl:lwjgl-freetype` under the existing
  `lwjgl = "3.4.3"` BOM (new catalog aliases), with the OS/arch native
  classifier shared through a new `buildSrc` utility
  (`lwjglNativesClassifier()`, also consumed by `kge-core` — the owner review
  asked for the de-duplication); web npm `harfbuzzjs` `1.6.1`
  (HarfBuzz `14.4.0`) and `@zkl2333/freetype-wasm` `2.14.3` (FreeType
  `2.14.3`), versions centralized as catalog `[versions]` entries. The JVM
  artifacts live in `jvmTest` and the npm packages in `webMain` for now — the
  only consumers; they move to the production source sets in round C.
- Smoke tests: a JVM `FunSpec` (`HarfBuzz.hb_version_string()` non-blank;
  `FT_Init_FreeType`/`FT_Done_FreeType` error-free) and a shared `webTest`
  `FunSpec` (HarfBuzz `versionString()` non-blank; the awaited FreeType
  `initFreeType()` non-null). Assertions are platform-local (JVM HarfBuzz and
  web HarfBuzz are different builds) — no cross-target equality in this round.

### Divergences and non-obvious findings (recorded)

- **`moduleKind = MODULE_ES` on the js target** (not in the micro-plan). With
  the Kotlin/JS default UMD, the module-only `@JsModule` is rejected and the
  only escape is JS-only `@JsNonModule`, which the shared `webTest` source set
  cannot reference; both npm packages are ESM-only and `harfbuzzjs` uses a
  top-level await. The setting is per-module, so `kge-core`/`kge-benchmark` are
  unaffected.
- **Web externals split into two files.** Two different `@file:JsModule`
  packages cannot coexist in one file; the shared `webTest` test itself stays a
  single file (one file per module, `: JsAny` supertypes for both js and
  wasmJs).
- **`kotlinx-coroutines-core` in `webTest`** for `Promise.await()` on the
  FreeType init.
- **Webpack config required.** The Emscripten glue references Node builtins
  inside its `ENVIRONMENT_IS_NODE` branch and `harfbuzzjs` has a top-level
  await; `webpack.config.d/harfbuzz-freetype.js` sets `resolve.fallback`
  (`module`/`fs`/`path` → `false`) and `experiments.topLevelAwait = true`. The
  micro-plan's UNVERIFIED risk (wasmJs webpack merge / wasm URL under Karma) did
  not materialize: both web targets pass.
- **Round-1 review fixes:** the `jvm()` target now sets `jvmTarget = JVM_11`
  (major 55, matching the core's bytecode target — the reproduced gap was the
  open Important finding); the FreeType smoke no longer calls `FT_Done_FreeType`
  on a slot that may be unset when init fails; the inert `crypto` fallback was
  dropped; the two new catalog aliases carry `version.ref = "lwjgl"` like their
  siblings.

### Gate and review

`./gradlew build --rerun-tasks` green (jvm + browser js/wasmJs of the new
module + ktlint + assemble/metadata). Round 1 two-axis review: Standards
FAIL (one Important — missing `jvmTarget 11`; three Minor), Spec PASS (three
Minor). Fixes applied, gate re-run green, re-review PASS on the fixed tree. The
owner Hunk review then asked to de-duplicate the native classifier (now the
shared `buildSrc` `lwjglNativesClassifier()`, consumed by `kge-core` too) and to
strip the build-file comments; gate re-run green and the change re-reviewed
PASS (reports under `.opencode/reviews/`).
