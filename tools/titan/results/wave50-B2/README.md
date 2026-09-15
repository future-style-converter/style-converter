# wave50-B2 — Compose percentage-inset level + atomic-inline shrink-to-fit

`_note`: lane B2 of wave 50 owned docs/BACKLOG.md ranked-queue items **0(b)**
(Compose `LocalContainingBlock` / `position-relative-006`) and **0(d)**
(`clip-path-contentBox-1d/1e` on Android). Both diagnoses in the queue turned
out to name the wrong mechanism, and both real fixes need ONE
`ComponentRenderer` edit each — a file this lane may not write. The decision
code and its pins are landed under
`runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/`;
the renderer edit is `componentrenderer-seam.patch` in this directory, and it
is INERT until applied: without it both landed mechanisms are no-ops and every
committed capture is byte-identical.

**No device gate ran this wave.** Everything below is JVM pins on verbatim
corpus payloads, corpus simulation over the frozen `wave49-final` run, and PNG
measurement against the frozen refs. The predicted flips are predictions.

## Files

| file | what it is |
|---|---|
| `componentrenderer-seam.patch` | the two-hunk `ComponentRenderer.kt` edit both fixes need. Generated against `747b28e4`. **Compile-verified on 2026-09-14**: applied to the wave-50 tree, `:runtime:compileDebugKotlin` BUILD SUCCESSFUL, then reverse-applied; a negative control (renaming the called function in the applied hunk) made the same compile fail with `Unresolved reference`, so the compile really type-checked the patched file rather than skipping it as up-to-date. |
| `census.mjs` | the two corpus censuses, as a runnable predicate (`node tools/titan/results/wave50-B2/census.mjs insets\|inline`). Committed because retro A1#3: a census with no script does not reproduce. |

## 0(b) — the percentage-inset containing block is read one level too deep

