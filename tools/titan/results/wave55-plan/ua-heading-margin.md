# ua-heading-margin — wave-55 brief for lane L4 (BACKLOG obligation 0(h))

**Lane.** L4: Compose + Swift UA heading rules + spacing. The lane is in two parts:
- **U1:** the UA `.67em`-family `margin-block` of a positioned (abspos) heading, on both natives. This is obligation 0(h).
- **U2:** behind U1, the held folded-host heading face (wave-54 L5 U2-ios and the `UAHeadingFoldGate` twins), restored
  from `tools/titan/results/wave54-ua-heading-face/held/` (`SHA256SUMS.txt`: 12/12 OK when read for this brief).

**Evidence run.** `tools/titan/runs/wave54-final`, tree at HEAD 0d0d3a3d (the wave-54 merge).
- Every score comes from `node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave54-open wave54-final`. The two runs
  agree on every cell quoted here.
- On the controls already scored by the running opening gate, `wave55-open` agrees as well: `css-break` and `CSS2`.
  `css-text-decor` was not yet captured when this brief was written (`runs/wave55-open/gate-driver/driver.log`:
  css-counter-styles at 11:00).
- The per-test IR and the native captures of inset-005/-006/-011/-014/-001 are byte-identical between wave53-final and
  wave54-final, and between wave54-open and wave54-final (`cmp`).
- Every PNG named below was opened: the ref next to the iOS and Android captures.
- Synthetic pictures are scored with the gate's own metric (`tools/titan/inject-wpt-block.mjs` `diffWebVsRef`).
- Nothing was built, run or captured. Only read-only node and python ran, at `nice`, because `wave55-open` is gating on
  this host.

**Written beside this file** (`tools/titan/results/wave55-plan/`):
- `ua-heading-margin.census.mjs` → `.census.out.txt` / `.census.json`. Covers all 1435 per-test IR documents, joined to
  the scored cells through `score-gate.mjs` `loadRun`.
- `ua-heading-margin.replay.mjs` → `.replay.out.txt`, plus `ua-heading-margin.calibrate.mjs` → `.calibrate.out.txt`.
  Pictures are in `ua-heading-margin-replay/`.
- `ua-heading-margin.fakes.py`, which builds the per-arm picture sets.
- `ua-heading-margin.geometry.py` → `.geometry.wave54-final.out.txt` and `.geometry.arms.out.txt` (the self-test over
  eight arms).
- `ua-heading-margin.wave54-probe-on-arms.py` → `.wave54-probe-on-arms.out.txt`. This runs the COMMITTED wave-54 probe,
  unchanged, against this lane's arms.

## Summary

The four folded `<h1>`s of `css-text-decor/text-decoration-inset-005/-006/-011/-014` share one setup:
- each is `position: absolute` inside a `position: relative` `<div>`;
- each has no author margin;
- each is a `meta.runs` host whose wire is only `Position ABSOLUTE` (014 adds `FontFamily monospace` and `Width 16ch`).

Chromium's UA sheet gives each of them two things:
- the 2em bold face;
- `margin-block: .67em` of that face, which is 21.44 px. For 014 it is 17.42 px: the 26 px monospace face.

Neither native gives a NESTED out-of-flow box any UA margin. Traced at HEAD (§2 B), which closes the BACKLOG 0(h) "Not
traced" clause:
- the in-flow collapse plan bails the whole container on any out-of-flow child, on both natives;
- the wave-52 T6 rule covers canvas-HOISTED ROOTS only.

So both natives paint the heading ~21 px high, and also at the 16 px regular face (the held half).

What the replays measured (gate metric):
- **The margin alone RAISES all eight native cells.** It is a pure translation of today's pixels: +0.0098…+0.0177 on
  005/006/014, +0.0061/+0.0124 on 011. It flips none.
- **Margin + face** scores 0.9959–0.998 at the ceiling. After the raster allowance measured on the anchors
  inset-001…004 that is P on all six 005/006/014 natives, as follows:
  - **iOS:** the three iOS flips are faithful. iOS wraps an abspos 2em heading at the containing block, seen on device in
    `wave54-final css-text-decor/text-decoration-inset-009 ios P 0.9585`.
  - **Android 014:** faithful. It has a definite `16ch` width.
  - **Android 005/006:** they also pass, but they are **DEGENERATE**. Compose measures an abspos child of a relative
    parent UNBOUNDED, so "fox" stays on line 1 and is clipped at x390. This is seen on device in
    `wave54-final css-text-decor/text-decoration-inset-009 android P 0.9638`: one line, ink to x373.

**GO: four units.**
- U1-android and U1-ios (the margin);
- U2-android and U2-ios (the held face, restored).

U1 is spec-right and up on its own. U2 is pre-registered only with U1 on the tree.

## 1. Targets (wave54-final, re-derived) and what the pictures show

| cell | wave54-open → **wave54-final** | role |
|---|---|---|
| `wave54-final css-text-decor/text-decoration-inset-005 ios f 0.8995` | f 0.8995 → **f 0.8995** | folded abspos `<h1>`: margin (U1) + face (U2) |
| `wave54-final css-text-decor/text-decoration-inset-005 android f 0.9` | f 0.9 → **f 0.9** | same |
| `wave54-final css-text-decor/text-decoration-inset-006 ios f 0.8987` | f 0.8987 → **f 0.8987** | same |
| `wave54-final css-text-decor/text-decoration-inset-006 android f 0.8993` | f 0.8993 → **f 0.8993** | same |
| `wave54-final css-text-decor/text-decoration-inset-014 ios f 0.9238` | f 0.9238 → **f 0.9238** | folded monospace abspos `<h1>`, `width: 16ch` |
| `wave54-final css-text-decor/text-decoration-inset-014 android f 0.923` | f 0.923 → **f 0.923** | same |
| `wave54-final css-text-decor/text-decoration-inset-011 ios f 0.6999` | f 0.6999 → **f 0.6999** | abspos `<h1>` whose fold BAILS (stacked): margin only (U1); the face stands down |
| `wave54-final css-text-decor/text-decoration-inset-011 android f 0.6852` | f 0.6852 → **f 0.6852** | same |

Web is P on all four: 005 0.9983, 006 0.9985, 011 0.9948, 014 0.9975. The web harness renders a real `<h1>` under
`margin: revert`. Web is not a carrier.

**Anchors.** These are the same abspos `<h1>` in the same relative `<div>`, but the extractor bakes `UA_H1_PROPS` on a
COLLAPSED chain: `FontSize 32`, `FontWeight 700`, `MarginTop/Bottom 21.44`.
- `wave54-final css-text-decor/text-decoration-inset-001 ios P 0.9707` / `android P 0.9848`
- `-002 ios P 0.9706` / `android P 0.9858`
- `-003 ios P 0.9706` / `android P 0.984`
- `-004 ios P 0.9634` / `android P 0.9859`

