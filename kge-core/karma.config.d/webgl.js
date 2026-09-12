// KGP's default `ChromeHeadless` launcher starts Chrome with `--disable-gpu`
// and no software-GL opt-in, so `canvas.getContext("webgl2")` returns null in
// the CI headless runs. Base a launcher on it and opt into SwiftShader so the
// GL smoke test (and, later, the C9 renderer tests) get a real WebGL2 context.
// `--no-sandbox` is required on the Ubuntu 24.04 runner, where AppArmor blocks
// the unprivileged user namespaces Chrome's sandbox needs.
config.customLaunchers = config.customLaunchers || {}
config.customLaunchers.ChromeHeadlessWebGL = {
  base: 'ChromeHeadless',
  flags: ['--no-sandbox', '--enable-unsafe-swiftshader']
}
config.browsers = ['ChromeHeadlessWebGL']