The queue (and `corpus-v6-15.json`'s `_note`) say `PercentInsetResolve`
"declines by design when the ancestor publishes no containing block on either
axis". **It does not decline on `position-relative-006`: it is never shown the
ancestor's block.** `PercentInsetPositioned` reads `LocalContainingBlock`
inside a `Modifier.composed { }` factory, which materialises in
`ComponentRenderer.RenderComponentContent` — already INSIDE the component's own
`LocalContainingBlock provides childContainingBlock`. So the value it reads is
the block the element publishes for its CHILDREN.

Evidence (`census.mjs insets`, over all 30 frozen `wave49-final` sections):

* 7 bare-number (percentage) inset carriers, all in `css-position`.
* The element-level and child-level containing blocks differ on 6 of them, but
  the USED inset differs on exactly **1**:
  `wave49-final css-position/position-relative-006 android f 0.9966 cF` —
  element-level cb `(100, null)` → used `0px`; child-level cb `(100, 100)` →
  used `-10000px`, which is bit-for-bit the legacy number-as-pixels value.
* Which is why the wave-49 repair moved nothing: `wave48-final` → `wave49-final`
  Android is `f 0.9966` → `f 0.9966` on -006 and score-identical on all seven.
* PNG: `tools/titan/runs/wave49-final/sections/css-position/android-screenshots/wpt__css-position__position-relative-006.png`
  is a bare RED 100×100 square (10 000 novel red px in the manifest's
  `novelInk`) where `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/css-position/position-relative-006.png`
  paints a green one. iOS and web captures of the same run paint green.

**Landed**: `layout/position/ElementContainingBlock.kt` (the correctly-levelled
channel + the selection function, with a `PropertyTracker` breadcrumb on the
fallback leg) and the read in `PercentInsetPositioned.kt`.
**Seam (hunk 2 of the patch)**: one `provides` line in the renderer's
`CompositionLocalProvider`.
**Prediction once applied**: `css-position/position-relative-006` Android
`f 0.9966 cF` → PASS (the green square covers the red parent; the 10 000 red px
go to zero). No other cell in the corpus changes — the other six carriers
resolve to the same used inset at both levels, and the whole lane is gated on
`LocalWptCaptureMode`, so no committed fixture baseline can move.

**Still open, re-scoped**: the CSS 2.1 §10.1 republish the queue asks for (a
non-block-container ancestor should hand down its own containing block instead
of nulling it) is a real gap in `DynamicValueResolver.childContainingBlock`,
but it is not what blocked -006 and it moves nothing on this corpus: its only
two carriers, -002's `<span>` parent and -008's `<tbody>` parent, resolve to the
same used inset with or without it.

## 0(d) — `clip-path-contentBox-1d/1e` is a WIDTH defect, not a clip defect

Measured from the frozen PNGs (bounding boxes by colour class):

| image | green | red |
|---|---|---|
| ref `clip-path__clip-path-contentBox-1d.png` | `[16,16]-[115,115]` 100×100 | — |
| web capture | `[24,24]-[123,123]` 100×100 | — |
| iOS capture | `[24,24]-[123,123]` 100×100 | — |
| **Android capture** | `[24,24]-[123,123]` 100×100 | **`[124,24]-[365,123]`, 24 200 px** |

The clip is CORRECT on Android — the 4 px `padding` band and the 4 px darkred
`border` are absent from the capture, which is what `clip-path: content-box`
must do. What is wrong is that `.clipped` (`display: inline-block`, no declared
width) is stretched to the full 358 px composed canvas by
`ComponentRenderer.blockFlowWidth`'s CSS 2.1 §10.3.3 emulation, so its own
`background-color: red` paints a content box running to the canvas edge. §10.3.3
is scoped to BLOCK-LEVEL boxes; §10.3.9 gives an atomic inline-level box
shrink-to-fit.

`1e` is the same defect twice. What the Android capture actually draws is a
**342×100 red pill with a green left cap**, where the reference is a **green
circle**: the same red band (23 034 px) plus a green area of 8 834 px against
the reference circle's 7 647 — with 342 px of content the `border-radius: 58px`
corners land at x≈316-366, so only the LEFT pair still rounds the 100×100 green
child and the right pair rounds the far end of the red band. There is no
separate radius bug.

**Landed**: `layout/position/AtomicInlineShrinkToFit.kt` (the §10.3.9
predicate, declared-display-only, `inline-table` left to `TableBoxTree`,
vertical writing modes left to the wave-47 orthogonal branch).
**Seam (hunk 1 of the patch)**: one `&&` clause on `blockFlowWidth`'s
horizontal `else if`.
**Blast radius once applied** (`census.mjs inline`; Android column only — this
is Compose code, so no web or iOS cell can move): 33 corpus tests carry a
declared atomic inline-level auto-width box; **11** carry it in a vertical
writing mode and stay frozen; the **22** that change (23 components —
`baseline-with-orthogonal-flow-001` carries two) are

```
css-masking/clip-path-contentBox-1d                            android f 0.8775   predicted → PASS
css-masking/clip-path-contentBox-1e                            android f 0.8875   predicted → PASS
css-multicol/balance-grid-container                            android f 0.8721   (web+iOS also fail)
css-writing-modes/ch-units-vrl-001                             android f 0.8075
css-writing-modes/ch-units-vrl-002                             android f 0.8075
css-writing-modes/ch-units-vrl-003                             android f 0.8075
css-writing-modes/ch-units-vrl-004                             android f 0.8075
css-writing-modes/ch-units-vrl-005                             android f 0.8204
css-writing-modes/ch-units-vrl-006                             android f 0.8204
css-writing-modes/ch-units-vrl-007                             android f 0.8204
css-writing-modes/ch-units-vrl-008                             android f 0.8204
css-tables/collapsed-border-sideways-rl-rtl-overflow           android f 0.8528
css-tables/collapsed-border-vertical-lr-rtl-overflow           android f 0.8506
css-tables/collapsed-border-vertical-rtl-overflow              android f 0.8528
css-writing-modes/inline-box-orthogonal-child-with-margins     android P 0.9709   at risk
css-display/display-contents-before-after-003                  android P 0.9713   at risk
css-writing-modes/baseline-with-orthogonal-flow-001            android P 0.9817   at risk
filter-effects/backdrop-filter-image-size-filter-size-mismatch android P 0.9841   at risk
css-overflow/line-clamp/block-ellipsis-022                     android P 0.9863   at risk
css-text-decor/text-decoration-propagation-02                  android P 0.9988   at risk
css-text-decor/text-decoration-propagation-03                  android P 0.9988   at risk
css-transforms/backface-visibility-hidden-006                  android P 1.0000   BLANK-VS-BLANK (not at risk)
```

**This table said NINE until wave-50 lane F1 corrected it.** Skeptic S3 ran the
predicate through the renderer's real inheritance merge and measured 22 tests /
23 components
(`tools/titan/results/wave50-S3/probe-output/atomic-inline-census.txt`; probe
source `…/probe-sources/AtomicInlineCensusProbeTest.kt.txt`). The cause was in
`census.mjs`: it approximated "vertical writing mode" as "this DOCUMENT
mentions one anywhere", which swept 13 tests whose vertical declaration sits on
a SIBLING subtree into the frozen bucket — the eight `ch-units-vrl-*`, both
orthogonal-flow tests and the three `css-tables` collapsed-border overflow
tests. The script now resolves the mode per component by inheritance (own
declaration wins, else the nearest `slot.parent`) and reproduces the 22 / 11
split exactly. The lane's own note called the over-count "the safe direction";
it was not — it hid blast radius rather than adding it.

This table is the CANONICAL home of these numbers. Wave-50 lane F4 moved it
out of `AtomicInlineShrinkToFit.kt`'s banner (skeptic S6-18's structural note):
`tools/visual/doc-staleness-check.sh` derives headline numbers in `docs/`, never
in a runtime banner, so 22 SSIM values sitting in production Kotlin would rot
silently at the next gate. The banner now cites this path.

