# wave-50 lane B6 — the text-decoration shorthand colour drop, and two multicol float residuals

Ranked-queue items **9(b)** (`text-decoration` shorthand colour-function
drop), **3(a)** (the dropped `<br clear="all">`) and **3(b)** (the
Compose 3px orange band shift). **No device gate ran in wave 50**: every cell
number below is a measurement of the frozen `tools/titan/runs/wave49-final/`
captures against the frozen refs at
`tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`,
a converter-CLI conversion, or a JVM pin. Deltas attribute against
`corpus-v6.15`.

This `_note.md` was written by **wave-50 fix lane F3** (the lane shipped its
three artifacts without one — skeptic S1's finding).

## Files here

| file | what it is |
|---|---|
| `br-clear-differential.json` | queue 3(a): the `br[clear]` presentational-hint bake, its full 1435-test extraction differential, the corpus carriers, the frozen-ref band measurements, and the **verdict that says do not apply it alone** |
| `br-clear-fold.patch` | that bake, as a `diff -u` against `tools/titan/extract-fixture.mjs` at `747b28e4`. **Deliberately NOT applied** — see §2 |
| `queue-3b-band-shift.json` | queue 3(b): the evidence that the queue's stated mechanism is wrong, and the corrected handover |
| `_note.md` | this file |

---

## 1. Queue 9(b) — LANDED IN TREE: the `text-decoration` shorthand dropped every CSS Color 4/5 colour

**File**: `converter/src/main/kotlin/app/parsing/css/properties/shorthands/TextDecorationExpander.kt`
— **+100 / −15 (115 lines touched)**, plus the new
`converter/src/test/kotlin/app/parsing/css/properties/shorthands/TextDecorationShorthandExpansionTest.kt`
(8 tests) and `.../ClearPresentationalHintTest.kt` (4 tests). Converter suite
**514 → 526**, 0 failures (skeptic S5 re-ran `:converter:test --rerun-tasks`
independently and got the same 526).

### Two defects in one `when`

1. **Colour syntax.** The expander tested only the `#` / `rgb` / `hsl`
   prefixes, so `text-decoration: underline oklch(…)` produced NO colour
   longhand at all — the identical defect `RuleShorthandExpansion` fixed in
   wave 24 for `column-rule`/`row-rule`. It now routes every token through
   the ONE syntactic classifier, `ColorSyntaxClassifier.isColorValue`, so the
   named table, the CSS-wide keywords, hex and every Color 4/5 function all
   reach `text-decoration-color`.
2. **Thickness keywords, and this is the half that COSTS INK TODAY.**
   `<'text-decoration-thickness'>` is `auto | from-font | <length> |
   <percentage>` (css-text-decor-4 §2.4). Neither keyword was in the
   classifier, so `auto` fell through to the legacy "any remaining bare ident
   is a named colour" net and **overwrote an already-parsed colour**:
   `text-decoration: green underline auto` expanded to
   `text-decoration-color: auto`, an invalid declaration every platform
   resolves back to `currentColor`. `from-font` never reached that branch at
   all (the hyphen fails the bare-ident regex), so it was dropped in silence
   — which the new `else` arm now makes loud, per the campaign's
   no-silent-fallthrough rule.

### The measured target — and it is a DEGENERATE PASS, so all three cells move

`css-text-decor/text-decoration-shorthands-001`
(`text-decoration: green underline auto`).

| | wave49-final | green rgb(0,128,0)±8 in the capture |
|---|---|---:|
| ref | — | **48 px, all on row 85** |
| web | **P 0.9998** | **0** |
| iOS | **P 0.9990** | **0** |
| Android | **P 0.9979** | **0** |

All three cells PASS while painting a **black** underline where the test
asserts a green one: 48 px of 234 000 sits far under the SSIM floor. This is
the wave-49 "a score is not a look" class, in the smallest possible form.
The sibling `-002` paints 4800 green px on the ref and on all three captures,
which is the control that says the pipeline CAN paint this colour.
(Independently verified by skeptic S5's `green-ink-probe.mjs` and re-run by
fix lane F3; skeptic S7 holds the scorer side.)

**Prediction: all three cells WILL move, expected TOWARD the ref** — the
underline changes from `currentColor` black to `green`, which is 48 px of the
ref's own ink appearing where the ref has it. No flip is claimed (all three
already "pass"); what changes is that the pass stops being degenerate. **The
gate must not read these three cells as "unchanged" — it must confirm the 48
green px appear.**