**The pictures** (390×600). Band = dark-ink rows merged across ≤ 2 blank rows (`geometry_common.bands`). All tops below are
scanned from y96, below the prose.

**Ref inset-005 / -006.** The explanation's three lines are y36–90.
- The heading is the 2em bold paragraph "the ultra-quick b(row)n", band y134–173, x16–336. The sup "quick" is the first
  ink.
- "fox" wraps onto line 2, band y188–212, x16–64.
- `ua-heading-margin.geometry.wave54-final.out.txt`.

**iOS -005 / -006.**
- ONE 16 px regular line, band y109–130, x16–191 / 194.
- The sup is raised and the sub lowered inside one paragraph: the fold is engaged.
- The underline is one continuous segment, x44–166.

**Android -005 / -006.**
- The same single line, band y107–131.
- The underline is drawn PER SPAN at each span's own baseline, in segments under "ultra-", the raised "quick", "b", the
  lowered "row" and "n". The ref draws one underline at the `u`'s baseline. This is the 4(e) baseline class; see §9 R3.
- I looked at `ua-heading-margin-replay/inset-005.ref-M21android-M21ios-Dandroid.sbs.png`.

**inset-014.**
- Ref: "the ultra-quick / brown fox" at 26 px bold monospace, bands y129–154 (x17–258) and y163–186 (x16–155).
- Natives: the SAME two-line wrap at 13 px regular, tops y111.
  - iOS: y111–121 / 128–137.
  - Android: y111–122 / 127–138.

**inset-011.**
- Ref: the stacked Arabic heading starts at y144 at the 2em face with `line-height: 2`.
- Natives: they paint the prose in a serif face on FOUR lines (y36–107; the ref has three, y36–89). The runs are stacked
  at 16 px from y137 (iOS) / y136 (Android), so the missing margin is partly hidden by the extra prose line.

**The margin, seen directly.** Compare the anchors with the targets. In all of them the 2em face top would sit at the
same place, and every native lands within +2 px of the ref:

| test | `<h1>` wire | ref | ios | android |
|---|---|---|---|---|
| inset-001 | baked `MarginTop 21.44` | 115 | 117 | 116 |
| inset-005 | `Position ABSOLUTE` only | 134 | 109 | 107 |
| inset-006 | `Position ABSOLUTE` only | 134 | 109 | 107 |
| inset-014 | `Position ABSOLUTE` only | 129 | 111 | 111 |

**The wrap, seen directly.** inset-009 has the same abspos `<h1>` with the baked face and margin, and its line is longer
than the 358 px containing block (I looked at `ua-heading-margin-replay/inset-009.ref-ios-android.sbs.png`):
- the ref wraps "fox" (bands y155–185 x351, y190–219);
- **iOS wraps** at the containing block: y157–186 x353, then y197–220;
- **Android does not**: ONE band y156–185 running to x373, "fox" clipped at the frame.

**The wire** (`tools/titan/runs/wave54-final/sections/css-text-decor/per-test-ir/`):
- The 005 `<h1>` `text-decoration-inset-005__1__0-069` carries only `Position ABSOLUTE`, with
  `meta.runs [{text:"the "},{child:u},{text:" fox"}]`. The `u` (`…__0__0-070`) is a runs host
  `[ultra-, sup, " b", sub, n]` carrying `TextDecorationColor black`.
- No component in the document declares a `FontSize`. The three roots carry `BackgroundColor`/`Color`, `Color`, and
  `Position`/`Color`. So the em base is the UA `medium`.
- 006 is the same.
- 014: `Position ABSOLUTE`, `FontFamily ["monospace"]`, `Width {v:16,u:CH}`.
- 011: `Position`, `LineHeight ×2`, `Width 10em`, `Direction RTL`, `lang ar`. Its runs are
  `[u.a, " —", br, " ", u.b]`.

## 2. Mechanism (traced at HEAD 0d0d3a3d), spec, arithmetic

### A. What the UA sheet gives the box, and where it lands

**The spec rule.** HTML LS §15.3 Rendering, "Sections and headings" — §15.3.7 in the numbering the task and the wave-54
brief use; the a5708b7e commit cites §15.3.6, and the number has drifted across revisions. The declarations:
```css
h1 { margin-block: 0.67em; font-size: 2.00em; font-weight: bold }
h2 { margin-block: 0.83em; font-size: 1.50em }
h3 { margin-block: 1.00em; font-size: 1.17em }
h4 { margin-block: 1.33em }
h5 { margin-block: 1.67em; font-size: 0.83em }
h6 { margin-block: 2.33em; font-size: 0.67em }
```
The supporting rules:
- css-values-4 §6.1.1: the margin's `em` is the element's OWN computed font-size. The font-size's own `em` is the
  parent's.
- css-cascade-4 §6.1: the UA origin loses to any author declaration, per longhand.
- css-cascade-4 §4.3: a UA declaration beats inheritance.
- css-logical-1: in horizontal-tb, `margin-block` = `margin-top` / `margin-bottom`.

**Where the box goes.**
- CSS 2.1 §10.6.4: for `top: auto` the static position is the hypothetical box's top MARGIN edge, so the margin offsets
  the border box below it.
- CSS 2.1 §8.3.1: "Margins of absolutely positioned boxes do not collapse", not even with the preceding sibling's margin.
- CSS 2.1 §9.4.1: an abspos box roots a new block formatting context.

**Arithmetic, inset-005 / -006 / -011.**
- The inherited size is 16 px (no ancestor FontSize, §1 wire). The face is 2em × 16 = 32 px, so the margin is
  .67 × 32 = **21.44 px**.
- The static position:
  - the `<p>`'s three 20 px lines end at ≈ y92;
  - its 16 px `margin-bottom` collapses through the empty `<div>` (§8.3.1, self-collapsing), which puts the div top at
    y108;
  - the `<h1>` border box starts at 108 + 21.44 = 129.44;
  - the raised sup is the first ink at **y134** (ref).
- The natives start the line box at the div top (y108), which puts the first ink at y109 / y107. With the margin it moves
  to **y130 / y128**.

**Arithmetic, inset-014.** There is no FontSize and the first family is the monospace generic.
- Blink scales the em UA heading by defaultFixed/default = 13/16, so the face is 2 × 16 × 13/16 = 26 px.
- Both twins of `UAElementFontRule` already compute this through `MonospaceUAFontSize`: Compose `emBasePx` :109, Swift
  `emBasePx(merged:)` :200; the L5 pin M2 gives 26.
- The margin is .67 × 26 = **17.42 px**: ref first ink y129; the natives' y111 moves to **y128**.

**Arithmetic, inset-011.** The face is 32 px (it declares no size), so the margin is 21.44. The natives' stacked runs move
down 21 px as a block: 137 → 158 (iOS), 136 → 157 (Android).

### B. Why neither native gives a nested abspos heading a UA margin

