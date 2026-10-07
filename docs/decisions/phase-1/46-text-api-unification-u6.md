## 2026-10-06 — Text API unification, round U6: the configuration cache, its reference count and the lease leak contract

The round that closes the unification. U1 landed the `KGEFont` vocabulary and
the bitmap backend, U2 the bundled italic fixtures, U3 the TTF family, its
loading and its draws, U4 the retirement of the legacy surface; U6 adds the
ownership model the unification touch-point specifies and chunks `41`, `44` and
`45` deferred here. Touch-point and micro-plan:
`docs/plans/2026-10-06-text-api-unification-u6-touchpoint.md` and
`docs/plans/2026-10-06-text-api-unification-u6-microplan.md`.

### Shipped

- **The per-family configuration cache.** One entry per `(face, size, canonical
  axis map)`; equal leases share one native face, one CPU atlas and one GPU
  carrier, and different sizes, axis sets or faces do not. The family's map sits
  behind a `kotlinx.coroutines.sync.Mutex` that spans lookup-and-build, so
  concurrent requests for one key construct once: measured with an injected
  construction counter, eight concurrent requests produced **one** construction
  and one shared chart, and eight callers on `Dispatchers.Default` produced one
  construction and one `createTexture`.
- **The reference count and the release.** The entry carries an `AtomicInt`
  (positive = live, zero = released). Acquisition is a CAS loop that refuses
  zero; `release()` is a CAS loop in which the 1 → 0 transition is the sole
  winner, and the winner frees the GPU carrier, then the CPU atlas, then the
  native face, in place and on the closing thread. **The lease's close path never
  mutates the map** — only the family's close clears it — so the entry stays as a
  resource-free tombstone and the next request for that key rebuilds it under the
  lock.
- **The lease is a tracked resource.** It is a `ResourceWrapper` represented as
  `configured font`, adopted under its own fresh private `ResourceScope.Key`,
  whose clean action *is* the release. `KGEFont.close()` and the caller's scope
  close are the same idempotent close, so the count is decremented exactly once.
  A lease collected without `close()` is **reported** and does **not** decrement,
  so the entry outlives the abandoned lease and the family's close finally
  releases it — C2's "GC may report but never performs normal ownership".
- **One internal test seam.** `KGEFont.onCollectionObserved()`, declared in the
  family's file, fires the collection path for a lease; the private family, face
  and lease stay file-`private` and nothing else was widened. Sharing and release
  are observed black-box through the recorded `BufferService` and `GLService`.
- **The single-flight proof and its construction seam.** `createTtfFamily` gained
  one defaulted `internal` parameter, `createFace`, so a test can count or fail
  the native construction; the success leg and the failure leg are pinned in
  `TtfConfigurationCacheTest` and, under real parallelism, in the new
  `TtfConcurrencyJvmTest`.
- **Dependencies and prose.** `commonMain` gained
  `implementation(libs.kotlinx.coroutines.core)` (the `Mutex` lives in common
  code), and the two test source sets whose new specs name the coroutines API
  directly (`commonTest` and `jvmTest`) gained the same declaration. Five KDoc
  blocks that the sharing change had made false were corrected: three about
  per-lease possession of the atlas or carrier (`TtfFont`'s class KDoc,
  `TtfFontDecalTest`'s carrier comment and `TtfFontMeasureTest`'s class KDoc —
  the last two in files the round did not otherwise touch, and visible in the
  diff as replaced lines) and two helper KDocs inside the round's own new specs,
  which appear only in their corrected form. A module-wide sweep of the module's
  KDoc followed and found nothing else false: the direct-construction fixtures
  really do own their face, atlas and carrier, and the cache-era blocks are
  accurate. That sweep's triage is not part of the committed record.

### Decisions

- **D1 — keep-and-rebuild** (owner, 2026-10-06). The release happens at the last
  lease's close; the map keeps a resource-free tombstone instead of being mutated
  on the lease's synchronous close path (the family's own close clears the map).
  Rejected: remove-at-zero, whose lock-free map mutation on the close path would
  have to be proven against a concurrent publication for no behavioural gain.
- **D2 — the entry owns the native face, the CPU atlas and the GPU carrier**, all
  lazily created as before (the carrier on the first decal, so `font()` never
  needs a GL context); release order is the inverse.
- **D3 — the lease is tracked** (owner, 2026-10-06), with the leak identity
  `configured font`. Rejected: reporting the entry instead, which would make an
  abandoned lease indistinguishable from a live one and would keep the count from
  ever returning to zero.
- **D4 — the test seam is one internal function**; sharing and release stay
  black-box. Rejected: widening the private types, and asserting sharing through
  the wrappers' uuids.
- **D5 — the bitmap family stays uncached** (owner, 2026-10-06): it creates a
  cheap `CoreFont` per lease and holds no native state to clone, and item 20
  already holds for it by construction.
- **D6 — `kotlinx-coroutines-core` joins `commonMain`** as `implementation`,
  never `api`: no coroutines type enters the module's public signatures.
