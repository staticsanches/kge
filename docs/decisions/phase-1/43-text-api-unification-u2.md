## 2026-10-04 — Text API unification, round U2: the bundled italic fixtures

Round U2 supplies the fixtures U3 needs before it can build a family. The
touch-point's "TTF/OTF family" section gives each payload exactly one face and
requires roman and italic to load atomically within their family; its contract
item 10 delegates the exact italic artifacts, checksums, versions, source and
license to the micro-plan; and the axis findings (§10) recorded that the italic
artifacts had never been fetched and were left to the micro-plan. Micro-plan:
`docs/plans/2026-10-04-text-api-unification-u2-microplan.md`.

### Shipped

- **The two italic variable payloads**, committed verbatim:
  `fonts/roboto/Roboto-Italic[wdth,wght].ttf` (530,944 B, sha256 `9725a847…7182`)
  and `fonts/roboto-mono/RobotoMono-Italic[wght].ttf` (196,792 B, sha256
  `49ac343b…9ff7`), both sfnt `00010000`, with their `PROVENANCE.md` rows and
  both upstream raw URLs.
- **The embedder generalized from one font per family to a payload list**:
  `EmbeddedFontSpec(member, path)` and `EmbeddedFamilySpec.fonts` in the spec,
  `EmbeddedFont(member, path, bytes, sha256, base64)` and
  `EmbeddedFontFamily.fonts` for the resolved payloads.
- **The generated accessor** gains one member per payload in manifest order
  (`variableFont` roman, `italicFont` italic) and one header-comment line per
  payload, and the generator emits the order-contract KDoc above the first
  payload member.
- **`BundledItalicFontsTest`** pins the new payloads' identity — byte size, sfnt
  magic, FNV-1a 64 and the chunk seam — plus that a family's two payloads decode
  to distinct bytes.

### Decisions

- **Payload order is generated contract, so members are emitted in manifest order
  and never sorted** — unlike families, which stay sorted by accessor name so the
  product is independent of input order. The first member is the family's default
  face, and the KDoc stating it is *generated* rather than hand-written: it is the
  only place that order-sensitive contract reaches a consumer, and the
  touch-point requires the contract in public KDoc.
- **The three new fail-fast rules live in the pure renderer** — an empty payload
  list, a blank member, and a duplicate member *within* a family, each message
  naming the offender — so buildSrc's own suite pins them; the task keeps only
  the filesystem checks. A duplicate *accessor* across families stays a separate
  rule, since it is a property of the file rather than of one family.
- **The roman accessor keeps the name `variableFont` this round.** The rename to
  `romanFont` would touch 96 call sites — 86 in `kge-text-ttf`'s test source
  sets, 2 in the benchmark, 8 in the bundle spec — and U3–U5 rewrite them anyway
  when the family API lands, so a data round does not carry 96 mechanical edits.
  The generator takes member names from the manifest, so this is a manifest
  choice and not a codec limitation.
- **Each roman/italic pair is a single-revision pair.** The committed romans
  re-hash byte-identical to their upstream copies in the same run that fetched
  the italics, so the italics inherit their roman's version (3.015 / 3.001)
  rather than carrying one of their own.
- **The shared test helpers were widened, not duplicated.** `decode` and
  `fnv1a64` drop from file-`private` to no modifier, naming
  `BundledItalicFontsTest` as the consumer; test source sets take `private`
  first and then no modifier, never `internal`.

### Not in this round

No `kge-core` or `kge-text-ttf` change, no dependency change, no golden
reference moves and no rendering change. The `name`-table assertions that the
italic payloads really report an italic subfamily, and both payloads' axis
descriptors, stay U3's, which owns the `fvar`/`name` reader.

### Verification

`tools/gradle build` green on the staged tree (`BUILD SUCCESSFUL in 3m 1s`;
205 executed, 5 from cache, 94 up-to-date): every target's tests, ktlint, the
metadata/kLIB compilation, and `buildSrcCheck`, which nests buildSrc's own
suite. No result XML predates the run, so nothing was a replayed green; there
are no failures or errors anywhere, and the only skips are the three
pre-existing jvm GLFW-window smokes in `kge-core`.

Counts per module and target: `kge-core` 746 jvm / 781 js / 781 wasmJs;
`kge-text-ttf` 107 / 110 / 110; `kge-font-roboto` 10 / 10 / 10, up from 6 / 6 / 6
by exactly the four new `BundledItalicFontsTest` cases on each target;
`kge-test-support` 8 / 9 / 9; `kge-benchmark` 21 jvm / 22 wasmJs; `buildSrc` 27.
Both browser suites report non-zero counts, so no target is a phantom green
(#35).

**Record correction.** The `kge-core` figures do not match the 744 / 779 / 779
carried in entry 42 and in both U1b round-2 review reports, and that figure does
not reproduce: `kge-core` is byte-identical to HEAD across this round (no
`kge-core` path appears in the diff), it has no dynamically generated tests, and
two independent samples of `:kge-core:jvmTest` over it both report 106 classes
and 746 tests. Every other count the U1b brief recorded reproduces exactly
(`kge-text-ttf` 107 / 110 / 110, the benchmark 21 / 22 and `kge-test-support`
8 / 9 / 9), and both r2 reports state the gate was "run by the orchestrator" and
repeat one identical counts string, so the `kge-core` figure was transcribed
from the round brief rather than measured. The committed tree reports 746 / 781
/ 781.

The round closes only when the two review axes pass on this exact staged tree,
and the marker names the report that carries its hash.
