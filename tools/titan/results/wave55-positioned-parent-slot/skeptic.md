TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-skeptic-L3

# wave 55 · L3 positioned-parent-slot — SKEPTIC report

**Verdict: CLAIM-HOLDS.** Every executed claim reproduced. The patches apply on 611c9f93 in landing order, and every
`patch:` sha256 matches. My own census, run with my own scripts, gives the same radius as `expectations.json`, with no
growth. All 13 pin mutations the lane names go red in the landed state and green again after a byte-exact restore. All
four revert states compile with the claimed test counts. Every cited cell matches `cells.mjs`.

There is one should-fix, and it does not block the gate. The U2 mechanism itself is not pinned on the JVM: two
mutations of `PositionedParentRtlOrigin.originModifier()` that switch off the origin leave the whole focused suite green.
Pin (c) covers the pure model `inkLeftPx`, not the modifier that moves the pixels. The stage-1 device keys are the real
evidence for U2, and they would catch it. Four nits follow. **mustFixBeforeGate: false.**

The skeptic worktree is a detached `git worktree add` of 611c9f93, set up per PLAN §0. It was never committed, checked
out, stashed or reset. Each state was built by `git apply` / `git apply -R` of the delivered patches. It is back in the
full state (unit-U1 + seam-1 + unit-U2): the gate sha256 is 754085db…, and 8 lane paths are dirty in it. Its
`tools/titan/runs/{wave53,wave54}-final` are gitignored symlinks to the shared tree. They exist so that the
whole-corpus tripwire `OutOfFlowContainingBlockTest` EXECUTES (8 tests, 0 skipped, in every run).

**Conflict in the task text.** Rule (1) says to write my `TREES:` line into the lane's `_note.md`. The MUST-DO says
"Never edit the lane's patches or note". I did not touch `_note.md`. My `TREES:` line is the first line of this file and
of `skeptic/mutation-log.txt`.

## Repros (all executed; evidence files under `skeptic/`)

| # | what | command (skeptic worktree unless noted) | result |
|---|---|---|---|
| R1 | patch sha256 vs note | `shasum -a 256 *.patch` vs `grep '^patch:' _note.md` | 3/3 equal (b22a582a… / 1f9c4717… / 13c8ee01…) |
| R2 | apply on 611c9f93 in landing order | `git apply --check` + `git apply` unit-U1 → seam-1 → unit-U2 | clean. Result bytes equal the builder worktree's (8/8 sha256) |
| R3 | landing read-only check | shared tree: `land-units.sh --check --only L3` | 0 errors, 1 warning (my own skeptic files < 120 s). Cumulative OK, revert dry-runs [U2,U1] and [U2] OK (`skeptic/land-units-check-L3.txt`) |
| R4 | focused suite, full state | `:runtime:testDebugUnitTest --rerun` with the PLAN §2 L3 "May run" filters | 60 tests, 0 failures, 0 skipped |
| R5 | pin mutations, full state | `skeptic/skeptic-mutate.py` (15 mutations, bracketed) | 13 red / 2 GREEN (see Pins). Every restore sha256 equals pre (20/20). Every restore re-run green |
| R6 | U1 pin mutations, U1-only state | the same script with unit-U2 reverse-applied | 5/5 red, pin (b) included (55 tests). Restored green |
| R7 | revert states | `revert-states.sh`: `git apply -R` U2 → seam-1 → U1, suite in each, then re-apply | full 60/0/0 · U1 55/0/0 · unit-U1 without seam-1 (rule 2b) 53/0/0 · base 50/0/0 (git status 0 entries) · restored full 60/0/0 (`skeptic/revert-states.txt`) |
| R8 | own census | `skeptic/skeptic-census.sh` (JVM half `SkepticL3HostDump.kt` in `:app`, Python half `skeptic-census.py`) | identical to expectations, no growth (below; `skeptic/skeptic-census.out.txt`) |
| R9 | geometry self-test | `geometry-gate.py wave54-final --base <b> --lanes L3 --self-test` | base wave54-final: exit 0, HOLDS. base wave55-open: exit 0, HOLDS. Gating 7/7 FAIL, report 2/2 FAIL, control 32/32 PASS. base wave54-open, as the task spelled it: exit 1 on `[C] bidi-lines-002 android`, a documented wave-54 mover (cells.mjs: wave54-open P 0.9534 → wave54-final P 0.9818). Not an L3 defect |
| R10 | cells | `cells.mjs '<terms>' wave54-final` | every cited cell matches (list below) |
| R11 | verbatim pins | parse the test wires and diff them with the per-test IR | U1b: 17/17 boxes verbatim. U2d: 8/8 boxes verbatim. No `Right` / `Bottom` / `InlineSize` / `Inset*` is hidden by the declaration subset |
| R12 | PNGs | ref, web, iOS and android strips of all 9 carriers plus the 2 non-identity controls | geometry as described (below) |

