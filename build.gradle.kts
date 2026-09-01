// Plugin aliases live in gradle/libs.versions.toml and are applied per-module.
// Do NOT declare plugins here with `apply false` (see docs/decisions/phase-1.md):
// root-declared plugins load in the root classloader scope, which breaks
// cross-plugin class visibility for plugins with compile-only KGP references.

// Serialize the JS and wasm yarn installs: they share Yarn's global instance mutex.
// On Windows, a concurrent zero-dependency wasm install can finish without writing
// build/wasm/yarn.lock, and :kotlinWasmStoreYarnLock then aborts on the missing
// input. Ordering removes the contention (see docs/decisions/phase-1.md #25).
tasks.matching { it.name == "kotlinWasmNpmInstall" }.configureEach {
    dependsOn(tasks.matching { it.name == "kotlinNpmInstall" })
}
