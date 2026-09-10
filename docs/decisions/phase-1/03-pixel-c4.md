## 2026-09-01 — C4 (Pixel) closed (touch-point decisions + implementation)

### 24. C4 (Pixel) — touch-point decisions (owner) + close facts

The open items of #22 were decided at the C4 touch-point (this session):

- **No `PixelService`, no endianness seam.** `Pixel` stores the packed value directly —
  `R | G shl 8 | B shl 16 | A shl 24` (memory bytes R, G, B, A). Zero byte conversion on
  any planned target; no `reverseBytes` anywhere. Factual corrections to #22's reasoning
  (verified at the touch-point):
  - wasm linear memory is little-endian **by spec**.
  - JS `TypedArray` byte order is the **host's native order per spec** — little-endian
    only by practice (no BE hosts shipped). #22's "by spec" for JS was wrong.
  - Every LWJGL target (x86_64, x86, arm64) is LE; Java's "big-endian" reputation is API
    convention (`ByteBuffer` default order = `BIG_ENDIAN`, network-order streams, value
    packings such as ImageIO's AARRGGBB int), not host memory layout.
  - `main` itself dropped BE support deliberately: `a60b5ca` (2025-05-03) chose the
    per-endianness service via `ByteOrder.BIG_ENDIAN.isNative`; `61447d6` (2025-05-31)
    removed it and hardcoded a LE default ("KGE only supports, by default, little endian
    systems"). Remaining BE "market" = JVM-on-s390x (LWJGL does not support it) and retro
    consoles (no KMP targets).
- **Representation.** `@JvmInline value class Pixel private constructor(nativeRGBA: Int)`
  — the constructor is PRIVATE (owner review, 2026-09-01): a `Pixel` is created through
  the factories or `Colors`, never from a raw packed value; the little-endian packing is
  an engine invariant and an internal path that needs it gets a factory, not a leak. The
  packed value is PUBLIC, named `nativeRGBA` (little-endian bytes R,G,B,A) with
  `rgba: UInt` exposing the canonical `0xRRGGBBAA` value convention — the `main` naming
  split is carried over deliberately: the two conventions are different things and must
  be distinguishable. No `Pixel()` default constructor (owner review): explicitness via
  `Colors` or a factory — the olc-style opaque-black default was dropped. Factories:
  `Pixel.rgba(r, g, b, a = 0xFF)` (saturating) and `Pixel.rgba(0xRRGGBBAAu)` (value
  convention, alpha in the low byte).
- **Arithmetic — olc clamp semantics** (decided: stay close to the reference engine):
  per-channel saturation to [0, 255] on plus/minus/times/div; minus clamps at 0 (main
  masked with `& 0xFF` — 10-20 wraps to 246; rejected because saturation is the reference
  behavior); times/div convert via Float→Int (truncation toward zero for finite
  fractions; NaN → 0; +Inf saturates to Int.MAX); `inv` inverts RGB only;
  `lerp = this * (1-t) + end * t` (truncating); alpha always kept from the receiver.
  Domain pinned at the review pass (2026-09-01), per channel: `x / 0f` → 255 for x > 0
  (positive value over zero = +Inf → Int.MAX, then saturate) and → 0 for x == 0
  (0/0 = NaN); `x / NaN` and `x * NaN` → 0; `x * 0f` → 0; negative factor/divisor → 0
  (incl. `-0f` = −Inf; converted value below 0 saturates to 0); exact tests cover every case, including the
  channel split of `rgba(0, 100, 50) / 0 → rgba(0, 255, 255)`.
- **Colors — CSS Color 4, regenerated from the spec, not from main.** Decision: the
  CSS palette is the reference of interest (web parity — canvas/WebGL), the olc palette
  is not; values are regenerated from the specification rather than carried over from
  main. The 148 §6.1 named colors (the spec table is the oracle: fetched, extracted, 18
  anchors verified — `green` #008000, `lime` #00FF00, `gray`/`grey` #808080,
  `rebeccapurple` #663399, …) + `TRANSPARENT` (fully transparent black — the value is
  CSS `transparent`, which the spec treats as a special value, not a named color; the
  constant carries the CSS name — owner rename from the working name `BLANK`,
  second Hunk review round, 2026-09-01).
  Named colors are opaque (`A = 0xFF`); the 9 alias pairs keep both spellings (gray/grey,
  aqua/cyan, magenta/fuchsia, darkgray/darkgrey, dimgray/dimgrey,
  darkslategray/darkslategrey, lightgray/lightgrey, lightslategray/lightslategrey,
  slategray/slategrey).
- **Pixel modes moved to the Raster concept (R1).** No consumer in C4 — on `main` every
  `Mode` consumer was raster (draw slow path + FillRect/FillTriangle/DrawSprite tests).
  `Mode` (Normal/Mask/Alpha/Custom) + the blend resolution math
  (`r = a*p.r + (1-a)*d.r`, floor) are in R1's macro requirement now; no blend formula in
  the kernel before that consumer. Roadmap S2/R1 updated accordingly.
- **Display format → new transversal concept T3 ("pixel display formats"), ordered right
  after C1.** C4 ships `toString()` fixed `#RRGGBBAA` (uppercase, 8 digits); NO
  `Format` abstraction ships in C4 (decided — display formats are T3's job). The
  engine-level choice of representation (main's `defaultPixelFormat` var — process-sticky,
  breaks tests — is the rejected anti-pattern) becomes a service seam per principle 1,
  realized by the T2 mechanism: default HEX; override exercised by the extension-contract
  test. `toString()`'s signature is stable in C4 — T3 re-resolves through the mechanism
  without breaking it.

Close facts: TDD (test → red → implement → green) with the micro-plan in this session's
session plan; full gate green — `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks`
(jvmTest 30, wasmJs node 30, js node 35 — the js figure adds kotest's context containers
(4) and its Kotest discovery suite (1); the executable test set is the same 30 on all
targets — the 6 domain-semantics tests included; 0 failures; `--rerun-tasks` mandatory
per items 9/11/15). The
ktlint function-signature rule wraps only 2+ parameter declarations (item 20) —
single-parameter signatures are single-line (the gate failed once on this; `ktlintFormat`
applied).
One commit for the whole concept instead of green-milestone commits — owner decision
(2026-09-01): one session's work in one commit keeps CI to a single run; a deliberate
deviation from the workflow's "several commits at meaningful green milestones".
Close review pass: two-axis review (Standards + Spec) of the commit — findings fixed in
the same commit: the alias test extended to all 9 pairs, the div/times domain (0/NaN/
negative) pinned by exact tests, doc/impl semantic contradiction resolved. The
`rgba`-property ruling made in that pass was later SUPERSEDED by the owner review below
(`nativeRGBA` is public). Committed docs carry no personal quotes (standing rule since
this close: decisions are recorded by rationale).
**Owner review (Hunk, 2026-09-01 — the human layer of the loop; five comments in the
first round, plus one in the second: `BLANK` renamed `TRANSPARENT` — the value is the
CSS special value `transparent`, so it carries the CSS name, not a KGE one):**
KDocs no longer reference decision logs (such files may disappear or rot — facts only);
the constructor is private (factory-only construction; raw packing stays an engine
invariant); `nativeRGBA` is public again and `rgba: UInt` exposes the canonical value —
the `main` naming split keeps the two conventions distinguishable (for extensibility,
such values must be available); the `Pixel()` opaque-black default was dropped
(explicitness via `Colors`/factory); `Colors` aliases reuse the canonical constant's
reference (`val GREY = GRAY`, `CYAN = AQUA`, `MAGENTA = FUCHSIA`, … all 9 pairs — one
constant per value, aliases point at it, with the hex equality cross-checked by the
generator).
Tooling find (same pass): `data object Colors` produced order-dependent zero-value
failures in full jsNodeTest runs under kotest 6.2.4 — the identical access passed in
isolation, in pairs and with a warm-up access, so the parity net caught it as a JS-only
failure with `expected #RRGGBBFF but was #00000000`; bisecting to `object Colors` fixed
it and the combination (object + aliases) is green on 3 consecutive full runs.