### Corpus census — the blast radius is exactly one test

Over all 1435 `wave49-final` per-test-IR documents: **70** `TextDecorationColor`
wires, of which **exactly ONE lacks an `srgb` block** —
`text-decoration-shorthands-001`, `{"original":"auto"}` — and **ZERO**
`auto`/`from-font` thickness wires (all 33 `TextDecorationThickness` wires are
`type:"length"`). Re-derived from scratch by skeptic S5
(`tools/titan/results/wave50-S5/independent-censuses.json →
textDecorationCensus`), whose verdict is "B6's claim CONFIRMED".

`text-decoration: inherit` is pinned too, and its carrier count is honest:
exactly **4** such declarations in all of `tools/wpt/css/**`, **none** inside
the 1435-test gate set. **Known residual, named by S5 and not closed here:**
the CSS-wide-keyword hole is wider than that pin — `initial` / `unset` /
`revert` / `revert-layer` mis-expand the same way, and `revert-layer` is a
*behaviour change introduced by this wave* (previously dropped outright; now
`text-decoration-color: revert-layer`). Zero gate carriers either way.

---

## 2. Queue 3(a) — `br-clear-fold.patch` is PREPARED, MEASURED, and DELIBERATELY NOT APPLIED

**What it adds.** `brClearPresentationProps(tag, attrs, props)` implementing
HTML Rendering §15.3's UA rule set for `<br clear>` —
`[clear=left i]{clear:left}` / `[right i]{clear:right}` /
`[all i],[both i]{clear:both}` — folded in `buildNode`'s presentational-bake
slot beside `uaLinkProps` / `uaHrProps` / `htmlTablePresentationProps`, with
the same author-wins guard. `all` is FOLDED to `both` rather than forwarded,
because `clear: all` is not CSS and the converter degrades it to an untyped
`GenericProperty` (pinned by `ClearPresentationalHintTest`, and confirmed by
S5 through the converter CLI: `left|right|both|none|inline-start` → typed
`Clear`; `all|up|object|inherit|ALL` → `Generic {_unmapped:true}`).

**Differential**: 4 fixtures of 2870 change, 0 `__ref` fixtures, over all 1435
tests. The four carriers are
`CSS2/floats-clear/floats-clear-multicol-{000,001,balancing-000,balancing-001}`;
in each, exactly one `<br>` component goes
`{width:0px, height:20px}` → `{width:0px, height:0px, clear:both}`. Base IR is
identical to the real gate IR for all four.

**WHY IT IS NOT APPLIED — 12 passing cells at risk for no gain.** The fold puts
`Clear: BOTH` on the `<br>` COMPONENT and drops its height 20px → 0px.
Compose's `MulticolFloatStrip.factsFor` reads `Clear` off the strip CHILD's own
property list, so a wire on the `<br>` *grandchild* (-000) or on the `<br>`
trailing the floats inside `.container` (-001) is still ignored and the orange
stays in column 1. Predicted (unmeasured on device): the band moves from
capture rows 131-133 to 111-113 (000/001) and 111-115 → 91-95 (balancing) —
**ink moved, not ink fixed**. The four carriers' twelve cells all PASS today
(web 0.9890 / 0.9890 / 0.9870 / 0.9870 · iOS 0.9871 / 0.9871 / 0.9854 / 0.9854
· Android 0.9857 / 0.9857 / 0.9839 / 0.9839), and they pass on float-slice
dominance while putting the orange in the wrong column — the degenerate-pass
instance BACKLOG obligation #3 names.

**The missing half** is a CSS 2.1 §9.5.2 LINE-BOX clearance rung: the `<br>`'s
line is pushed to the relevant floats' bottom and the CONTAINING BLOCK'S HEIGHT
grows to hold it (CSS 2.1 §10.6.3). The box top does NOT move, which is why the
existing `clear`-on-the-box offset jump cannot model it. Growing a box is not
something the strip can do from `place()` offsets alone; it needs the child
measured with a `minHeight` of (clearance line + the box's own trailing band −
its offset), a `MulticolFloatStripMeasure` change that **cannot be verified
without a device**. Apply the fold only together with that rung.

Elsewhere in `tools/wpt/css`, 87 files carry a `<br … clear=…>` (values seen:
`all` ×194, `both` ×2, `left` ×1, plus 2 written `clear ="all"`), so the rung
is worth having.

---