UA block margins reach a native box by exactly three routes. A nested out-of-flow heading takes none of them.

1. **In-flow CHILDREN, through the §8.3.1 collapse plan.**
   - Compose: `ComponentRenderer.kt` `blockCollapsePlanFor` :473 → `uaChildBlockEdges` (:598, `spacing/UaBlockChildMargins.kt`).
   - iOS: `StyleEngine/spacing/MarginCollapsePlanner.swift` :151 `UABlockMargin.childBlockEdges`.
   - **Both bail the WHOLE container on any out-of-flow child** (the B4 rule): Compose `ComponentRenderer.kt` :534
     `CollapsePlanResult(null, "out-of-flow child")`, iOS `MarginCollapsePlanner.swift` :82–89. So the `<div>` holding
     the abspos `<h1>` has no plan at all.
2. **Composed ROOTS, through the root stack.**
   - Android: `apps/android-harness/…/UaBlockMargins.kt` `composedRootStackPlan` :594.
   - iOS: `StyleEngine/spacing/ComposedRootStack.swift` `composedRootStackPlan` :428.
   - An RC1 static-position root takes Shape 1/2, which is (0, 0) with no UA margin (iOS :429–431). The `<h1>` is not a
     root anyway: it has `slot.parent`.
3. **Canvas-HOISTED roots, through wave-52 L2 T6 `withUaBlockMarginOnHoistedRoot`.**
   - Android: `UaBlockMargins.kt` :651, called at `ScreenshotCaptureScreen.kt` :1769.
   - iOS: `ComposedRootStack.swift` :650, called at `apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift`
     :391.
   - These ROOTS only. Its one corpus carrier is `CSS2/css21-errata/s-11-1-1b-006`'s root `<p>`.

**The renderer does paint an abspos box's DECLARED margin.** That is why the anchors land within +2 px of the ref:
- Compose: `StyleApplier.applyProperties(effectiveProperties, collapsedMargin, …)` at `ComponentRenderer.kt` :1416, with no
  `LocalCollapsedMargin` override for a `RenderAbsoluteChild` (:4707).
- iOS: the merged → resolved list (:433) feeds `StyleBuilder` / `MarginApplier` on the overlay child (`positionedChildren`
  :2094).

So a margin injected into the element's MERGED list at the cascade step paints with no change to layout code.

**The inheritance channel cannot leak it.**
- Compose: children inherit `effectiveProperties.filter { it.type in INHERITED_PROPERTY_TYPES }` (:1930).
- iOS: children inherit `InheritedText.inheritable(from:)`.

**The fold cannot see it.** `grep -c Margin` returns 0 on each of `typography/inline/InlineRunFold.kt`,
`InlineSpanRing.kt`, `InlineRunFlow.swift` and `InlineSpanRing.swift`. So the 005/006/014 fold verdicts (`Folded`) and
011's (`Bailed`) are invariant under U1.

### C. The fix: one more tag-keyed UA cascade step, in `spacing/`

**`UaPositionedHeadingMargin`** (Compose) / **`UAPositionedHeadingMargin`** (Swift). This is the out-of-flow twin of
`uaChildBlockEdges` and T6, run at the cascade step where `ListStyleUaRule` and `UAElementFontRule` already run.

**It fires iff all of these hold:**
- WPT capture is on (`LocalWptCaptureMode.current` / `wptCaptureMode`). This is the same gate the whole UA-margin family
  uses (`uaChildBlockEdges(enabled:)`). A product render keeps no UA margin for any heading, in flow or not, and the
  327-pair corpus carries no `_tag` anyway.
- The tag is one of `h1`…`h6`.
- The element's OWN post-reset list declares `Position ABSOLUTE | FIXED` (css-position-3 §2.1; Position is not inherited).
- At least one block edge is undeclared: `MarginTop` / `MarginBlockStart`, `MarginBottom` / `MarginBlockEnd`. Author
  beats UA per edge (css-cascade-4 §6.1), exactly the `uaChildBlockEdges` rule.
- The writing mode is horizontal. A vertical-mode heading is logged once and left alone, because `margin-block` would map
  to left/right. There are 0 corpus carriers.

**What it emits.** `m = UaBlockMarginFontBasis.uaBlockMarginEm(tag) × F`, appended as `MarginTop {"px": m}` /
`MarginBottom {"px": m}` on each undeclared edge. That is the T6 wire leaf shape, which every margin reader already
decodes.

**F is the UA-CASCADE font-size:**
- **Author-unsized heading:**
  - F = `UAElementFontRule.sizeMultiplier(tag) × UAElementFontRule.emBasePx(preUa)`, where `preUa` is the merged list
    BEFORE the font step (`listUaProperties` on Compose; the hoisted `mergedPreUA` on iOS).
  - This is deliberately **independent of the face gate**. The margin is the UA sheet's computed value, and the face's
    stand-down is a rendering compromise.
  - With the face held, U1 still draws the margin of the 2em face. The independence is what makes U1 and U2 separate
    units: the margin does not change when U2 lands or reverts.
- **Author-sized heading:**
  - F = `UaBlockMarginFontBasis.ownFontSizePx(own, inheritedPx, tag)` rungs B3–B8, with `inheritedPx` the parent's
    published px, else 16.
  - An unresolvable size (var / calc / unexpanded `Font`) is logged once and skipped. Repo rule: no silent fallthrough.
  - There are 0 carriers: every author-sized abspos heading in the corpus declares both margins.

**It returns the SAME list instance when it does not fire.** The `remember` keys downstream key on it.

**Rejected basis: the face the native PAINTS.** This would be 16 px while the face is held, giving 10.72 / 8.71.
- It is lower than the UA-cascade basis on 005/006/014 on both natives: R11/R9 versus M21/M17 in `.replay.out.txt`, e.g.
  005 ios 0.9067 vs 0.9167.
- It would couple U1 to U2: the margin would change when the face lands.

**Rejected placement: a harness IR rewrite** (recursing T6 over every nested component).
- The em base needs the render-time inheritance channel, and the cascade step is where the element's own list
  post-`all` / buckets is known.
- It would edit two over-cap harness files: `ScreenshotCaptureScreen.kt` 2570, `CaptureCanvas.swift` 1163.
- The in-flow twin (`uaChildBlockEdges`) already lives in the runtime.

**Scope: headings only, this wave.**
- The census (§5) finds two more out-of-flow UA-margin components that are not headings. Both are nested `<p>`s, with
  no positioned ancestor and an inset (hoisted at runtime, but not roots, so not T6):
  - `CSS2/css21-errata/s-11-1-1b-005`, an f row on both natives;
  - `css-anchor-position/anchor-center-safe-rtl` ×2, unscored, and BACKLOG (i)'s named control for another lane.
- Widening is queued with that census (§9 R9). The rule logs once when it meets an out-of-flow non-heading UA-margin tag.

### D. The held face (U2): what it needs, and its radius

