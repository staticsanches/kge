# KGE

Guidance for the orchestrating agent in this repository. The rules are
harness-neutral: `.opencode/` holds this project's sub-agent definitions and
review state, and those definitions are **binding**. Under a harness with no
sub-agent definitions of its own, the content of the definition file is the
prompt of the general sub-agent that takes the role; the order and the
completion criteria do not change.

## Project

**KGE** — a Kotlin Multiplatform game engine, a free adaptation of
[olcPixelGameEngine](https://github.com/OneLoneCoder/olcPixelGameEngine), for
JVM (LWJGL/GLFW/OpenGL) and web (WebGL2 via kotlin-wrappers).

Greenfield restructure on the work branch: concepts are rebuilt one at a time,
and the previous engine lives in `main` (evidence, not a mandate:
`git show main:<path>`). **Where we are and what is next come from the roadmap's
ordering and the decisions index — this file carries no per-round log.**

## Read first

- `docs/plans/2026-08-31-kge-restructure-roadmap.md` — the macro roadmap:
  concept list, guiding principles, decision lenses, ordering, per-concept
  workflow. The plan is the roadmap; detail is not frozen ahead.
- `docs/decisions/phase-1.md` — append-only log of verified facts and
  per-concept decisions, written as an **index**: read the index, then only the
  relevant chunk under `docs/decisions/phase-1/`, never every chunk. Entries are
  settled; reopen one only with evidence.

Older plans and specs were deleted — git history is the archive — and the dated
documents under `docs/plans/` stay authoritative for their own concept.

**Settled — do not redo without new evidence:** the font-library research
(`docs/decisions/phase-1/14-text-r6.md`), the R6 touch-point decisions
(`docs/plans/2026-09-16-r6-text-touchpoint.md`), the font-bundle findings
(`docs/plans/2026-09-17-font-bundle-findings.md`) and the renderer-lever
measurements (`docs/plans/2026-09-20-renderer-lever-measurements.md`).

## Role

The session agent is the **orchestrator**. It owns the touch-point, the
micro-plan, the gate, the review dispatch, the marker and the commit, and it
decides what happens next. It writes **no production and no test code**:
implementation always goes to a `tdd-developer` sub-agent and each review axis
to a fresh review sub-agent. Code written in the orchestrator's own context
defeats the separation of duties the flow exists for — it is a process defect,
not a saving.

## Round flow

Every round runs this sequence:

1. **Touch-point** — design confirmation; open items decided against the three
   sources of truth (below) and each divergence recorded.
2. **Micro-plan** — 1–2 pages of TDD steps, written just-in-time; its test code
   is the contract.
3. **Implement — dispatched, never in the orchestrator's context.** Give a
   `tdd-developer` sub-agent (`.opencode/agent/tdd-developer.md`) the micro-plan
   path, the step or slice, the files, the success criteria (the tests that must
   pass) and the already-resolved decisions. It runs strict test-first per
   feature — failing test → run (red) → implement → run (green) — and never
   commits or edits the spec; a wrong or ambiguous plan comes back as a blocker.
   The orchestrator steers, resolves blockers and re-dispatches; it does not take
   the code over.
4. **Gate** — `tools/gradle build` (below), once, before the reviews.
5. **Review** — two axes, two fresh sub-agents in parallel, never the model that
   produced the diff: Standards and Spec conformance, defined by
   `.opencode/agent/review-standards.md` and `.opencode/agent/review-spec.md`.
   They review the diff statically and never run the gate. The Spec axis audits
   the three sources of truth — a plan-conformance review does not catch a defect
   of the plan itself — lists recorded divergences as accepted rather than
   suppressed, and covers the resource, API and scope disciplines below.
   Findings are fixed in a delta and re-reviewed, or dispositioned in the
   decisions entry; they are not chased past the round cap.
6. **Decisions entry** — the round's entry (`docs/decisions/phase-1/<n>-<name>.md`
   plus its row in the index), staged with the round **before the review round
   whose report the marker will name**. The commit gate recomputes
   `git diff --cached <base> | shasum` and requires that report to carry the
   matching `tree:` line, so an entry staged after the final review round cannot
   be committed with the round — it costs one more review round instead.
