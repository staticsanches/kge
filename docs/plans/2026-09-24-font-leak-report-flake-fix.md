# `kge-text-ttf` — micro-plan, round D-fix (flaky leak-report spec)

**Date:** 2026-09-24. Closes the `ubuntu-latest` CI red on `5a21f18` (round D,
run `36073429094`). Everything here is test-only: no production source and no
other test file moves.

## Diagnosis (verified)

- **CI red.** `:kge-text-ttf:jvmTest` — `FontLeakReportTest[jvm] > a rasterized
  font is still reported as the face` fails with
  `java.lang.IllegalArgumentException: List has more than one element.` at
  `FontLeakReportTest.kt:68`. Only `ubuntu-latest` failed (44 tests, 1 failed);
  `macos-latest` and `windows-latest` were green.
- **Reproduced, not inferred.** With the test JVM pinned to `-Xmx24m
  -XX:+UseSerialGC` (a scratch Gradle init script in `.tmp/`), the unmodified
  `HEAD` fails on the same test, line and message; 12 consecutive normal runs of
  the same spec pass. The failure is GC-timing dependent, and the Linux runner's
  heap/collector timing is what surfaces it.
- **Mechanism.** The spec asserts `reports.single()` on the process-wide
  `LeakReporterService`: `Font.onCollectionObserved()` →
  `KGEResourceCleanableState.onCollected()` claims the **face's** state and
  *drops* its release action — pinned by `KGEResourceCleanableStateTest`
  ("reports the representation once, never runs the action"). `NativeFace
  .release()`, the only path that closes the payload wrapper, therefore never
  runs, so the payload the font owns stays registered and unclosed. When the
  collector later reclaims it, its Cleaner callback reports
  `byte buffer (font) (uuid: …)` asynchronously into whichever reporter is
  installed **at that moment**. A GC inside the third test (it allocates the
  512×512 chart) delivered the first test's pending payload report, so the list
  held two entries and `single()` threw.
- **Only one source.** With the spec run alone, test 1 is the one font left
  unclosed (test 2 closes it; test 3's font is alive throughout), so the stray
  report is test 1's payload. Test 3 strands its own payload the same way when it
  fires the seam, so both need the treatment below.

## Fix

`kge-text-ttf/src/commonTest/kotlin/dev/staticsanches/kge/text/ttf/FontLeakReportTest.kt`
— pin every font whose simulated collection strands its payload, so no later
test's collection can deliver the pending report into a reporter this spec
installed. The three assertions stay byte-identical.

```kotlin
@OptIn(KGESensitiveAPI::class)
class FontLeakReportTest :
    FunSpec({
        fun reporting(reports: MutableList<String>): LeakReporterService =
            object : LeakReporterService {
                override fun report(representation: String) {
                    reports += representation
                }
            }

        // A fired collection trigger drops the release action, so the font's
        // payload keeps a pending report; pin the font that leaked it.
        val pinnedLeaks = mutableListOf<Font>()

        test("an unclosed font is reported as the face, not the payload") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            try {
                val font = Font.load(Roboto.variableFont)
                pinnedLeaks += font

                font.onCollectionObserved()
                // …assertions unchanged
            } finally {
                LeakReporterService.override(LeakReporterService.original)
            }
        }

        // test("a closed font is not reported …") — unchanged, the font is closed

        test("a rasterized font is still reported as the face") {
            // …after Font.load:
            //     pinnedLeaks += font
            // everything else unchanged, including the trailing font.close()
        }
    })
```

## Round 2 delta (Standards Important, round 1)

Round 1's Standards review found the third test's pin inert: `font.close()` nulls
`DefaultResourceWrapper.internalResource`, so the pinned `Font` no longer reaches
the `NativeFace` and its payload, which stays weakly reachable and can still
report later. Nothing observes that close — closing a rasterized font is pinned
by `FontResourceTest` — so it is deleted, and the pin roots the chain to the end
of the spec on all three targets.

```kotlin
        test("a rasterized font is still reported as the face") {
            // …Font.load, pinnedLeaks += font, shape, glyph,
            // onCollectionObserved, the two assertions — and no font.close()
        }
```

- **No red is constructible for this delta.** Deleting a line nothing observed
  changes no assertion, and the exposure is latent (the third test is the spec's
  last, and no other `kge-text-ttf` spec installs a reporter). TDD's red step
  does not apply and is not faked: the evidence is the reachability argument plus
  the harness and the full suite staying green.
- Documentary corrections carried with the delta: the `git status` success
  criterion below reads **one source file** (the round also stages these docs);
  the decisions entry scopes its residual to the JVM and cites chunk 34.

## Rejected

- **Run the release action on collection** (the face's action closes the
  payload, so one leaked font reports once). Rejected: it inverts a pinned core
  rule — `KGEResourceCleanableStateTest` proves `onCollected` never runs the
  action — and releasing from the collector thread is unsafe for the engine's
  thread-bound resources. Recorded as a residual divergence.
- **Capture the seam's report in a window** (drop what arrives off the test
  thread): leaves a race on the window edge and hides the payload report; the
  pin removes the asynchronous writer entirely.
- **Filter the list by representation**: weakens the round C claim that test 1
  exists to pin (`never as the payload`).
- **Reorder so the stranding tests run last**: implicit and order-dependent;
  the pin is explicit and works on all three targets.

## Steps (TDD)

1. **RED** — the scratch harness must fail at line 68 *before* touching the
   spec: `tools/gradle -I .tmp/forcegc.init.gradle :kge-text-ttf:jvmTest
   --tests "dev.staticsanches.kge.text.ttf.FontLeakReportTest" --rerun-tasks`.
2. **GREEN** — add the spec-scoped `pinnedLeaks` and the two `pinnedLeaks +=
   font` lines; rerun the same command until green.
3. **Regression** — `tools/gradle :kge-text-ttf:jvmTest --rerun-tasks` green,
   44 tests reported; the harness green three times in a row.

## Success criteria

- The tight-heap harness passes three consecutive runs.
- `:kge-text-ttf:jvmTest` reports 44 tests and passes.
- `git status` shows exactly one changed **source** file: `FontLeakReportTest.kt`.