This is the wave-54 L5 builder-pass gate, restorable verbatim. The verdicts below were executed on wave53-final wire,
which is byte-identical in wave54-final.
- **The predicate.** `typography/UAHeadingFoldGate.{kt,swift}` stands the heading half down iff the heading has children
  AND its runs do not fold.
- **Verdicts:** inset-005/-006/-014 → `Folded` (the face applies); inset-011 →
  `Bailed member-prop:TextDecorationColor-divergence` (stands down).
- **Sound because:** fold admission reads no FontSize, FontWeight or Margin (L5 brief §4 D, plus the grep above).

**U2's radius.** The census's U2 section covers every `h1`–`h6` with slot children, any position: **exactly 4 hosts**,
inset-005/-006/-011/-014. So U2 changes only 005/006/014; 011 stands down and is byte-identical under U2.

**What does not apply cleanly at HEAD:**
- `held/seam-1.foldgate.patch` does NOT apply: `git apply --check` fails at `ComponentRenderer.kt:1138`, because GO-SMALL
  landed in its place (a5708b7e).
- `held/UAElementFontRule.swift.foldgate.diff` does NOT apply (the GO-SMALL docs differ).

**What does apply cleanly:** `held/seam-2.foldgate.patch` and `held/UAElementFontRuleTests.swift.foldgate.diff`.

**What the held Compose test needs.** `held/UAHeadingFoldGateTest.kt.txt` uses `UAHeadingFaceReplay.site` and
`UAHeadingFaceWire.INSET_005/006/011/014`. At HEAD these exist as `internal object`s inside
`UAElementFontRuleTest.kt` (:178 / :220).

**What U2 changes at the seams:**
- **Compose:** `standsDown = UAElementFontRule.headingStandsDown(component)` (:1161) becomes
  `UAHeadingFoldGate.standsDown(component, listUaProperties)`. `headingStandsDown` (:124) becomes dead and is removed.
- **iOS:** the `hasElementChildren:` argument (:356) becomes
  `UAHeadingFoldGate.standsDown(component:inFlowChildren:mergedPreUA:)` (`inFlowChildren` :780).

## 3. Ownership (disjoint)

**Owned by L4.**