## Census (R8): my own, independent of `census/`

- **JVM half.** It decodes every wave54-final per-test IR the way the harness does (`IRDocumentDecoder` →
  `SlotComposer`). It then applies the harness ROOT REWRITE that ComposedCaptureCanvas applies before `hostActivates`
  (`withCanvasOwnedBodyMargin` → `withUaBlockMarginOnHoistedRoot` → `composedCanvasRoots`). The lane's census did not
  apply this rewrite. The JVM half records `CanvasRootHoist.hostActivates` on the raw and on the rewritten roots, plus
  the runtime's own Box-branch inputs and `applies`.
- **Python half.** It recomputes the decision table from the PLAN text, not by calling the gate. It uses two models of
  Compose's used direction:
  - A: the DOM chain;
  - B: the chain cut at canvas-hoisted boxes.
- **Results:**
  - 1435 documents. The host is inactive on 1221. Raw and rewritten host flags differ on 0 documents. The direction
    models disagree on 0 documents.
  - Applying Column-loop pairs: 138 children, 107 parents, 61 documents. Box-branch pairs skipped: 37.
  - **U1:** 56 children / 46 parents / 16 documents. **U2:** 13 / 11 / 7. **Union:** 21 documents.
  - RTL parents: 11 in 7 documents. U1 RTL-guard withholds: 9. U2 declines: 0.
- **Cross-check against expectations:**
  - U1 captures not reached by me: 0. U2 captures not reached: 0. U2 reached but not listed: 0.
  - The 13 U1-reached documents outside the carriers are exactly the brief's 13 controls (bidi-tab-001 counts through
    its LTR rows).
  - None of the reached parents has its own text, runs, pseudos or a marker. Every reached child is ABSOLUTE.
- **The two controls that carry a non-identity class, checked by hand on the PNGs:**
  - `block-end-aligned-abspos-nested`: CLAMP. The parent is 0×0, so the 50×50 child, clamped today, now paints at
    (16, 138)–(66, 188). That lies inside the green 100×100 at (16, 88)–(116, 188), and both are rgb(0, 128, 0), so it
    is green on green.
  - `text-decoration-subelements-002`: AUTO. The parents do not paint and the child is `white-space: nowrap`. The
    relative grandparent loses its children's reported heights, but it is the last root and the 568-px canvas floor
    dominates.
  - Both stay identity.
- **Verdict: CENSUS IDENTICAL, no regeneration need.**

## Pins (R5 / R6): brackets in `skeptic/mutation-log.txt`

| pin | mutation (state) | result |
|---|---|---|
| U1 (a) | drop `composedCapture &&` (full; U1) | red: U1a, U1c |
| U1 (a)+(b) | drop `!parentRtl` = PLAIN (U1 state) | red: U1a, **U1b**, U1c |
| U1 (a)+(b) | drop `!parentRtl` = PLAIN (**full** state) | red: U1a, U1c. **U1b stays green**: U2's RTL branch returns MOUNT_RTL_ORIGIN before `engages` is consulted (nit N1) |
| U1 (a) | collapse to `hostActive` (full; U1) | red: U1a, U1b, U1c |
| U1 (c)+(d) | ComponentRenderer.kt back to its 611c9f93 bytes (full; U1) | red: PW1, PW2, SeamReachabilityTest names `[layout/position/PositionedParentSlotGate.kt]` |
| breadcrumb | drop `markUnhandled(RTL_UNMOUNTED_BREADCRUMB)` (full; U1) | red: U1c |
| U2 (a) | `originPx` sign flipped | red: U2a, U2c |
| U2 (b) | drop the px-Width rule | red: U2b, U2e, U1c |
| U2 (c) | model `inkLeftPx` without the origin | red: U2c, "expected 70.0 but was 170.0", which reproduces the lane's message |
| U2 (c) | shared `placedXPx` mirrored (placeRelative semantics) | red: U2c (270) |
| U2 (c) | literal `placeable.placeRelative(` in `originModifier` | red: U2c (source pin) |
| U2 (d) | drop the gate's RTL branch | red: U2d, U2e |
| U2 (e) | default `rtlOrigin = true` | red: PositionedParentFlowSlotTest one-element pin, U2e, U1c |
| breadcrumb | drop `recordRtlOriginDecline` in the MOUNT branch | red: U2e |
| **mechanism** | `originModifier`: origin from `constraints.minWidth` | **GREEN, 60/0/0** (S1) |
| **mechanism** | `originModifier`: `val origin: Int? = null` (the device no-op) | **GREEN, 60/0/0** (S1) |

