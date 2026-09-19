# Bundled fonts for KGE — research findings (font choice + delivery format)

**Date:** 2026-09-17. **Status:** research/spike complete; **decisions open**
(see "Open decisions"). Nothing was committed; the spike was removed.

Goal: ship a few fonts **inside KGE** so consumers do not have to source them,
as an **opt-in module** they may import or not. This is *not* the round C test
asset — round C's test font is a separate concern (see "Round C" below).

Round C context: `docs/plans/2026-09-17-kge-text-ttf-round-c-microplan.md`.

## 1. Font choice

### Popularity (Google Fonts metadata, 2026-09-17)

1,946 families; 51 monospace; **only 12 have both a proportional and a
monospace variant** — the requirement from the owner. Ranked (lower = more
popular):

| # | Pair | prop. rank | mono rank |
|---|---|---|---|
| 1 | **Roboto / Roboto Mono** | **#2** | **#17** |
| 2 | Google Sans / Google Sans Code | #4 | #262 |
| 3 | Ubuntu / Ubuntu Mono | #31 | #408 |
| 4 | Share Tech / Share Tech Mono | #64 | #307 |
| 5 | Overpass / Overpass Mono | #113 | #524 |
| 6 | Geist / Geist Mono | #114 | #159 |
| 7 | Oxygen / Oxygen Mono | #131 | #784 |
| 8 | Syne / Syne Mono | #189 | #804 |
| 9 | Chivo / Chivo Mono | #242 | #615 |
| 10 | M PLUS 1 / M PLUS 1 Code | #357 | #1180 |
| 11 | Cutive / Cutive Mono | #399 | #956 |
| 12 | B612 / B612 Mono | #860 | #919 |

**Roboto wins by a wide margin**: prop #2, mono #17 — the runner-up's mono is
#262. `isBrandFont: true` on Roboto is **not** a licensing restriction (it is
Google's own identity font); the files live under `ofl/` and are downloadable.

### Licenses (measured from each font's embedded `name` table and its OFL header)

- **No MIT family has both variants.** MIT repos are either mono-only
  (Departure Mono, Cozette, Commit Mono) or proportional-only (Fifteen,
  `burodepeper/fifteen`). All 12 pairs above are OFL-1.1.
- **License trap found:** `ark-pixel-font` advertises MIT, but that covers the
  **build program**; the **fonts are OFL-1.1** (the ZIP ships `OFL.txt`, and the
  README states it in Chinese). Same pattern in Hack (MIT code, Bitstream Vera
  font). Choosing on the repo badge alone would pick the wrong license.
- **Roboto changed license across versions:** the older **2.137 (2017)** is
  **Apache-2.0**; the current **3.016 (2026)** is **OFL-1.1**. Both Roboto and
  Roboto Mono 3.016 are **OFL-1.1 with no Reserved Font Name** — so they may be
  bundled, redistributed, **and subset/modified without renaming**.