Compose:
- NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt` (≤ 200 lines).
- NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMarginTest.kt`.
- NEW, patch-borne, created by seam 1: `runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMarginSeamWiringTest.kt`.
- `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleSeamWiringTest.kt`
  (sha256 1c67fe44…): **patch-borne edits only**.
  - Seam 1 changes its handoff token `val unresolvedProperties = rawProperties`.
  - Seam 3 changes its `fontStep` `standsDown =` token.
  - If L7's "test-file pins" claims this file, the orchestrator must assign it. The edits must ride the seam patches
    (rule 2b): the file pins renderer source text.
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` (199 lines, sha256 c78f3557…):
  - U1: the header sentence at :26–27, "UA heading MARGINS stay with spacing/UaBlockChildMargins.kt + the harness", is
    rewritten in place to name the new file;
  - U2: `headingStandsDown` is removed and its kdoc refs re-pointed at the gate.
- `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt` (243 lines, sha256
  4c9dd125…). U2 retargets three things:
  - the `GO-SMALL - every heading host with child boxes keeps its list` pin (:96);
  - the replay's `seam` (:210) to the gate;
  - pins G1/G2.
- RESTORED (U2-android): `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt` (held,
  105 lines, sha256 e0e3186d…) and `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAHeadingFoldGateTest.kt`
  (held, 130 lines, sha256 0d99cd9a…).

Swift:
- NEW `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift` (≤ 200 lines).
- NEW `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginTests.swift`.
- NEW, patch-borne, created by seam 2: `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginSeamWiringTests.swift`.
  The precedent is `AbsposStaticOffsetSeamWiringTests.swift`.
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift` (286 lines, sha256
  27cd64a9…), **docs only**:
  - U1: the KNOWN GAPS bullet at :69 ("The UA heading MARGINS … are NOT injected here — spacing/UABlockMargin.swift
    already owns them") gains the out-of-flow owner;
  - U2: TWIN STATUS and the `headingAppliesTo` record (:160–171) are re-dated.
  - The net edit is ≤ +12 lines. Past the ~300-line split line, the table moves to the lane note: the wave-54 N7
    precedent.
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAElementFontRuleTests.swift` (sha256 58b3f1fa…): U2 adds the held
  diff, which applies clean.
- RESTORED (U2-ios): `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift` (held,
  85 lines, sha256 2ca760a6…) and `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift` (held,
  214 lines, sha256 5c275b88…).

Lane dir: `tools/titan/results/wave55-ua-heading-margin/`.

**Read-only. The rule CALLS these and never edits them:**
- `spacing/UaBlockMarginFontBasis.{kt,swift}` (`uaBlockMarginEm`, `ownFontSizePx`);
- `spacing/UaBlockChildMargins.kt`, `UABlockMargin.swift`, `UABlockChildMargin.swift`, `MarginCollapsePlanner.swift`,
  `ComposedRootStack.swift`;
- `typography/MonospaceUAFontSize.{kt,swift}`;
- `core/variables/DynamicValueResolver.kt`. It is **L2's**; I call `fontSizePxOf` through `UAElementFontRule.emBasePx`.
  If L2 changes its semantics, re-run my pins.
- the inline folds `InlineRunFold.kt` / `InlineRunFlow.swift` / `InlineSpanRing.{kt,swift}`, and `InlineRunPlan`;
- the harness T6 files;
- `layout/position/**`, which includes L3's `PositionedParentFlowSlot.kt` / `CanvasRootHoist.kt`;
- `typography/LineBoxMetrics.swift` (L1's).

**Not touched:** every web file; `apps/web-harness/src/sdui/ComponentRenderer.tsx`; `tools/titan/extract-fixture.mjs`;
`lists/**`; `borders/**`; `effects/**`; the wave-54 probe `wave54-plan/ua-heading-face.geometry.py`.

**Disjointness checked against the other lanes' files in this directory as of 13:04:**
- No other wave-55 brief file names an inset-00x cell, `RenderAbsoluteChild` or `absposOverflowMeasure`.
- L1's census (`hyphen-ios-linebox.census.out.txt`) touches only `line-height: normal` text. Its css-text-decor
  documents are `text-decoration-dotted-001/-002`, not mine.

## 4. Units (one commit each)

| unit | paths (one commit) | predicts | revert |
|---|---|---|---|
| **U1-android** | `UaPositionedHeadingMargin.kt` + test; `UAElementFontRule.kt` (one doc line); seam 1 (+ its two patch-borne test hunks) | the 4 android carriers move DOWN by 21 / 17 px as a block; alone, all four rise (005 0.9 → ≈0.9121, 006 → ≈0.9117, 014 → ≈0.9328, 011 → ≈0.6913); no flip | alone; reverting it ALSO reverts U2-android (U2's rows without U1 are the B-arm FALLS, −0.0103…−0.0135) |
| **U1-ios** | `UAPositionedHeadingMargin.swift` + tests; `UAElementFontRule.swift` (one doc bullet); seam 2 (+ its patch-borne test) | the 4 ios carriers down by 21 / 17 px; alone 005 → ≈0.9167, 006 → ≈0.9164, 014 → ≈0.9336, 011 → ≈0.7123; no flip | `revertOrder`: U2-ios first (seam 4 reads seam 2's `mergedPreUA`); reverting it reverts U2-ios too |
| **U2-android** | restored `UAHeadingFoldGate.kt` + test; `UAElementFontRule.kt` (`headingStandsDown` removed); `UAElementFontRuleTest.kt` retargets; seam 3 (+ its patch-borne test hunk) | 014 android → P ≈0.985 (faithful); 005 / 006 android → P ≈0.967 **DEGENERATE** (no wrap) | alone; seam 3 changes the `standsDown =` line (:1161), seam 1 the lines after :1162, with one unchanged line between them. The lane proves both revert orders in a `git archive` export tree |
| **U2-ios** | restored `UAHeadingFoldGate.swift` + tests; `UAElementFontRuleTests.swift` (held diff); `UAElementFontRule.swift` (docs); seam 4 | 005 / 006 / 014 ios → P ≈0.966–0.967 (faithful if iOS wraps, §9 R1) | alone |

**GO-SMALL fallback**, if the orchestrator will not carry two DEGENERATE-labelled passes: U1-android, U1-ios and U2-ios,
with U2-android held. That gives iOS +3, Android 0 flips, and four Android rows up.

**What U2-android depends on.** It earns its 005/006 rows faithfully only after a Compose §10.3.7 shrink-to-fit for an
abspos child of a RELATIVE parent:
- `RenderAbsoluteChild` → `absposOverflowMeasure`, `measurable.measure(Constraints())` at `ComponentRenderer.kt` :4898;
- the RC1 / `PositionedParentFlowSlot` mounts already have one: `CanvasRootHoist.staticPositionShrinkToFit`.

That is position layout, not this lane's. It is queued with its existing degenerate carrier,
`wave54-final css-text-decor/text-decoration-inset-009 android P 0.9638`.

## 5. Carrier census (`ua-heading-margin.census.mjs` → `ua-heading-margin.census.out.txt`, wave54-final)

**Components in the census:** 1435 documents scanned; **21 documents carry an out-of-flow (ABSOLUTE|FIXED) UA-margin
tag: 22 components.**
- hoisted root: 1 (s-11-1-1b-006's `<p>`, T6);
- RC1 root: 0;
- nested: 21;
- bucket-only Position: 0.

**Where the rule would fire, before the heading scope:** on 7 components in 6 documents. The heading scope keeps 4 of
them.

The 12 declared-margin abspos `<h1>` documents all declare both edges (`decl=TB`), so they are identity:
- inset-001/002/003/004/009/015/016/024, which are P on both natives;
- inset-007/008/012/013, which are f.

**Capture carriers (stems, `css-text-decor/<stem>.png`):**

| unit | ios | android | web |
|---|---|---|---|
| U1 | `wpt__css-text-decor__text-decoration-inset-005`, `-006`, `-011`, `-014` | the same 4 | none |
| U2 | `…-005`, `-006`, `-014` (011 stands down: identical under U2) | the same 3 | none |
| union | 4 | 4 | 0 |

**Wire carriers: 0.** Both units are runtime-only. No converter, extractor or IR byte changes, and no freeze event.

**Currently passing cells among the carriers: 0.** All 8 are f, so revert rule 1 cannot fire on a carrier.

**Must not move.** Decoded-pixel identical; control-check:
- The declared-margin abspos anchors, 24 native cells: inset-001/002/003/004/009/015/016/024 ios + android (P
  0.9585–0.9859) and inset-007/008/012/013 ios + android (f 0.8579–0.9162). Identity by "author declares both edges".
- Every web cell of the 16 abspos-`<h1>` documents.
- `wave54-final css-break/block-in-inline-015-print ios P 0.9894` / `android P 0.9986`: in-flow leaf headings, untouched
  by U1 (not positioned) and by U2 (leaves).
- Every other heading cell. U1 needs ABSOLUTE / FIXED and the census finds none outside these 16 documents; U2's radius
  is exactly the 4 hosts.
- `wave54-final CSS2/css21-errata/s-11-1-1b-005 ios f 0.9536` / `android f 0.9307`: an abspos `<p>`, outside the heading
  scope.
- `wave54-final CSS2/css21-errata/s-11-1-1b-006 ios P 0.9992` / `android P 0.9983`: the T6 root, which declares both
  edges after T6.
- The 9-fixture net and the 327-pair corpus. The IR carries no `_tag` there, the rule is capture-gated, and of the
  conformance goldens only 6 carry `sourceTag`, none of them a heading.

**Restated from wave 54.** The wave-54 `mustNotMove` list includes the six inset-005/-006/-014 native cells and the
inset-011 natives; the wave-54 probe calls the inset-011 natives its CONTROL. **All eight are this lane's carriers now.**
The wave-55 expectations must drop them from must-not-move and supersede the wave-54 011 control (§7).

## 6. Predictions (wave54-final → the wave-55 closing gate; restate against wave55-open if it differs)

**Basis.** Replay arms, gate metric, `ua-heading-margin.replay.out.txt`:

| cell | today | M = U1 alone (±1 px) | R (rejected basis) | B = U2 alone | C = U1+U2, wrap | D = U1+U2, no wrap |
|---|---|---|---|---|---|---|
| inset-005 ios | 0.8995 | 0.9167 (0.9157–0.9172) | 0.9067 | 0.8882 | 0.9976 | — |
| inset-005 android | 0.9 | 0.9121 (0.9117–0.913) | 0.9048 | 0.8865 | 0.9959 | 0.9791 |
| inset-006 ios | 0.8987 | 0.9164 (0.9153–0.9169) | 0.9062 | 0.8879 | 0.9977 | — |
| inset-006 android | 0.8993 | 0.9117 (0.9112–0.9126) | 0.9041 | 0.8861 | 0.9959 | 0.9792 |
| inset-014 ios | 0.9238 | 0.9336 (0.9328–0.9337) | 0.9279 | 0.9139 | 0.998 | — |
| inset-014 android | 0.923 | 0.9328 (0.9323–0.9332) | 0.9281 | 0.9127 | 0.9968 | — |
| inset-011 ios | 0.6999 | 0.7123 (0.7106–0.714) | 0.7115 | = today | = M | = M |
| inset-011 android | 0.6852 | 0.6913 (0.6896–0.6933) | 0.6985 | = today | = M | = M |

**Calibration.** The T column reproduces every wave54-final cell exactly.

**Raster allowance.** Arm C on the anchors inset-001…004 minus their real cells (`.calibrate.out.txt`):
- iOS 0.0281–0.0345 (mean 0.0297);
- Android 0.0107–0.0136 (mean 0.0123).

Each C / D estimate below has that range subtracted.

| cell (wave54-final) | → predicted | tier | floor | gate | unit | direction |
|---|---|---|---|---|---|---|
| css-text-decor/text-decoration-inset-005 ios f 0.8995 | **P ≈0.966** (0.9631–0.9695) | MED | **0.95** | [M] + [F] gating | U2-ios (needs U1-ios) | up |
| css-text-decor/text-decoration-inset-006 ios f 0.8987 | **P ≈0.966** (0.9632–0.9696) | MED | **0.95** | [M] + [F] gating | U2-ios | up |
| css-text-decor/text-decoration-inset-014 ios f 0.9238 | **P ≈0.967** (0.9635–0.9699) | MED-LOW (no monospace anchor, §9 R6) | **0.95** | [M] + [F] gating | U2-ios | up |
| css-text-decor/text-decoration-inset-014 android f 0.923 | **P ≈0.985** (0.9832–0.9861) | MED | **0.95** | [M] + [F] gating | U2-android (needs U1-android) | up |
| css-text-decor/text-decoration-inset-005 android f 0.9 | P ≈0.967 (0.9655–0.9684) **DEGENERATE** (`stayDegenerateEvenIfPass`: "fox" clipped on line 1) | MED-LOW (§9 R3) | — | [M] gating, [F] REPORT | U2-android | up; never claimed |
| css-text-decor/text-decoration-inset-006 android f 0.8993 | P ≈0.967 (0.9656–0.9685) **DEGENERATE** | MED-LOW | — | [M] gating, [F] REPORT | U2-android | up; never claimed |
| css-text-decor/text-decoration-inset-011 ios f 0.6999 | f ≈0.712 (0.7106–0.714) | MED | — | [M] gating | U1-ios | up (directed) |
| css-text-decor/text-decoration-inset-011 android f 0.6852 | f ≈0.691 (0.6896–0.6933) | MED | — | [M] gating | U1-android | up (directed) |

**The A/B drop-U2 arm (U1 alone).** Predicted by arm M, a translation of today's own pixels:
- 005 ios ≈0.9167 / android ≈0.9121;
- 006 ios ≈0.9164 / android ≈0.9117;
- 014 ios ≈0.9336 / android ≈0.9328;
- every one up and f, MED-HIGH.

The direction is robust at ±1 px. What is unexecuted is that the device does exactly this.

**Expected movement.**
- iOS: +3 (2 MED + 1 MED-LOW).
- Android: +1 faithful (MED), plus 2 DEGENERATE passes (MED-LOW). Those are labelled, never written as gains and never
  picture-correct. The scorer's totals count them as P. The plan lists them under `stayDegenerateEvenIfPass`, the
  wave-54 category that carried `block-ellipsis-002 ios` (wave-54 PLAN §1).
- Lost 0. Web 0. Newly measured 0. Unmeasured-now 0.

**Under GO-SMALL** (U2-android held): Android +0 and 014 android ≈0.9328 f.

## 7. Geometry probe — `ua-heading-margin.geometry.py <run> [--arm TAG]` (written, run)

**What it extends.** It extends `wave54-plan/ua-heading-face.geometry.py`, which stays unchanged, by separating the
MARGIN axis from the face:
- it imports `wave53-plan/geometry_common.py` (`paths`, `load`, `dark`, `bands`, gap 2);
- every line ends in `→ GEOMETRY OK` or `→ GEOMETRY WRONG (<why>)`;
- the ref rows self-check: exit 1 if one fails.

**The keys** (substring keys as `geometry-gate.py` reads them; expect string `GEOMETRY OK` unless stated):

| key | class | unit it gates | rule |
|---|---|---|---|
| `[M] text-decoration-inset-005 ios`, `[M] text-decoration-inset-006 ios`, `[M] text-decoration-inset-014 ios` | **gating** | U1-ios | FACE-AWARE band-1 top (scan from y96): with band 1 ≥ 30 px tall (014: ≥ 20) the 2em face is present, so top = ref ±3 (134 / 134 / 129); otherwise top = wave54-final top + the margin ±2 (109+21 = 130; 014: 111+17 = 128) |
| `[M] text-decoration-inset-005 android`, `-006 android`, `-014 android` | **gating** | U1-android | same; Android base 107 → 128, 014 111 → 128 |
| `[M] text-decoration-inset-011 ios`, `[M] text-decoration-inset-011 android` | **gating** | U1-ios / U1-android | EVERY band (scan from y112) = the recorded wave54-final band + 21 ±1 px on top and bottom, x-extent ±1 (the margin is a pure translation; nothing else may move) |
| `[F] text-decoration-inset-005 ios`, `-006 ios`, `-014 ios` | **gating** | U2-ios | wave-54 `verdict_inset` with the band-top check (scan from y96): 2 bands; band 1 ≥ 30 / 20 px; band 2 right edge ±4 / ±6 px of x64 / x155; every top ±3 px of the ref |
| `[F] text-decoration-inset-014 android` | **gating** | U2-android | same |
| `[F] text-decoration-inset-005 android`, `[F] text-decoration-inset-006 android` | **report** | — | same rule; expected `GEOMETRY WRONG (1 heading line band(s) vs ref 2` until a Compose abspos shrink-to-fit lands. A pass here would be the degenerate one |
| `[C] text-decoration-inset-001/-002/-003/-004/-009/-015/-016/-024 ios|android` (16) | control | — | bands (scan from y96) EXACTLY the recorded wave54-final bands (declared margins: the rule defers) |
| `[C] block-in-inline-015-print ref|ios|android` | control | — | wave-54 `verdict_015` vs the ref (in-flow leaf headings) |
| `[C] s-11-1-1b-005 ios|android` | control | — | bands (y0–140) EXACTLY wave54-final's (abspos `<p>`: outside the heading scope) |
| every `ref` / `web` `[M]` / `[F]` row | control (self-check) | — | band-1 top = ref ±3; `[F]` as above |

**On wave54-final** (`ua-heading-margin.geometry.wave54-final.out.txt`, exit 0):
- all 8 `[M]` native rows are WRONG:
  - e.g. `heading top y109 vs y130±2 (16px face: wave54-final y109 + the 21 px UA margin)`;
  - 011: `band 1 y137–151 vs y158–172±1 (wave54-final + the 21 px UA margin)`;
- all 6 `[F]` native rows are WRONG;
- every `[C]` row and every ref / web row is OK.

**So the self-test holds:**
- every gating key FAILs today;
- every control PASSes;
- both report keys FAIL.

**The fakes it rejects** (`ua-heading-margin.geometry.arms.out.txt`, 8 arms × 14 native rows; the probe exits 0 on every
arm):

| arm (what it models) | `[M]` | `[F]` |
|---|---|---|
| T: today | WRONG ×8 | WRONG ×6 |
| **U1**: margin, face held | **OK ×8** | WRONG ×6 (stays report until U2) |
| R: margin at the PAINTED face (10.72 / 8.71) | WRONG ×8 (`heading top y120 vs y130±2`) | WRONG |
| X2: a double margin | WRONG ×8 (`y151 vs y130±2`; 011 `y179–193 vs y158–172`) | WRONG |
| B: face with NO margin (U2 without U1) | WRONG ×6 (`heading top y113 vs ref y134 (2em face present: the UA margin is missing or wrong)`); 011 WRONG | WRONG ×6 (`band top y113 vs ref y134`) |
| **C**: margin + face + wrap | **OK ×8** | **OK ×6** |
| **D**: as predicted per native (Compose 005/006 no wrap) | **OK ×8** | OK ×4; **WRONG ×2** (005 / 006 android `1 heading line band(s) vs ref 2 (bands [(134, 173, 16, 389)])`) |
| C4: margin + face, heading 4 px LOW | WRONG ×6 (`heading top y138 vs ref y134`) | WRONG ×6 |

**The control the task names: the wave-54 report rows** (`ua-heading-margin.wave54-probe-on-arms.out.txt`). The
committed wave-54 probe was executed unchanged on the arms:
- **Under U1:** its six 005/006/014 native report rows stay WRONG, so they stay report.
- **Under D (U1+U2):** ios ×3 and 014 android become `GEOMETRY OK`, so they become PASS; 005 / 006 android stay
  `1 heading line band(s) vs ref 2`, so they stay report.
- **Its inset-011 native CONTROL rows FAIL under both arms** (`GEOMETRY WRONG (control moved: [(158, 172, 57, 184), …])`).
  This is by construction: U1 moves 011. The wave-55 expectations must **reclassify** those two wave-54 control keys,
  superseded by `[M] text-decoration-inset-011 ios|android` (gating). Otherwise they read as a control failure on L4's
  units.

**Limitation.** The `[M]` 16-px-face expectation is computed from the recorded wave54-final top. Any other lane that
moves those natives' heading by ≥ 3 px would fail it too. That is intended: the union carrier read (wave-54 §0 "Shared
tree").

## 8. Device probe (before the closing gate)

**One section: `css-text-decor`.** It holds all 8 carriers, the 16 declared-margin controls and the 011 key. All four
units position boxes or change faces on a native, so all four are probed here.
- **Run 1:** with all four units on. Read `ua-heading-margin.geometry.py <probe-run>` plus `score-gate.mjs` on the
  css-text-decor rows.
- **A/B arm:** drop U2-ios + U2-android (U1 alone), on the same section. Read against arm M (§6).

**Decisions (rule 4 and rule 2):**
- A gating `[M]` FAIL reverts U1-x and U2-x.
- A gating `[F]` FAIL reverts U2-x.
- A fall ≥ 0.002 on a directed row reverts its unit. Under U1 alone every carrier row is directed up.
- The two `[F]` android 005/006 report rows decide nothing.

The `css-break` and `CSS2` controls are must-not-move cells read at the closing gate; they need no probe run.

## 9. Risks, and what I could not determine

1. **R1 — iOS wrap of a FOLDED 2em paragraph (MED).**
   - The only device evidence is inset-009 ios: a COLLAPSED leaf `<h1>` on the same overlay path (`positionedChildren`
     :2094; the overlay proposes the `<div>`'s 358-px padding box), which wraps.
   - The folded hosts paint through the fold's paragraph label instead. If that path takes `.fixedSize(horizontal:)`
     or does not wrap, `[F]` 005/006 ios print `1 heading line band(s)` and U2-ios reverts.
   - The probe decides.
2. **R2 — Compose does not wrap (HIGH certainty).**
   - By code (`absposOverflowMeasure` :4898) and by device (inset-009 android).
   - The 005/006 android passes are DEGENERATE; see the §4 fallback and the queued shrink-to-fit item.
3. **R3 — The sup/sub decoration class doubles at the 2em face (MED-LOW, Android 005/006).**
   - Compose draws per-span underlines at each span's own baseline (§1). The allowance comes from sup-less anchors, so
     005/006 android may land under 0.9655.
   - These rows are not gated and not counted.
4. **R4 — inset-011 was a wave-54 control.**
   - Its captures move by design under U1 (directed up by replay: +0.0124 / +0.0061).
   - Its two wave-54 control keys must be reclassified (§7), and its four cells (with web) removed from wave-54-derived
     must-not-move lists (§5).
5. **R5 — The basis assumes the face, by design.**
   - Under U1 alone a 21.44-px margin sits above a 16-px heading. That is the UA sheet's computed value (HTML §15.3,
     css-values-4 §6.1.1), and it is up on all 8 cells.
   - The cost: if U2 is reverted the margin is still "for" the 2em face.
6. **R6 — inset-014 has no monospace anchor (MED-LOW, iOS).**
   - 26 px × `16ch` must re-resolve against the UA face. Compose's step precedes `DynamicValueResolver` (the
     `UAElementFontRuleSeamWiringTest` fact); iOS `StyleBuilder` reads the merged FontSize. Neither is executed on
     device.
   - The iOS monospace face's advance against the ref's DejaVu Sans Mono can move the 16ch wrap.
7. **R7 — iOS may place 21.44 / 17.42 pt fractionally (AA rows).**
   - `[M]` tolerates ±2 px. The replay is up at both whole-px neighbours.
8. **R8 — Shared seam regions.**
   - Seam 1 / seam 3 sit at `ComponentRenderer.kt` :1141–1165, after `AllReset` and before `DynamicValueResolver`. L3's
     declared Compose seam is elsewhere (PositionedParentFlowSlot / CanvasRootHoist).
   - The landing order and both revert orders must be dry-run in an export tree by the lane. No lane edits a seam file.
9. **R9 — The non-heading out-of-flow UA margin is a queued item, not done.**
   - `<p>`, `ul`, `ol`, `blockquote`, `pre`, `figure` have the same rule (HTML §15.3.3).
   - Census: 2 nested abspos `<p>` documents.
     - s-11-1-1b-005: the Android capture sets the text at y20–34 where the ref has y36–50, exactly 16 px high. iOS
       paints it clipped inside the 20-px table cell (y5–16).
     - anchor-center-safe-rtl: unscored, and L3-adjacent BACKLOG (i)'s control.
   - It needs its own probe: horizontal UA insets (blockquote/figure 40 px) are not modelled by either table.
10. **R10 — Observed while tracing, not this lane's, not censused: the B4 bail drops in-flow siblings' UA margins.**
    - Both planners drop the UA child margins of every IN-FLOW sibling of an out-of-flow box (Compose :534, iOS
      MarginCollapsePlanner :82–89: a whole-container bail).
    - A browser skips only the out-of-flow box.

**Could not determine** (nothing here is labelled picture-correct):
- Every device picture and score above. They are replay estimates: Chromium glyphs or translated native pixels, never a
  capture.
- That a correct-looking seam call produces the right paragraph on device. The seam is pinned only at SOURCE level, by
  the patch-borne wiring tests, the wave-54 skeptic finding-3 precedent.
- That the held Kotlin / Swift gate files compile unchanged against HEAD. Their dependencies exist (§2 D), but nothing
  was built.
- Whether `UAHeadingFaceReplay.seam` (`UAElementFontRuleTest.kt` :210) needs more than the `standsDown` retarget.
- Blink's 13/16 scaling under an inherited px size (L5 skeptic NIT 7). The basis takes the inherited px; there are 0
  carriers.

**Recommendation: GO, four units, effort S–M.**
- U1-android and U1-ios are GO: spec-exact, pinnable on verbatim wire, and up on all 8 cells by replay.
- U2-ios is GO behind U1-ios: 3 MED / MED-LOW faithful iOS flips.
- U2-android is GO behind U1-android, with the two 005/006 android rows pre-registered as `stayDegenerateEvenIfPass`,
  or held (the §4 GO-SMALL fallback) if the orchestrator prefers no degenerate passes.
- Queue alongside: the Compose relative-parent abspos shrink-to-fit (carrier: inset-009 android P 0.9638 DEGENERATE
  today), and R9.

## Seam registry (patches, never direct edits; the lane delivers `tools/titan/results/wave55-ua-heading-margin/seam-<n>.patch`)

**seam 1:** `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` (sha256
2445d9ea2674576fc0946e3f630f48b88d48f0f9d8c98df3249ba67d7a026aa5 at 0d0d3a3d), `RenderComponent` cascade step :1157–1165.
U1-android. The hunk:
- after `val rawProperties = …UAElementFontRule.apply(…)`, inserts
  `val uaMarginProperties = com.styleconverter.runtime.spacing.UaPositionedHeadingMargin.apply(component._tag,
  allReset.own, listUaProperties, rawProperties, enabled = LocalWptCaptureMode.current)`;
- changes `val unresolvedProperties = rawProperties` to `= uaMarginProperties`;
- patch-borne: the `UAElementFontRuleSeamWiringTest.kt` handoff token, and the NEW
  `UaPositionedHeadingMarginSeamWiringTest.kt`.

**seam 2:** `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` (sha256
417853cefdc7627fadc21003efa735d99d47ed0c545b6c1747d7292403fd26f3), `mergedProperties(now:)` :340–356. U1-ios. The hunk:
- hoists the `ListStyleUaRule.apply(…InheritedText.merge(…))` result into `let mergedPreUA` (the held seam-2's hoist,
  verbatim);
- binds the font step to `let fontProperties = UAElementFontRule.apply(…, merged: mergedPreUA, hasElementChildren:
  <unchanged>)`;
- returns `UAPositionedHeadingMargin.apply(sourceTag:own:preUA: mergedPreUA, merged: fontProperties, enabled:
  wptCaptureMode)` (`@Environment` :138);
- patch-borne: the NEW `UAPositionedHeadingMarginSeamWiringTests.swift`.

**seam 3:** `ComponentRenderer.kt` :1161, the `standsDown =` argument → `com.styleconverter.runtime.typography
.UAHeadingFoldGate.standsDown(component, listUaProperties)`. U2-android. Patch-borne: the
`UAElementFontRuleSeamWiringTest.kt` `fontStep` token. It replaces `held/seam-1.foldgate.patch`, which does not apply at
HEAD.

**seam 4:** `ComponentRenderer.swift` :352–356, the `hasElementChildren:` argument →
`UAHeadingFoldGate.standsDown(component: component, inFlowChildren: inFlowChildren, mergedPreUA: mergedPreUA)` (with its
comment). U2-ios. It is regenerated from `held/seam-2.foldgate.patch`, which applies clean at HEAD, on top of seam 2.
`revertOrder`: U2-ios before U1-ios.

No hunk in `apps/web-harness/src/sdui/ComponentRenderer.tsx` or `tools/titan/extract-fixture.mjs`.

## Pins (each with the mutation that must turn it red; verbatim wire from wave54-final per-test IR, spliced as constants)

**U1, both natives.**
- inset-005 / -006 / -011 `<h1>` with capture on → appends `MarginTop` / `MarginBottom {px 21.44}`.
  - Mutation: compute F from the post-font list's FontSize. That is the painted basis: 10.72, red, because the face is
    held.
- inset-014 → 17.42.
  - Mutation: em base 16 (21.44, red).
  - Mutation: the 16-px-root table (21, red).
- inset-001 (declares both edges) → the SAME instance.
  - Mutation: drop the per-edge author guard.
- Synthetic `<h1>` declaring only `MarginTop` → appends `MarginBottom` only.
- Capture off → the SAME instance.
  - Mutation: ignore `enabled`.
- block-in-inline-015-print's in-flow leaf `<h1>` → the SAME instance.
  - Mutation: drop the out-of-flow test.
- A RELATIVE or STICKY `<h1>` → the SAME instance.
- s-11-1-1b-005's abspos `<p>` → the SAME instance, logged once.
  - Mutation: widen to the full UA table.
- A vertical-writing-mode `<h1>` → the SAME instance, logged once.
- The fold verdict on 005 / 006 / 014 (`Folded`) and 011 (`Bailed`) is unchanged after the margin step.
- Seam wiring (patch-borne): the step exists once, after the font step and before the resolver's input, with
  `enabled = LocalWptCaptureMode.current` / `wptCaptureMode`.
  - Mutations: the HEAD renderer; `enabled: true`; the step fed `listUaProperties` as `merged`.

**U2.** The held pins, re-executed:
- 005 / 006 / 014 → applies;
- 011 → stands down;
- leaf → applies;
- held mutations M3 / M3s (the old has-children gate → 005 red) and M5 / M5s (fold-verdict invariance);
- the retargeted `UAElementFontRuleTest` GO-SMALL pin goes red under seam 3 / 4's absence.

**Suites** (after the gate releases the host; single-writer full sweep by the orchestrator):
- `--tests '*UaPositionedHeadingMargin*' --tests '*UAElementFontRule*' --tests '*UAHeadingFoldGate*' --tests '*InlineRunFoldTest'`;
- Catalyst `-only-testing:StyleConverterRuntimeTests/UAPositionedHeadingMarginTests`, `UAElementFontRuleTests` and
  `UAHeadingFoldGateTests`.

**Docs owed by the docs pass:**
- BACKLOG 0(h) rewritten: its "Not traced" clause is now traced (§2 B);
- the two new queue items (§4, R9);
- the test-count restamp.

No new `*Applier`, so `coverage-audit` is unchanged.

STATUS: COMPLETE
