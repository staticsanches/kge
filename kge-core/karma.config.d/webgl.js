// KGP's default `ChromeHeadless` launcher starts Chrome with `--disable-gpu`
// and no software-GL opt-in, so `canvas.getContext("webgl2")` returns null in
// the CI headless runs. Base a launcher on it and opt into SwiftShader so the
// GL smoke test (and, later, the C9 renderer tests) get a real WebGL2 context.
config.customLaunchers = config.customLaunchers || {}
config.customLaunchers.ChromeHeadlessWebGL = {
  base: 'ChromeHeadless',
  flags: ['--enable-unsafe-swiftshader', '--use-angle=swiftshader']
}
config.browsers = ['ChromeHeadlessWebGL']
