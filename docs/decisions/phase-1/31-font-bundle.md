## 2026-09-19 — Bundled fonts: `kge-font-roboto` data module + `buildSrc` embedder

The bundled-fonts round, which lands **before** round C of the text work (owner,
2026-09-17, option A). Context: `docs/plans/2026-09-17-font-bundle-findings.md`,
`docs/plans/2026-09-17-resource-packaging-mechanism.md`,
`docs/plans/2026-09-17-fonts-module-touchpoint.md` and the micro-plan
`docs/plans/2026-09-19-kge-font-roboto-microplan.md`. It delivers the packaging
mechanism (family A: generated chunked base64) as shared build tooling plus its
first content consumer: a data-only module shipping Roboto and Roboto Mono.

### Shape

- **`buildSrc` `EmbedResourcesTask` + `embedResources(...)`**: deterministic and
  `@CacheableTask` (`@InputDirectory @PathSensitive(RELATIVE)`, `@OutputDirectory`,
  `@Input` package/families, chunk size default 32768); families are emitted
  sorted by accessor name; the fixed accessor file is `EmbeddedFonts.kt`; blank
  or missing inputs and duplicate accessor names fail naming the offender. The
  renderer is a pure function covered by `buildSrc` unit tests
  (`kotlin("test")`, five cases: object shape + constants, literal escaping,
  chunk seam, order independence, duplicate name); the task is a thin I/O shell.
- **`kge-font-roboto`** (jvm/js/wasmJs, browser-only web, no engine dependency,
  `kotlinx-collections-immutable` as `implementation` only): one object per
  family exposing `FAMILY`/`VERSION`/`LICENSE_ID`/`SOURCE`,
  `variableFont: List<String>` (chunked base64 backed by `persistentListOf`) and
  `licenseText`. The public surface is base64-only; materialisation belongs to
  the consumer.
- **Committed assets**: Roboto **3.015** (`Roboto[wdth,wght].ttf`, 488,584 B,
  sha256 `d7598e12…a134`) and Roboto Mono **3.001** (`RobotoMono[wght].ttf`,
  183,700 B, sha256 `66a80e79…c567b2b`), each with its own `OFL.txt`
  (4,394 / 4,395 B), plus `PROVENANCE.md` (upstream URLs, versions, hashes).
  License in three layers: the repository files, the embedded `licenseText`, and
  `jvmMain/resources/META-INF/licenses/<family>/OFL.txt` — the last asserted
  **inside** `jvmJar` by a `check`-wired `verifyJvmLicenseJar`, content compared
  against the committed file, not mere presence.
- **CI**: `./gradlew -p buildSrc build --rerun-tasks --stacktrace` added before
  the root build step.

### Divergences and non-obvious findings (recorded)

- **Version label corrected.** The variable builds are Roboto 3.015 and Roboto
  Mono 3.001 (name tables read 2026-09-19), not "3.016" as the findings doc
  recorded. The byte sizes match the delivery spike exactly, so the spike
  measured these files and only the label was wrong.
- **Explicit family specs, not resource-tree-derived names** (mechanism §3's
  suggestion). Upstream filenames (`Roboto[wdth,wght].ttf`) are not identifiers
  and derive neither version nor source; the explicit spec keeps the public API
  legible and the provenance accurate while the emitted order stays
  deterministic.
- **The `buildSrc` tests need their own gate command.** `buildSrc` is not in the
  main build's task graph, so `./gradlew build` alone cannot cover them; the
  round's gate is `-p buildSrc build` **plus** `build`, and CI runs both. The
  touch-point's single-command gate statement was corrected.
- **Gradle cannot instantiate a `private` task class** (`Class
  Build_gradle.VerifyJarLicenseEntriesTask is private`), so the verifier task is
  script-public like `kge-core`'s `GenerateGoldenImagesTask`, while its provider
  property stays `private val`.
- **FNV-1a 64 in `commonTest`** (`-4_869_763_841_651_235_108` /
  `17_806_645_914_498_892`): common Kotlin has no SHA-256, and the fingerprint is
  what catches a lost chunk or a reordered seam on every target; the sha256 stays
  in the generated header and `PROVENANCE.md`.
- **Plan defects found by the gate.** The micro-plan's one-line
  `class X : FunSpec({` violates this repo's ktlint class-signature rule (every
  `kge-core` spec puts the supertype on a new line), and its
  `JvmLicenseResourcesTest` block was not a standalone file (missing `package`
  and imports). Both were corrected in the implementation.
- **`buildSrc/src/main/kotlin` has no Kotlin DSL implicit imports**: the reified
  `TaskContainer.register<T>` needs an explicit
  `import org.gradle.kotlin.dsl.register`.
- **The converter's license entry is mandatory, and its model is font-shaped**
  (`EmbeddedFamilySpec.font`/`.license`, `renderEmbeddedFonts`), which narrows the
  touchpoint's "optional `licenseFile(…)`" and the mechanism's generic wording.
  Accepted (Spec round 1, Minor) with this rationale: the converter's model has
  one consumer — bundled fonts — so its entry shape is not yet a shared
  abstraction, and generalizing it without a second, non-font consumer follows
  mechanism §3's rule ("extract it to a convention … at the second consumer
  module, not before"); §3.4's "extract now" argument counted two consumers of
  the *task's location* (this module and round C's fixture), which is what this
  round did by putting the task in `buildSrc` instead of inline. Making the
  license optional would also let a font ship without its OFL, and OFL-1.1
  requires the text to accompany the font — a mandatory entry makes that
  omission unrepresentable rather than silently possible. Loosening it later is
  additive.

### Gate and review

`./gradlew -p buildSrc build --rerun-tasks` green (5 `buildSrc` tests) and
`./gradlew build --rerun-tasks` green (224 tasks executed: the three targets, all
modules, ktlint and assemble/metadata), both read from executed results rather
than cached state. Test evidence from `build/test-results/**/*.xml`:
`BundledFontsTest` 5 tests on jvm, js and wasmJs, `JvmLicenseResourcesTest` 1 on
JVM, 0 failures/errors; `ktlintKotlinScriptCheck` green; `unzip -l` confirms both
license entries inside `kge-font-roboto-jvm.jar`.

Two-axis review, round 1, on the first staged tree: **Standards PASS** (0
Critical/Important, 3 Minor — the verifier task's over-wide `@InputDirectory`
fixed as `@InputFiles` over the two `OFL.txt` files it actually reads, with the
`@CacheableTask` half **declined**: Gradle's cacheability resolver returns
`NO_OUTPUTS_DECLARED` and its execution-mode resolver `noOutputs()` for a task
with no declared outputs, so the annotation would be inert and the input
narrowing is a declaration-fidelity fix rather than an up-to-date one; the
unpinned `RobotoMono.LICENSE_ID` assertion added; positional
`EmbeddedFamilySpec` calls switched to named arguments) and **Spec PASS** (0
Critical/Important, 1 Minor — the mandatory license entry accepted above).
Round 2 on the fixed tree: both axes **PASS** (0 Critical/Important), sharing one
docs-only Minor — this entry cited the second-consumer rule to mechanism §3.4
instead of §3 — corrected here. The final tree was re-gated and re-reviewed
before the commit; the axis reports are under
`.opencode/reviews/kge-font-roboto-*.md`.
