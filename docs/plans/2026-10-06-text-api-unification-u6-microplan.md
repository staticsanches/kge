# Text API unification — round U6 micro-plan (configuration cache, reference counting, lease leak contract)

Base: `914f43b` (round U4). Touch-point:
`docs/plans/2026-10-06-text-api-unification-u6-touchpoint.md`. Gate:
`tools/gradle build`, once, before the two review axes.

## What this round delivers

One native configuration per `(face, size, canonical axes)` key, shared by equal
leases; a reference count whose 1 → 0 transition releases the entry in place
while the family keeps a resource-free tombstone that a later request rebuilds; a
lease that is a tracked resource, so an abandoned one is reported without GC ever
running the release; and the single-flight that makes concurrent construction
share one clone or one failure. The bitmap family is untouched.

## Success criteria

1. Test-contract items 18–24 of
   `2026-10-04-text-api-unification-touchpoint.md` each have a named test.
2. `kge-text-ttf`'s public surface is unchanged: `KGETtfFontService` +
   `TtfFontAddon` and nothing new.
3. No spec's case count drops; no golden reference moves.
4. Gate green with the counts in "Expected counts".

## Pin inventory (the contract)

**`TtfConfigurationCacheTest` (new, `commonTest`)**

1. equal keys share one native face and one CPU/GPU atlas while returning
   distinct leases (item 18) — observed by recording `BufferService`/`GLService`
   across two leases.
2. a different size does not share (item 18).
3. a different canonical axis set does not share (item 18).
4. a different face does not share (item 18).
5. closing one lease leaves an equal live lease usable (item 20).
6. closing the **last** lease releases the entry in place: GPU carrier, then CPU
   atlas, then native face, all observable through the recorded services
   (item 20).
