# Wave 50 — lane B11 (BACKLOG queue 9(a) + 9(f))

Two items: `visibility: collapse|hidden` semantics on both natives, and the
Android placeholder-floor under-size. **No device gate ran this wave**, so
everything below is JVM / Catalyst pins, PNG measurements of frozen captures,
and corpus-simulated blast radius. Every prediction carries a number.

---

## 9(a) — `visibility` on the natives

### What the corpus actually carries

`visibility-census.mjs` (re-runnable; output frozen in
`visibility-census.json`) walks every `per-test-ir/` document in
`tools/titan/runs/wave49-final/sections/` and reports each component that
declares `Visibility`, plus the ancestor/descendant relation that decides
whether the natives' subtree-wide `alpha(0)` / `.opacity(0)` shortcut is
spec-equivalent. **Four tests in 1379 declare the property**:

| gate cell | verdict |
|---|---|
| `wave49-final css-anchor-position/anchor-center-visibility-change` | score-EXCLUDED (`requires-anchor-positioning-runtime`, `requires-script-mutation`) |
| `wave49-final css-lists/counter-reset-reversed-display-none web P 1.0000 · ios P 1.0000 · android P 0.9971` | passes; hidden box, no children |
| `wave49-final css-view-transitions/capture-with-visibility-hidden-child web P 1.0000 · ios P 1.0000 · android P 1.0000` | passes; hidden box, no children |
| `wave49-final css-view-transitions/capture-with-visibility-mixed-descendants web f 0.9215 · ios f 0.9196 · android f 0.9196` | **the carrier** |

`visibility: collapse` has **zero** carriers — neither in the WPT corpus
(`per-test-ir` grep for `"Visibility", "data": "COLLAPSE"` is empty) nor in
`fixtures/` (the only `visibility` in the fixture net is
`visual-test.json` `Visibility_Hidden`, a childless box, plus two
`visual-test-controls.json` rows).

### The defect, seen in the PNGs

`capture-with-visibility-mixed-descendants` has a `visibility: hidden` red
500x500 box (`…__0__0-064`) whose child `…__0__0__0-065` declares
`visibility: visible` and is a green 10x10 square. CSS 2.2 §11.2 says that
child paints.

* frozen ref `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/css-view-transitions/capture-with-visibility-mixed-descendants.png` — green square at ~(216,215) on a pink ground
* `…/wave49-final/sections/css-view-transitions/screenshots/…png` (web) — green square present
* `…/android-screenshots/…png` and `…/ios-screenshots/…png` — **white there; the square is gone**

Cause: both natives hide with ONE subtree layer, which a descendant cannot
escape (`interactions/InteractionApplier.applyVisibility` → `Modifier.alpha(0f)`;
`StyleEngine/visibility/VisibilityApplier` → `.opacity(0)`).

The manifest confirms the square is the ONLY native-vs-web difference on this
test: `pairs.iOS-web.pixelMismatchedCount` is **exactly 100** (= 10x10),
`pixelMismatchedPct` 0.043, ssim 0.9973, while `iOS-Android` is 1.0000 /
0 px — the two natives agree with each other and disagree with web on 100
pixels, which is the square and nothing else.

### What landed (in-tree, this lane's ownership)