## Defects, ranked

**S1 (should-fix): the U2 mechanism is unpinned on the JVM.**
- `PositionedParentRtlOrigin.originModifier()` is the code that moves pixels. Its origin computation
  `val origin = originPx(if (constraints.hasBoundedWidth) constraints.maxWidth else null)` can be replaced by `null`,
  or read from `minWidth`, and the whole focused suite stays green.
- The lane's "drop the origin" mutation edited the pure model `inkLeftPx`, which shares only `originPx` / `placedXPx`
  with the modifier. The source pin only asserts the `place(...)` line and the absence of `placeRelative`.
- Stage 1 would catch a broken origin. The gating keys `[S] dynamic-offset-rtl-001`, `[S] bidi-tab-001 rtl-rows` and
  `[S] dir-style-02a row6 / row9` fail on today's picture, which is exactly what a no-op origin reproduces. So this is
  not ship-blocking. But PLAN §0 "every pin proven able to fail" is met only for the model, not for the mechanism.
- **Fix:**
  - Add a pure `internal fun boundedMaxPx(c: Constraints): Int? = if (c.hasBoundedWidth) c.maxWidth else null`.
    `androidx.compose.ui.unit.Constraints` is a JVM-safe value class.
  - Use it in `originModifier` and in `inkLeftPx`.
  - Pin `boundedMaxPx(Constraints.fixedWidth(358)) == 358` and `boundedMaxPx(Constraints()) == null`.
  - Extend the U2c source pin to require `originPx(boundedMaxPx(constraints))`.
  - Re-execute both of my mechanism mutations (red).

**N1 (nit): pin (b) is inert in the landed state.** In the full state, PLAIN (drop `!parentRtl`) leaves U1b green. The
neverShip is still guarded, because U1a and U1c turn red. On the corpus, PLAIN in the full state also changes nothing,
since all 11 RTL parents take the origin first. The note labels its [U1-a2-b-final] run as the U1-only state, which is
accurate. A one-line note in the pin table would stop a reader from assuming (b) guards the landed tree.

**N2 (nit, perf, runtime product path): `applies` now runs on every block child.**
- `slotModifier` → `decide` evaluates `PositionedParentFlowSlot.applies` for every block child, on every composition,
  on every path: the production runtime, the dark stage and the inbox. On the IDENTITY branch it is evaluated twice.
- Before wave 55, the `LocalActive.current &&` short-circuit skipped it entirely outside the capture.
- Fix: in `slotModifier`, return `Modifier` first when `!hostActive && !composedCapture`. The truth table makes that
  identity anyway.

**N3 (nit, latent R1): `hasPxWidth` ignores the writing mode.**
- It treats `InlineSize` as the physical width whatever the writing mode. ComponentRenderer maps `InlineSize` to Height
  under a vertical writing mode (:1455 / :1458).
- A vertical RTL parent that declares only `inline-size: <px>` would take the origin with W ≠ maxWidth.
- There are 0 corpus cases. The vrl-rtl abs parents declare `Width` and carry no `WritingMode`, because the body-root
  holds it as a sibling root.

**N4 (nit): one unbreadcrumbed branch.** When `originModifier()` sees an unbounded width, it keeps today's placement
without a breadcrumb. While `hasPxWidth` holds, a px Width fixes the Column's constraints, so the branch is practically
unreachable. Worth one comment, or a log-once.

**Not a defect, recorded.** `"transforms/TransformInheritance.kt",` is the last list entry at HEAD, so L3's pinned
anchor sits at the list end at 611c9f93. That is the anchor the PLAN pins, and `land-units.sh --check` proves the
orders.

## Cells (R10, wave54-final), all as cited

