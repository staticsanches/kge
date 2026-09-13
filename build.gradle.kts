// Plugin aliases live in gradle/libs.versions.toml and are applied per-module.
// Do NOT declare plugins here with `apply false`: root-declared plugins load in
// the root classloader scope, which breaks cross-plugin class visibility for
// plugins with compile-only KGP references.

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