* `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/visibility/VisibilityBoxRules.swift`
  — the pure rule table (declared-vs-inherited resolution, the `collapse`
  box-type split, and `subtreeAlphaEquivalent`, the predicate the seam must
  consult), pinned by `VisibilityBoxRulesTests` (8 tests, Catalyst) on
  VERBATIM wave49-final wire payloads.

  **THE RULE LANDED ON iOS ONLY.** This lane also wrote a byte-parallel
  Compose twin (`runtimes/compose/.../visibility/VisibilityBoxRules.kt`, 176
  lines) plus its 7-test `VisibilityBoxRulesTest`. Wave-50 skeptic S1 found
  the Kotlin twin had **zero references anywhere in the repo outside itself
  and its own test**, while the Swift one IS dispatched
  (`VisibilityApplier.swift` / `VisibilityExtractor.swift`). Lane F1 DELETED
  both Kotlin files on the orchestrator's decision. The reason is not tidiness:
  Compose has no table-track removal path for the rule to dispatch INTO, so
  wiring it there would have produced a dispatch whose two branches render
  identically — a `treatment` call whose `collapse` and `hidden` arms both end
  at the same `Modifier.alpha(0f)`. The Compose twin is therefore **deferred to
  the Compose table-renderer item**, which is where a real `table-row` /
  `row-group` / `column` / `column-group` removal first becomes expressible.
  Re-deriving it then is a 176-line file with a committed shape (it is in this
  wave's git history) — the iOS file is the reference. The Swift banner carries
  the same note (amended by lane F2).
* **iOS `collapse` fixed live**: `VisibilityApplier.swift` used
  `.frame(width: 0, height: 0).hidden()` for EVERY collapsed element, so an
  ordinary `visibility: collapse` div lost its layout on iOS while Compose
  (which folds collapse into its `isHidden` alpha) kept it. §11.2 removes
  only a table row / row group / column / column group; "for other elements,
  `collapse` is treated the same as `hidden`". The applier now asks
  `VisibilityBoxRules.treatment`, and `VisibilityExtractor` reads the box
  type from the component's declared `Display` (deliberately without setting
  `touched`, pinned).
  **Blast radius: zero measured cells** — no corpus or fixture carrier of
  `collapse` exists. This is a correctness + twin-parity fix, not a flip.

### What did NOT land — the seam (see "Deferred" in the lane report)

The mixed-descendants fix needs the renderer, which is outside this lane's
ownership, and needs more than a call-site swap:

1. an inherited-visibility ambient (`CompositionLocal` on Compose,
   `Environment` on SwiftUI) — the wire does NOT carry a computed
   `visibility` on every component (`tools/titan/extract-fixture.mjs` ships
   it only through `ROOT_INHERITED_TRIGGER_PROPS`), so an undeclared
   descendant must inherit hidden and only a declared `visible` may escape;
2. **self-ink suppression** — when `subtreeAlphaEquivalent` is false the
   hidden element must drop its OWN background / border / shadow / own text /
   `::before`+`::after` and still render children. That is a config-level
   rewrite in `ComponentRenderer` on both platforms, not a modifier.

Call sites for whoever picks it up: Compose
`interactions/InteractionApplier.applyVisibility` (the `alpha(0f)`) reached
from `StyleApplier.applyConfig`, and the child render loop in
`core/renderer/ComponentRenderer.kt`; iOS
`StyleEngine/visibility/VisibilityApplier` (the `.opacity(0)` branch,
already commented with the gap) and `Renderer/ComponentRenderer.swift`.

**Prediction if it lands.** One cell changes ink: `capture-with-visibility-
mixed-descendants` gains a 10x10 = 100px green square on both natives
(0.043% of the 390x600 canvas). It does **not** flip to PASS — web fails the
same cell at 0.9215 for an unrelated reason (the ref is 390x732 and paints a
pink `::view-transition` ground the harness does not bake; `viewTransitionBaked:
false`), so ios-ref/android-ref move 0.9196 → ~0.9215 and stay `f`. What it
does buy is native parity: `nativeParity` iOS-web / Android-web 0.9973 → ~1.0.
Path (not output) also changes for `anchor-center-visibility-change`, whose
hidden box has children — that cell is score-excluded.

---

## 9(f) — Android placeholder-floor under-size

`placeholder-floor-band.patch` — **APPLIED to the tree.** (This heading said
"verified, NOT applied" until wave-50 lane F1 corrected it; skeptics S3 and S7
both found the patch already in the working tree. `git diff` on
`runtimes/compose/.../StyleApplier.kt` shows `placeholderFloorInsets` and the
reworked `placeholderFloorMinSize` — the patch file is kept beside this note as
the readable record of the change, not as something still to apply.)

