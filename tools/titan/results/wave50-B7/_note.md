# wave-50 lane B7 — three verified extractor patches, deferred to lane B1

`tools/titan/extract-fixture.mjs` is lane B1's file this wave, so B7's three
repairs ship here as patches instead of as tree edits. Each one was applied to
a private copy of the extractor, measured with a **full re-extraction
differential over all 1435 tests of `tools/titan/runs/wave49-final`**, unit
pinned, and mutation-proved. Nothing in this directory has been applied to the
tree; `git apply` the patches in any order (verified: all three apply together
against the tree as of 2026-09-14 21:07 UTC).

| patch | BACKLOG item | changes N tests of 1435 | predicted flips (as CORRECTED in wave 50 by fix lane F3 — see each section) |
|---|---|---|---|
| `ua-hr-separator-box.patch` | 5(a) | 4 (3 substantive, 1 breadcrumb-only) | **+1 web certain, +2 natives conditional** on the baked rule painting (was: +3) |
| `root-scope-conflict.patch` | 5(g) | 2 | +3, plus a 0.0001→~1 repair |
| `table-duplicate-text.patch` | 5(g), 1(b) | 5 | **0 of its own** — byte-for-byte SUBSUMED by lane B4's autoclose; the cells are B4's and must be counted ONCE (was: +6 measured-safe, +1–3 likely) |

Reproduce any differential: `node --test` the suites each patch adds, then
re-extract with the patch applied and diff the fixtures test-by-test against
the un-patched extractor over `tools/titan/runs/wave49-final/sections/*/tests.list`
(the static path only — no `--post-load` / `--bidi-bake` / `--vt-bake`, so the
comparison isolates the change). Per-test cells and the three censuses are in
`censuses.json`.

