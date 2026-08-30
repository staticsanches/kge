# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

KGE — a Kotlin Multiplatform game engine inspired by olcPixelGameEngine. Two targets:

- **JVM** (`jvmMain`): LWJGL (GLFW + OpenGL)
- **JS** (`jsMain`, IR): WebGL2 via kotlin-wrappers + `kotlinx.coroutines`

Group `dev.staticsanches.kge`, version `0.2.0-SNAPSHOT` (bumped in root `build.gradle.kts`).

## Commands

JDK 11 (jvmToolchain), Kotlin 2.1.20, ktlint 12.3.0. All via `./gradlew`:

| Task | Command |
|---|---|
| Build + tests | `./gradlew build` |
| Lint | `./gradlew ktlintCheck` (auto-fix: `./gradlew ktlintFormat`) |
| JVM tests | `./gradlew :kge-core:jvmTest` |
| JS tests | `./gradlew :kge-core:jsBrowserTest` (Karma + ChromeHeadless — Chrome must be installed) |
| All tests | `./gradlew allTests` |
| Run JS example (dev server, watch) | `./gradlew :kge-example-js:jsBrowserDevelopmentRun --continuous` |
| JS example production bundle | `./gradlew clean :kge-example-js:build -x check` → `kge-example-js/build/dist/js/productionExecutable` (what gh-pages deploys) |

The JVM example has no `application` plugin — run `dev.staticsanches.kge.example.FirstExample` from the IDE.

Publishing: `kge-core` and `kge-natives/*` publish to Maven Central (vanniktech plugin, CENTRAL_PORTAL). The version catalog lives in `gradle/libs.versions.toml`.

## Architecture

### Addon-based engine API

`KotlinGameEngineBase` (`engine/KotlinGameEngineBase.kt`) is an **interface** extending ~15 addon interfaces in `engine/addon/` (DrawAddon, ClearAddon, LayersAddon, ...). Each addon provides default methods that delegate to the `Rasterizer` singleton or to state exposed via `WindowDependentAddon` → `WithKGEState`.

Users mix in **callback addons** (`KeyboardCallbackAddon`, `FileDropCallbackAddon`) — the platform `KotlinGameEngine` (jvmMain/jsmain) checks `this is KeyboardCallbackAddon` and only then registers GLFW key callbacks / DOM event listeners. Adding a public API surface means: new addon interface + `Rasterizer` service + state in `WithKGEState`/`Window`.

### expect/actual platform split

`commonMain` holds interfaces, state, and the rasterizer; `jvmMain` and `jsMain` provide `actual`s (`WindowMainResource`, `KGECleanable`, buffer/GL types, service implementations). Note the API asymmetry: on JS the engine loop and all user callbacks (`onUserCreate`, `onUserUpdate`, `onKeyEvent`...) are **suspend** (`awaitAnimationFrame`), on JVM they are blocking with `glfwPollEvents`. Any new platform functionality must be added to all three source sets.

### KGEExtensibleService — service plugin mechanism

Services (`Renderer`, `BufferWrapperService`, `SpriteService`, `PixelService`, `GLService`, rasterizer *Service singletons) implement `KGEExtensibleService` with a `servicePriority`. `getOptionalWithHigherPriority()` picks the best registered implementation:

- **JVM**: `java.util.ServiceLoader` (register via `META-INF/services`)
- **JS**: manual registration into the companion (a HashMap) via `KGEExtensibleService.register`

`Rasterizer` is a `data object` aggregating all rasterizer services at `Int.MIN_VALUE` priority. `Renderer`'s companion delegates to the highest-priority renderer, falling back to `originalRendererImplementation` (expect/actual). This is how users can replace the renderer.

### Rendering pipeline

User draw calls → `Rasterizer` services write pixels into `Sprite` (a `PixelMap`). Each frame the engine renders layers back-to-front (`layers.reversed()`): each shown layer is drawn as a textured quad; a sprite uploads to GPU only when `layer.update` is set (`Renderer.updateTexture`), gated by `suspendTextureTransfer`. `Decal` = GPU-cached sprite, drawn via `layer.decalInstances` (queued and cleared per frame). Layer 0 always exists and is always shown. A layer's `functionHook` replaces the standard quad path with custom rendering.

### Resource lifecycle & leak detection

Every native/GPU handle is wrapped in `ResourceWrapper<R>` (`resource/`): accessing `.resource` after `close()` throws; `KGELeakDetector` logs an error when a wrapper is GC'd without being closed. Resources are explicitly closed by users (examples use `invokeForAll(decal, sprite) { it.close() }`); `applyClosingIfFailed` / `letClosingIfFailed` auto-close on failure. `KGESensitiveAPI`-annotated members are internal-ish and require opt-in (enabled project-wide in `kge-core/build.gradle.kts`).

### Engine lifecycle

`start(configurator)` → `Configurator` (screen/pixel dimensions, resizable, keepAspectRatio, fullScreen, vSync) → window creation → `onUserCreate()` → loop: `timeState.tick()` → `onUserUpdate()` → render layers → `displayFrame()` → `onUserDestroy()`. The window's `Window` implements `WithKGEState`: `dimensionState` (screen/pixel/window sizes, viewport), `timeState` (fps), `inputState` (keyboard state).

## Modules

- `kge-core` — the engine (KMP: commonMain/jsMain/jvmMain + tests)
- `kge-example-js` / `kge-example-jvm` — runnable demos (the pair demonstrates the JS-vs-JVM API differences)
- `kge-natives/*` — per-OS/arch `java-library` modules that bundle `kge-core` + the matching LWJGL natives classifier (e.g. `natives-macos`); the JVM example depends on the module auto-selected from `os.name`/`os.arch`. `kge-core`'s own jvmTest pulls LWJGL natives directly from Maven Central via the same OS/arch logic.

## Conventions

- ktlint enforced (jlleitschuh plugin, applied to all modules except `kge-natives`); `.editorconfig`: max line length 120, `ktlint_standard_argument-list-wrapping` disabled; Kotlin official code style
- `kge-core` compiles with `-Xexpect-actual-classes` and opt-ins for `ExperimentalStdlibApi`, `ExperimentalUuidApi`, `KGESensitiveAPI` — new source files don't need to repeat the opt-ins
- CI (`build-multiple-os.yaml`, `gh-pages.yaml`) runs only on `workflow_dispatch`: `./gradlew build --stacktrace` on 4 OS images (zulu JDK 11)