| cell | android | iOS |
|---|---|---|
| dir-style-02a | f 0.9425 | f 0.9455 |
| bidi-tab-001 | P 0.9805 | |
| dynamic-offset-rtl-001 | P 0.9952 | P 0.9955 |
| dynamic-offset-rtl-002 | P 0.9953 | |
| dynamic-offset-vrl-rtl-001 | P 0.9952 | |
| dynamic-offset-vrl-rtl-002 | P 0.9952 | |
| anchor-position-multicol-nested-001 | f 0.9573 | |
| anchor-position-multicol-004 | P 0.9686 | |
| hyphenate-character-005 | f 0.9474 | |

The control cells also match: block-end-aligned P 0.9967, overflow-zero P 0.9984, bidi-lines-002 P 0.9818,
paint-order ×2 P 0.9791, dir-selector-change-003 / -004 P 0.9994 / 0.9944, counter-suffix P 0.989,
text-decoration-subelements-002 f 0.9047, boundary-shaping-009 f 0.7531. attachment-local-positioning-3 / -4 are
unscored.

## PNGs (R12)

- **dir-style-02a android:** rows 6 and 9 are mirrored at x246–373 and clipped at the 374 edge ("This element is rt").
  Row 7 has no dot. iOS mirrors rows 6 and 9 as well, which is R7 and not this lane's.
- **dynamic-offset-rtl-001 / vrl-rtl-001 android:** magenta at x186 against the ref's x86.
- **rtl-002 android:** x126 against the wire's x26. Web paints 26 but the ref paints 86: the ref is taken after the
  script runs.
- **vrl-rtl-002:** web paints at the bottom while the ref paints at the top.
- **bidi-tab-001 android:** the `0` of the four RTL rows is mirrored, and row 3's sits outside the yellow box.
- **multicol-004 android:** P with red visible.
- **nested-001 android:** stacked squares.

**Honesty (h).**
- rtl-002 / vrl-rtl-002 (the ref is post-script), multicol-004 (red visible) and counter-suffix android are in
  `stayDegenerateEvenIfPass`. The note says rtl-002 "stays DEGENERATE".
- nested-001 is f→f under the coverage veto and is "NOT a flip".
- `pictureCorrectTally` claims only bidi-tab-001, rtl-001 and vrl-rtl-001 android, each behind its geometry key.
- The dir-style-02a flip is gated by its three row keys. Nothing is mislabelled.

## House rules / ownership (e) and unit paths (g)

- **Paths per unit.** unit-U1 = {gate NEW, FlowSlot (comment), GateTest NEW}. seam-1 = {ComponentRenderer.kt,
  SeamWiringTest NEW, SeamReachabilityTest}. unit-U2 = {RtlOrigin NEW, gate, FlowSlot, RtlOriginTest NEW,
  SeamReachabilityTest}. These equal the note's unit sections, the PLAN "Owned" list and the manifest (seq 19:
  unit-U1 → seam-1; seq 20: unit-U2).
- **Forbidden rules.** No rename or copy. The seam patch touches one seam file. No path is shared between a unit patch
  and its own seam patch.
- **Seam header.** It names 611c9f932bcf…, the seam file's sha256 at base 2445d9ea… (recomputed: equal), and
  SeamReachabilityTest at base 7a1b061f… (equal).
- **Rule 2b.** "unit-U1 without seam-1" compiles: 53/0/0.
- **Sizes.** Gate 198 lines, RtlOrigin 178, FlowSlot 169 → 179, SeamReachabilityTest 239 → 241, ComponentRenderer
  8488 → 8492 (seam only). New logic stays ≤ 200.
- **Hygiene.** Every statement is commented; parameter lists and imports are not, which is house style. No scratchpad
  pointer and no "engine" in the patches, `cut-patches.sh` or `census/`. No println, Log, TODO or debug leftovers.
- **Callers.** The only caller of `slotModifier` is seam 1. The only callers of `mount(…, rtlOrigin)` are in the gate.

## Not verified

- **Nothing on a device.** No Chromium, emulator or simulator. My census predicts decisions and classes, not pixels.
- **Identity controls.** The "byte-identical" claim for the 13 U1 controls rests on the shape classes plus two PNG
  checks. It is not proven: stage 1 / stage 2 `[C]` keys decide it.
- **Replays.** I did not re-run the brief's replays or `probes-teeth*`. The plan-skeptic rounds own those.
- **Census approximations, both halves.**
  - `renderBlockChild` is assumed reached for every non-Box-branch parent. The float / inline-atom / fold segment
    paths could route a child elsewhere, but none of the reached parents has text or runs.
  - Transform-CB is read through the runtime's public `establishes` predicates.
- **Other lanes.** I did not compose with other lanes' patches. The landing `--check` covered L3 alone.