- **D7 — item 19's failure leg is read as "no partial entry, retry later"**
  (owner, 2026-10-06). The strict reading ("observe the same failed
  construction") is unattainable with the lock-across-build shape: a waiter that
  enters after the builder threw finds no entry and re-attempts. Measured with
  the injected counter, eight concurrent failing callers ran eight constructions,
  in `commonTest` and under real JVM parallelism. Sharing one failure would need
  the attempt published as a promise with the build outside the lock — which
  contradicts the lock-across-build sentence and adds a promise lifecycle plus
  cancellation handling for a builder cancelled mid-flight. The owner read the
  clause by its emphasis ("without a leaked partial entry") and by the consumer:
  no engine consumer needs a shared failure. The pins therefore assert that every
  concurrent caller fails with the injected instance, that no partial entry is
  published, and that a later request constructs again (no negative caching) and
  yields a usable lease — in `TtfConfigurationCacheTest` ("N concurrent failing
  requests each observe the failure and leave no entry behind") and in
  `TtfConcurrencyJvmTest`'s failure leg under `Dispatchers.Default`.

### Divergences and retained differences

- **The count is the liveness state** — there is no separate dead flag. Positive
  means live and zero means released, so there is no window in which an acquirer
  could adopt an entry the release has already claimed. This is strictly stronger
  than D1's wording, which described marking the entry dead as a step.
- **The tracked wrapper's handle is the shared configuration, not the lease**:
  wrapping the lease itself would publish a partially constructed object into the
  leak detector. The lease's identity is the wrapper's uuid and its
  `configured font` representation, and the handle is what the lease consumes.
- **`wrapConfiguredFont` is a new, untested guard.** It mirrors `wrapNativeFace`:
  between `configuration(...)` taking the reference and `scope.register`
  succeeding, a throw would strand the count. It has no injection point, so it is
  untested like the other `letClosingIfFailed` guards.
- **Pin 6's native-face release is not black-box observable.** A native face
  allocates no engine buffer and issues no GL call, so the pin covers the
  observable release order (carrier texture, carrier scratch, atlas chart buffer)
  and the boundary that the shared payload survives the entry release; the
  native leg itself has no seam.
- **Pin 11 reds through its precondition**: the family's close already released
  live entries before this round, so its own claim was only reachable once the
  tombstone existed.
- **The failure pins do not discriminate the two readings of item 19.** They
  assert that every concurrent caller fails with the injected instance, that no
  partial entry is published and that a later request constructs again —
  properties the strict shared-failure reading would satisfy too. The reading is
  a design decision (D7) recorded with its measured re-attempt cost, not an
  assertion: pinning `constructions == N` would pin an implementation artifact
  rather than the contract.
- **An acquisition racing the family's `close()` may publish into a closed
  family.** Accepted, not pinned: `ResourceScope` is documented single-threaded
  and this round's obligation is item 19's CAS-versus-acquisition race.
- **The bitmap family is uncached** (D5) — recorded so "a family caches native
  configuration state" reads as the TTF family's obligation.
- **Mid-round plan corrections.** Step 1's claim that all six of its pins failed
  before the cache was wrong: only two did, and the other four are negative
  guards that pass while nothing shares and that fail if the key over-shares. A
  pin was added for item 18's "one native clone" (`BufferService`/`GLService`
  cannot see it), the expected-count arithmetic was corrected (an earlier
  `166/168` omitted the `jvmTest` pin), and the failed-registration pin moved
  from step 4 to step 2, where the count it gives back is introduced.
- **The `jvmTest` coroutines declaration corrects a wrong dispatch premise.** The
  literal instruction said that source set needed no declaration; the round's own
  race spec names the API, and the usage-based rule governs.
- **No behavior-reference claim.** olcPixelGameEngine v2.30 has no font
  abstraction, no configuration identity and no cache; `main` has no
  `kge-text-ttf`. Parity is vacuous for this round, as for U4.

### Not in this round

Nothing of the unification remains: with this close the concept's own definition
of done is met — the model and its operations exist, the built-in font is the
engine default with TTF fonts selectable per call, multifile families, axes,
caching, ownership and failure cleanup satisfy the test contract on JVM, JS and
WasmJS, the bundled fixtures load atomically, the old public surfaces are gone,
and the gate, both review axes and this entry are the close's record. Outside the
unification, the deferred rich-text concept (roadmap, 2026-10-04) is what
remains.

### Verification

`tools/gradle build` green on the staged tree (304 actionable tasks, exit 0):
every target's tests, ktlint, the metadata/kLIB compilation and `buildSrcCheck`.
Counts per module and target, from the gate's own result XMLs:

| module | jvm | js | wasmJs |
|---|---|---|---|
| `kge-text-ttf` | 168 | 169 | 169 |
| `kge-core` | 746 (3 skips) | 781 | 781 |
| `kge-font-roboto` | 10 | 10 | 10 |
| `kge-test-support` | 8 | 9 | 9 |
| `kge-benchmark` | 21 | — | 22 |
| `buildSrc` | 27 | — | — |

`kge-text-ttf` moved from 148 jvm / 150 js / 150 wasmJs to 168/169/169: nineteen
`commonTest` pins in the new `TtfConfigurationCacheTest` plus one `jvmTest` pin in
the new `TtfConcurrencyJvmTest`. Every other module is unchanged and no target
reports zero tests (chunk `35`). The KDoc sweep and the `jvmTest` dependency
addition moved no count, which was the step's own red flag.