**GATE NOTE — three of the 17 extractor-changed documents do NOT take the
static path (wave-50 fix lane F6, on skeptic S2's differential).** The
differentials in this directory are static-path only, and for these three the
static differential does not predict the gate result:
`css-pseudo/active-selection-057` and `css-tables/border-collapse-dynamic-col-001`
run post-load-extracted (`[post-load: extracted]` and
`[post-load: extracted+structure]` on their wave49-final `extract.log` lines),
and `css-counter-styles/counter-suffix` runs through
`[bidi-bake: baked — 2 roots, 4 runs] [stale-runs dropped: 2]
[counter-bake: baked — 10 markers, 2 declined]`. At the gate, read
counter-suffix's NEW `extract.log` line first — the bake stages decide what the
extractor's change is even allowed to reach.

---

## 1. `ua-hr-separator-box.patch` — BACKLOG queue 5(a)

**Defect.** A rule-less `<hr>` matched no author rule and no own text, so it
fell through `buildNode`'s "empty node" branch and shipped
`{width:100px, height:100px}` — a 100px white square where the browser paints
a 2px rule. `wave49-final css-images/gradient/gradient-hue-direction` carries
three of them: capture 390×**894** against the 390×600 frozen ref, i.e.
+294px = 3 × (100 − 2) exactly. Cells: web f 0.6241 · iOS f 0.6235 · Android
f 0.6230.

**Fix.** A UA-origin bake (`UA_HR_PROPS`) in the same slot as the wave-30 UA
link bake: `display:block; height:0; box-sizing:content-box; border:1px inset
#eeeeee; margin-block:8px; overflow:hidden`. The colours are **measured off the
frozen ref PNG**, not guessed — the hr at y=188/189, x 16–373 of
`tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/css-images/gradient__gradient-hue-direction.png`
is rgb(154,154,154) over rgb(238,238,238) with rgb(196,196,196) mitres, which
is exactly `border: 1px inset #EEEEEE` under Blink's own two-tone model
(`Dark(0xEE)`: v = 0.93333, multiplier max(0,(v−0.33)/v) = 0.64643, 238 ×
0.64643 = 153.8 → 154; light gate contrast(#EEE,#9A9A9A) = 2.43 ≥ 1.75 ⇒ no
lift ⇒ 238). `box-sizing: content-box` is stated explicitly because Compose
only inflates a declared size by the padding+border band for an EXPLICIT
content-box (`SizingConfig.contentBoxInflateY`); without it the 0px would be
the frame height on Android and the rule would paint nothing.

**The bake is atomic.** Any author declaration naming any key declines the
whole box and stamps `ua-hr-separator-declined`. That is what protects
`css-pseudo/active-selection-057`, the only one of the four `<hr>` carriers
that PASSES today (web P 1.0000 · iOS P 0.9994 · Android P 0.9986): it declares
`height: 100px` and `border: none 0px`, so it declines and its **properties bag
is byte-identical** — the differential's only delta there is the `_lossy`
breadcrumb, which never reaches the IR wire (verified: `_lossy` appears in the
IR only inside raw `pseudos` bags).

**Differential.** 4 tests of 1435 — the exact `<hr>` census. Converted output
validated against `schema/ir-v2.schema.json` (`makeValidatorV2` → ok); every UA
key maps to a known IR type (`Display/Height/BoxSizing/BorderXWidth/
BorderXStyle/BorderXColor/MarginTop/MarginBottom/OverflowX/OverflowY`), zero
generic properties.

**Predicted flip — RE-SCOPED by wave-50 fix lane F3 on skeptic S7's replay:
+1 web CERTAIN, +2 natives CONDITIONAL on the baked rule actually painting.**

The original claim here was "+3 cells, gradient-hue-direction f→P on all
three", on the grounds that removing the 98/196/294 px displacement leaves the
web capture matching the ref with zero pixels differing by more than 8 and
maxDelta ≤ 1 **over every gradient band**. S7 reproduced that band statement
exactly — and then measured the FULL FRAME, which the band statement does not
cover. Scored through the campaign's own `diffComposedVsRef`:

| variant | web | iOS | Android |
|---|---|---|---|
| **shift only** (displacement removed, NO rule painted) | **P 0.9521** | **f 0.9491** | **f 0.9495** |
| shift + the ref's 2 px rule painted | P 1.0000 | P 0.9970 | P 0.9973 |

Full-frame, the shift-only web copy still differs on **2148 px at maxDelta
101** — exactly the 3 × 2 × 358 px of missing separator rule. So the three
`<hr>` squares are the whole of the *displacement*, but not the whole of the
failure: **both natives land BELOW the bar without a painted rule**, at
0.9491 / 0.9495, and only web clears it on geometry alone.

S7's sensitivity band says ANY painted rule is enough — full-width
0.9952/0.9956, flat grey 0.9749/0.9753, one row only 0.9960/0.9963, pure black
0.9506/0.9510 — so the two native cells hinge ENTIRELY on the baked
`border: 1px inset #eeeeee` box actually painting on Compose and SwiftUI.
**That is a runtime question this patch does not answer**; wave-50 fix lanes
F1 (Compose) and F2 (SwiftUI) pin it. Measured fallback if the rule does not
paint: **iOS 0.9491 · Android 0.9495**, i.e. the natives stay failing and the
wave gains one cell, not three.

The other two changed tests (direction-upright-001/-002) gain a ~2px rule each
and are not claimed to flip.

**Pins** (`tools/titan/extract-fixture-ua-hr.test.mjs`, 7 tests, VERBATIM
corpus payloads). Mutation-proved: tag gate inverted → 6/7 fail; bake made
non-atomic → 3 fail; `border-color` moved off the measured value → 3 fail.

---

## 2. `root-scope-conflict.patch` — BACKLOG queue 5(g), first half

**Defect.** `propsForBodyRoot` folds `html`-scope and `body`-scope declarations
onto ONE synthetic component in source order. Those are two different elements:
css-cascade-5 §6 orders declarations that apply to a single element, so source
order let whichever rule came last silently delete the other's value.

`css-contain/contain-html-overflow-002`: `html { height: 400px }` beat
`body { height: 200px }`, so the root came out 200×400 carrying body's
`overflow: hidden` — and the 200×200 `<p>` plus the 200×200 red `<div>` then
BOTH FIT, so nothing was clipped and the capture paints the red square the ref
forbids (web f 0.8709 · iOS f 0.8705 · Android f 0.8698, `novelInkFailed` on
all three). Its six siblings `contain-{body,html}-overflow-001/003/004` are
byte-identical documents whose `<html>` rule declares only `contain:` — no
conflict, so the root keeps 200px — and **all six PASS at 1.0000 / 0.9996 /
0.9989**. The conflict is the whole difference.

**Fix.** Record which scope last wrote each key, then re-decide **only the keys
html and body both declare**, per family:

* the **canvas background** is the root element's (css-backgrounds-3 §2.11.2 —
  body's background propagates only when the root's is transparent), so an
  html-scope background wins;
* everything else on this component describes the **body box** it renders — its
  size, its clip, its margins — so body scope wins.

Overwriting an existing key leaves its position in the bag untouched, so a
document with no conflict emits **byte-identical** IR. (An earlier re-merge
formulation moved 15 more tests by key order alone; that was rejected.)

**Differential.** 2 tests of 1435, 2 values:
`contain-html-overflow-002` root `height 400px → 200px`, and
`CSS2/css21-errata/s-11-1-1b-005` root `background black → white`.

**Predicted flip: +3 cells, `contain-html-overflow-002` f→P on all three, at
its siblings' scores (web 1.0000 · iOS 0.9996 · Android 0.9989).** Converted
and compared token by token: the patched -002 root IR differs from the PASSING
-001 root IR in exactly one token — `Contain=["PAINT"]` vs `Contain=["LAYOUT"]`
— and the two child components are byte-identical. `contain: paint` adds
clipping; it never removes any.

`s-11-1-1b-005` is a repair, not a claimed pass: the wave49-final web capture
is **234000 of 234000 pixels black** (body's `background: black` flooding the
canvas) against a ref that is white with one line of text — all three cells
f 0.0001. Restoring the root's white should take it to a white canvas plus the
`<p>`; a pass is plausible but the document also puts `display: table-cell` on
the root and an abspos `<p>` over it, so no flip is claimed.

**~~Named residual, not folded in:~~ a `*` root-scope rule conflicting with an
`html`/`body` one is still decided by source order, not by Selectors-4 §17
specificity. Zero documents in the census hit it.** — **WITHDRAWN. This
paragraph was FALSE** (skeptic S2 defect 3, corrected in tree by wave-50 fix
lane F3). `parseCompound` consumes `*` as "universal — no constraint" and
never writes `needTag`, so `rootScopeOf`'s `parsed.needTag === '*'` arm was
unreachable — instrumented over the synthetic cases and all 1435 corpus tests
it fired **0** times, while the no-tag fallthrough took 41. Every `*`
root-scope rule therefore buckets as **html** scope, exactly like `:root`, and
**IS** re-decided by the conflict pass. Measured against HEAD (747b28e4):

| stylesheet | HEAD | with this patch |
|---|---|---|
| `*{background:red} body{background:black}` | `black` (source order) | **`red`** (canvas family → root element) |
| `body{height:200px} *{height:300px}` | `300px` (source order) | **`200px`** (body-box family) |

The dead arm and the `star` bucket it fed are now deleted rather than left as
a lie about what the pass sees. **The honest residual that replaces the false
one:** this is a re-decision by FAMILY, not by Selectors-4 §17 specificity —
`*` has specificity 0 and would lose to both `html` and `body` in a real
cascade, which this pass does not model. Corpus exposure is nil: of the 1435
wave49-final tests **17** carry a bare `*` root-scope rule (24 rules) and
**ZERO** declare a property an `html`/`body` root-scope rule in the same
document also declares. That census is complete because **ZERO corpus tests
link an external stylesheet**, so the inline-`<style>` scan sees every rule.

**Pins** (`tools/titan/extract-fixture-root-scope.test.mjs`, now **6** tests,
VERBATIM stylesheets). Mutation-proved: conflict pass removed → 3 fail;
background family emptied → 2 fail; in-place overwrite turned into a re-merge
→ the key-order pin fails; and for the new `*` pin (F3) — re-bucketing bare
`*` into a resurrected `star` scope the conflict pass ignores turns **exactly
that one pin** red, while pointing `rootScopeOf`'s fallthrough at `'body'`
turns it red along with three others.

---

## 3. `table-duplicate-text.patch` — BACKLOG queue 5(g) second half, and item 1(b)

**Defect — this is the mechanism 5(g) asked for, and it is shared.** A
`<table>`/`<tr>` component ships its CELLS' text a SECOND time, as its own
`_text` and as `{text:…}` entries in its `_runs`. The walker's ownText scan
hoists descendant text onto the ancestor (harmless for a `<div>`), and CSS 2.1
§17.2.1 then wraps every non-cell child of a table-row in an **anonymous
table-cell** — so each duplicate mints a phantom column that steals width from
the real ones.

The census is exhaustive and unanimous: **five** documents put ownText on a
table-internal box, and in **all five** that text is exactly the concatenation
of that box's own children's text (ASCII whitespace aside). Never independent
content.

* `contain-content-004` — each `<tr>` gets `[td, dup, td, dup]`, so
  `table-layout: fixed` splits the declared 206px over FOUR columns:
  (206 − 5 × 2px border-spacing) / 4 = **49px**. The web capture's white cells
  measure exactly 49px, at columns 1 and 3 in row 1 (x 18–66, x 120–168) and
  columns 2 and 3 in row 2 (x 69–117, x 120–168) — the real `<td>`s, with the
  blue table background showing through the transparent phantom cells: **29500
  blue pixels on web, 34836 Android, 34636 iOS, against the ref's 2436**. The
  `<table>`'s own duplicate adds an anonymous ROW — the 66px blue band above
  the real ones and the reason the green "PASS" sits at y 226 against the ref's
  y 192. This is the "blue-FILLED where the ref wants blue-BORDERED hollow
  cells" of the brief, explained.
* `direction-upright-002` — ten tables, each duplicating "ABC" on the table AND
  on its row. **This answers BACKLOG item 1(b)'s "first diagnostic: why 2805px?"**:
  390×2805 (web) / 2544 (iOS) / 2316 (Android) against a 390×954 ref, the
  reference-grade web runtime included, because the mechanism is upstream of
  all three.
* `background-color-animation-with-table{1,3,4}` — table "1 2" over cells "1","2".

**Fix.** Drop the parent's `_text` and the run list's text entries **only when
the parent's text IS its children's text** (ASCII whitespace normalised out —
css-text-3 §1.1's set, never `String.trim()`, which would eat the U+00A0 that
IS content). Real stray text in a row does not match and is kept, because
§17.2.1 gives THAT text an anonymous cell correctly. `td`/`th`/`caption` are
not in the set: their whitespace is ordinary flow content (CSS 2.1 §16.6).

**Differential.** 5 tests of 1435 — exactly the census, nothing else.

### THIS PATCH IS SUBSUMED BY LANE B4's AUTOCLOSE — it adds no cells of its own

Corrected in wave 50 by fix lane F3, on skeptic S2's defect 4. S2 re-extracted
all 1435 tests with **B4's autoclose alone** and with **B4 + this patch**, and
diffed the two fixture trees: **0 of 2870 fixture files change.** Byte for
byte, this patch's entire effect on the corpus is already produced by
`tools/titan/results/wave50-B4/wave50-B4-extract-fixture-autoclose.patch`
(both close the same duplicate-ancestor-text hole from different ends — B4 at
`scanOwnText`'s omitted-end-tag pairing, this one at the table-internal
equality test).

**Consequence for the wave's flip accounting: the cells below are B4's
account, and they must be counted ONCE.** The "+6 measured-safe, +1–3 likely"
originally written here is the SAME cell set B4 claims; adding it to B4's
would double-count the wave.

**Keep the patch anyway, for what it uniquely is: a STRAY-TEXT GUARD.** Its
predicate is "the parent's text IS its children's text", which B4's
end-tag-pairing fix does not express. On a document where a table-internal box
carries text that is *not* a duplicate, §17.2.1's anonymous cell is correct
and this predicate deliberately keeps the text (pinned). It is defence in
depth against a shape the corpus does not currently contain, not a second
source of cells.

**Predicted flips (B4's account, restated here with S7's correction).**
The original paragraph read: *"Android already suppresses the duplicate — on
`background-color-animation-with-table1` its ink is x[21–26] y[24–58] against
the ref's x[20–26] y[23–58] (P 0.9998), while web paints out to x=132 and iOS
to x=38"*. That holds for **table1 and table3 only**. Skeptic S7 measured
table4 and it does **not** hold: Android's ink box there is **x[16–110]
y[20–73], 300 px**, against table1/3's x[19–27] y[23–58] / 100 px — **Android
paints the duplicate on table4 too.**

So the corrected mechanism sentence is: **Compose's table path suppresses the
duplicate on table1 and table3; table4 paints it.** And the corrected class
prediction is **+7: web + iOS on table1/3/4, and Android on table4** (S7
replayed table4's column erasure to P 0.9945 / P 0.9943 / P 0.9936 on the
three platforms; erasing changes nothing on Android table1/3, still 0.9998).
Android's two passing cells at 0.9998 remain predicted unchanged and flagged
for the gate to confirm. Measured boxes: ref x[19–27] y[23–58] 109 px; web to
x=133, iOS to x=39, Android(1,3) x[19–27].

`contain-content-004` should give the web cell (the table becomes two real
100px columns and the "PASS" lands at the ref's y); both natives stay blocked
by the abspos containing block (BACKLOG 0(b)), so +1 likely, +3 at best.
`direction-upright-002` will shrink substantially on all three; no flip
claimed — item 1(b) also names orthogonal flow and upright text-flow. All of
these belong to B4's account too.

**Pins** (`tools/titan/extract-fixture-table-text.test.mjs`, 5 tests, VERBATIM
markup). Mutation-proved: predicate forced false → 3 fail; run-list half not
filtered → 1 fails; `trim()` substituted for the css-text-3 whitespace set →
1 fails; `td` admitted to the internal set → 1 fails.

---

## Also verified this lane (no code needed)

**BACKLOG 5(h) is DONE, and the brief that re-assigned it was stale.** The
`image()` candidate chain landed in wave 49 on both natives —
`runtimes/compose/.../images/ImageCandidateChain.kt` +
`runtimes/swiftui/.../StyleEngine/images/ImageCandidateChain.swift`, consumed by
`color/ColorExtractor.kt` / `ColorApplier.kt` on Compose and by
`background/BackgroundImageConfig.swift` `.imageNotation(srcs:fallback:)` +
`BackgroundImageNotationResolver.swift` on iOS. `css-image-fallbacks-and-
annotations002/003/004` pass on all three (web 1.0000 · iOS 0.9990 · Android
0.9982). **PNG-checked against the frozen refs, per the standing rule** — these
are honest passes, not red-square degenerates: a 200×200 green square at
x[16–215] y[68–267] with a 40000-pixel count identical to the ref's; on 004
ALL THREE platforms — web included — paint (0,127,0) x 40000 against the ref's
(0,128,0), the one-step-off GCT the BACKLOG already records. (That sentence
used to read "only 004's natives paint (0,127,0)". Skeptic S6 measured the WEB
capture and it is (0,127,0) x 40000 too, so the one-step-off GCT is not a
native-only residual — corrected by wave-50 fix lane F6, S6 finding 18,
`tools/titan/results/wave50-S6/backlog-corrections.json`.) No work was done and none is needed.

---

## If a patch goes stale (B1 kept editing the file all wave)

Each hunk is a pure insertion anchored on a unique, stable line, so a rebase is
mechanical — re-insert at the anchor, no context needed:

* `ua-hr-separator-box.patch`
  1. the `UA_HR_*` block + `uaHrProps()` go **after**
     `` const ROOT_INHERITED_REASON = 'body-inherited-baked'; ``
  2. the fold goes **after** `` if (uaLink) Object.assign(props, uaLink); ``
  3. the reason push goes **after** `` if (uaLink) reasons.push(UA_LINK_REASON); ``
  plus the new file `tools/titan/extract-fixture-ua-hr.test.mjs`.
* `root-scope-conflict.patch`
  1. `CANVAS_BACKGROUND_PROPS` + `rootScopeOf()` go **before**
     `` export function propsForBodyRoot(rules) { ``
  2. `const scoped = …` goes with the `` const pseudo = {}; `` declaration in
     that function
  3. the second `assignDeclarations(scoped[…], r.props)` goes **after** the
     existing `` assignDeclarations(props, r.props); `` in its loop
  4. the conflict pass goes **immediately after that loop**, before the
     `::marker` cleanup
  plus `tools/titan/extract-fixture-root-scope.test.mjs`.
* `table-duplicate-text.patch`
  1. `TABLE_INTERNAL_TAGS` + `stripAsciiWhitespace` + `isDuplicatedTableText()`
     go **before** `` export function buildComponents(cleaned, rules, idPrefix ``
  2. `const tableDupText = isDuplicatedTableText(node);` and the guard go at
     `` if (node.ownText) cmp._text = node.ownText; ``
  3. the `.filter(…)` goes on the `` cmp._runs = node.runs.map(… `` chain
  plus `tools/titan/extract-fixture-table-text.test.mjs`.

After re-applying, re-run the differential before trusting the flip counts:
the numbers above are against the extractor as of 2026-09-14 21:07 UTC.
