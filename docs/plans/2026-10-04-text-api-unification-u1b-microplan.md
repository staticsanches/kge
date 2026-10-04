# U1b micro-plan — names, proportionality and the named core faces

Round contract for the addendum that closes the three vocabulary gaps the owner's
review of U1 (commit `e4aa440`) found. Design and evidence: the touch-point's
"Revision 2026-10-04 — the close review of U1" section and its contract items
38–43. Base commit `e4aa440`; branch `feature`.

## Scope

**In:** three vocabulary members, one public interface, the construction seam's
return type, their pins, and the migration of the identity filters that the
named accessor replaces.

**Out:** every TTF value (`name` and `monospaced` are U3's to populate from the
payload), the bundle manifest (U2), and any change to measuring or drawing — this
round must not move a single pixel or advance.

## Already-resolved decisions

- `Face.monospaced` is **declared** by the face, never derived.
- `Family.name`, `Face.name`, `Face.monospaced` and — by the same rule, in this
  round — `Face.axes`, `KGEFont.size` and `KGEFont.axisCoordinates` are **inert
  values**: they do **not** check whether the family is open, because reading
  them consumes nothing. The fail-fast rule stays on consumable state — the
  handles that reach the payload (`faces`, `defaultFace`, `monospaced`,
  `proportional`, `family`, `font(...)`, a configured font's `face`/`family`) and
  the operations that read it (`measureText`, the draws) — and that asymmetry is
  itself pinned, so neither half can drift.
- The built-in family's name is the existing sheet-name constant
  (`KGE bitmap font`) so the built-in font has one name; its faces are
  `Monospaced` and `Proportional`, in that order, and `monospaced` is
  `defaultFace`.
- `KGECoreFontFamily` is an **interface**; the concrete family class stays
  private behind it, and the seam keeps returning an interface so it stays
  overridable.

## Step 1 — the vocabulary members (red → green)

`kge-core` `text/KGEFont.kt`: `Family.name`, `Face.name`, `Face.monospaced`,
each with at most a two-line KDoc. The implementation in
`text/KGECoreFontService.kt` holds them as plain immutable values — no
`checkOpen()` — because they are inert metadata.

Red: declaring the members leaves `CoreFace`/`CoreFontFamily` incomplete, so the
module does not compile; the pins below then fail on the unfilled values.

```kotlin
test("the core family and its faces report their names") {
    withFamily { _, family ->
        family.name shouldBe "KGE bitmap font"
        family.faces.map { it.name } shouldBe listOf("Monospaced", "Proportional")
        family.defaultFace.name shouldBe "Monospaced"
    }
}

test("only the monospaced core face reports itself monospaced") {
    withFamily { _, family ->
        family.defaultFace.monospaced shouldBe true
        family.faces.single { !it.monospaced }.name shouldBe "Proportional"
    }
}
```

The close asymmetry is **not** a test of its own: Step 4's two scenario pins
carry it, and the family-close one is where `name`, `monospaced`, `axes`, `size`
and `axisCoordinates` are asserted readable beside the throwing handles. One
scenario, both halves — not a third test repeating them.

## Step 2 — the public core family and its named faces (red → green)

Add `KGECoreFontFamily : KGEFont.Family` with `monospaced` and `proportional`,
make the concrete family implement it, and change
`KGECoreFontService.createResources` (interface, companion proxy and `Default`)
to return `KGECoreFontFamily`.

Red: the second pin below does not compile while the seam still returns
`KGEFont.Family` — that compile failure is the contract's own proof that the
named route is reachable only through the widened type.

```kotlin
test("the core family names both faces and defaults to monospaced") {
    withFamily { _, family ->
        family.monospaced shouldBeSameInstanceAs family.defaultFace
        family.proportional shouldNotBeSameInstanceAs family.monospaced
        family.proportional.monospaced shouldBe false
        family.faces.toSet() shouldBe setOf(family.monospaced, family.proportional)
    }
}

test("the seam still accepts a foreign implementation of the core family") {
    // Follow TtfTextServiceTest.kt's override-and-restore pattern exactly:
    // KGECoreFontService.override(...) around a ResourceScope().use { ... } block,
    // restored afterwards so no override leaks into another test.
    // The fake implements KGECoreFontFamily (name, faces, defaultFace,
    // monospaced, proportional, close) and returns itself from createResources.
    KGECoreFontService.createResources(scope) shouldBeSameInstanceAs foreign
}
```

That second pin is the API-discipline evidence for the widening: it fails if the
seam is typed on the private concrete class, and it is the extender the type's
publicity is bought by. The core faces' `close()` idempotence and use-after-close
behaviour are already pinned; the new accessors add no ownership of their own.

## Step 3 — retire the identity filters the accessor replaces

Twelve `faces.single { it !== defaultFace }` sites live in the test source sets.
Where the family's static type is `KGECoreFontFamily` — the helpers fed by
`KGECoreFontService.createResources` — read `family.proportional` instead.
Changing such a helper's parameter type from `KGEFont.Family` to
`KGECoreFontFamily` is allowed and honest: the helper is about the core family.
Keep every site that pins `faces` itself, and keep the assertions that prove face
order and membership. This step adds no behaviour and must leave every existing
pin green; it exists so the accessor has a real consumer inside the round.

## Step 4 — move the fail-fast pins onto the rule (red → green)

The rule reaches three accessors U1 had pinned as failing fast: `Face.axes`,
`KGEFont.size` and `KGEFont.axisCoordinates`. Drop `checkOpen()` from those three
getters; they are values, not handles.

Red: the existing pins in `CoreFontFamilyTest` expect them to throw after a close
(the configured-font case and the after-family-close case), so relaxing the
getters turns those assertions red — that failure is the evidence that the pins
were testing the old rule.

The pins must then assert both sides, in the two existing scenarios:

```kotlin
// after the family closes: the two scenario tests are the only pins of the rule
family.name shouldBe "KGE bitmap font"
mono.name shouldBe "Monospaced"
mono.monospaced shouldBe true
mono.axes shouldBe emptyMap()
shouldThrow<IllegalStateException> { family.faces }
shouldThrow<IllegalStateException> { family.defaultFace }
shouldThrow<IllegalStateException> { family.monospaced }
shouldThrow<IllegalStateException> { family.proportional }
shouldThrow<IllegalStateException> { mono.family }
shouldThrow<IllegalStateException> { mono.font(scope, 8.fontPx) }

// after the configured font closes
font.size.px shouldBe 8
font.axisCoordinates shouldBe emptyMap()
shouldThrow<IllegalStateException> { font.face }
shouldThrow<IllegalStateException> { font.family }
shouldThrow<IllegalStateException> { font.measureText("A", TAB_SIZE) }
```

The after-family-close pin is the one that carries the whole rule, including the
new metadata; the after-lease-close pin carries the configured font's half. No
third test repeats them.

`KGEFont.face` and `KGEFont.family` keep failing fast: they are the route back to
creating fonts over the released payload, and the default `family` reads through
`face`. No other existing pin may be weakened.

## Note for the orchestrator's staging

This round's diff also lands the two record fixes left pending by the U1 close:
entry 41's gate counts, and the roadmap's rich-text deferral note. Entry 41 also
gains a pointer to this round's entry, which records the reopening: the
decisions index is append-only, so the revision is entry 42
(`42-text-api-unification-u1b.md`) with its own index row, not an edit of 41's
decisions.

## Success criteria

- `tools/gradle build` green on JVM, JS and WasmJS, with ktlint clean and no
  zero-test regression; the counts are recorded for the entry after the gate.
- Each of the touch-point's contract items 38–43 has an executable pin, and the
  foreign-implementation pin proves the widened type is an interface.
- No existing assertion weakened, no pin deleted, no golden reference touched:
  a byte-identical golden run is the round's no-pixel-change evidence.
