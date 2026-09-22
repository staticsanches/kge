// The js bundle is an async webpack entry, so Karma can start mocha before it
// registers a suite. Serve the shim that holds the run until the suites exist.
config.files = config.files || []
config.files.push({
  pattern: require('path').resolve(
    config.basePath, '../../../..', 'kge-text-ttf', 'karma', 'await-suites.js'
  ),
  included: true,
  served: true,
  watched: false
})
