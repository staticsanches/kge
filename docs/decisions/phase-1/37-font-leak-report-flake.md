## 2026-09-24 — `kge-text-ttf` round D-fix: the leak-report spec read an asynchronous stream

`ubuntu-latest` CI went red on round D's commit (`5a21f18`, run `36073429094`):
`:kge-text-ttf:jvmTest` — `FontLeakReportTest[jvm] > a rasterized font is still
reported as the face` — `java.lang.IllegalArgumentException: List has more than
one element.` at `FontLeakReportTest.kt:68` (`reports.single()`); 44 tests, 1
failed. `macos-latest` and `windows-latest` were green, and the same suite is
green on this machine, so the round-D gate could not have caught it. Design
material: `docs/plans/2026-09-24-font-leak-report-flake-fix.md` (micro-plan).

### Root cause — a composite leak reports every tracked layer, asynchronously

- `KGEResourceCleanableState.onCollected()` claims the state with
  `exchange(null)` and **drops the release action** — a deliberate, pinned rule
  (`KGEResourceCleanableStateTest`: "reports the representation once, never runs
  the action"; same in `ResourceWrapperTest`). A fired collection trigger
  therefore reports the leak and releases nothing.
- `Font` owns `ResourceWrapper<NativeFace>`, whose clean action is the only path
  that closes the payload wrapper (`NativeFace.release()` → `payload.close()`).
  When the trigger fires, that action is discarded, so the payload stays
  registered and unclosed — and later, when the collector reclaims it, **its own
  Cleaner callback reports `byte buffer (font) (uuid: …)` into whichever
  `LeakReporterService` is installed at that instant**, on the Cleaner thread.
  The nested payload is the JVM face's; the web face closes its staging payload
  at load and holds none.
- `FontLeakReportTest` asserts an exact count (`reports.single()`) on that
  process-wide stream. Its first test deliberately leaves a font unclosed, so
  that font's payload keeps a pending report; the spec's third test allocates a
  512×512 atlas chart, and a GC during it delivered the pending report into the
  third test's fresh list. Two entries, `single()` threw. Test 3 strands its own
  payload the same way when it fires the seam.
- **Reproduced deterministically, not inferred:** with the test JVM pinned to
  `-Xmx24m -XX:+UseSerialGC` through a scratch init script, the unmodified
  `HEAD` fails on the same test, line and message; 12 consecutive runs at the
  default heap pass. The Linux runner's heap/collector timing is the difference.
  With the spec run alone, test 1 is the only font left unclosed, so the stray
  report can only be its payload.

### Decision — the spec is pinned, the engine is not reopened

- The fix is **test-only**: a spec-scoped list holds every font whose simulated
  collection strands its payload, so no later test's collection can deliver the
  pending report into a reporter this spec installed. The three round-C
  assertions stay byte-identical.
- **Rejected: run the release action on collection** so a leaked font reports
  once (the face) and the payload is claimed. It inverts a pinned core rule, and
  releasing from the collector thread is unsafe for the engine's thread-bound
  resources (GL). Not reopened for a test flake.
- Also rejected: a capture window around the seam (races on the window edge and
  hides the report), filtering the list by representation (drops the `never as
  the payload` claim test 1 exists to pin), and reordering the spec so the
  stranding tests run last (implicit, order-dependent).

### Residual divergence (recorded, not fixed)

Chunk 34's wording — "the leak detector therefore reports a font **face**, never
the byte buffer" — holds for the font's **own** registration, not for the whole
leak stream: on the JVM a leaked `Font` produces the face report immediately and
the payload's `byte buffer (font)` report later, and no tracked layer's native
memory is released on the collection path (the web face has no payload to report,
so only that second half is JVM-shaped). Suppressing the payload report needs
either `onCollected` running the action (rejected above) or a core concept for an
owned, non-root resource whose leak is not reported separately. That is a
core-context decision, not part of this fix; it is left to the owner.

### Round 2 — the delta and the round-1 findings

- **Standards (round 1, Important): the third test's pin was inert.** The pin
  roots the chain `Font` → wrapper handle → `NativeFace` → payload, and the
  third test closed the font *after* firing the seam, which nulls the handle, so
  the pinned object could no longer reach the payload. The close was observed by
  nothing — closing a rasterized font is pinned by `FontResourceTest` — so it is
  deleted: the pin now roots the chain to the end of the spec on all three
  targets.
- **Spec (round 1, three Minors, all documentary):** the residual above is now
  scoped to the JVM and cites chunk 34; the micro-plan's `git status` success
  criterion now reads "one source file"; and no red is claimed for the deleted
  line, which nothing observed (the exposure was latent: the third test is last
  and no other `kge-text-ttf` spec installs a reporter).

### Verification

- Red harness, unmodified `HEAD`: `tools/gradle -I .tmp/forcegc.init.gradle
  :kge-text-ttf:jvmTest --tests "dev.staticsanches.kge.text.ttf.FontLeakReportTest"
  --rerun-tasks` → `IllegalArgumentException: List has more than one element.` at
  `FontLeakReportTest.kt:68`, `tests="3" failures="1"`. The scratch init script
  (deleted at cleanup) pinned the test JVM to `maxHeapSize = "24m"` and
  `jvmArgs("-XX:+UseSerialGC")`.
- Green: the same harness three consecutive runs after the fix (3 tests, 0
  failures each), `:kge-text-ttf:jvmTest` 44 tests / 0 failures, and
  `tools/gradle build` green over the round's tree: `kge-core` jvm 715 / js 750 /
  wasmJs 750, `kge-text-ttf` jvm 44 / js 47 / wasmJs 47, `kge-font-roboto` 6 on
  each target, `kge-benchmark` 16 / 17, `buildSrc` 13 — zero failures, with
  `FontLeakReportTest` itself 3/3 on all three targets. The marker names one of
  the two round-2 reports; both must carry the staged tree's hash.
