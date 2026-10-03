// KGP's default headless launcher starts Chrome with no software-GL opt-in, so
// `canvas.getContext("webgl2")` returns null and the recording GL service
// cannot fabricate its real WebGL2 DOM handles. Opt into SwiftShader so the
// decorator suite gets a context, keeping the no-sandbox flag.
config.customLaunchers = config.customLaunchers || {}
config.customLaunchers.ChromeHeadlessWebGL = {
  base: 'ChromeHeadless',
  flags: ['--no-sandbox', '--enable-unsafe-swiftshader']
}
config.browsers = ['ChromeHeadlessWebGL']
