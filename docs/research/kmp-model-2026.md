# Kotlin Multiplatform model — state as of August 2026 and what changed since Kotlin 2.1.20

Research conducted against primary sources (kotlinlang.org, JetBrains Blog, official repos, official Koin docs) on 2026-08-30. Items marked **[verify]** could not be fully confirmed on a primary source and need a check at implementation time (scaffolding/testing).

## 1. Kotlin versions: what exists today

- Kotlin **2.3.0** (2025-12-16), 2.3.10 (Feb 2026), 2.3.20 (2026-03-16 — C and JS/TS interop focus).
- Kotlin **2.4.0 (2026-06-03)** — a platform/evolution release; 2.4.0-RC2 on 2026-05-28; **2.4.20-Beta1** on 2026-06-24.
- Relevant to KGE: **UUID API is stable in the common stdlib** (the project currently opts in to `ExperimentalUuidApi` — that opt-in can go away); **Kotlin/Wasm incremental compilation on by default**; experimental WebAssembly Component Model; Kotlin/JS support for exporting value classes; compatible up to Gradle 9.5.0; support for Java 26.
- Migration care: in 2.4, some warnings became errors and annotation use-site defaults changed ([whatsnew24](https://kotlinlang.org/docs/whatsnew24.html), [JetBrains 2.4.0](https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/)).

## 2. The big structural change: new KMP default project structure (May 2026)

- The default is no longer a single `composeApp` module (library + app entry points mixed); it is now a **`shared` module** (pure KMP library) plus **one application module per runnable platform**: `androidApp`, `desktopApp`, `webApp` (iOS keeps `iosApp` Xcode project).
- Drivers: AGP 9.0 no longer allows applying the Android app plugin inside a multiplatform module; single responsibility; easier modularization.
- For a **library + examples** project (KGE's case): the library module (`kge-core`) already *is* the "shared module"; the examples become per-platform app modules (`desktopApp` for JVM, `webApp` for JS/wasmJs). The post does not explicitly cover library-only projects, but the pattern adapts trivially ([JetBrains Blog](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/)).
- Not mandatory for pure libraries without Android — it is the default of the wizard/reference projects (kotlinconf-app, KMP-App-Template).

## 3. Source set hierarchy

- The **default hierarchy template** is applied automatically; type-safe accessors exist for template source sets.
- The **"JVM or Android + Web" combination is supported** (our case: jvm + js + wasmJs). Unsupported: several JVM targets, JVM + Android, several JS targets.
- The template has a **shared web group (`js` + `wasmJs` + `wasmWasi`)** — the exact name (`webMain`/`webTest`) comes from the template image **[verify at scaffold]**; the hierarchy docs show the manual path: call `applyDefaultHierarchyTemplate()` and create `by creating { dependsOn(commonMain) }` + `dependsOn` edges from each target set. Watch out: **adding manual `dependsOn()` edges cancels the template** — call `applyDefaultHierarchyTemplate()` explicitly in that case ([kotlinlang hierarchy docs](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html), [advanced structure](https://kotlinlang.org/docs/multiplatform/multiplatform-advanced-project-structure.html)).

## 4. Browser targets: JS and wasmJs coexist

- `js(IR)` remains the model; `wasmJs` is a first-class target and got incremental compilation by default in Kotlin 2.4. Both can coexist in one project (and, with the template web group, share code).

## 5. Publishing to Maven Central

- **OCRRH / Sonatype Nexus has been retired** — new publications go through the **Central Portal**.
- Official path: Gradle's `maven-publish` + the KMP plugin generating per-target publications and the root `kotlinMultiplatform` one (the Kotlin plugin generates the root JAR the Central requires) ([kotlinlang publish docs](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html)).
- De facto shortcut: **vanniktech `gradle-maven-publish-plugin` 0.35.0** (requires Gradle 8.13+; Central Portal integration, GPG signing via `signAllPublications()`; the project currently pins 0.32.0) ([vanniktech README](https://raw.githubusercontent.com/vanniktech/gradle-maven-publish-plugin/main/README.md)).

## 6. Multiplatform testing

- `kotlin.test` + `commonTest` remain the base; `kotlinx-coroutines-test` for the unified loop.
- JS: the project uses Karma + ChromeHeadless; for wasmJs, tests run on Node or a browser **[verify current best practice]**.

## 7. Koin

- Stable line: **4.2.x** (4.2.0 Mar 2026, 4.2.1 Apr 2026, 4.2.2 Jun 2026); `koin-bom` manages versions.
- **Full KMP support including `js` and `wasmJs`** (koin-core is KMP; koin-android etc. remain platform-specific). Since 4.1: WASM-safe UUIDs (uses Kotlin 2.1.20's UUID generator), config blocks reusable across JVM/JS/WASM. `koin-annotations` follows the same timeline ([Koin KMP setup docs](https://openaidoc.org/koin/reference/koin-core/kmp-setup), [Koin release blog](https://blog.kotzilla.io/koin-4.1-is-here)).
- The `KoinComponent` + `by inject()` pattern (lazy, memoized at first resolution) satisfies the requirement of "static access points without per-use lookup cost".

## 8. kotlin-wrappers

- **Not deprecated/archived** (verified on the repo README: active, 13k commits, JS and WASM targets, `kotlin-browser` module with a Maven Central badge) — the `web.gl` API in use today remains valid; **update the version catalog** (currently pinned to `2025.6.3`) to the latest release ([JetBrains/kotlin-wrappers](https://github.com/JetBrains/kotlin-wrappers)).

## 9. Toolchain

- The new default asks for JDK 17+; Kotlin 2.4 supports Java 26. Proposal for KGE: **JDK 17 toolchain + JVM 11 bytecode target** (consumers stay on JVM 11+) **[verify]**. Gradle 8.14.1 → **Gradle 9.x** (Kotlin 2.4 compatible up to 9.5.0).

## Sources

- https://kotlinlang.org/docs/whatsnew23.html (What's new 2.3.0)
- https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/
- https://kotlinlang.org/docs/whatsnew24.html • https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/
- https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/
- https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-advanced-project-structure.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html
- https://raw.githubusercontent.com/vanniktech/gradle-maven-publish-plugin/main/README.md
- https://openaidoc.org/koin/reference/koin-core/kmp-setup • https://blog.kotzilla.io/koin-4.1-is-here
- https://github.com/JetBrains/kotlin-wrappers
