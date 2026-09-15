# wave-50 lane B4 — counter/marker gaps: what they actually are

Ranked-queue items **2(b)** (`css-lists/counter-004`, iOS half) and **2(d)**
(counter-suffix doubled text + missing korean/RTL markers;
`name-case-sensitivity` missing marker content). Everything below is measured
against `tools/titan/runs/wave49-final/` and the frozen refs at
`tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`.
No devices were used; every claim is a PNG measurement, a per-test-IR read, or
an executed unit repro.

## 0. Three queue-entry corrections (the PNGs disagree with the queue text)

| queue text | what the captures show |
|---|---|
| counter-004 "lays the georgian counter string ONE GLYPH PER LINE (zero-width wrap)" | It is **one SPAN per line**. The document has 40 `<span>`s per row; the iOS capture has exactly 40 lines, and the two-glyph spans (`ია`, `იბ`) and the five-glyph last span (`ჵჰშჟთ`) each sit on ONE line. Nothing wraps at zero width — the inline run never forms. |
| counter-suffix "Korean + RTL marker rows missing on web, iOS AND Android alike" | **Web renders both correctly.** `screenshots/wpt__css-counter-styles__counter-suffix.png` shows `일, foo` / `이, bar` and all four RTL markers. Only the two NATIVES lose them, for two different reasons (§2, §3). Web's only defect on this test is the doubled text, which IS shared. |
| `name-case-sensitivity` "missing marker content on all three" | The markers are **painted** on both natives — they are all painted **at the same x**. Ink census: ref x 17–343 over two lines (1131 dark px), web x 18–245 one line (565), iOS x **16–35** (153), Android x **16–35** (156). The document is 14 `float: left` boxes inside 8 zero-height `<ol>`s; the natives place every float at its own container's origin instead of continuing the float line across sibling containers. This is a **float-placement** defect, not a marker defect, and it is outside `lists/`. |

## 1. The doubled item text — SHARED, upstream, FIXED (patch in this directory)

`tools/titan/extract-fixture.mjs`'s `scanOwnText` never mirrored
`walkChildren`'s `AUTO_CLOSE_TRIGGERS` rule (HTML Living Standard §13.2.6.4,
optional end tags). With no literal `</li>` the close-pairing loop leaves
`lastCloseStart === -1` and `cursor === tagOpenEnd + 1`, so the scan resumes
INSIDE the child and the child's text is ALSO counted as the parent's own.
`counter-suffix`'s six `<ol><li>foo<li>bar</ol>` lists therefore ship

```
"_text":"foobar", "_runs":[{child:li0},{text:"foo"},{child:li1},{text:"bar"}]
```

and every renderer paints `1. foo` then a bare `foo`. The wave-33 lane-N banner
two lines above the defect describes exactly this double-count for UNCLOSED
WRAPPERS and fixes only that half; this is the other half.

