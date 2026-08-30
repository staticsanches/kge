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
