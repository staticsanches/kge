// The HarfBuzz/FreeType Emscripten glue references Node builtins inside an
// `ENVIRONMENT_IS_NODE` branch; the browser bundle must not try to resolve
// them. `harfbuzzjs`'s ESM entry also uses a top-level await.
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
    module: false,
    fs: false,
    path: false,
});
config.experiments = config.experiments || {};
config.experiments.topLevelAwait = true;
