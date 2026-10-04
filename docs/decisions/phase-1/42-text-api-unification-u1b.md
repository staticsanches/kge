## 2026-10-04 — Text API unification, round U1b: names, proportionality and the named core faces

The addendum that answers the owner's close review of U1 (commit `e4aa440`),
which found the vocabulary incomplete in three ways: neither `KGEFont.Family`
nor `KGEFont.Face` carried a name, although the model describes faces as named
designs and U3 resolves a load by family and subfamily name; nothing answered
whether a face is monospaced, although rejecting the `*Prop` operations rests on
proportionality being a property of the chosen face; and the touch-point promised
an application could reach the core `proportional` face while the API offered
only `faces` and `defaultFace`, so reaching it meant filtering by identity. It
runs before U2 because U2 generates the bundled fixtures from a manifest and U3
owns loading and naming: the vocabulary ships complete and those rounds fill it
instead of reopening it. Touch-point revision and contract items 38–43:
`docs/plans/2026-10-04-text-api-unification-touchpoint.md`; micro-plan:
`docs/plans/2026-10-04-text-api-unification-u1b-microplan.md`.

### Shipped

- **`KGEFont.Family.name` and `KGEFont.Face.name`**, the latter unique within
  its family. The built-in family reuses its sheet's own name string
  (`KGE bitmap font`) and names its faces `Monospaced` and `Proportional`, in
  that order.
- **`KGEFont.Face.monospaced`**, declared by the face rather than derived; the
  monospaced core face answers true and the proportional one false.
- **`KGECoreFontFamily : KGEFont.Family`**, public, with `monospaced` and
  `proportional`. The concrete family stays private behind it, and
  `KGECoreFontService.createResources` returns the interface, so the seam keeps
  accepting a foreign implementation.
- **The open check guards consumption, not metadata.** `name`, `monospaced`,
  `axes`, `size` and `axisCoordinates` are inert values that answer after a
  close, while the handles that reach the payload (`faces`, `defaultFace`,
  `monospaced`, `proportional`, `family`, `font(...)`, a configured font's `face`
  and `family`) and the
  operations that read it (`measureText`, the draws) keep failing fast. Both
  halves are pinned in the two close scenarios — after the family closes and
  after the lease closes — rather than once per accessor.
- **Ten identity filters replaced** by `family.proportional` where the family's
  static type is the core family; the pins that assert `faces` itself stay.
- The round also lands the two record fixes left pending by the U1 close: entry
  41's gate counts and the roadmap's rich-text deferral note.

### Decisions

- **A name is intrinsic to the payload.** A TTF family takes the preferred
  English family name from the `name` table and a face its subfamily name;
  bundle provenance metadata still belongs to `kge-font-roboto`, so the two do
  not collide.
- **`monospaced` is declared, never derived.** U3 populates it for TTF faces from
  the payload's own metric (`post.isFixedPitch`).
- **The widened type is an interface, not the concrete family.** The consumer
  that justifies the widening is the application selecting a core design; typing
  the seam on the concrete class would have made it unoverridable, which the
  foreign-implementation pin now prevents.
- **The rule supersedes three U1 pins** that asserted `Face.axes`, `KGEFont.size`
  and `KGEFont.axisCoordinates` fail after a close: a descriptor or a size is
  never released, so reading it consumes nothing, and the guard belongs to what
  the close released.
- Rejected: a general `Family.face(name)` lookup, which is stringly-typed and
  answers a different question than the route the touch-point named; and leaving
  the core family internal, which would have left that route non-existent.

### Not in this round

Any TTF value: the module publishes no `KGEFont` implementation yet, so U3
supplies names and spacing from the payload. The bundle manifest (U2) and every
drawing or measuring behaviour are untouched.

### Verification

`tools/gradle build` green on the staged tree (the gate: every target's tests,
ktlint, the metadata/kLIB compilation and `buildSrcCheck`). Core suites report
744, 779 and 779 tests on jvm, js and wasmJs with no failures — the three jvm
skips are the pre-existing GLFW-window smoke tests; the benchmark reports 21 on
jvm and 22 on wasmJs, and `kge-text-ttf` 107, 110 and 110, all unchanged. Every
golden reference is byte-identical to the U1 commit's, which is this round's
no-pixel-change evidence. The round closes only when the two review axes pass on
this exact staged tree, and the marker names the report that carries its hash.
