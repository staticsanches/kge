# Bundled fonts — data module and resource converter: touch-point

**Date:** 2026-09-17. Touch-point for the bundled-fonts round (`kge-font-…`),
which lands **before** round C of the text work (option A). Status: **decided
(owner) 2026-09-17**; the micro-plan is written just-in-time on top of this.

Context: `docs/plans/2026-09-17-font-bundle-findings.md` (font choice, licenses,
sizes, delivery spike — do not re-research) and
`docs/plans/2026-09-17-resource-packaging-mechanism.md` (delivery families,
module boundary, zero-copy, converter, license layers).

## Real consumers

- **Round C of the text work** — the module is its `commonTest` shaping fixture,
  instead of a TTF committed in the text module (option A).
- **An application** that wants a default bundled font without sourcing one: it
  takes the module as an opt-in dependency and hands the bytes to the text
  module's loader.

## Established (decided or verified)

- **Fonts: Roboto + Roboto Mono, variable 3.016, OFL-1.1**, no Reserved Font Name
  (subsetting and modification allowed without renaming). One variable file per
  family; the variable retains the hinting tables the static 3.016 dropped
  (findings §1). Payload 673 KB raw / 896 KB base64.
- **Delivery: generated base64 embedded in the artifact**, chunked at 32768
  characters — the only target-agnostic mechanism with no consumer-side plugin
  (mechanism §3). The published-consumer spike is moot.
- **Off-heap:** `Font` allocates through `BufferService`; on JVM HarfBuzz
  references the direct buffer without copying (`hb_blob_create` +
  `HB_MEMORY_MODE_READONLY`), on web `harfbuzzjs` copies into wasm memory
  (mechanism §3.2).
- **`buildSrc` already exists**, so the converter goes there with no new build
  structure; it must stay KGP-free (mechanism §3.4).
- **License:** OFL-1.1 requires the license text and copyright notice to
  accompany the font. The families are separate upstream downloads, so each ships
  its own `OFL.txt`; both are committed and both texts embedded (mechanism §3.5).
- **No kernel module**: the data module has no engine dependency, so the
  `resource`/`buffer` contracts are not needed outside `kge-core` (mechanism
  §3.2).

## Touch-point decisions (owner)

1. **One data-only module** (working name `kge-font-roboto`), KMP
   jvm/js/wasmJs, holding both families. It does not depend on `kge-text-ttf` or
   `kge-core`; its only dependency is `kotlinx-collections-immutable` as
   `implementation`, because the generated accessors return `persistentListOf`.
2. **Public surface: chunked base64 only** — `List<String>` per entry, plus each
   family's license text as a `String`. No `ByteArray` and no `ByteBuffer`:
   materialisation belongs to the consumer, which is exactly what lets the JVM
   path avoid the copy.
3. **Converter: a `buildSrc` task plus a registration helper** — deterministic
   (sorted walk, no absolute paths or timestamps), cacheable, emitting a
   provenance header and taking an optional `licenseFile(…)`. It stays out of
   image decoding and out of moving files around the module tree.
4. **License in three layers:** the verbatim file in the repository, the text
   embedded in the generated accessor (the only form that reaches the web
   artifacts) and the file under `jvmMain/resources/META-INF/…` plus the POM
   `<licenses>` when publishing lands.
5. **The fixture is shared with round C** (option A): round C's `commonTest`
   depends on this module and its shaping constants are re-measured on the
   shipped font.

## Consequences for round C (supersede its current micro-plan)

- Drop the committed `Roboto-Regular.ttf` (2.137, Apache-2.0), its license and
  the inline test-font generator.
- `Font` gains the base64 entry point and the `BufferService` path; the native
  face retains the buffer wrapper and closes it last on JVM.
- Re-measure the pinned contract (`"AV To Wave 123"` glyph ids, advances, offsets
  and clusters; metrics; kerning) on Roboto 3.016 variable at its default
  instance. The test should also assert the fixture identity, so a later font
  version change fails loudly instead of silently re-pinning advances.

## Out of scope

- Publishing (Central Portal, POM `<licenses>`) — a follow-up.
- Exposing variable axes or named weights as API: round C shapes at the font's
  default instance; weight selection is later and additive.
- A source/stream seam for large or external resources (mechanism §3.3).
- Compressing the embedded payload (`base64(gzip(raw))`, mechanism §3.6): the jar
  already compresses the base64, and A′ is recorded as the escalation path for
  when the web bundle becomes the binding constraint.
- Migrating the golden-image task onto the converter — a different concern
  (ImageIO decoding, structured output).

## Gate

`./gradlew build --rerun-tasks` green (all targets plus the `buildSrc` tests),
then the two-axis review, the decisions-log entry, then one commit for the round.
