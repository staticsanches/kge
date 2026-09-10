## 2026-09-03 — Toolchain refresh: Gradle 9.7.1 + ktlint-gradle 14.2.0

- **Gradle wrapper 9.5.0 → 9.7.1.** Current at check time (services.gradle.org,
  build 2026-08-19). The CLAUDE.md pin updated. Same JDK 21 daemon, config
  cache unchanged; full gate fresh on the new wrapper.
- **ktlint-gradle 12.3.0 → 14.2.0** (latest/release on the Plugin Portal at
  check time). Supersedes item 5's "no bump needed, the plan's pin stays"
  (2026-08-30, Gradle-9.5-era pin — the owner decision today: refresh). The
  resolved engine is **ktlint-cli 1.5.0** (the 12.3.0 default was 1.0.1).
- **New-rule migration:** the 1.5.0 `standard:class-signature` (super type
  must start on a newline) flagged 10 Kotest classes (`FunSpec({ ... })`
  same-line supertypes — SmokeTest + the 5 C4/T2 era + 4 C2 era test suites).
  One `ktlintFormat` pass restyled them (`class X :` / `FunSpec({ ... })`
  with reindented bodies); ktlintCheck green after.
- **The explicit final-newline pin in `.editorconfig` earned its keep**: the
  ktlint 2.x-era engine drift to "final newline not enforced" is exactly the
  scenario the pin protects against in a bump.
- Gate on the new pair: `./gradlew :kge-core:allTests ktlintCheck
  --rerun-tasks` green — jvmTest **58**, jsNodeTest **62**, wasmJsNodeTest
  **57**, zero failures, zero `w:` lines (all targets compiled+ran fresh).

## 2026-09-04 — C3 (native memory, S1): first consumer of the resource contract (touch-point + implementation)

### Touch-point decisions (owner)

