# Text API unification — round U6 (configuration cache, reference counting, lease leak contract): touch-point

**Status: decided with the owner, 2026-10-06.**

The last round of the unification. U4 retired the legacy surface; what remains is
the ownership model the unification touch-point specifies and chunks `41`, `44`
and `45` deferred here: one native configuration per key, shared by equal
leases, released when the last lease closes, with the family/lease leak identity
the U4 close recorded as missing.

Material: `docs/plans/2026-10-04-text-api-unification-touchpoint.md`
§"Ownership, caching, and scope" and test-contract items **18–24**; decisions
chunks `41` (U1), `44` (U3), `45` (U4); the current sources.
Baseline: `914f43b`, `kge-text-ttf` 148 jvm / 150 js / 150 wasmJs.

## Verified facts (this touch-point)

1. **There is no cache.** `TtfFace.font(scope, size, axes)` always builds a fresh
   configuration — `wrapNativeFace(createNativeFace(payload, coordinates))`
   (`TtfFontFamily.kt:105`) — and each lease owns its own `GlyphAtlas` and
   `GlyphAtlasGpu`, created lazily on first use. Two equal requests therefore
   produce two native faces, two CPU atlases and two GPU carriers.
2. **The payload is already shared.** One `TtfPayload` per face, one engine
   buffer per payload; `PayloadAllocationTest` pins two buffers for two payloads
   and no extra buffer for two `font()` calls on the same face.
3. **The atlas resources are observable.** `GlyphAtlas` (internal, `KGEResource`)
   allocates its charts through `SpriteService`/`BufferService`, and
   `GlyphAtlasGpu` (internal, `KGEResource`) allocates a scratch buffer and a GL
   texture. Recording those services is therefore enough to see sharing and
   release without reaching into the cache.
4. **`KGEResource.close()` is not `suspend`.** The construction path is
   (`Face.font` is suspend and on the web awaits `freeTypeModule()`, itself a
   `Mutex` single-flight in `webMain`), but `close()` is synchronous. A
   reference count therefore **cannot** be guarded by the coroutines `Mutex`
   on the close path, and a cache entry cannot be removed from a map under that
   lock when the count reaches zero.
5. **`kotlinx-coroutines-core` is not on `commonMain`'s compile classpath.**
   Only `webMain`/`webTest` declare it, and `kge-core` carries it as
   `implementation`, so nothing leaks it transitively. A `commonMain` `Mutex`
   needs the dependency declared in `commonMain`.
6. **The leak machinery needs a wrapper.** `ResourceWrapper<R>` registers with
   `KGELeakDetector` under a representation string; `close()` runs the clean
   action while the public `@KGESensitiveAPI fun ResourceWrapper<*>.onCollectionObserved()`
   fires the report path *without* running it (C2's semantics and the `#37`
   flake's mechanism). A resource that is never wrapped is never reported.
7. **A lease is not tracked today.** `TtfFont` is a plain file-private class with
   a `closed` flag, registered directly in the caller's `ResourceScope`. The
   native face it owns *is* wrapped (as `font face`) and the payload buffers are
   wrapped (as `font`), so an abandoned *lease* is invisible to the detector
   while its shared native state stays alive.
8. **The core bitmap family has the same shape** — one `CoreFont` per lease, no
   cache, no wrapper — and its lease holds no native state of its own. Test
   contract item 20 already holds for it by construction, so the cache this
   round adds is the TTF family's; whether the core should also dedupe Kotlin
   objects is an open item.
9. **No behavior-reference claim.** olcPixelGameEngine v2.30 has no font
   abstraction, no configuration identity and no cache, and `main` has no
   `kge-text-ttf`, so this round argues no parity and owes no header extraction —
   as in U4.
10. **The contract's own words are the spec**: item 18 (equal keys share one
    clone and atlas state, distinct leases; different face/size/axes do not
    share), 19 (concurrent JVM creation is single-flight on success **and**
    failure), 20 (closing one lease leaves equal live leases usable; closing the
    last immediately frees native and CPU/GPU atlas state), 21 (idempotent close
    and family-close invalidation), 22 (the family does not strongly retain
    leases; abandoned live leases are reported as leaks, without GC-driven
    cleanup), 23 (fresh private scope key per family and lease; scope-taking APIs
    sensitive), 24 (every allocate-then-fail path closes).

## Decisions

### D1 — the cache keeps a tombstone; the close path never mutates the map (owner, 2026-10-06)

The cache is a plain map keyed by `(face, size, canonical axes)`. Construction is
single-flight under a `Mutex` that spans lookup-and-build. Closing a lease
decrements an atomic count, and **only the 1 → 0 transition releases**: the
winner frees the entry's GPU carrier, then its CPU atlas, then its native face,
in place and on the closing thread, and marks the entry dead — **no map mutation
happens on the synchronous close path**. The entry stays as a tombstone (key, a
dead flag, a zero count, no resources), and a later request for the same key
rebuilds it under the lock.

The release moment is therefore the last lease's `close`, immediate — test
contract item 20 is met punctually; the tombstone holds no native or GPU memory,
and the family's close releases every live entry and drops the tombstones.

The CAS 1 → 0 versus a concurrent acquisition that has just adopted the entry is
the round's real concurrency obligation, and it exists in either publishing
strategy: the acquire path runs under the `Mutex` and must revalidate liveness
and rebuild when the entry died, and the micro-plan pins that race with a test
alongside the post-close rebuild.