**What is therefore still outstanding is a GATE action, not a code action**:
the three Android baselines are not re-captured and the six ledger lines that
excuse the old divergence are still in
`tools/visual/cross-platform-expectations.json`, so `compare-screenshots.mjs`
exits non-zero on stale lines until both are done in one change (S7,
"The gate-blocking consequence of B11").

### The arithmetic, derived not remembered

`ComponentRenderer` builds the component as

```
… .then(baseModifier)        // applyConfig; padding is step 8 = its INNERMOST node
   .then(placeholderFloor)   // StyleApplier.placeholderFloorMinSize → defaultMinSize
   .then(borderContentInset) // StyleApplier.borderContentInset → Modifier.padding(band)
```

Compose measures outside-in, so the **band is already inside the floor node**
and the padding is added outside it:

```
Android total = padding + max(content + band, minH)
web    total = max(content + band + padding, 30)       // box-sizing: border-box
```

The floor subtracted padding **and** the band, i.e. `minH = 30 - padding -
band`, which gives `30 - band` whenever the floor binds — the band was
subtracted once and never added back. With `minH = 30 - padding` the two
sides are identical.

**Where this lives in the tree now** (cited by SYMBOL, not by line — the
standing constraint in docs/BACKLOG.md, since every `path:LINE` in this wave's
notes had drifted by the round-2 skeptics): all three are in
`runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt` —
`placeholderFloorMinSize` (the modifier the chain calls),
`placeholderFloorInsets` (the pure inset math: padding only, deliberately NO
`borderBandInsets` term — that comment is the fix) and `borderBoxFloorMins`
(the pure floor math). The last two are `internal` precisely so the JVM suite
can pin them without a device; `PlaceholderFloorBorderBoxTest` does.

### Measured before (committed baselines)

`baseline-ink-boxes.mjs` → `baseline-ink-boxes.json` (re-runnable ink
bounding boxes of `tools/visual/baseline/`):

| component | web | iOS | Android | short by |
|---|---|---|---|---|
| `091_Button_Outline` (padding 10/22, border 2) | 115x30 | 50x30 | **115x26** | 4 = 2x2 |
| `094_Input_Field` (padding 12/16, border 1) | 200x30 | 200x30 | **200x28** | 2 = 2x1 |
| `105_Edge_DeepNesting` (padding 10, border 2) | 115x30 | 50x30 | **115x26** | 4 = 2x2 |
| `016_Border_Solid` (padding 15, border 3) | 97x36 | 97x36 | 97x36 | exact |
| `017_Border_Dashed` (padding 15, border 2) | 102x34 | 102x34 | 102x34 | exact |
| `097_Glass_Effect` (padding 20, border 1) | 100x42 | 50x42 | 100x42 | exact |
| `090_Button_Primary` (padding 12/24, no border) | 115x30 | 50x30 | 115x30 | exact |

The old model reproduces every number: `26 = 20 + max(4, 30-24)`,
`28 = 24 + max(2, 30-26)`. The controls are exact because their padding alone
(30 / 30 / 40) already consumes the 30px floor, so the minimum never binds —
which is also why the fix cannot move them.

### Predicted after

Exactly **3 of the 390 committed baselines change**. Each grows in HEIGHT by
exactly its border band, to the height web already has — and **two of them also
grow in WIDTH**, which this list did not say until wave-50 fix lane F4 (skeptic
S3). "To the value web and iOS already have" was also wrong for iOS: see the
open item at the end of this file.

* `Android__091_Button_Outline.png` — **border box 48 → 50 wide** and
  **26 → 30 tall**. The ink bbox stays **115** wide: the synthesized
  placeholder label overflows the box, so the ink measurement is the label,
  not the box. Canvas 390x58 → 390x62, ink 115x26 → 115x30.
