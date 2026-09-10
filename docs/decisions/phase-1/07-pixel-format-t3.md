## 2026-09-02 — T3 (pixel display formats): first real T2 consumer (touch-point + implementation)

### 29. T3 (pixel display formats) — touch-point decisions + close facts

The open item of the T3 macro requirement (the service shape and default
representation) was decided at the touch-point (this session):

- **Service name — `PixelFormatService`** (owner): the facade-contract
  convention of the exemplar (`TranslatorService`), applied to the first real
  engine service. The capability is "render a `Pixel` as a display string":
  `fun format(pixel: Pixel): String`. Package `dev.staticsanches.kge.image`,
  beside `Pixel` — no new subpackage for one type.
- **Default — `HexPixelFormat`** (engine-fixed): the same uppercase
  `#RRGGBBAA` (8 digits) the C4 `Pixel.toString()` shipped, **moved** from
  `Pixel`'s companion into the service default (private object, single
  instance — identity by construction per #28: `PixelFormatService.original`
  IS the un-overridden default; file-private is the minimum — only the
  companion uses it, and the public KDoc does not reference the shell
  implementation). `Pixel.toString()` now delegates through the
  facade (`PixelFormatService.format(this)`) — the C4 signature is untouched;
  the default output is bit-identical, so the existing `PixelTest` cases pass
  on both sides of the move (description renamed: "fixed" → "default
  uppercase hex").
- **RGBA representation — test-local only** (owner): the proof's override is a
  private test object (`RgbaPixelFormat`, `rgba(r, g, b, a)` form); nothing
  beyond the HEX default ships. The macro's "hex/rgba" delimits the capability
  domain, not a shipped format catalog (minimal form: no consumer asks for
  RGBA output).
- **No expect/actual** in this service: the HEX default is language-agnostic —
  `HexFormat`/`toHexString` are stdlib **common** (already used by C4 in
  `commonMain`). The #28 "platform defaults via `internal expect`/`actual`"
  line is for platform-backend services (S1/R4 style), not T3.
- **Duplicate-registration proof not repeated**: the register guard is proven
  by `KGEOverridableExtensionTest` (#28, 6th test); T3's suite proves the real
  service's contract instead.
- KDoc facts-only rule (#24): no references to the decisions log in code docs.

Implementation per the micro-plan, one red→green cycle per feature
(`jvmTest` per cycle; full gate at the end):

- **F1** service + default HEX — red (compile: type missing), green.
- **F2** `Pixel.toString()` delegates — red (assertion: still the C4 fixed
  formatter), green (delegation + `HexFormat` moved into `HexPixelFormat`).
- **F3–F5** decorator-on-`original`, last-declared-wins, `resetAll` — guards,
  passed on first run by design (their subjects were proven by the earlier
  cycles and by #28).
- ktlint caught in the gate, fixed: `max-line-length` on the Proxy supertype
  listing (wrapped like `TranslatorService`'s) and `function-signature` —
  body expression fits the signature line (single-line).
- **Teardown invariance: none needed in `PixelTest`** — the `afterTest`
  `resetAll()` pattern lives in `PixelFormatServiceTest` only; every other
  suite sees the engine default.
- Semantics note: `override` is process-wide by design (#28); with an override
  active, **every** `Pixel.toString()` in the process changes — the capability
  is observable in logs/tests, which is the point; `resetAll` on destroy
  restores the engine default. Test suites that override must reset (the
  suite-level `afterTest` covers it).

Gate: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green (fresh
run per item 15). Counts from the XML reports: jvmTest 41
(`PixelFormatServiceTest` 5, 0 failures), wasmJsNodeTest 41 (same),
jsNodeTest 46 (the usual kotest discovery + context-container overhead:
41 + 5). Baseline shift vs the #28 gate (36/41/36): exactly +5 per target —
the new T3 suite; `SmokeTest` 1/0/0 on every target.

### Close review pass (2026-09-02)

Owner review round(s) in Hunk: one comment — the engine default should be
private and not referenced from the public interface KDoc. Fixed in the same
changeset (`HexPixelFormat` is `private`, the `[HexPixelFormat]` reference
removed from the interface KDoc; wording above adjusted). Gate re-run after
the fix (green, counts unchanged).

Two-axis review of the tree (round 1): **Standards CLEAN · Spec CLEAN** — no
defects, no fix delta (adversarial verify pass vacuous); evidence + verdict in
`.claude/kge/reviews/t3-round1-twoaxis.md`. Three nits, no action: the KDoc
"e.g. a debug build rendering rgba(...)" is a hypothetical illustration;
`RgbaPixelFormat` is declared after the spec class (cosmetic — the exemplar
declares the service first); `Pixel.toString()` pays one atomic load per call
(by design, #28 per-call resolution).

Not committed: the owner holds the commit (their review completes the loop;
push is theirs by standing rule).

