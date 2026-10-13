# hyphen-ios-linebox — wave-55 family brief (BACKLOG "Next-wave obligations" 0(f))

Lane **L1-hyphen-ios-linebox** (L1 · SwiftUI typography line boxes). Tree `…/trusting-bohr-bd6fbf`, branch
`campaign/applier-campaign`, HEAD `0d0d3a3d` (the wave-54 merge). Contract: `tools/titan/results/wave54-plan/PLAN.md`
§0 (rules), §2 (how a lane is specified); model: `tools/titan/results/wave54-plan/hyphenate-character-compose.md`.

**Evidence.**
- Runs: `tools/titan/runs/wave54-final` (the closing gate; its iOS bytes are the shipped tree's) and
  `tools/titan/runs/wave54-ab-drop-U3b` (arm tree 286f4740 = the gate tree minus U3b, css-text only;
  `tools/titan/results/wave54-gate/_note.md:201`).
- Cells: `node tools/titan/results/wave52-gate/cells.mjs` (record `hyphen-ios-linebox.cells.out.txt`).
- Composed scores: the gate's own `tools/titan/inject-wpt-block.mjs` `diffWebVsRef`. Every capture score below is
  reproduced exactly by `hyphen-ios-linebox.replay-score.mjs` / `.radius-score.mjs`.
- Frozen refs: `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`.
- Every PNG named below was opened (the raw captures and the look sheets).

**Nothing was built, run or captured.** The opening gate `wave55-open` is capturing on this host
(`tools/titan/runs/wave55-open/gate-driver/driver.log`: CSS2 done 10:43:59, css-anchor-position started). The tree is
byte-identical to 0d0d3a3d outside `tools/titan/results/wave55-plan/`; every script here only reads runs, refs, font
files and per-test IR (`nice`d, small).

**Files written beside this brief** (all `hyphen-ios-linebox.*`):
- `.linetops.py` → `.linetops.out.txt`: whole-row ink tops per line (4 tests × 3 platforms × 2 runs);
- `.pitch.py` → `.pitch.out.txt`: sub-pixel line centroids, group-first offset and in-group slope;
- `.facemetrics.py` → `.facemetrics.out.txt`: hhea of the three faces from the font FILES (fontTools), raw vs
  Blink-rounded line box;
- `.census.mjs` → `.census.out.txt` + `.census.json`: the unit's radius over all 1435 per-test IR documents, and the
  carrier stems;
- `.replay.py` → `.replay.out.txt` (pictures in `hyphen-ios-linebox-replay/`), scored by `.replay-score.mjs` →
  `.replay-score.out.txt`;
- `.radius-replay.py` → `.radius-replay.out.txt`, scored by `.radius-score.mjs` → `.radius-score.out.txt`;
- `.geometry.py` → `.geometry.runs.out.txt` / `.geometry.replays.out.txt` / `.geometry.fakes.out.txt`;
- `.look.py` → `.look-001…004.png` (ref | iOS final | iOS drop-U3b | iOS replay Rm | web final, 2×, a red guide at
  every ref line top) and `.look.out.txt`;
- `.cells.out.txt`.

**Summary.**
- **U3b is not the iOS defect; it removed a compensation.** iOS consumes the `<br>` verbatim, as a stacked 0 × H box.
  It does not double-count, and no TextKit line is laid for it.
- **The iOS TEXT RUNS are wrong.** A run whose line-height is the declared keyword `normal` lays out on the face's
  RAW metrics: 18.625 px per line for DejaVu Sans Mono 16 px ((1901 + 483) / 2048 × 16). The box is unpinned, and
  SwiftUI ceils the N-line box: 94 / 75 / 56 px for 5 / 4 / 3 lines. The host gets `normal` from `font: 16px monospace`
  (CSS 2.1 §15.8).
- **Chromium and Compose round.** Chromium (the frozen refs, and web) and Compose lay `normal` on the ROUNDED metrics,
  round(14.85) + round(3.77) + round(0) = 19 px, so their boxes are 95 / 76 / 57.
- **So every iOS run is one row short**, and lines 4–5 of a group sit one row high.
- **The 20-px `<br>` used to cancel that.** It is one row taller than the ref's 19-px blank line, so the two errors
  cancelled at every group start: in the drop arm the group firsts sit at 0 / 0 / 0 / 0. U3b's 19.2 px takes 0.8 px of
  that cancellation away per group, which gives −1 / −2 / −2 rows at wave54-final.
- **The fix is one SwiftUI unit, NB-ios.** Under WPT capture, a run with declared `normal` AND an exact line count is
  pinned to Blink's rounded line box. The wave-30 pin machinery already exists; it gets a new value from a NEW file and
  a one-call seam.
- **Replay on the device pictures.** The method was checked first: re-placing the drop-arm lines at wave54-final's
  offsets reproduces final's three scores to 4 decimals. The replay gives -001 / -002 / -003 / -004 ios = **0.978 /
  0.9691 / 0.9759 / 0.975**, against the arm's 0.9778 / 0.9692 / 0.9758 / 0.9742; -004 flips back to P.
- **Web and Android are byte-identical by construction.** No web, Compose, converter or extractor byte changes.

## 1. Targets and the pictures

**Cells** (re-derived; `hyphen-ios-linebox.cells.out.txt`; the arm row is the A/B record
`tools/titan/results/wave54-gate/final/ab-drop-U3b.txt`):

| cell (css-text/hyphens/…) | wave54-open | wave54-ab-drop-U3b (arm) | **wave54-final** | obligation 0(f) |
|---|---|---|---|---|
| hyphenate-character-001 ios | f 0.9287 | P 0.9778 | **P 0.9611** | back to ≥ 0.9778 |
| hyphenate-character-002 ios | f 0.9261 | P 0.9692 | **P 0.9534** (DEGENERATE: TextKit emergency breaks) | back to ≥ 0.9692 |
| hyphenate-character-003 ios | f 0.9337 | P 0.9758 | **P 0.957** | back to ≥ 0.9758 |
| hyphenate-character-004 ios | f 0.9134 | P 0.9742 | **f 0.9477** | back to ≥ 0.9742 (P) |

Cell form: `wave54-final css-text/hyphens/hyphenate-character-004 ios f 0.9477`, and so on.

**Keep (U3b's lifts, wave54-final):**
- `-004 android P 0.9774` (arm f 0.9456);
- `-001 / -003 / -004 web P 0.985 / 0.9825 / 0.9832` (arm 0.9665 / 0.9617 / 0.9534);
- `-001 / -003 android P 0.9802 / 0.9792` (arm 0.9594 / 0.956).

Platform totals at wave54-final: web 1243/1372 · iOS 1131/1362 · Android 1128/1362 (`score-gate.mjs` `loadRun`).

**What the pictures show.**
- **The ref.** -001 (`…/css-text/hyphens__hyphenate-character-001.png`) has four groups of [5, 4, 3, 3] lines. Group
  first ink tops are y111 / 225 / 320 / 396, and the in-group pitch is 19.
  - Group-to-group the ref advances **114 / 95 / 76 = 6 / 5 / 4 × 19 px**. Its text line box AND its blank line are
    19 px, not the converter's 19.2.
  - -003 group tops: y91 / 205 / 300 / 376. -004 ([4, 4, 4, 3]): y91 / 186 / 281 / 376, each step 95 = 5 × 19
    (`.linetops.out.txt`).
- **iOS at wave54-final (U3b on).** The strings and breaks are the ref's: `""` / `•` / `/-/`. The wave-54 second
  reader called the -004 picture faithful (`tools/titan/results/wave54-gate/final/cell-review.second-reader.json`).
  Every group from the second one on sits one to two rows HIGH. Per-line offsets against the ref:
  - 001 (and 003): [[0, 0, 0, −1, −1], [−1, −1, −1, −2], [−2, −2, −2], [−2, −2, −2]];
  - 004: [[0, 0, 0, −1], [−1, −1, −1, −2], [−2, −2, −2, −3], [−2, −2, −2]].

  In `.look-001.png` column 2, "hy" sits two rows above its red guide.
- **iOS in the drop-U3b arm.** Every group's first line is exactly on the ref (0 / 0 / 0 / 0). But inside every group of
  four or more lines, the 4th and 5th lines sit one row high ([0, 0, 0, −1, −1], [0, 0, 0, −1]). The in-group spread is
  [1, 1, 0, 0] in BOTH arms: this is the iOS run's own pitch, and U3b did not create it.
- **Sub-pixel** (`.pitch.out.txt`).
  - Every iOS line sits within ±0.07 px of a whole-row offset.
  - The iOS in-group slope is **−0.31 px/line** in the 4–5-line groups. Web and Android are 0.000 ± 0.05: their runs
    advance the ref's 19 px.
  - Web lands at [0, +1, +2, +3] in the arm and [0, 0, 0, +1] at final. Android does the same in the arm and lands at
    [0, 0, 0, 0] at final, with a constant +0.1–0.2 px sub-pixel offset.
- **-002 iOS** paints `tati/on` and `atio/n` (groups [5, 4, 3, 4]: CF-dictionary breaks, DEGENERATE,
  `stayDegenerateEvenIfPass`). Its glyph-identical lines show the same −1 / −2 / −2 group drift (`.look-002.png`).
- **Residuals that are not this lane's:** the iOS `/-/` hyphen is shorter than Menlo's (a DejaVu outline difference,
  `.look-004.png`), and the lost `<b>` in the preamble (the corpus `inline-run-merged` convention).

## 2. Mechanism (traced at 0d0d3a3d; spec by section)

**The question asked, answered: neither.**
- **(a) SwiftUI does NOT apply the host's line box to the `<br>`.** The host has only text and `<br>` members, so it
  bails the inline fold. `InlineRunFlow.swift:451-455` (`br-stacked-equivalent`: "the stacked fallback already renders
  the forced-break line structure") sends each `<br>` to a stacked box of its wire Height.
  - U3b changed only those Heights: 12 brs, 20 → 19.2 px (d64d4c6e; `tools/titan/results/wave54-hyphenate-character/u3-differential.out.txt:44-47`).
  - The iOS group firsts moved by exactly −0.8 px per preceding blank `<br>`, which renders as −1 / −2 / −2 rows, and the
    in-run pattern did not change. A double count would have moved them 20 / 40 px.
- **(b) TextKit lays no `<br>` line on this path.** The defect is the TEXT RUN's line box.

**Spec.**
- CSS 2.1 §10.8.1: `line-height: normal` "tells user agents to set the used value to a 'reasonable' value based on the
  font of the element". The value is UA-defined, so the frozen refs encode Chromium's choice.
- Also CSS 2.1 §10.8.1: every line box starts with a strut of the element's font and line height, so a line-start
  `<br>`'s blank line is ONE `normal` line box.
- CSS 2.1 §10.6.3: an auto-height block is as tall as its line boxes.
- CSS 2.1 §15.8: `font: 16px monospace` resets `line-height` to `normal`. The wire carries it as
  `LineHeight {"multiplier":1.2,"original":"normal"}` (`…/per-test-ir/wpt__css-text__hyphens__hyphenate-character-001.json`,
  host `…-001__1-177`).

**What Chromium computes, and the evidence for it.**
- Blink's `SimpleFontData::PlatformInit` sets line spacing = lroundf(ascent) + lroundf(descent) + lroundf(lineGap),
  after `FontMetrics::AscentDescentWithHacks` rounds ascent and descent
  (`third_party/blink/renderer/platform/fonts/simple_font_data.cc`). That source is cited from memory: there is no
  Chromium checkout on this host. The rule rests on these measurements:
  - **Menlo == DejaVu Sans Mono at 16 px** (hhea 1901 / −483 / 0 per 2048; `tools/titan/mono-pin.mjs:19,32`):
    14.8516 + 3.7734 + 0 → 15 + 4 + 0 = **19**. The ref's groups advance 114 / 95 / 76 = 6 / 5 / 4 × 19.
  - **Arial 92 px** (hhea 1854 / −434 / 67): 83.28 + 19.50 + 3.01 → 83 + 19 + 3 = **105**. The three divs of the ref
    `text-decoration-dotted-001` advance 151 = 105 + the 46-px collapsed margin (`.5em`). Rounding the SUM would give
    106, so **the ref picks round-each**.
- **Compose's FontMetricsInt rounds the same way, on three faces:**
  - DejaVu 16: Android in-group slope 0 (19 px);
  - Linux Libertine 36: the stacked `boundary-shaping-001` runs advance 41 (tops 23 / 64 / 107, the same as web);
  - Inter 92: `text-decoration-dotted-001` advances 157 = 111 + 46.
- The hhea numbers are in `.facemetrics.out.txt`.

**What iOS does today** (`Renderer/ComponentRenderer.swift`, `PlaceholderLabel` `:4737`, body `:4870`):
- `effectiveLineHeight(declared:, declaredNormal: true, …, wptCaptureMode: true)` (`:5046`) returns `.natural` → nil
  (`:669-670`; `LineHeightNormal.lineBoxSource`, `LineHeightNormal.swift:126`).
- `LineBoxMetrics.leading` therefore returns (0, 0), and `pinnedBoxHeight` returns nil (`LineBoxMetrics.swift:80, 171`:
  "`normal`/undeclared: there is no authored box").
- So the run lays out on `UIFont.lineHeight` = 18.625 px/line, and SwiftUI ceils its height (`LineBoxMetrics.swift:131`:
  "a Text's reported height is whatever the face measures, ROUNDED UP").

**The arithmetic.** The ref box is 19 · N and its blank line 19. One model fits all four tests in both arms. Each table
cell is a group's offset from the ref, cumulative, so a cell is the ref's step minus the platform's, added to the cell
above.

| group step | N | ref | iOS drop arm (br 20) | iOS final (br 19.2) | iOS + NB-ios (br 19.2) |
|---|---|---|---|---|---|
| g1 → g2 (001/003) | 5 | 95 + 19 = 114 | ceil(93.125) = 94 + 20 = 114 → 0 | 94 + 19.2 = 113.2 → −0.8 | 95 + 19.2 = 114.2 → +0.2 |
| g2 → g3 | 4 | 76 + 19 = 95 | 75 + 20 = 95 → 0 | 94.2 → −1.6 cumulative | 95.2 → +0.4 |
| g3 → g4 | 3 | 57 + 19 = 76 | 56 + 20 = 76 → 0 | 75.2 → −2.4 cumulative | 76.2 → +0.6 |
| rendered rows | | 0 / 0 / 0 / 0 | **0 / 0 / 0 / 0** ✓ | origins 113.2 → 113, 207.4 → 207, 282.6 → 283: **0 / −1 / −2 / −2** ✓ | 114 / 209 / 286: **0 / 0 / 0 / +1** (= web today) |
| in-run line k = 0…4 | | 19k | ceil(18.625k) = 0 / 19 / 38 / 56 / 75 → **0 / 0 / 0 / −1 / −1** ✓ | the same ✓ | 19k → 0 |

- For 004 (groups 4 / 4 / 4 / 3): 75 + 20 = 95 = 5 × 19, so the arm sits at 0. Final is 75 + 19.2 = 94.2, which
  renders as −1 / −2 / −2 ✓, and in-run line k = 3 sits at −1 ✓.
- "Origins rounded, then baseline ceiled" is the snapping model that fits every measured row. §9 R1 names the one model
  that would break the prediction.

**Verdict on 0(f)'s wording.** "Platform-aware" is right about WHERE (iOS only) and wrong about WHAT. No runtime should
consume the `<br>` differently; the iOS run should lay `normal` on the same rounded box the other two runtimes already
use. The wave-54 brief named the compensation in §4.E ("iOS's zero is a compensation", estimated at 0.2 px/line short).
Measured here it is 0.375 px/line, plus one ceil per run.

**The task's candidate files, rejected.** GreedyLineBreaker.swift, SpentHyphen.swift and TypographyAggregate.swift (all
three touched by wave-54 L3 U2-ios, per `tools/titan/results/wave54-hyphenate-character/_note.md` "Revert unit
U2-ios") decide the breaks and the strings, and the pictures show those are right. None of them sets a line box.

## 3. Ownership (disjoint)

**Lane-owned files:**
- NEW `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift` (≤ 200 lines;
  every line commented).
- NEW `runtimes/swiftui/Tests/StyleConverterRuntimeTests/NormalLineBoxTests.swift` (≤ 200 lines).
- `tools/titan/results/wave55-hyphen-ios-linebox/`: the lane note, `seam-1.patch`, mutation logs.

**Seam (a patch, never a direct edit):**
- seam 1: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` (sha256
  `417853cefdc7627fadc21003efa735d99d47ed0c545b6c1747d7292403fd26f3` at 0d0d3a3d, 6291 lines), `PlaceholderLabel.body`
  line-box block. The hunks:
  - (i) CUT the `leading` statement and its banner, `:5055-5069` ("Fidelity wave 3 — CSS line-box leading split" …
    `faceName: textConfig.fontFaceName)`).
  - (ii) After `:5151-5152` (`let lineCountIsExact = … && !uprightPlans`), INSERT
    `let usedLineHeight = NormalLineBox.usedLineHeight(effective: effectiveLineHeight, declaredNormal: textConfig.lineHeightIsNormal, lineCountIsExact: lineCountIsExact, wptCapture: wptCaptureMode, fontSizePx: textConfig.fontSize ?? 16, design: textConfig.fontDesign, faceName: textConfig.fontFaceName)`.
  - (iii) Re-insert the cut `leading` statement right after it, reading `lineHeightPx: usedLineHeight`.
  - (iv) `:5154` `lineHeightPx: effectiveLineHeight,` → `lineHeightPx: usedLineHeight,` (inside
    `LineBoxMetrics.pinnedBoxHeight(`).

  The move is legal because `leading` is read only at `:5295` / `:5335` / `:5385`, all after the new site. The block
  reorders, but no new logic enters the oversized file: one call site, one argument, and one statement moved.
  `subNaturalShift` (`:5175`) and `.frame(minHeight: pinnedBoxHeight ?? effectiveLineHeight …)` (`:5365`) are left
  untouched: a pinned box makes the first 0 and makes the second read the pin.
- No Compose `ComponentRenderer.kt` hunk, no web-harness `ComponentRenderer.tsx` hunk, no `tools/titan/extract-fixture.mjs`
  hunk.

**Read-only** (named so nobody edits them for this lane):
- `StyleEngine/typography/LineBoxMetrics.swift` (217 lines; NormalLineBox mirrors its face resolution and a pin guards
  against drift);
- `typography/line/LineHeightNormal.swift`, `LineHeightExtractor.swift`, `Renderer/StyleBuilder.swift`;
- the wave-54 L3 files `GreedyLineBreaker.swift`, `wrapping/SpentHyphen.swift`, `TypographyAggregate.swift`,
  `wrapping/HyphenateCharacter*.swift`;
- `InlineRunFlow.swift`, `tools/titan/extract-fixture.mjs` (U3b stays as it is), every Compose and web file.

**Overlap check** against the six other lanes:
- **L4 (Swift UA heading rules + spacing) and L5 (Swift lists marker)** may own their own Swift files. They also bring
  `ComponentRenderer.swift` seams. Wave 54 placed the UA-heading seam at `:343` (`UAElementFontRule.apply`). The
  list-marker frame calls `effectiveLineHeight` at `:4716`, which this lane does not touch. seam 1 lives only inside
  `:5055-5160`. `integrate-seams.sh` must order the hunks and re-anchor them by content, not by line.
- **L7 (test-file pins).** This lane's only test file is NEW, so there is no shared test file.
- **No lane may claim** `NormalLineBox.swift`.

## 4. Units (each one commit, separately revertible)

### NB-ios — a declared-`normal` WPT run on iOS lays out on Blink's rounded line box (one commit)

**Paths:** `NormalLineBox.swift` (new), `NormalLineBoxTests.swift` (new), and `ComponentRenderer.swift` ← `seam-1.patch`.

**What `NormalLineBox` holds** (an enum of pure statics; it creates the symbols the seam consumes, and nothing owned
depends on the seam, so the tree compiles at every moment):
- `blinkLineSpacing(ascender:descender:leading:) -> CGFloat`
  - Computes `ascender.rounded(.toNearestOrAwayFromZero) + (-descender).rounded(.toNearestOrAwayFromZero) +
    leading.rounded(.toNearestOrAwayFromZero)`.
  - This is lroundf, half away from zero. Blink's rule as measured in §2.
- `faceMetrics(fontSizePx:design:faceName:) -> (ascender, descender, leading)` resolves the RENDERED face in exactly
  `LineBoxMetrics.contentHeight`'s order (`LineBoxMetrics.swift:42`):
  1. `faceName` → `UIFont(name:size:)` (the document face; the DejaVu mono pin rides here);
  2. `.default` → `UIFont(name: "Inter")`, else Inter's hhea constants 1984 / −494 / 0 per 2048 (the test bundle);
  3. any other design → `UIFont.systemFont(ofSize:)`.
- `usedLineHeight(effective:declaredNormal:lineCountIsExact:wptCapture:fontSizePx:design:faceName:) -> CGFloat?`:
  - returns `effective` verbatim whenever it is non-nil (every declared and calibrated run: byte-identical by
    construction);
  - otherwise returns `blinkLineSpacing(faceMetrics(…))` iff `declaredNormal && wptCapture && lineCountIsExact`;
  - otherwise nil (product path, unpinned runs: unchanged).
  - When the Blink box is below the raw line height (an L < content case, e.g. Libertine 36 or Inter 92), it logs ONCE
    through `PropertyTracker.logOnce` ("no silent fallthroughs": SwiftUI's `.lineSpacing` cannot be negative, so a
    multi-line run's advance stays raw).

**What it changes.** Through seam 1, the existing wave-30 pin (CSS 2.1 §10.8: N line boxes of exactly L) receives L,
together with the leading split around the rendered face (`LineBoxMetrics.leading`). For DejaVu 16 that split is 0.375
px of `.lineSpacing` and a half-leading of 0.1875 px. In-run lines then advance 19, and the N-line box is 19 · N.

**Why gate on `lineCountIsExact`.** Without the pin, a non-nil L would add the CSS leading ON TOP of SwiftUI's ceiled
natural height: the wave-30 "box = ceil(raw) + leading" defect (`LineBoxMetrics.swift:138`). Only pinned runs get
the new box.

**Predicts:**
- the four iOS rows of §6;
- iOS +1 (-004);
- 10 more iOS pixel carriers (§5), score-flat or up;
- web 0, Android 0, wire 0.

**Unit pins** (`NormalLineBoxTests`, Catalyst). Each pin has the mutation that must turn it red. The builder executes
red → restore → green and records sha256 before / after.

| # | pin | mutation that must turn it red |
|---|---|---|
| P1 | `blinkLineSpacing(ascender: 1901/2048·16, descender: −483/2048·16, leading: 0) == 19` (the ref's 19-px pitch) | — (anchor) |
| P2 | Arial 92 `(1854, −434, 67)/2048·92 → 105` (the ref's dotted-001 box) and DejaVu 20 `(1901, −483, 0)/2048·20 → 24` | M1 round the SUM (106 / 23); M2 ceil the sum (106 / 24 → red on Arial) |
| P3 | `(14.5, −4.5, 0) → 20` (lroundf: 15 + 5) | M3 `.toNearestOrEven` (14 + 4 = 18) |
| P4 | `usedLineHeight(effective: nil, declaredNormal: true, lineCountIsExact: true, wptCapture: true, …)` is non-nil; each of the three flags false → nil (three cases) | M4 drop the exactness guard; M5 drop the WPT guard |
| P5 | `usedLineHeight(effective: 20, …all flags true…) == 20`, and the same with each flag flipped (identity for every declared / calibrated run) | M6 let the Blink box override a non-nil `effective` |
| P6 | drift guard: for (16, `.default`, nil), (16, `.monospaced`, nil) and (16, `.serif`, nil), `ascender − descender + leading` of `faceMetrics` == `LineBoxMetrics.contentHeight(…)` | M7 resolve every design to Inter in `faceMetrics` (red on `.monospaced` / `.serif`) |
| P7 | verbatim wire: `StyleBuilder.build(from: <the -001 host's properties, verbatim from tools/titan/runs/wave54-final/sections/css-text/per-test-ir/wpt__css-text__hyphens__hyphenate-character-001.json>).text` has `lineHeightIsNormal == true`, `fontDesign == .monospaced`, and `ComponentRenderer.effectiveLineHeight(declared:…, declaredNormal: true, fontSizePx: 16, wptCaptureMode: true) == nil`: the exact inputs seam 1 hands `usedLineHeight` | — (input pin; held green) |

**Plan-skeptic round 1 amendment (generator-truth M1; PLAN §9 D22) — binding on the builder.** As written above,
NB-ios would MOVE `css-text/hyphens/hyphens-vertical-001.html ios`, a stage-1 byte-identity control. Its box `__2-320`
(`'hyphen\xadation'`, `WritingMode VERTICAL_RL`, `Hyphens MANUAL`, Width 3em, `LineHeight {original: normal}`) has no
whitespace, and `SoftHyphenPolicy.admitsPreBreak` declines a vertical space-less run (`SoftHyphenPolicy.swift:159-167`),
so `singleLineText` is true (`ComponentRenderer.swift:5082`), `lineCountIsExact` is true (`:5151`) and
`renderedLineCount` is 1 (`LineBoxMetrics.swift:114`), while TextKit draws THREE lines (wave54-final iOS: hyph / ena- /
tion). Today `pinnedBoxHeight` is nil only because a declared `normal` has `lineHeightPx == nil` (`:179`); with the
Blink box it would pin box 1 to 1 × 19 px. Therefore:
- `usedLineHeight` gains a `horizontalWritingMode: Bool` parameter and returns nil when it is false (CSS Writing Modes 4
  §7: in a vertical mode the line box is the inline-axis column — the horizontal line-count proxy does not describe it).
  Seam 1 hunk (ii) passes PlaceholderLabel's own stored `horizontalWritingMode` (`:4868`, set at `:3220` from
  `WritingModeExtractor.extract(from: resolvedProperties)?.isVertical != true`).
- **P8** (verbatim wire): `usedLineHeight(…)` on hyphens-vertical-001 `__2-320`'s properties, verbatim from
  `tools/titan/runs/wave54-final/sections/css-text/per-test-ir/wpt__css-text__hyphens__hyphens-vertical-001.json`
  (`declaredNormal: true, lineCountIsExact: true, wptCapture: true, horizontalWritingMode: false`) `== nil`; the same
  call with `horizontalWritingMode: true` is non-nil. Mutation **M8**: drop the guard → red.
- The Catalyst pin LOGS `lineCountIsExact` / `renderedLineCount` for the five SAME controls (all-prop-001's `'321'` in a
  0.5em box is the other space-less run): the census asserts them, it does not derive them. The note records the log.
- The census line for the cell now reads "vertical writing mode: declined by the NB-ios horizontal guard"
  (`hyphen-ios-linebox.census.mjs` re-run; `.census.json` byte-identical: the cell stays a SAME control and a stage-1
  byte-identity row).

Notes on the pins:
- **Catalyst cannot see the defect.** On Mac Catalyst `UIFont` metrics are already integral (`LineBoxMetrics.swift:151`),
  so P1–P5 are arithmetic on explicit metrics, as wave 30's `LineBoxPinTests` are.
- **Existing suites that must stay green, untouched:** `LineHeightNormalTests`, `LineBoxPinTests`,
  `WPTCaptureModeTests`, `LineHeightWireTests`, `LhUnitLineHeightTests`, `DecorationInsetLineBoxTests`. They prove the
  static `effectiveLineHeight` / `lineBoxSource` / `pinnedBoxHeight` are unchanged.
- **The seam hunk has no failing unit test** (it is a render path). The device probe (§8) and the geometry probe (§7)
  are its check, as wave-54 L3's seams were.

**The lane may run:**
- `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'` with
  `-only-testing:StyleConverterRuntimeTests/NormalLineBoxTests`, `…/LineHeightNormalTests`, `…/LineBoxPinTests`,
  `…/WPTCaptureModeTests` and `…/LineHeightWireTests`, with seam 1 applied under the lock and then restored
  byte-exact;
- the scripts beside this brief.

The lane runs no simulator, no gate and no full suite.

**revertOrder.**
- NB-ios is ADDITIVE on U3b (wave 54, on the tree). After NB-ios lands, U3b becomes load-bearing on iOS too. A later U3b
  revert would put the four iOS rows on replay arm Rd: 0.9601 / — / 0.9559 / 0.9494, and -004 would go P → f. So: revert
  NB-ios before U3b, never U3b alone.
- NB-ios reverts alone cleanly: it is one commit, the new files plus one patch.

### NB-ios-b — CONTINGENCY, pre-registered, landed only if the probe says so

- **Trigger.** The probe's three gating rows print `LINEBOX WRONG` with the reason `anchor: group 1 line 1 offset 1`
  and nothing else. That is the §9 R1 shape: SwiftUI did not round the 0.1875-px half-leading pad away, and the 15.04-px
  baseline was ceiled to 16.
- **seam 1b** (same file): `:5335` `.padding(.vertical, leading.halfLeading)`. For a pinned declared-`normal` run only,
  this becomes `.padding(.top, 0).padding(.bottom, 2 × leading.halfLeading)`, decided by a `NormalLineBox.verticalPads`
  helper that returns (top, bottom) = (half, half) for every other run.
- **Effect.** The baseline then stays at the face's raw ascent (14.85 → row 15 under either snapping rule), and the box
  is still N · L.
- **Replay.** Rm (§6) under both snapping models.
- **Commit.** Its own, after NB-ios. A revert of NB-ios-b restores NB-ios's symmetric pad.

### Not staffed, queued: U3b-exact

U3b's `normal → 1.2 × font-size` (`extract-fixture.mjs` `brHostLineBoxPx`, d64d4c6e) is the converter's
approximation. Blink's blank line here is 19, not 19.2.
- **The change.** Map `normal` to Blink's rounded box for the corpus's two pinned ref faces: monospace → Menlo/DejaVu
  1901 / 483, and the Inter pin → 1984 / 494. The wire would carry 19 instead of 19.2. It is one IR value for all three
  runtimes: 12 brs in hyphenate-character-001…004.
- **What it would buy.** It removes the +1 at group 4 that the 0.6 px accumulation leaves on web (today) and on iOS
  (after NB-ios). iOS would go to replay R0 (0.9803 / — / 0.9785 / 0.9782), and web up by about the same.
- **Why it is not staffed:**
  - it is an `extract-fixture.mjs` seam, outside this lane's named ownership;
  - it changes wire bytes, so it needs its own wire differential;
  - Android's byte identity under it is unproven (its 19.2-px boxes already land on 19-px rows; `.pitch.out.txt`).
- **For the BACKLOG.**

## 5. Carrier census (`hyphen-ios-linebox.census.mjs wave54-final` → `.census.out.txt`, `.census.json`)

**The rule the unit fires on:** an iOS `PlaceholderLabel` run whose effective `LineHeight` (own, else inherited:
`InheritedText.inheritedTypes` carries `"LineHeight"`, `Renderer/InheritedText.swift:47`) is `{original: "normal"}`,
under WPT capture, with an exact line count.

**Radius:**
- 60 text-bearing components in **19 documents** of 1435 (40 declare it themselves, 20 inherit it);
- 25 `<br>` boxes in 5 documents (heights 0 or 19.2, untouched).

Per face × size, with the line box raw → Blink (`.facemetrics.out.txt`):

| iOS face @ size | documents | raw (today, per line) | Blink | 1-line box today → after |
|---|---|---|---|---|
| DejaVu 16 | 6 | 18.625 | 19 | 19 → 19 (multi-line: ceil(18.625 N) → 19 N) |
| DejaVu 20 | 1 | 23.2812 | 24 | 24 → 24 |
| Inter 16 | 1 | 19.3594 | 20 | 20 → 20 |
| Inter 36 | 1 | 43.5586 | 44 | 44 → 44 |
| Inter 92 (the leading split's face for `Arial`) | 2 | 111.3164 | 111 | **112 → 111** |
| Linux Libertine 36 (boundary-shaping's `test` @font-face) | 8 | 41.04 | 41 | **42 → 41** |

**Capture carriers:**
- **iOS, 14** (the pixels are predicted to move):
  - TARGET: `wpt__css-text__hyphens__hyphenate-character-001`, `-002`, `-003`, `-004`;
  - MOVES: `wpt__css-text__boundary-shaping__boundary-shaping-001` … `-008` (three stacked one-line runs, boxes 2 and 3
    up 1 and 2 rows, which is web's and Android's stacking) and `wpt__css-text-decor__text-decoration-dotted-001` /
    `-002` (divs 2 and 3 up 1 and 2 rows).
- **web 0 · Android 0 · wire 0:** no converter, extractor, web or Compose byte changes.

**Must not move inside the radius** (iOS, byte-identical by the model: the one-line box is unchanged, and the
half-leading pad of 0.19–0.36 px rounds away):
- `wpt__css-cascade__all-prop-001` P 0.9641;
- `wpt__css-counter-styles__counter-style-at-rule__disclosure-styles` f 0.6127;
- `wpt__css-tables__anonymous-table-ws-001` P 0.9988;
- `wpt__css-text__boundary-shaping__boundary-shaping-010` P 0.965;
- `wpt__css-text__hyphens__hyphens-vertical-001` f 0.8801.

These five are the R1 tripwire: if any of them moves a row, the snapping model is wrong.

**Must not move, everything else:** every web and Android cell (all 2734), and every iOS cell outside the 14 carriers.
The unit fires on nothing else, and the fixture net does not run under WPT capture, so its 9 gate fixtures and the
committed baselines are byte-identical.

**Stability.** Every radius cell has been byte-stable over wave53-final → wave54-open → wave54-probe → wave54-final
(`.cells.out.txt`), so any iOS mover in the radius is the unit's.

## 6. Predictions (from `wave54-final`; the plan re-asserts every "from" against `wave55-open`, which should equal it)

**Replay basis** (`.replay-score.out.txt`, gate metric, the iOS capture's own glyphs re-placed by whole rows):
- **X** is the method check: drop-arm lines at final's offsets reproduce final's scores 0.9611 / 0.957 / 0.9477
  exactly.
- **Rm** is the unit's model, pixel-identical to Rw on 001 / 003 / 004.
- **R0** is every line exactly on the ref.
- **R1** is the §9 risk arm (every line one row low).
- **Rd** is the unit with U3b reverted.

| cell | from | → predicted (replay) | tier | floor · geometry | direction |
|---|---|---|---|---|---|
| css-text/hyphens/hyphenate-character-001 ios | P 0.9611 | **P ≈ 0.978** (Rm 0.978; arm 0.9778; ceiling R0 0.9803; risk R1 0.9628) | MED | **0.95** · gating `hyphen-ios-linebox 001 ios` | up |
| css-text/hyphens/hyphenate-character-003 ios | P 0.957 | **P ≈ 0.976** (Rm 0.9759; arm 0.9758; R0 0.9785; R1 0.9592) | MED | **0.95** · gating `… 003 ios` | up |
| css-text/hyphens/hyphenate-character-004 ios | f 0.9477 | **P ≈ 0.975** (Rm 0.975; arm 0.9742; R0 0.9782; R1 0.9556) | MED | **0.95** · gating `… 004 ios` | up (flip) |
| css-text/hyphens/hyphenate-character-002 ios | P 0.9534 | P ≈ 0.969 (Rm 0.9691; arm 0.9692, equal within replay precision; Rm1 0.9558); **stays DEGENERATE** (emergency breaks) | MED | report `… 002 ios` | up |
| css-text/boundary-shaping/boundary-shaping-001…008 ios | P 0.9792 / 0.9695 / 0.9781 / 0.9774 / 0.9774 / 0.9714 / 0.9764 / 0.9789 | P, the same to ±0.0001 (radius replay U: 0.9792 / 0.9694 / 0.9781 / 0.9774 / 0.9774 / 0.9714 / 0.9764 / 0.9789) | MED-HIGH | report `… stack boundary-shaping__boundary-shaping-00N ios` | flat (undirected, within 0.002) |
| css-text-decor/text-decoration-dotted-001 / -002 ios | f 0.5318 / f 0.5426 | f ≈ 0.5355 / 0.5472 (U), stays f | LOW-MED | report `… stack text-decoration-dotted-00N ios` | up |
| the five in-radius controls (§5) | as listed | byte-identical | MED | control: R1 tripwire | — |
| -004 android P 0.9774, -001 / -003 android P 0.9802 / 0.9792, -001 / -003 / -004 web P 0.985 / 0.9825 / 0.9832, and every other web / Android cell | as listed | byte-identical | HIGH | control: `hyphen-ios-linebox 00{1,3,4} web\|android → LINEBOX OK` | — |

**Floors.** The gating rows floor at the pass threshold 0.95, as the wave-54 lesson requires: a geometry-gated row never
floors above the threshold. The obligation's own bar, ≥ the arm values, is read as the DIRECTION and magnitude of the
up rows (rule 2). It is reported, never a trigger: Rm meets it to within the replay's ±0.0008.

**Expected movement against `wave55-open`** (= wave54-final):
- iOS +1 (1131 → 1132), web 0, Android 0;
- lost 0; newly measured 0; unmeasured-now 0;
- picture-correct with no change of verdict: -001 / -003 ios (P → P, `LINEBOX OK`).

**DEGENERATE.** -002 ios stays on `stayDegenerateEvenIfPass` (TextKit's dictionary emergency breaks, not this unit's).
No row is labelled picture-correct from a replay; only the device capture plus its `LINEBOX OK` earns it.

**A/B arm at the closing gate** (pre-registered, `ab-arms`): drop-NB-ios on css-text and css-text-decor, iOS, read
`ab-diff <arm> <final> --threshold 0.002 --platforms ios`.
- **Expect exactly 4 cells to differ:** hyphenate-character-001 / -002 / -003 / -004 ios, Δ ≈ +0.017 / +0.016 / +0.019
  / +0.027 (the -004 row a flip).
- The boundary-shaping (within 0.0002) and dotted (+0.004 / +0.005) carriers stay within or near the threshold.
- Any other differing iOS cell is a leak.

## 7. Geometry probe — `hyphen-ios-linebox.geometry.py` (written, run on 3 runs, 7 replay arms and 6 fakes)

**Why it is new.** It extends `tools/titan/results/wave54-plan/hyphenate-character.geometry.py`, which is not edited.
That probe's ±4-px tops print GEOMETRY OK on wave54-final AND on the drop arm (both re-run here), so it cannot see 0(f).

**How the new probe measures.** It reads each line's ink CENTROID against the same ref line and rounds the difference to
whole rows. Every capture sits within ±0.07 px of an integer, so the rounding loses nothing.

**The LINEBOX rules:**
- **anchor:** group 1, line 1 offset = 0;
- **pitch:** one offset per group, i.e. in-run pitch = the ref's 19;
- **drift:** group offsets non-decreasing, each 0 or +1 (the 19.2-px brs accumulate ≤ 0.6 px);
- **lines:** per-group counts equal the ref's. On report row -002 only glyph-identical lines are compared (ink width
  ±2 px).

**The STACK rule.** On a radius document, box k's ink top moves up exactly k − 1 rows against the frozen wave54-final iOS
capture.

**Self-checks.** Every ref row must print `LINEBOX OK` (exit 1 otherwise). `--fakes` exits 1 if any fake is accepted.

**Keys** (for `expectations.json` `lanes.L1-hyphen-ios-linebox.geometryProbes`, read by the `geometry-gate.py` idiom:
the key is the leading tokens, the verdict is the exact line ending):

| key | expect (exact line ending) | class | unit / requires |
|---|---|---|---|
| `hyphen-ios-linebox 001 ios` | `→ LINEBOX OK` | **gating** | NB-ios (requires U3b on the tree) |
| `hyphen-ios-linebox 003 ios` | `→ LINEBOX OK` | **gating** | NB-ios (U3b) |
| `hyphen-ios-linebox 004 ios` | `→ LINEBOX OK` | **gating** | NB-ios (U3b) |
| `hyphen-ios-linebox 001 web`, `003 web`, `004 web`, `001 android`, `003 android`, `004 android` | `→ LINEBOX OK` | control (U3b's lifts; they PASS today and must keep passing) | — |
| `hyphen-ios-linebox 001 ref` … `004 ref` | `→ LINEBOX OK` | self-check | — |
| `hyphen-ios-linebox 002 ios` | `→ LINEBOX OK` | report (DEGENERATE row; WRONG today) | NB-ios |
| `hyphen-ios-linebox stack boundary-shaping__boundary-shaping-00N ios` (N = 1…8) | `→ STACK OK` | report (WRONG today) | NB-ios |
| `hyphen-ios-linebox stack text-decoration-dotted-001 ios`, `…-002 ios` | `→ STACK OK` | report (WRONG today; css-text-decor is read at the closing gate) | NB-ios |
| wave-54 `hyphenate-character-00{1,3,4} {web,ios,android}` (`wave54-plan/hyphenate-character.geometry.py <run>`) | `→ GEOMETRY OK` | control (glyph, lines, ±4 px) | — |

**Executed** (`.geometry.runs.out.txt`, `.replays.out.txt`, `.fakes.out.txt`):

**On runs:**
- **wave54-final:** the 3 gating rows WRONG (`pitch: group 1 line 4 offset -1 …; drift: group-first offsets [0, -1, -2,
  -2]`); the 6 controls OK (web [0, 0, 0, 1], Android [0, 0, 0, 0]); report 002 ios WRONG; the 10 stack rows WRONG
  (`displacement [0, 0, 0] … want [0, -1, -2]`).
- **wave54-ab-drop-U3b:** gating WRONG on `pitch:` alone (first lines [0, 0, 0, 0]). Web and Android WRONG on `drift:
  [0, 1, 2, 3]`: the gate would have caught the arm on every platform.
- **wave53-final:** WRONG on `lines:`.

**On replays:**
- **Rw, Rm, R0:** the gating rows OK; Rm's 002 ios OK; radius replay U: 10 / 10 `STACK OK`.
- **R1:** WRONG `anchor: group 1 line 1 offset 1`, the NB-ios-b trigger.
- **Rd:** WRONG `drift: [0, 1, 2, 3]`.
- **X:** WRONG (it is the final picture).

**The fakes it must reject, all rejected, every ref row OK, exit 0.** Each is built from the REF's own pixels, so only
the geometry differs:
- `white`: no ink, `lines:`;
- `whole+1`: `anchor:`;
- `u3b-today`: wave54-final's pattern, `pitch:` + `drift:`;
- `drop-u3b`: the arm's in-run pattern, `pitch:`;
- `no-u3b`: +1 per group, `drift:`;
- `g4+2`: `drift:`.

So the rule rejects the defect on the ref's own glyphs, and fails both today's arms, while web and Android hold.

**Reasons name the unit (rule 6):**
- `pitch:` or `drift:` → NB-ios (or U3b reverted underneath it);
- `anchor:` alone → swap in NB-ios-b;
- `lines:` → not this lane (wave-54 U2-ios / U3; the wave-54 probe's `glyph:` / `lines:` say which).

**`--self-test` contract** (on `wave54-final`, or on `wave55-open` when scored):
- gating 3 / 3 FAIL, control 6 / 6 PASS, report 11 / 11 FAIL;
- the wave-54 9 GEOMETRY OK controls PASS.

## 8. Device probe before the closing gate

**One section: `css-text`**, all three platforms (`tools/titan/gate-driver.sh`, quiet host, about 2.5 min), as the
orchestrator's window after NB-ios lands.
- It holds the 4 targets, the 8 boundary-shaping MOVES carriers and 2 in-radius controls (boundary-shaping-010,
  hyphens-vertical-001).
- Web and Android in the same section are the free byte-identity control: no cross-platform leak.

**Read with:**
- `python3 tools/titan/results/wave55-plan/hyphen-ios-linebox.geometry.py <probe-run>`;
- `python3 tools/titan/results/wave54-plan/hyphenate-character.geometry.py <probe-run>`;
- `node tools/titan/results/wave52-gate/cells.mjs 'hyphenate-character|boundary-shaping|hyphens-vertical-001' wave55-open <probe-run>`;
- `ab-diff.mjs wave55-open <probe-run> --threshold 0.002`, on the union carriers if other lanes' units are in.

**Pre-registered decision:**
- **KEEP NB-ios iff** all of these hold:
  - the 3 gating rows print `→ LINEBOX OK`;
  - -001 / -003 / -004 ios ≥ 0.95;
  - every web and Android css-text capture is byte-identical to wave55-open;
  - iOS boundary-shaping-010 and hyphens-vertical-001 are byte-identical;
  - no iOS css-text cell outside the 12 css-text carriers moves ≥ 0.002.
- **Swap to NB-ios-b** if the only reason is `anchor: … offset 1`, then re-probe css-text.
- **Any other WRONG** → revert NB-ios (one commit) and record the line.
- The css-cascade, css-counter-styles, css-tables and css-text-decor radius rows are read at the closing gate (report /
  control), not probed.

## 9. Risks, and what I could not determine

1. **R1, the snapping rule (LOW-MED).** Two snapping models fit every measured row today:
   - "view origins rounded, baseline ceiled";
   - "round baseline on ceiled line-fragment origins".

   Both predict Rm for the symmetric 0.1875-px pad. One model fits nothing measured but cannot be excluded off-device:
   no origin rounding plus a ceiled baseline. Under it every line lands one row low: R1, 0.9628 / 0.9558 / 0.9592 /
   0.9556. Those values are still P, but below the arm. The gating `anchor:` reason and the five in-radius controls
   catch it, and NB-ios-b is the pre-registered answer. Only the device probe decides (the wave-53 lesson: a replay
   cannot predict native row assignment).
2. **Thin margin over the arm values (MED).** Rm beats the arm by only +0.0002 / −0.0001 / +0.0001 / +0.0008. The +1 at
   group 4 that U3b's 19.2 leaves costs about what the in-run −1s cost today. The ceiling R0 (+0.0025) needs
   U3b-exact, which is queued and not staffed (§4). A device raster within ±0.001 of the replay is assumed. The X arm
   reproduced final exactly, but that is one sample.
3. **Rounding rule (LOW).** Round-each rests on the Arial 92 ref box (105, where round-sum gives 106) and on Compose
   agreeing on three faces. The Blink source is cited from memory: no Chromium checkout on this host.
4. **Face resolution (LOW).** `faceMetrics` mirrors `LineBoxMetrics.contentHeight` (P6 guards the drift), and it
   inherits that function's simplification: generic designs other than `.default` measure `systemFont`, not SF Mono.
   On the gate, the mono pin supplies `fontFaceName`. The measured iOS boxes (94 / 75 / 56 = ceil(18.625 N); SF Mono
   would give 95 / 76 / 57) prove the DejaVu metrics are the ones in force today.
5. **Sequencing (MED impact).** NB-ios makes U3b load-bearing on iOS: a later U3b-alone revert gives Rd, and -004 ios
   goes P → f. revertOrder records it.
6. **Census reach (LOW).** The census reads `text` and `meta.runs`. Any declared-`normal` glyph run that reaches
   `PlaceholderLabel` some other way would be an uncounted mover. The probe's iOS identity control over css-text, and
   the closing gate's full-corpus control-check, cover it. `all-prop-001`'s face label in the census (DejaVu 20) is
   doubtful: its iOS capture paints a proportional face at about 16 px (the `all: initial` reset). Either way its
   one-line box is unchanged (24 → 24, or Inter 16's 20 → 20).
7. **Seam collisions (LOW).** L4 / L5 may also patch `ComponentRenderer.swift`. seam 1's region (`:5055-5160`) is
   disjoint from wave 54's known anchors (`:343`, `:4716`, `:5011`, `:5026`), but `integrate-seams.sh` must re-anchor
   by content.
8. **Not determined here:** the exact SwiftUI/TextKit snapping implementation; whether Compose's 19.2-px `<br>` boxes
   land on 19-px rows by `roundToPx` (they behave so, `.pitch.out.txt`); the Android byte identity of U3b-exact.
9. **Docs hand-off** (closing docs pass, not this lane):
   - the SwiftUI XCTest count in `CLAUDE.md`'s suite table moves by the number of `NormalLineBoxTests` cases (2212 → +7
     expected); `tools/visual/doc-staleness-check.sh` derives it;
   - no coverage-audit count moves (NormalLineBox is not an applier);
   - BACKLOG 0(f) is rewritten from §2: "not U3b's; the iOS run's `normal` line box".

STATUS: COMPLETE