The open items of the S1 macro requirement were decided at the C3 touch-point
(this session). Evidence base: the real consumers on `main` and the discarded
buffer attempt (#23; rulings 16–19 as starting candidates).

- **Lean contract — absolute access only.** The surface consumers (PixelMap:
  `getInt((y*w+x)*INT)`; DrawService: `fillInts(index, endX-startX+1, color)`)
  use absolute indexed access + bulk ops; the cursor family has exactly one
  observable consumer on `main` — the renderer batch (QuadInfo/BaseRenderer
  IntBuffers with `position()`/`flip()`, staging flip-then-upload). Cursor
  semantics and typed views (IntBuffer/FloatBuffer) are deferred to the
  renderer concept (C9), whose shape that consumer decides. Growth is
  additive (members + extension functions), nothing ships for a consumer that
  does not exist.
- **Contract shape — `expect abstract class ByteBuffer` mirroring `java.nio`
  (owner redirect mid-session: use the java.nio buffer directly on JVM — no
  wrapper object, no per-access getter chain).** The main-era solution is
  exactly this: `actual typealias ByteBuffer = java.nio.ByteBuffer`
  (typealias-actual). Mechanics verified during the concept (compiler
  diagnostics as the oracle, below), including `-Xjdk-release=11` and the
  expect/actual mirror constraints.
- **KGE-specific ops as commonMain extension functions** (one implementation
  on all targets): `byteAt` (0..255 mask), `putByte` (validates 0..255 →
  IllegalArgumentException), `fillInts(fromByteOffset, count, value)` — count
  in int units, start in byte offsets, range `[from, from + count*4)` — the
  real-consumer form (DrawService's call shape), re-decided over the discarded
  Task-5 `(from, to)` frame; `copyInts(dstFromByteOffset, source,
  sourceFromByteOffset, count)` — memmove-safe same-buffer overlap in both
  directions.
- **No zeroing requirement** (owner: efficiency-first when it does not cost
  usability — an image buffer must define its pixels anyway; main parity):
  initial content is unspecified. JVM `memAlloc` (raw); the web backing store
  is zeroed by platform spec (ArrayBuffer) but that is not the KGE contract.
  The first variant (memCalloc + a "zeroed buffer" contract test) was
  discarded in the same session.
- **Little-endian fixed; no `ByteOrder` seam** (re-affirming 16/19 + the C4
  no-endianness decision): JVM `.order(ByteOrder.LITTLE_ENDIAN)` at
  allocation; web DataView `littleEndian = true` on every int access.
  Exceptions uniform: `IndexOutOfBoundsException` for out-of-range (the JDK's
  `BufferIndexOutOfBoundsException` is a subclass; web explicit check),
  `IllegalArgumentException` for `putByte` out of 0..255 and negative
  `sizeInBytes`.
- **Service — `MemoryAllocatorService`** (T2 facade, `PixelFormatService`
  pattern; package `dev.staticsanches.kge.buffer` beside the contract):
  `allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer>`; `internal expect
  val memoryAllocatorDefault` with per-platform actuals — JVM: LWJGL
  `memAlloc(...).order(LE)` + `memFree` as the wrapper clean action (the
  buffer IS the direct JDK buffer, so a GPU upload later is zero-copy
  by construction); web: `Uint8Array`+`DataView` emulation, no-op clean action
  (GC-managed memory; the wrapper owns lifetime + leak detection).
  Representation for leak reports: `"byte buffer (N bytes)"`. Facade validates
  `sizeInBytes >= 0` before delegating. Duplicate-registration guard not
  re-proven (#28 exemplar's 6th test).
- **Deferred (with the future consumer):** cursor + typed views (C9 renderer),
  buffer-level `duplicate` (C5 sprite — `copyInts` covers the copy), GL/unwrap
  accessors (R5 — nothing to unwrap on JVM), uninitialized-alloc option (no
  consumer; the allocator seam is the exit).

### Verified facts (this session)

- **JDK-21 sealing vs typealias-actual.** The diagnostics chain: class level
  `expect: public abstract expect class` vs `actual: ... typealias` →
  `reason: the modalities are different ('abstract' vs 'sealed')`; members:
  expect final vs JDK `abstract` → `('final' vs 'abstract')` (and the inverse:
  `capacity()` reported **final** by the metadata — the expect member stays
  non-abstract). Resolution: expect members `abstract` except `capacity()`;
  class `abstract`; `-Xjdk-release=11` to read the JDK-11 API surface, where
  the class is a plain abstract class. Verified pairs (Kotlin enforces
  `-Xjdk-release` ≥ `jvmTarget` — the pair moves together): **(11,11) and
  (17,17) compile; (21,21) fails** — sealing arrived in the 21 metadata.
  Runtime is the pinned JDK 21 daemon (Temurin 21.0.11) — tests exercise the
  real 21 classes; compile surface is the 11 API. Future JDK-API need: raise
  the pair by one line; a JDK-21+ compile surface is NOT reachable while the
  typealias stands (wrapper is the alternative, rejected at the touch-point on
  JDK-directness). Recorded in the roadmap risks.
- **Add-time re-verification:** lwjgl-bom 3.4.3 and kotlinx-browser 0.5.0 still
  current (`<release>` on repo1, checked 2026-09-04) — no bump; both were
  removed with the #23 discard and re-added now.
- **Test-hygiene finding (methodology):** the first `ByteBufferTest` left 4
  allocated wrappers unclosed → the C2 best-effort GC test
  (`LeakDetectionJvmTest`) in the same JVM saw 5 leak reports in its poll
  (`expected:<1> but was:<5>` — the 4 buffer wrappers + its own). Resource
  tests must close every wrapper they allocate except the intentional leak,
  which gets claimed via the deterministic seam. The net caught a defect in
  the test, not in the (correct) leak detector.
- **memmove direction (root-cause note):** the initial default `copyInts` had
  the overlap condition inverted (`` `src > dst` `` instead of `dst > src` for
  the backward pass), caught by the exact two-direction tests; the correct
  rule matches the discarded `copyIntsOverlapping` (`dstFrom <= srcFrom` →
  forward, else backward). The common default implementation is shared across
  targets; per-platform native bulk overrides remain for the consumer that
  proves the need (C6 hot loop).

### Owner Hunk review round (2026-09-04 — three comments, fixed in the same changeset)

- **KDocs succinct, external-consumer value only** (the #24 standing rule,
  re-applied): the ByteBuffer class KDoc dropped its project-decision phrasing
  ("the engine name IS the platform type", "the only consumers of cursor
  operations are the renderer batch paths, decided when they land", the
  web-zeroed non-contract note) and the member docs are terse; the service KDoc
  dropped the test-flavored example (kept "an alternate backend"). It also
  caught a stale inconsistency the discarded variant left behind: the
  `allocate` KDoc still said "zeroed" — now "unspecified content".
- **No KDoc on self-evident declarations** — the `internal expect val
  memoryAllocatorDefault` doc removed.
- **Favor private, close to use** — `WebByteBuffer` moved into
  `MemoryAllocatorWeb.kt` as a `private` class (its only instantiation site),
  `checkIndex` private there; the actual mirror exposes only a `protected`
  view. Removed: `internal val view`, `internal fun checkIndex`.

### Close review pass (round 1, two-axis)

Two-axis review of the tree (one lean agent per the owner's rule; verdict +
evidence in `.claude/kge/reviews/c3-round1-twoaxis.md`, tree fingerprint
`8c2d1852…`): **Spec CLEAN** on the touch-point items (verified literally —
including a jshell probe on Temurin 21.0.11 confirming the JDK's out-of-range
throws are `IndexOutOfBoundsException` subclasses and the web bound
equivalence; grep sweeps: zero hits for deferred names/ops, zero log
references); **Standards CLEAN** except 2 doc-only findings (the item-8 KDoc
rule): `MemoryAllocatorJvm`'s KDoc used the banned "IS the platform type"
phrasing + a forward-looking engine-internal example ("future GPU upload"),
and the public web actual's KDoc linked the private `WebByteBuffer` impl +
carried internal rationale (the #29 T3 pattern). Fixed in the same changeset —
both KDocs reworded to external facts. Fix-delta verified: full gate re-run
fresh — BUILD SUCCESSFUL, counts unchanged (the fixes are doc-only).

### Gate

`./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` — fresh run, XML
counts: jvmTest **80**, jsNodeTest **84** (js adds kotest
discovery/context-container overhead), wasmJsNodeTest **79** (the jvm-only C2
GC test excluded), **0 failures**. Baseline vs #30 (58/62/57): **+22 per
target** — `ByteBufferTest` 18 + `MemoryAllocatorServiceTest` 4. ktlint: one
format pass (class-signature wrapping) and the `standard:filename` rename of
the web actual file (`ByteBufferWeb.kt` → `ByteBuffer.kt`; no facade clash —
no top-level declarations there). One commit for the concept (#24
single-commit decision).

## 2026-09-04 — Build warning cleanup: the KT-61573 flag

- **Observation:** the post-C3 full gate emitted 8 `w:` lines (the 09-03
  toolchain gate was zero — C3 introduced them): all of them the KT-61573
  Beta warning for expect/actual classes, pointing at the `ByteBuffer` expect
  (`commonMain/ByteBuffer.kt:11`), its `webMain` actual (`ByteBuffer.kt:13`)
  and the JVM `actual typealias ByteBuffer` (`MemoryAllocatorJvm.kt:9`) — each
  fires per compilation (metadata, js, wasmJs, web metadata, jvm).
- **Fix:** `-Xexpect-actual-classes` in the `kge-core` project-wide
  `compilerOptions` — the compiler's own recommended suppression (KT-61573).
  Standing, on purpose: `ByteBuffer` is deliberately expect/actual (JVM NIO
  typealias vs web TypedArray emulation), so this is not a temporary hack.
- **Node `DEP0169` left as-is — verified third-party:** the two remaining
  warning lines are `url.parse()` deprecations from the node processes inside
  `:kotlinNpmInstall` / `:kotlinWasmNpmInstall`. In KGP 2.4.10
  `KotlinNpmInstallTask` is a `DefaultTask` with no exec/env surface (javap of
  the plugin jar: not an `Exec`; `NodeJsRootExtension` and
  `PackageManagerEnvironment` expose no env knob) — no supported hook to pass
  node CLI flags into that spawn; suppression only via daemon-level
  `NODE_OPTIONS`, which the gate command will not carry. Yarn classic
  internals, not engine code; the lines appear only when the install tasks
  execute (every forced gate run).
- **Gate:** fresh `./gradlew build ktlintCheck --rerun-tasks` — BUILD
  SUCCESSFUL, `w:` grep = 0 matches; the two `DEP0169` lines remain, expected.

