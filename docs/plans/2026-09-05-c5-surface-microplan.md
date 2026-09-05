# C5 (surface — S3/S4) micro-plan

**Date:** 2026-09-05. Touch-point done (roadmap S3/S4 entries). Concept: the 2D
pixel surface over native memory — first consumer of the C3
`ByteBuffer`/`MemoryAllocatorService` contracts. PNG moved to S5 (PNG codec),
next in the order.

## Contract

```kotlin
// image/Pixmap.kt
interface Pixmap : Sequence<Pixel> {
    enum class SampleMode { NORMAL, PERIODIC, CLAMP }

    val width: Int
    val height: Int
    val sampleMode: SampleMode

    fun get(x: Int, y: Int): Pixel                       // never throws; OOB per policy:
    // NORMAL → transparent Pixel(0,0,0,0); PERIODIC → abs(y%h)*w + abs(x%w);
    // CLAMP → max(0,min(x,w-1)) / max(0,min(y,h-1))
    fun uncheckedGet(x: Int, y: Int): Pixel               // in-bounds only, hot path
    fun sample(u: Float, v: Float): Pixel                 // nearest: get(min(int(u*w),w-1), …)
    fun sampleBL(u: Float, v: Float): Pixel               // u*w-0.5 floor, four reads via get,
    // r/g/b weighted, truncated to byte, alpha 255
}

interface MutablePixmap : Pixmap {
    fun set(x: Int, y: Int, pixel: Pixel): Boolean        // false when outside; never throws
    fun uncheckedSet(x: Int, y: Int, pixel: Pixel)
    fun clear(pixel: Pixel)
    fun inv()                                             // per-pixel Pixel.inv() (C4)
}

// image/Sprite.kt
class Sprite(
    width: Int,
    height: Int,
    private val buffer: ResourceWrapper<ByteBuffer>,
    override var sampleMode: SampleMode = NORMAL,
) : MutablePixmap, KGEResource

// image/SpriteCreationService.kt
interface SpriteCreationService : KGEOverridable {
    fun create(width: Int, height: Int, sampleMode: Pixmap.SampleMode, name: String?): Sprite
    fun duplicate(sprite: Sprite): Sprite                  // preserves sampleMode
    companion object : KGEOverridable.Proxy<SpriteCreationService>(..., default)
}
```

Default vs abstract — the contract owns the algorithms, the implementer
provides the raw accessors (Kotlin stdlib style):

- `Pixmap`: abstract `width`/`height`/`sampleMode`/`uncheckedGet`; `get`,
  `sample`, `sampleBL`, `iterator` are default bodies over them.
- `MutablePixmap`: abstract `uncheckedSet`; `set` (bounds-check → Boolean) and
  `inv` (per-pixel loop) are default bodies. `clear` has a correct default
  loop, but `Sprite` overrides it with `fillInts` (hot path).

Storage: pixel `(x, y)` at byte offset `(y * width + x) * INT`, value
`pixel.nativeRGBA` (LE packed — `putInt`/`getInt` use it directly). Init:
`require(width > 0 && height > 0)`; `require(capacity == w * h * INT)`. Created
content is unspecified; the default service allocates via
`MemoryAllocatorService.allocate(w * h * INT)` — `create`/`duplicate` are
platform-independent here (PNG is S5).

## TDD (kotest, commonTest on all targets; red → green per step)

1. **Basics** — `create(3, 2)`: dims; row-major sequence (6 distinct pixels);
   `create(0, 1)`/`create(1, 0)` throw; wrong-capacity buffer throws.
2. **get/set semantics** — round-trip; set OOB → false, no change; get OOB per
   policy (NORMAL → `Pixel(0,0,0,0)`; PERIODIC wrap incl. negative; CLAMP edges);
   unchecked in-bounds.
3. **sample / sampleBL** — 2x2 distinct corners: `sample(0,0)`/`sample(1,1)` =
   corners; `sample` clamps u > 1; `sampleBL` center of black/white 2-col →
   127 channels, alpha 255; PERIODIC + `u = -0.25` wraps (differs from NORMAL).
4. **clear / inv** — clear(RED) fills; inv inverts RGB, keeps alpha.
5. **Ownership** — close → `get`/`set` fail-fast; close idempotent; unclosed
   creation reported via `LeakReporterService` (override + `resetAll` — C2
   pattern).
6. **SpriteCreationService** — extension-contract A: override the allocator
   (counting) → created Sprite's buffer is the override's. Contract B: decorator
   service filling BLANK → observable change. `duplicate`: equal pixels, copy
   mutation leaves the original untouched, `sampleMode` preserved.
7. **Gate** — `./gradlew build --rerun-tasks` green, then decisions-log entry.

## Files & open micro-details

- New: `image/Pixmap.kt`, `image/Sprite.kt`, `image/SpriteCreationService.kt`
  (+ 3 commonTest files). No platform code in this concept.
- `SampleMode` nests in `Pixmap` (cohesive concept; Flip will nest where C6's
  draw API lands). Leak representation = the allocator wrapper's own string
  (`name` param is logging-only). Color-fill convenience as a common extension
  (`clear` + close-on-failure), service API stays lean. `override`/`resetAll`
  markings inherited from T2.