**Patch**: `wave50-B4-extract-fixture-autoclose.patch` (source) +
`wave50-B4-extract-fixture-autoclose.tests.mjs` (append verbatim to
`tools/titan/extract-fixture.test.mjs` — delivered as an append rather than a
diff hunk because lane B1 is also growing that file's tail).
Delivered as a patch, not in-tree, because the extractor is outside lane B4's
ownership list and lane B1 is editing the same file.

**Verification already done**

* Full-corpus differential: all **1435** wave49-final tests re-extracted
  (static path) before and after. **10 tests change**, listed with their
  wave49-final cells in `blast-radius.json`.
* **51** components lose a duplicate `_text`; the leaf-text sequence of all 10
  fixtures is **byte-identical** before and after (`leafTextIdentical: true`
  on every row) — no glyph is lost anywhere, only ancestor copies removed.
* 27 of the 30 cells in the blast radius currently FAIL or are excluded. The
  three that PASS are safe, argued from code rather than hope:
  * `background-color-animation-with-table{1,3}` **android 0.9998** — Compose's
    table path (`ComponentRenderer.RenderTableContainer`'s rows loop) is a
    REWRITE: the `<table>`'s and each `<tr>`'s own text are never rendered, only
    `RenderComponent(cellComponent)`. The patch leaves the `<td>` text
    untouched, so the Android render cannot move. (Its web/iOS twins at 0.9884
    / 0.9904 DO paint the duplicates and should improve.)
  * `broken-symbols` **web 0.9749** — a degenerate pass: the web capture prints
    `1. Should have "1." as bullet point.` TWICE where the ref prints it once.
    Removing the duplicate moves web toward the ref, not away.
* `node --test tools/titan/*.test.mjs` with the patch applied: **1591 pass, 0
  fail, 2 skipped** (1593 total; the 2 skips are pre-existing).
* Both new pins **proved able to fail** by three separate mutations of the
  mechanism (branch 1 off / branch 2 off / whole mechanism off) — see the
  test's own MUTATION PROOF banner.

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

**Predicted movement (numbers, not adjectives) — RESTATED in wave 50 by fix
lane F3 on skeptic S7's PNG replay, which measured the bands this paragraph
had estimated.**

The original text said `counter-suffix` *"loses 12 of its 24 painted text
lines on every platform"*. The captures say otherwise: they carry **20 line
bands against the ref's 12**, so the duplicate is **8 bands, not 12**
(`capBandCount` 20 / `refBandCount` 12 / `keptBandCount` 12 on all three
platforms — `tools/titan/results/wave50-S7/replays.json → replay-B4.mjs`).
S7 then scored two post-patch pictures with the campaign's own scorer:

| `css-counter-styles/counter-suffix` | wave49-final | erase-in-place | reflow (optimistic bound) |
|---|---|---|---|
| web | f 0.8867 | f 0.8897 | **P 0.9818** |
| iOS | 0.8883 (was excluded) | f 0.8914 | **f 0.9292** |
| Android | 0.8901 (was excluded) | f 0.8931 | **f 0.9044** |

So the restated prediction is: **web f 0.8867 → P ~0.98, an UNCLAIMED FLIP
this lane did not argue for; the two natives → 0.90-0.93, still failing** —
and the reflow row is the *optimistic* bound (it assumes the remaining lines
close up perfectly), so 0.9044 is a ceiling for Android, not a midpoint. The
original "all three rise but do not reach 0.95 on the natives" holds and is if
anything generous.

**DENOMINATOR WARNING — the gate must classify the two native cells as
denominator-added, NEVER as lost.** Retro R13 removed this test's
`native-font-parity` exclusion, so wave 50 SCORES two cells that wave 49 did
not score at all (their wave49-final rows carry `wptPass: null` +
`scoreExcluded: "native-font-parity"`; the raw 0.8883 / 0.8901 above are
replay-computed, not manifest verdicts). They will land as **new FAILS**, which
is a larger denominator, not a regression. `tools/titan/score-gate.mjs` already
does exactly this — a cell absent on the old side and present on the new lands
in its **NEWLY MEASURED** list, which is printed apart from GAINED and LOST —
so the rule is: read those two rows off NEWLY MEASURED and do not let them
reach any "zero lost" arithmetic.

`direction-upright-002` (queue 1(b), all-three failure, web canvas 2805px vs
the 954px ref) loses 13 duplicated table texts and is the single most likely
place for a large, unpredicted move — the queue's "first diagnostic: why
2805px?" should be re-run AFTER this patch.

**And one cell this lane's account GAINS, from lane B7's side of the same
mechanism** (S7, folded in here by F3 so the wave counts it once):
`background-color-animation-with-table4` **Android**. The bullet above says
Compose's table path never renders the `<table>`/`<tr>` own text — true for
table1 and table3, **false for table4**, whose Android ink box is x[16–110]
y[20–73] / **300 px** against table1/3's x[19–27] y[23–58] / 100 px. Android
paints the duplicate there. Erasing the duplicate column scores **P 0.9945 /
P 0.9943 / P 0.9936** (web / iOS / Android) and changes nothing on Android
table1/3 (still 0.9998). So the class is **+7 — web + iOS on table1/3/4, plus
Android on table4** — and it belongs to THIS lane's account, because B7's
`table-duplicate-text.patch` is byte-for-byte subsumed by this autoclose patch
(0 of 2870 fixtures change when B7's is applied on top of B4's; skeptic S2).
Counting the +7 in both lanes' totals would double the wave.

## 2. Korean markers — NATIVE-only, iOS half SHIPPED

`ListMarkerResolver.markerType(fromKeyword:)` had no `korean-hangul-formal`
row, so it answered nil, the resolver kept the running value (the `<ol>` UA
`decimal`, HTML §15.3.7) and iOS painted `1.` / `2.`. Shipped in
`runtimes/swiftui/.../StyleEngine/lists/KoreanHangulFormal.swift` +
`ListMarkerStyle.swift` + `ListMarkerText.swift`, pinned by
`KoreanHangulFormalTests` (4 tests, including the end-to-end resolve on the
VERBATIM `counter-suffix__0__3` IR), mutation-proved twice.

Corpus census over all 1435 per-test IR documents: exactly **one** component
declares a `korean-*` list-style-type, so exactly one test can move.

**TRACKED FOLLOW-UP — the Compose twin does not exist.** Three edits, in
`runtimes/compose/src/main/java/com/styleconverter/runtime/lists/`, named by
SYMBOL (skeptic S4 found this lane's original pointer named a file that does
not exist — there is no `ListStyleType.kt`; wave-50 fix lane F2 re-grepped
each site below and corrected the banner in the source):

1. `ListStyleConfig.kt` — the `ListStyleType` enum: add
   `KOREAN_HANGUL_FORMAL` after `KATAKANA_IROHA`.
2. `ListStyleExtractor.kt` — `typeFromKeyword`'s `when`: the key must be the
   UNDERSCORED `"korean_hangul_formal"`, because that function normalises
   with `rawKeyword.lowercase().replace("-", "_")` before matching. The wire
   value the converter emits is the hyphenated `"korean-hangul-formal"`; a
   hyphenated table key would never match.
3. `ListStyleApplier.kt` — `getMarker`'s `when`: one arm beside
   `ListStyleType.KATAKANA_IROHA`.

Until then the two natives answer differently for this keyword (iOS `일,`,
Compose `1.`), which is stated in `KoreanHangulFormal.swift`'s TWIN
DIVERGENCE banner rather than left silent.

## 3. RTL markers — NATIVE-only, and BLOCKED UPSTREAM (do not fix in the runtime)

Executed repro (Catalyst, lane B4): for the verbatim
`counter-suffix__0__4__0` `<li>`, `ComponentRenderer.isOutOfFlow(li) == true`.
Out-of-flow children go to `overlayChildren` / `positionedChildren`, and the
marker branch lives only in the IN-FLOW children loop — so `meta.markerText`
(`"1."`, present on the wire) is never painted. That half is a real renderer
gap and is a one-call-site fix.

**But painting it there would make the cell worse, not better.** The same repro
reads `Direction: LTR` off the RTL `<ol>`: `tools/titan/bidi-bake.mjs` rewrites
the bake root's `direction` to `ltr` on purpose ("the reorder is already in the
coordinates"), and the `::marker` is NOT part of the reordered text run — its
SIDE is the one thing that still depends on `direction`. The frozen ref hangs
these markers on the **right** (ink x 133–145, item box ends at x 128); a
renderer reading the wire would hang them on the left at x≈60, adding wrong ink
while the right-hand ink stays missing.

**Recommended fix, upstream**: the bidi bake already emits each item's text as
an explicitly positioned box; it should do the same for the item's marker (it
can measure the live geometry), or preserve a marker-side signal. Then the
renderer half is trivial and direction-free. Census: exactly **4** components in
the whole corpus carry `meta.markerText` together with
`position: absolute|fixed`, all four in `counter-suffix`, so nothing else moves
either way.

## 4. counter-004 (iOS) — THREE independent gaps, all measured, none in `lists/`

wave49-final `css-lists/counter-004`: iOS x(native-font-parity, raw **0.7532**,
canvas 390×1704 vs the 390×600 ref) · android raw 0.7531 (390×1784) · web f
0.7779. Web renders the two rows CORRECTLY (it emits real `<span>` DOM and lets
Chromium flow the paragraph); its 0.7779 is a separate small-offset/AA term.

Executed repro (Catalyst, lane B4, on the verbatim IR shape):

1. **No `meta.runs` on the wire.** The two `<div>`s have 40 children each and
   no `runs` key, so `InlineRunFlow.fold(runs: nil, …)` returns nil — the fold
   path never engages and `ComponentRenderer` stacks the 40 spans as blocks.
   This is by design upstream: `scanOwnText`'s wave-34 emission condition needs
   own text AND a kept child, and the only own text here is the inter-sibling
   whitespace, which the `ws-after` channel owns instead (its banner says so).
2. **Even with runs the fold REFUSES.** Feeding synthesised runs returns nil:
   each member declares `CounterIncrement`, which is not in
   `InlineRunFlow.emptyMemberInertTypes`, so `classify` bails `member-prop`.
3. **Even if admitted the glyphs vanish.** The control run (same members with
   the `CounterIncrement` removed) folds to `text: " "` with
   `droppedEmptyMembers: 2`: `classify` reads `member.text` only, and these
   members' glyphs live in `pseudos.before._text`. `PseudoTextFold.resolve` runs
   in a component's OWN `ComponentRenderer` init
   (`ComponentRenderer.swift:834`), so a CHILD's pseudo text is invisible to the
   parent's fold. Any `::before`-only inline member is silently dropped from a
   folded paragraph today.

Gap 3 is a genuine correctness bug with a wider reach than this test (any
`<span>`-with-`::before` inside a folded run). Gaps 1–3 all live in
`tools/titan/extract-fixture.mjs`, `StyleEngine/typography/inline/` and
`Renderer/` — outside lane B4's ownership — so they are reported, not patched.

## Files here

* `wave50-B4-extract-fixture-autoclose.patch` — §1, apply with `git apply -p1`
  from the repo root (verified to apply cleanly against the wave-50 tree with
  lane B1's extractor edits already in it).
* `wave50-B4-extract-fixture-autoclose.tests.mjs` — §1's pins; append verbatim
  to `tools/titan/extract-fixture.test.mjs`.
* `blast-radius.json` — the full 1435-test differential behind §1.