* `Android__094_Input_Field.png` — 200x28 → **200x30**, canvas 390x60 →
  390x62. Its width is DECLARED (200), so the horizontal floor never binds
  and only the height moves.
* `Android__105_Edge_DeepNesting.png` — **border box 46 → 50 wide** and
  **26 → 30 tall**; the ink bbox stays 115 wide for the same reason as 091.
  Canvas 390x58 → 390x62, ink 115x26 → 115x30.

The two horizontal moves fall out of the SAME one-line correction, applied to
the same constant pair: `borderBoxFloorMins` subtracts the CSS padding from the
50x30 floor (`PLACEHOLDER_FLOOR_MIN_WIDTH` / `_MIN_HEIGHT` in
`StyleApplier.kt`) and no longer subtracts the border band as well. So 091's
minimum content width goes `50-44-4 = 2` → `50-44 = 6` and its box
`44+max(0+4,2) = 48` → `44+max(0+4,6) = 50`; 105's goes `50-20-4 = 26` →
`50-20 = 30` and its box `20+max(0+4,26) = 46` → `20+max(0+4,30) = 50`.
**The PNG diff on 091 and 105 will therefore show a horizontal edge move, and
that is part of the fix, not a fourth defect.** The stop condition in the gate
procedure below is a fourth changed FILE — never a second changed axis inside
these three.

and the 6 `android-harness-placeholder-floor` ledger lines (Button_Outline
x2, Input_Field x2, Edge_DeepNesting x2, expiry 2026-09-30) become removable.
The general condition for any change is `content + band + padding < 30` (or
`< 50` horizontally) **and** a non-zero band; nothing else in the fixture net
satisfies it.

**Corpus impact: zero.** The titan feeder captures in composed mode
(`ScreenshotCaptureScreen.kt` provides `LocalWptComposedMode = true`) and
`ComponentRenderer` skips the floor entirely in that mode
(`if (composedWpt) Modifier`), so no WPT cell can move.

### Gate procedure — the patch is ALREADY in the tree; this is what the gate owes

There is no `git apply` step. The code is applied; what remains is the
measurement and the ledger.

1. `(cd apps/android-harness && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :runtime:testDebugUnitTest --tests "com.styleconverter.runtime.PlaceholderFloorBorderBoxTest")` — 13 tests
2. On the QUIET host (docs/BACKLOG.md "Gate on a quiet host"):
   `UPDATE_BASELINE=1 ./test-all.sh fixtures/visual-test.json`
3. **Exactly three** Android PNGs must move, and only these three —
   `Android__091_Button_Outline.png`, `Android__094_Input_Field.png`,
   `Android__105_Edge_DeepNesting.png` — each to canvas **390x62** with ink
   **115x30 / 200x30 / 115x30** respectively (`git status` for the set, `node
   tools/titan/results/wave50-B11/baseline-ink-boxes.mjs` for the boxes).
   **A FOURTH changed PNG is the STOP CONDITION**: the model is wrong, revert
   the change rather than refreshing the baseline — that is the check STATUS's
   "change it with a measured before/after, not by reasoning about the model"
   caveat asks for. A horizontal edge move INSIDE 091 and 105 is expected and
   is not the stop condition (their border boxes go 48→50 and 46→50 wide —
   see "Predicted after"); their ink bboxes stay 115 wide.
4. Only AFTER re-measuring the Android-web pairs, delete the six
   `android-harness-placeholder-floor` lines from
   `tools/visual/cross-platform-expectations.json`. Deleting them is correct
   only where the divergence has actually gone. S7 measured the post-fix
   iOS-Android pairs at **091 0.9540 · 094 0.9998 · 105 0.9510** — 105 clears
   the 0.95 bar by 0.001, so if the re-captured Android differs from web by a
   hair more than `090_Button_Primary` does (0.9999 / Δpx 0.000), that pair is
   a REAL divergence: it needs a NEW ledger line naming the real cause, never
   the silent deletion of the old one.