Rejected: **remove-at-zero**, which has the same release moment but adds a
lock-free map mutation on the close path that must be proven against a
concurrent publication — more to review and to test for no behavioural gain.
Also rejected, as the unification touch-point already recorded:
`ConcurrentHashMap.computeIfAbsent` (not in `commonMain`) and a bare atomic
(cannot be held across the suspending construction).

### D2 — what the entry owns, and when each part is created

The entry owns the native face (`ResourceWrapper`, represented as `font face`),
the CPU atlas and the GPU atlas. The native face and the CPU atlas keep today's
lazy creation, and the GPU carrier keeps its lazy first-decal creation — creating
it eagerly would require a GL context at `font()` time, which the contract does
not ask for. Release at zero is GPU carrier → CPU atlas → native face, the order
the current lease already uses.

### D3 — the lease is a tracked resource (owner, 2026-10-06)

The lease is a `ResourceWrapper<…>` represented as `configured font (uuid: …)`,
adopted into the caller's scope under its own fresh private `ResourceScope.Key`
(item 23). Its clean action decrements the reference count. A lease collected
without `close()` is **reported** under that representation and does **not**
decrement, so the entry outlives it and the family's close is what finally
releases it — C2's "GC may report but never performs normal ownership".

Rejected: leaving the lease unwrapped and reporting the entry instead (a leaked
lease would be indistinguishable from a live one, the count would never return to
zero, and item 22 would be effectively off).

### D4 — the test seam is one internal function; everything else is black-box

Sharing and release are observed **black-box**, by recording `BufferService`
(charts and the GPU scratch) and `GLService` (the chart texture): two equal
leases must show one atlas and one carrier, and the last close must show the
release. The **one** added seam is an `internal` function in the family's file
that fires the collection path for a lease — the shape U4 deleted with `Font` —
because the collection path has no public route to the private wrappers. The
family has no such seam and needs none: its own identity is its payload buffers,
which `BufferService` already tracks, and the lease is the only round-owned
resource whose abandonment has to be reported (item 22). (This decision first
promised the seam "for a lease and for the family"; the shipped seam covers the
lease, and the narrowing is recorded here and in the entry.)

Rejected: widening `TtfFont`/`TtfFontFamily` to `internal` (U4 justified
`internal` only for `ShapedGlyph`/`TextMetrics`), and asserting sharing through
`ResourceWrapper.uuid` from the tests (it would require exposing the wrappers).

### D5 — the core bitmap family stays uncached (owner, 2026-10-06)

The cache exists to share expensive native configuration; the bitmap family
creates a cheap `CoreFont` per lease and holds no native state to clone, and item
20 already holds for it by construction. The divergence is recorded so the
contract's "a family caches native configuration state" reads as the TTF
family's obligation.

### D6 — `kotlinx-coroutines-core` joins `commonMain`

`commonMain` gains `implementation(libs.kotlinx.coroutines.core)` so the `Mutex`
lives in common code (fact 5). `implementation`, never `api`: no coroutines type
enters the module's public signatures. The version is the catalog's, already used
by `webMain` and by `kge-core`.

### D7 — item 19's failure leg is read as "no partial entry, retry later" (owner, 2026-10-06)

Test item 19 says callers "share one successfully constructed native
configuration, **or observe the same failed construction** without a leaked
partial entry", and the cache paragraph above says to "publish the construction's
success or failure before releasing". Read strictly, the failure leg would mean
one attempt whose failure every concurrent caller observes.

With the lock spanning the build — the shape this document fixes — a waiter that
enters after the builder threw finds no entry and **re-attempts**. Measured with
an injected construction counter while implementing the round: eight concurrent
failing callers ran eight constructions, in `commonTest` and under real JVM
parallelism, each observing its own failure. Sharing one failure instance would
require publishing the attempt as a promise and building outside the lock, which
contradicts the lock-across-build sentence and adds a promise lifecycle
(publication, completion on both outcomes, removal so a later request retries)
plus explicit handling for a builder cancelled mid-flight.

The owner read the clause by its emphasis — "without a leaked partial entry" —
and by the consumer: no engine consumer needs a shared failure, so the failure
leg means **every concurrent caller fails, no partial entry is published, and a
later request retries**. The reading is recorded here because a reader of item 19
could take the stricter one; the Spec axis audits it.

## What the micro-plan will own

The exact cache/entry/lease shapes, the atomic used for the count (the project's
`kotlin.concurrent.atomics`, already opted into by T2), the failure-path tests
(item 19's failure leg needs an injectable construction — either a small internal
cache unit with a factory parameter, unit-tested with a failing stub, or an
internal seam over `createNativeFace`), and the per-test inventory of items
18–24 with the expected counts.

## Confirmed with the owner (2026-10-06)

1. **D1 — keep-and-rebuild**; the release happens at the last lease's close, and
   the map keeps a resource-free tombstone instead of mutating on the close path.
2. **D3 — the lease is tracked**, with the leak identity `configured font`; a
   collected lease reports without decrementing.
3. **D5 — the core bitmap family stays uncached.**

## Gate

`tools/gradle build`, once, before the two review axes. Expected counts: no
spec's case count may silently drop, and the new caching/lifecycle spec adds
cases; the micro-plan states the expected per-target totals.