## 3. Queue 3(b) — the queue's stated mechanism is WRONG; handover in `queue-3b-band-shift.json`

The queue records 3(b) as *"measured-height rounding in the Kotlin strip
walk"*. It is not. No code change was made (the repair is outside lane B6's
`columns/` ownership and cannot be verified without a device); the evidence
and the corrected diagnosis are in `queue-3b-band-shift.json`.

**Symptom** (pixel column x=237, orange rgb(255,165,0)): `-003` ref/web/iOS
y161-163, Android y158-160 (−3 px on a 3 px band); `balancing-003`
ref/web/iOS y171-175, Android y166-170 (−5 px on a 5 px band). The controls
`-002` / `balancing-002`, where the band is on the CLEARED BOX ITSELF rather
than a nested child, are Android ref-exact.

**Why it is not the strip**: columns 1 and 2 are pixel-identical to the ref in
both tests (a wrong offset or C/H would move them too); in `balancing-003`
Android still paints exactly 85 aqua rows, i.e. it balanced to
H = ceil(C/3) = 85, which requires C ∈ [253,255] — the ref's 255, not a strip
5 px short; and the shift equals the band's OWN width in both tests (3 and 5),
which a strip error cannot produce. The pure plan is pinned exact
(`MulticolFloatStripTest` FS-C2/FS-C4, and new this wave
`MulticolFloatStripRefRowsTest`, which converts the plan into capture rows and
asserts 161-163 / 171-175 against the frozen ref).

**The actual mechanism, two Compose arms**: the shape is
`.clear { clear:left; height:0 } > .bar { border-bottom: <w> solid orange }` —
the band is on a NESTED child of a zero-height box.
`SizingApplier.kt`'s `is LengthValue.Exact -> m.height(v.px.toFloat().dp)`
hands the content FIXED 0 constraints, so `.bar` measures 0 tall instead of its
CSS border-box 3/5 px (css-overflow-3 §2: `overflow: visible` content is not
clamped by its box); then `BorderSideApplier.kt`'s
`Side.BOTTOM -> Offset(0f, size.height - inset)` (inset = width/2) centres the
stroke on −w/2 at height 0, i.e. the half-open band [−w, 0) — w px ABOVE the
box. w = 3 and w = 5 reproduce the measured −3 and −5 exactly. iOS is exact
because SwiftUI's `.frame(height:)` positions and clips but does not shrink the
child's own measured size.

**Proposed repair** (device-gated, not a drive-by): a declared height must not
clamp an overflowing child — apply the Compose height with
`wrapContentHeight(Alignment.Top, unbounded = true)` or equivalent, then
re-measure the whole fixture net; blast radius is EVERY explicit-height box in
the Compose runtime. The narrower alternative — make `BorderSideApplier` refuse
to draw a side band outside the box — fixes the paint position but leaves the
box 3-5 px short. **Do not "fix" it by shifting the strip**, which is what the
queue's wrong diagnosis would have led to.

---

## 4. Queue corrections this lane proposes

1. **3(b)'s stated cause is wrong.** Replace *"measured-height rounding in the
   Kotlin strip walk"* with the zero-height-box / bottom-border-offset pair in
   §3, and move the item out of `columns/` — it belongs to `sizing/` +
   `borders/sides/`. Evidence: `queue-3b-band-shift.json`.
2. **3(a) is HALF a fix, and the half that is ready is the one that cannot
   ship alone.** Re-word the item to name the CSS 2.1 §9.5.2 line-box
   clearance rung as the blocking half, and record that the bake itself is
   already written, differential-measured and committed here
   (`br-clear-fold.patch`) so no future wave re-derives it.
3. **3(a)'s eight cells are degenerate passes, and the wave-50 gate should say
   so rather than counting them.** All 8 × 3 = 24 cells pass at 0.984-1.000
   with the orange in the wrong column; the 12 belonging to the four carriers
   are the ones any 3(a) change touches.
4. **9(b) is DONE in the converter and its target is a degenerate pass.** When
   the item is closed, record the expected *visible* change (48 green px on
   row 85 of `text-decoration-shorthands-001`), not a score delta — all three
   cells already "pass".
5. **New, from S5: the CSS-wide-keyword hole in this expander is wider than
   the `inherit` pin** (`initial`/`unset`/`revert`/`revert-layer`), and
   `revert-layer` changed behaviour this wave. Zero gate carriers; worth one
   queue line so it is not rediscovered.