**`css-transforms/backface-visibility-hidden-006` is a BLANK-VS-BLANK cell, not
a pass at risk.** Its frozen reference and all three captures decode to a single
uniform white with no ink anywhere — one of the seven such tests (21 cells) in
`tools/titan/results/wave50-S6/blank-vs-blank-passes.json`. Its carrier declares
neither background nor border, so a shrink-to-fit used width keeps a blank page
blank and the `1.0000` is unmoved in either direction. A gate that reports it
"kept" has measured nothing. **Seven** of the eight passes are therefore real
at-risk cells; the count below is left at eight only where it means "rows
marked at risk in the table".

Of the **eight** at-risk passes, seven declare no background AND no border on
the inline-level box (`display-contents-before-after-003`,
`baseline-with-orthogonal-flow-001` — both of its carriers —
`block-ellipsis-022`, `text-decoration-propagation-02/03`,
`backface-visibility-hidden-006`,
`backdrop-filter-image-size-filter-size-mismatch`), so the stretch is invisible
today and shrink-to-fit changes only where their CONTENT is measured; Compose's
wrap-content is `min(max-content, available)`, which is §10.3.9's formula, so
left-aligned content at the same origin should paint the same pixels.
`inline-box-orthogonal-child-with-margins` is the exception and the most
exposed cell of the 22: its carrier declares `border-*-style: dashed`, so the
box's own chrome IS painted and WILL move with the used width — its 0.9709 is
both the lowest at-risk score and the only one whose ink is known to change.
All of that is argument, not measurement: **the gate is the measurement**. The
ring-fenced `filter-effects/backdrop-filter-basic-blur` declares no atomic
inline-level box and is untouched by construction.

### Coupled with lane B3 on `display-contents-before-after-003`

Component `wpt__css-display__display-contents-before-after-003__3-024` is
changed by **two** wave-50 lanes at once: hunk 1 of this lane's seam patch
(the §10.3.9 fill removal) and **lane B3's `::after` text fold**
(`tools/titan/results/wave50-B3/compose-pseudo-after-textfold.patch`, whose
population list carries this component). Neither lane's report said so.

Joint prediction: the cell is `wave49-final css-display/display-contents-before-after-003
android P 0.9713` today, i.e. an at-risk pass with 0.0213 of head-room, and it
is the only cell in the corpus carrying both changes. If it drops below 0.95
the two changes must be A/B'd SEPARATELY before either is blamed: stub
`AtomicInlineShrinkToFit.suppressesBlockAutoWidth` to `false` (B2 off, B3 on),
then re-run; then restore it and stub `PseudoTextFold.resolve` to the identity
(B2 on, B3 off), then re-run. Both stubs are generic switches on the mechanism.
It must NEVER be narrowed by test name — the standing ring-fence rule in
docs/BACKLOG.md forbids test-specific code, and a `if (id == "…-003")` carve-out
would be exactly that.

## What a later wave must do

1. Apply `componentrenderer-seam.patch` (or re-derive the two hunks — they are
   fully commented in the patch) and run the gate.
2. Attribute the two `clip-path-contentBox` cells and
   `position-relative-006` against `corpus-v6.15`.
3. If any of the **eight** at-risk Android passes drops, the honest move is to
   narrow the predicate, not to widen the threshold: the two gains are worth
   naming individually. For `display-contents-before-after-003`, A/B lane B3's
   `::after` fold out first (see the coupling section above) — it is the one
   at-risk cell whose drop could belong to either lane.
4. All eight at-risk cells plus the 14 failing ones are listed in
   `tools/titan/results/wave50-gate/watchlist.txt` under the B2 hunk-1 heading.
