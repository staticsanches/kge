// Karma signals "all files loaded" while this bundle still awaits its wasm, so
// hold `start` until mocha holds a suite; the deadline lets the build fail loud.
(function () {
  var karma = window.__karma__
  var start = karma.start
  var started = false
  var deadline = Date.now() + 10000

  function ready() {
    return window.mocha && window.mocha.suite && window.mocha.suite.total() > 0
  }

  function begin(args) {
    if (started) return
    started = true
    start.apply(karma, args)
  }

  karma.start = function () {
    var args = arguments
    if (ready() || Date.now() >= deadline) {
      begin(args)
      return
    }
    setTimeout(function poll() {
      if (ready() || Date.now() >= deadline) begin(args)
      else setTimeout(poll, 10)
    }, 10)
  }
})()
