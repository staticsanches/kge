// Plugin aliases live in gradle/libs.versions.toml and are applied per-module.
// Do NOT declare plugins here with `apply false`: root-declared plugins load in
// the root classloader scope, which breaks cross-plugin class visibility for
// plugins with compile-only KGP references.
plugins {
    base
}

// buildSrc is a separate build, and Gradle does not expose it as an addressable
// included build, so the root check runs its build nested.
val rootCheckRunner =
    if (System.getProperty("os.name").startsWith("Windows")) {
        "gradlew.bat"
    } else {
        "./tools/gradle"
    }

val buildSrcCheck =
    tasks.register<Exec>("buildSrcCheck") {
        group = "verification"
        description = "Runs buildSrc's own ktlint and tests, which live in a separate build."
        workingDir = rootDir
        commandLine(rootCheckRunner, "-p", "buildSrc", "build")
    }

tasks.named("check") {
    dependsOn(buildSrcCheck)
}

// Tests must execute, never replay: an up-to-date or cached result cannot prove
// the engine ran (decisions log #15). Everything else stays incremental.
subprojects {
    tasks.withType<AbstractTestTask>().configureEach {
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
}

// Root yarn tasks share Yarn's global instance mutex, but the configuration
// cache runs them in parallel (even within a project); on Windows that overlap
// leaves the wasm installs incomplete — :kotlinWasmStoreYarnLock aborts on a
// missing build/wasm/yarn.lock, and :kotlinWasmToolingSetup can leave the
// tooling dir without kotlin-web-helpers, breaking :wasmJsBrowserTest. Order
// them to serialize.
tasks.matching { it.name == "kotlinWasmToolingSetup" }.configureEach {
    dependsOn(tasks.matching { it.name == "kotlinNpmInstall" })
}
tasks.matching { it.name == "kotlinWasmNpmInstall" }.configureEach {
    dependsOn(tasks.matching { it.name == "kotlinWasmToolingSetup" })
}