7. **Marker → commit → hand back** — the marker at `.opencode/review-passed` is
   `<base-commit> <branch> <rounds> <status> <report-file>`: `<base-commit>` is
   the commit the round's diff is taken from, and the named report must contain
   the staged diff's `tree:` line. Then one squashed commit for the round. Marker
   write and commit stay separate commands — under opencode the commit gate
   (`.opencode/plugin/review-gate.ts`) reads the marker before the command runs.
   The orchestrator then stops: it never pushes, and the owner reviews, pushes
   and may implement parts personally.
8. **Cleanup** — delete the round's scratch from the gitignored `.tmp/` — logs,
   extracted trees, downloaded sources, spike output — so nothing accumulates
   across rounds.

At most **three** review rounds per close: a PASS closes it and records residual
minors in the report instead of chasing another round; a third FAIL escalates to
the owner.

## Rules

- **Three sources of truth** — consulted at every touch-point, micro-plan and
  review. **(1) olcPixelGameEngine v2.30**
  (`~/workspace/olcPixelGameEngine/olcPixelGameEngine.h`) is the behavior
  reference: a divergence is a finding unless a rationale is recorded.
  **(2) `main`** is the previous Kotlin implementation — not a mandate, but
  evidence to mine for Kotlin-level solutions; a regression against it is a
  finding unless a recorded rationale justifies it. **(3) Kotlin/KMP** is the
  realization medium: respect its constraints (value-class boxing, web `Long`,
  `expect`/`actual`, browser single-threading) and write idiomatic Kotlin rather
  than a C++ re-enactment — olc parity is behavioral, the form is ours.
- **Ship concepts whole**: never a slice, and never a provisional API a later
  concept must break — restructure at the concept checkpoint.
- **Gate**: `tools/gradle build` = `check` + `assemble`, so it covers every
  target's tests, ktlint and the intermediate-source-set metadata/kLIB that
  `allTests` misses. Test tasks always execute (up-to-dateness is disabled), so a
  plain `build` is already forced; `ktlintFormat` stays manual. **What that does
  not buy:** the zero-test guard fires on a zero only, so a suite that reports
  *fewer* tests than it has still exits 0 — check the reported counts, and force
  the web target with `--rerun-tasks` when one is short or missing (decisions log,
  chunk 35).
- **Resource discipline**: every failure path of code that allocated a resource
  closes it; allocate-then-construct call sites wrap construction in
  `letClosingIfFailed`.
- **API discipline**: every public parameter has an observable effect, pinned by
  a test.
- **Scope discipline**: `private` is the default, and **widening is what needs
  justifying** — the justification names a consumer that must reach it.
  `internal` is for a real cross-file or cross-source-set consumer and is a cost,
  not a safe landing: inside the module it is as reachable as `public`.
  `public` is only the extension contract (a seam, or a type an extender must
  name). A concrete implementation shared across files is a `private` type behind
  an `internal` factory, never an `internal` type. The decomposition decides how
  much *can* be private, so the width of a platform seam is a touch-point
  decision: `expect`/`actual` makes everything it carries module-visible, so a
  seam is one internal entry point and the rest stays file-private. Recorded
  trade: a `private` production type is unreachable from `commonTest` — pin
  behavior through the exposed API, not the implementation. In test source sets
  `internal` is a no-op: `private` first, then no modifier.
- **KDoc discipline**: at most two lines per comment or KDoc block, the contract
  and the non-obvious only. Rationale lives in the decisions log, not in the
  code; public KDoc reads on its own, without naming `internal`/`private`
  concepts or restating the diff or the plan.
- **Dependencies**: use the current release at add-time unless a known problem
  exists; record non-obvious findings in the decisions log.
- **Language**: docs and commit messages in English; committed documents carry
  no personal quotes — decisions are recorded by rationale, not by who said
  them.
- **Commit messages**: one-line subject, at most two short body lines, every line
  within 80 columns, carrying only the why and the non-obvious consequences. No
  gate history, test counts, review narrative, change recap or doc pointers.
- **Delivery**: the agent commits the round and never pushes; the owner reviews,
  pushes, and may implement parts personally. The next round starts after that.

## Commands

```bash
tools/gradle build               # the gate; identical to ./gradlew build
tools/gradle :kge-core:allTests  # tests only (jvm + js browser + wasmJs browser)
tools/gradle :kge-core:jvmTest   # JVM only
```

Agents run `tools/gradle` in place of `./gradlew`: it needs no escalation, and
outside a confining sandbox it is exactly the wrapper. JDK 21 toolchain; bytecode
target 11.