7. the released key rebuilds on the next request — a new native face and a new
   atlas (item 20 + D1's tombstone).
8. creating a lease with `axes` outside the face's descriptors or range still
   fails before any entry is published (item 15's guard, now against the cache).
9. a lease that never draws creates no GPU object (the carrier stays lazy).
10. two equal leases created back to back allocate exactly one chart for the same
    glyph (item 18, the CPU half).
11. family close releases every live entry and invalidates its leases (item 20 +
    item 21).
12. a failed lease **registration** decrements the count in a fresh acquisition:
    after `scope.register` throws for the key, the entry is not stranded
    (item 24).
13. an unclosed lease is reported as `configured font` (item 22) — via
    `LeakReporterService` and the internal collection seam.
14. a closed lease is not reported (item 22).
15. a leaked lease does **not** decrement: the entry survives it and is released
    by the family's close (D3).
16. lease close is idempotent, and a scope close after an explicit lease close
    releases exactly once (items 21, 23).
17. single-flight on **success**: N concurrent requests for one key invoke the
    construction once and every caller receives a lease over that one entry
    (item 19).
18. the **failure** leg: N concurrent requests whose construction fails all
    observe a failure with no partial entry published — a later request
    constructs again (no negative caching) and succeeds once the construction
    does (item 19 as read by touch-point D7).

**`TtfConcurrencyJvmTest` (new, `jvmTest`)**

19. the same two legs under real parallelism (`Dispatchers.Default`, N coroutines
    racing one key), asserting one construction and one shared entry (item 19's
    "concurrent JVM creation").

**`TtfConfigurationCacheTest`, continued**

20. two equal leases construct the native face once (item 18's "one native
    clone", which the recorded services cannot see: a native face allocates no
    engine buffer and no GL object). Pinned through the factory seam step 4 adds.

### A note on the negative pins

Pins 2–4 and 9 are **guards, not reds**: nothing shares before the cache exists,
so "a different size/axis set/face does not share" and "a lease that never draws
creates no GPU object" pass on the base commit and must still pass after the
cache. They are what fails if the key drops a component or the carrier is built
eagerly. Only pins 1 and 10 fail before the change; step 1's own red evidence is
those two.

## Shape (the touch-point's decisions, fixed)

- **Cache key**: the face, the `KGEFont.Size` and the complete canonical axis
  map (defaults filled).
- **Entry**: the native face (`ResourceWrapper`, `font face`), the CPU atlas and
  the GPU carrier, all lazily created as today, plus the atomic reference count.
- **Cache**: a plain map in the family, with a `kotlinx.coroutines.sync.Mutex`
  spanning lookup-and-build. The close path never mutates it (D1).
- **Release**: the CAS 1 → 0 winner frees GPU carrier → CPU atlas → native face
  and marks the entry dead; the acquire path revalidates liveness under the lock
  and rebuilds a dead entry.
- **Lease**: a tracked resource represented as `configured font`, whose clean
  action decrements; `KGEFont.close()` and the scope's close are the same
  idempotent close.
- **Seams**: one `internal` function in the family's file that fires the
  collection path for a lease; one defaulted `internal` factory parameter on
  `createTtfFamily` so a test can inject a failing construction (D4). Nothing
  private is widened, and `commonMain` gains
  `implementation(libs.kotlinx.coroutines.core)` (D6).

## Steps

**Step 1 — the cache and sharing (items 18, 20).** Write pins 1–4 and 9–10; they
fail today (two leases build two native faces and two atlases). Introduce the
key, the entry and the family's cache behind the `Mutex`, and route `TtfFace.font`
through it. Green.

**Step 2 — the count and the release (items 20, 21, 24).** Pins 5–7, 11, 12, 16.
The atomic count, the CAS 1 → 0 release in order, the dead entry and the rebuild,
the family's close releasing live entries, and the failed-registration leg that
gives the count back. Pin 12 belongs here rather than in step 4: it is the same
acquisition the count introduces, and leaving it out would ship a count that
leaks on that path. Green.

**Step 3 — the tracked lease and the leak contract (item 22).** Pins 13–15. Wrap
the lease, add the `internal` collection seam, and record the identity. This is
the round's one behavioral addition to the leak story; the family itself keeps
today's identity (its payload buffers are tracked).

**Step 4 — single-flight (item 19) and the injectable construction.** Pins 8, 17,
18, 20 and the JVM race 19. The defaulted factory parameter, the success leg
proven as one construction per key, and — under D7's reading — the failure leg
proven as "every concurrent caller fails, no partial entry is published, and a
later request retries". (This step first read "the promise published under the
lock on both outcomes"; the measured blocker retired that shape and D7 replaced
the reading, so the line is corrected here.) Green.

**Step 5 — the dependency and the tidy-up.** `commonMain` gains
`implementation(libs.kotlinx.coroutines.core)`; the stale KDoc of any touched
type is corrected; no dead helper survives the change.

## Expected counts

`kge-text-ttf` at the base: 148 jvm / 150 js / 150 wasmJs. The round adds 19
`commonTest` pins (the cache spec's 19 cases, pin 20 included) and one `jvmTest`
pin, so the closing tree is expected at **168 jvm / 169 js / 169 wasmJs**:
`148 + 19 + 1` and `150 + 19`. (An earlier version of this section said
166/168 — it forgot the `jvmTest` pin in the jvm total; corrected here, and the
developer's per-step counts are the ledger.)

Per step, from the base: step 1 lands six `commonTest` pins → 154/156/156;
step 2 six → 160/162/162; step 3 three → 163/165/165; step 4 four `commonTest`
(pins 8, 17, 18, 20) → 167/169/169 plus the `jvmTest` race → 168/169/169;
step 5 adds none.

No other module changes (the benchmark and the core are untouched), so
`kge-core` 746/781/781, `kge-font-roboto` 10/10/10, `kge-test-support` 8/9/9,
`kge-benchmark` 21/22 and `buildSrc` 27 stay as they are. The developer
reconciles the actual per-target counts against the named pins, not the number
alone (chunk `35`).

## Dispatch

One `tdd-developer` per step, in order, none overlapping. Each step is red →
green on its own; a step that turns green only after the next step's code is a
plan defect — report it rather than reordering. The orchestrator runs
`tools/gradle build` once after step 5 and before the reviews.