- **OFL is compatible with an MIT project** (this was the owner's question).
  Per the official FAQ §1.3: *"Does this mean my program also has to be Free/
  Libre and Open Source Software? **No.** Only the portions based on the Font
  Software are required to be released under the OFL."* Obligations are
  attribution-shaped, not copyleft-on-the-project: ship the `OFL.txt` + the
  copyright notice, do not sell the font by itself. **Only a GPL-without-font-
  exception font would actually be incompatible.**
- Reserved Font Name (RFN) only bites on **modification** (OFL clause 3) — i.e.
  if the font is later **subset**, RFN families must be renamed. Roboto has no
  RFN. Share Tech (`'Share'`), Cutive, IBM Plex (`"Plex"`), Hack do.

### Variable fonts — verified usable

Tested end to end on both backends (no assumption left):

| Check | Result |
|---|---|
| FreeType **wasm** exposes variation API | ✅ 19 `FT_*Var*` / `MM_*` / `Named_Instance` exports |
| FreeType **JVM** (LWJGL) | ✅ `FT_Get_MM_Var`, `FT_Set_Var_Design_Coordinates` |
| HarfBuzz **web** axis control | ✅ `setVariations([Variation("wght", 700)])` changes advances |
| HarfBuzz **JVM** axis control | ✅ `hb_font_set_variations` |
| Rasterization JVM × web | ✅ **byte-identical** |
| Axes | Roboto `wght 100–900` (def 400), `wdth 75–100`; Mono `wght 100–700` |

Two non-obvious findings:

1. **Variable at its default (400) ≠ static 3.016 Regular rasterization.**
   Cause is **hinting**, not the axes: the static 3.016 dropped its hinting
   tables (`cvt `, `fpgm`, `prep`, `gasp`), while the variable **retains** them.
   With hinting off the two converge (ink 9563 vs 9425). So the variable is the
   only one of the two that hints — relevant for small-size pixel-art text.
2. **Shaping matches** between variable-default and static 3.016 exactly; the
   older 2.137 differs slightly (e.g. advance `576` vs `575` on `1`,`2`,`3`).

### Sizes

| File | raw | base64 | gzip | base64+gzip |
|---|---|---|---|---|
| Roboto var `[wght,wdth]` | 488,584 | 651,448 | 280,238 | 330,706 |
| Roboto Mono var `[wght]` | 183,700 | 244,936 | 128,803 | 144,923 |
| Roboto 3.016 static | 355,956 | 474,608 | 199,750 | 251,653 |
| Roboto 2.137 static | 168,260 | 224,348 | 88,052 | 106,131 |
| Roboto Mono static (2.137) | 87,236 | 116,316 | 53,588 | 62,880 |

Variable pair total: **673 KB raw / 896 KB as base64**. One variable file covers
all weights; static would need one file per weight.

## 2. Delivery format — spike results

Throwaway spike (gitignored `.tmp/`, removed afterwards): a temporary module
with the real `gradlew`, fonts delivered both ways, the same test asserting a
**full-byte fingerprint** on jvm + js + wasmJs.

### Approach A — generated base64 in Kotlin ✅ all three targets

Same 4 tests on each target, **0 failures**, identical fingerprint
(`488584:7452393035407880832`, `183700:-6972989262200993946`), including bytes
at the 32768-char chunk seams:

| Target | Result |
|---|---|
| JVM | 4 tests, 0 failures |
| JS (ChromeHeadless) | 4 tests, 0 failures |
| wasmJs (ChromeHeadless) | 4 tests, 0 failures |

Cost: 673 KB of fonts → **896 KB** of Kotlin source; the JS test artifact
(`module.js`) grew to **915,244 B**; a clean JVM test compile took ~5 s.

### Approach B — per-source-set resources ⚠️ JVM only

| Target | Result |
|---|---|
| **JVM** `jvmMain/resources` | ✅ works; the jar **compresses**: 488,584 → **280,930 B** (57.5%) |
| **JS / wasmJs** `webMain/resources` | ❌ emitted to `processedResources`, **not served**: `fetch(...)` → **404** |

The web side fails because the Karma webpack does not copy `processedResources`
into the served bundle. Five candidate paths were probed; none resolved.
**This is unverified for a *published* module** — a consumer's own bundler might
serve the asset differently; that path was not tested (see Open decisions).

### Cost comparison (Roboto, 488,584 B raw)

| Format | Bytes | vs raw |
|---|---|---|
| raw | 488,584 | — |
| base64 in Kotlin source | 651,448 | +33% |
| base64 + gzip (web delivery) | 330,706 | −32% |
| **jar entry (approach B)** | **280,930** | **−42%** |

### Methodology note (worth keeping)

The first web probe **silently passed while the resource was 404**: `fetch`
*resolves* with `ok=false` instead of rejecting. It only surfaced when the test
asserted the real `byteLength`. Any future web-asset test must assert content,
not mere resolution — the same class of phantom green the gate rule exists for.

## 3. Open decisions (owner)

**Status 2026-09-17:** all four items are closed — 3 and 4 by the mechanism
findings, 1 and 2 by the owner (§6 below).

1. **Font:** Roboto / Roboto Mono (recommended; popular and OFL with no RFN).
2. **Version:** variable `[wght,wdth]` + `[wght]` (one file per family, hinting,
   weights as a parameter) vs static 3.016 vs static 2.137 (Apache-2.0, smallest).
3. **Delivery format:**
   - **(a) hybrid** — JVM `resources` (smaller, 281 KB) + web generated base64
     (the only form proven to reach the browser); costs one `expect/actual`.
   - **(b) base64 only** — one format, no `expect/actual`, works on all three
     today; ~90 KB larger jar and slower compile.
   - **(c) a second spike** simulating a real consumer that imports the module,
     to see whether the web asset path works once published.
4. **Keep the test font and the shipped font separate.** Round C's test asset is
   a committed TTF for shaping assertions; this module is consumer-facing data.
   They need not be the same version.

## 4. Agreed so far (owner)

- **Module is data-only**: ships the font bytes + `OFL.txt` + a typed accessor;
  it must **not** depend on `kge-text-ttf` (so the bitmap path or a future atlas
  can reuse it). — decided 2026-09-17.
- **Implementation order reversed 2026-09-17 (option A):** the module and the
  `buildSrc` converter land **before** round C, which then takes the module as a
  `commonTest` dependency instead of committing its own TTF. The module is still
  its own round, so the two concepts do not share a commit.

## 5. Resume here

1. Owner picks the remaining open decisions (1 family, 2 version) — the license
   file and the round C shaping contract both follow from them.
2. The mechanism is settled
   (`docs/plans/2026-09-17-resource-packaging-mechanism.md`): generated base64
   bytes (family A), a base64-only data module, a `buildSrc` converter and the
   license in three layers. The published-consumer spike (c) is moot — embedded
   bytes have no asset to serve.
3. Write the touch-point/micro-plan for the fonts module + converter first, then
   revise round C's micro-plan to take the module as its test fixture.

**Repo state at hand-off:** working tree clean (`git status` empty apart from
the round C micro-plan as untracked); all spike files removed; `.tmp/` deleted.

**Environment note:** `./gradlew` needs write access to `~/.gradle` (locks,
native services) which is **outside** the session workspace; the sandbox blocks
it, so Gradle runs require a one-shot `danger-full-access` escalation.

## 6. Revisions — 2026-09-17 (mechanism findings)

Supersedes parts of §3–§5; the mechanism itself is recorded in
`docs/plans/2026-09-17-resource-packaging-mechanism.md`.

- **Font (was open items 1–2): Roboto + Roboto Mono, variable 3.016,
  OFL-1.1** (no Reserved Font Name). One variable file per family, weights and
  width as axes; the variable retains the hinting tables the static 3.016
  dropped. The families are separate upstream downloads, so each ships its own
  `OFL.txt`; both are committed and both texts embedded (verify each file's
  copyright line at commit time).
- **Delivery format (was open item 3): generated base64 embedded in the artifact
  ("family A").** It is the only mechanism target-agnostic by construction and
  needs no consumer-side plugin; the hybrid (b) buys ~50 KB per font on JVM, and
  the consumer-path spike (c) is moot because there is no asset to serve. The
  data module exposes the payload as **chunked base64 only** (32768 chars,
  stdlib) — not `ByteArray` — declared `List<String>` and backed by
  `persistentListOf` (`kotlinx-collections-immutable` as `implementation` only).
- **Test fixture (was open item 4): shared, not separate** (option A). Round C
  takes the data module as a `commonTest` dependency, removing the committed TTF,
  its license and the inline test-font generator. Consequence: the round C
  shaping constants must be re-measured on the font that ships, since the
  recorded ones come from Roboto 2.137 while the shipped candidate is
  3.016/variable.
- **Off-heap and cleanup:** `Font` allocates the font bytes through
  `BufferService`; on JVM HarfBuzz references that direct buffer without copying
  (`hb_blob_create` + `HB_MEMORY_MODE_READONLY`), so the retained bytes are an
  engine-owned, leak-detected wrapper; on web `harfbuzzjs` copies into wasm
  memory and the engine buffer is transient.
- **License:** version-specific, in three layers — the verbatim file in the
  repository, the text embedded in the generated accessor (the only form that
  reaches the web artifacts) and the file in `jvmMain/resources/META-INF/…` plus
  the POM `<licenses>` when publishing lands.
- **Converter:** a `buildSrc` task plus registration helper, KGP-free,
  deterministic and cacheable; `licenseFile(…)` optional.