### Residual found while measuring (NOT fixed by this patch)

* `ComponentRenderer.kt` `animatedSizeFloor` calls the same
  `placeholderFloorMinSize` from a chain position **outside** `baseModifier`,
  where padding and band are both already inside it — so there the floor
  should subtract NOTHING and today subtracts both (the patch leaves it
  subtracting padding). It under-clamps an animation-supplied width/height by
  the padding. Zero fixture carriers; no corpus carriers (composed mode skips
  the floor). Needs its own call-site-aware entry point.
* **iOS CLIPS the synthesized placeholder label; web and Android let it
  overflow.** Looking at the baselines directly:
  `tools/visual/baseline/web__090_Button_Primary.png` paints a ~50px blue
  box with "BUTTON PRIMARY" spilling out to its right (ink bbox 115x30);
  `iOS__090_Button_Primary.png` paints the same box with the label truncated
  to "BUT" inside it (ink bbox 50x30). Same split on `091_Button_Outline`,
  `105_Edge_DeepNesting` and `097_Glass_Effect` (iOS 50 wide, web/Android
  115/115/100). The box geometry agrees — only the label's overflow does, so
  this is NOT the height bug this lane measured and the fix does not touch
  it. No `cross-platform-expectations.json` line covers any of these four
  components on the iOS pairs, so either those pairs are scoring above
  threshold despite the difference or they are not being scored; worth one
  check at the next gate before anyone calls them clean.

## OPEN ITEM — the iOS half-width label is a WIDTH divergence, and no ledger line names one

Added by wave-50 fix lane F4 from skeptic **S7**, which re-measured the
committed baselines from scratch and reproduced `baseline-ink-boxes.json`
exactly — and then found the axis this lane's brief had mis-stated.

**iOS draws the synthesized placeholder label at HALF the width web and
Android draw it at.** Committed-baseline ink boxes:

| component | web | Android | iOS |
|---|---|---|---|
| `090_Button_Primary` | 115x30 | 115x30 | **50x30** |
| `091_Button_Outline` | 115x30 | 115x26 (→ 115x30 after this patch) | **50x30** |
| `105_Edge_DeepNesting` | 115x30 | 115x26 (→ 115x30 after this patch) | **50x30** |
| `097_Glass_Effect` | 100x42 | 100x42 | **50x42** |
| control `094_Input_Field` | 200x28-30 | 200x28 (→ 200x30) | 200x30 — no clip, the label fits |

That is a **65 px width divergence** (35 px on 097) on four components, and it
is independent of the height bug this lane fixed: web and Android let the label
overflow the box, iOS truncates it at the box edge ("BUT" / "GLAS").

**The consequence for the ledger, which is the part a later wave must not get
wrong.** The six `android-harness-placeholder-floor` lines in
`tools/visual/cross-platform-expectations.json` excuse a **HEIGHT** divergence
(Button_Outline / Input_Field / Edge_DeepNesting x2 each, expiry 2026-09-30).
After the re-capture that height divergence is gone, so those lines go stale and
`compare-screenshots.mjs` fails on them. But **no line in the ledger names a
width divergence at all** — so if an iOS-Android (or iOS-web) pair on 090 / 091
/ 105 / 097 is still out of threshold after the re-capture, it must get a NEW
line naming the WIDTH and its real cause (the iOS harness clipping the
placeholder label). Re-pointing a surviving height line at it, or letting one
of the six survive "because the pair still fails", would inherit a reason that
is now false. S7 measured the post-fix iOS-Android pairs at **091 0.9540 · 094
0.9998 · 105 0.9510** — 105 clears the 0.95 bar by 0.001, so this is a live
possibility, not a hypothetical.

Not fixed here: the label is a HARNESS placeholder, not a CSS property, so
every cross-platform number read off these four components is measuring the
label rather than the style. Needs its own lane.
