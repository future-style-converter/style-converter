# queue-scout-text-web — wave-54 planning miner (TEXT / INLINE / WEB-TAIL families not touched in wave 53)

**Method.**
- Evidence run: `tools/titan/runs/wave53-final` (dev 7cce3b22).
- Every score is a `node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave53-open wave53-final` look-up. The two
  runs agree on every cell quoted.
- Every defect named below was looked at: ref, web, iOS and Android PNGs side by side, and measured with the scripts named
  in each row.
- Censuses scan all 1435 per-test IR documents of wave53-final through `score-gate.mjs`'s loader.
- Nothing was built, run or captured, and no file outside `tools/titan/results/wave54-plan/` was written: the
  `wave54-open` gate was running.

**Candidates read in full:** BACKLOG obligations 0(a)–(e); queue 4(b), 4(b′), 4(c), 4(d), 4(d′), 4(f), 5 (a)–(j), 0(m),
2(c¹), 11 (a)–(h).

**Disjointness was checked against the sibling briefs already in this directory:**
- `compose-table-body-cell.md`: Compose `table/**` plus `ComponentRenderer.kt` :2638-2697.
- `label-chrome-all-reset.md`: `tools/visual/label-chrome-*`, no seams.
- `web-out-of-flow-hyphen-box.*`: the web hyphenation path.
- `hyphenate-character.*`: native `typography/wrapping`.
- `queue-scout-layout.*`: the abspos containing-block family.

## 0. Ranking

| rank | lane (brief) | queue | measurable targets at wave53-final | verdict |
|---|---|---|---|---|
| **1** | **ua-heading-face** (`ua-heading-face.md`) | 0(g) + the real mechanism under 4(b′) | block-in-inline-015-print android f 0.9489; text-decoration-inset-005 ios f 0.8995 / android f 0.9; -006 f 0.8987 / f 0.8993; -014 f 0.9238 / f 0.923 | **GO** (S–M) |
| **2** | **web-root-separator** (`web-root-separator.md`) | 5 (web tail), untracked | box-sizing-007 web f 0.9036, -008 f 0.8943, -022 f 0.9442, position-absolute-semi-replaced-stretch-other web f 0.941; plus 14 DEGENERATE web passes | **GO** (S) |
| 3 | iOS space-less CJK one-line pin | 4(f) | hanging-punctuation-inline-001 ios P 0.9555 (thin) | not now (§3) |
| 4 | first-letter extraction | 0(m) | first-letter-005 f ×3 | not now (§3) |
| 5 | block-ellipsis hanging-whitespace | 4(c) | block-ellipsis-032 android f 0.9457 | not now (§3) |
| 6 | Compose block-decoration propagation | 4(b) | subelements-003 android f 0.9084 / ios f 0.9043 | not now (§3) |
| — | the rest | 4(d)/(d′), 5 named items, 2(c¹), 11 | — | §3, one line each |

## 1. #1 — ua-heading-face (full brief: `ua-heading-face.md`)

**Targets.**

| cell | wave53-final | role |
|---|---|---|
| css-break/block-in-inline-015-print | android f 0.9489 (ios P 0.9894 is the anchor) | target |
| css-text-decor/text-decoration-inset-005 | ios f 0.8995 / android f 0.9 | target |
| css-text-decor/text-decoration-inset-006 | ios f 0.8987 / android f 0.8993 | target |
| css-text-decor/text-decoration-inset-014 | ios f 0.9238 / android f 0.923 | target |
| css-text-decor/text-decoration-inset-011 | ios f 0.6999 / android f 0.6852 | control |

**Defect as seen** (`ua-heading-face.geometry.out.txt`): the natives paint the author-unsized `<h1>` at the 16 px
(13 px mono) regular body face.
- **block-in-inline-015-print android:** band heights 9–12 px, right edges x42–53. The ref has 18–25 px and x72–96. iOS,
  which has the rule, matches the ref to ±2 px.
- **inset-005 / -006 natives:** ONE 20-px band, x16–191/194. The ref has TWO lines: y134–173 to x336, and "fox" at
  y188–212.
- **inset-014 natives:** the same 2-line wrap as the ref, at half height (11–12 px bands against 26).
- **The fold is engaged on both natives.** The sup and sub are shifted inside one paragraph, and 014 wraps as one
  paragraph at 16ch. What is missing is the face.

**Mechanism** (file + symbol):
- **Android.** Compose has no twin of `UAElementFontRule.swift` (BACKLOG 0(g)). `ComponentRenderer.kt` :1141 runs
  `ListStyleUaRule.apply` as the only tag-keyed UA step, and `mergedComponent` (:1841) feeds `InlineRunFold.fold` (:3839)
  and the paragraph TextStyle at 16 sp regular.
- **iOS.** `UAElementFontRule.headingAppliesTo(hasElementChildren:)` stands down for every heading with children. It is
  called from `ComponentRenderer.swift` `mergedProperties(now:)` :343-356. That gate was measured on the STACKED
  pre-fold render in wave 40, and the fold at :3383 now paints those hosts as one paragraph through `style.text`.
- **Extractor.** It bakes `UA_H1_PROPS` only for COLLAPSED runs (`extract-fixture.mjs` `collapseInlineRun` :3166-3172,
  lossy `ua-heading-defaults` :11365). That is why inset-001…004 pass on both natives (ios 0.9634–0.9707, android
  0.984–0.9859) and 005/006/014 do not.
- **Correction to 4(b′).** The `TextDecorationColor`-divergence refusal is no longer what limits 005/006/014. The div above
  carries `Color black` (lossy `body-inherited-baked`), and both folds use the MERGED host list.

**Census** (`ua-heading-face.census.out.txt`):
- 24 documents carry h1–h6/sup/sub.
- 8 carry an unsized heading (4 leaf, 4 folded).
- 14 carry only sized headings, so the rule stands down: byte-identical, except the bold-only half on 2 filter-effects h1s
  (android f 0.5977 / f 0.4904, movers).
- 2 carry only sup/sub.
- The only PASSING native cell in the carrier set is block-in-inline-015-print iOS, on an unchanged leaf path.

**Ownership:**
- New Compose `typography/UAElementFontRule.kt` and `typography/UAHeadingFoldGate.kt`, plus tests.
- New Swift `typography/UAHeadingFoldGate.swift`, plus tests.
- Docs-only edit of `UAElementFontRule.swift`.
- **Seams:** one hunk each in Compose `ComponentRenderer.kt` :1141 and `ComponentRenderer.swift` :343-356.
- Disjoint from `typography/wrapping/**`, `lists/**`, `table/**` and the web.

**Geometry probe:** `ua-heading-face.geometry.py`. Today it prints `GEOMETRY WRONG` on the 7 target rows, OK on the
ref, web and iOS-leaf rows, and OK on the 011 control. Every row must print "→ GEOMETRY OK" after the lane.

**Predictions:**

| cell | from → to | confidence | floor |
|---|---|---|---|
| block-in-inline-015-print android | f 0.9489 → **P ≈ 0.989** | HIGH | 0.97 |
| inset-005 android | f 0.9 → P ≈ 0.98 | MED | — |
| inset-006 android | f 0.8993 → P ≈ 0.98 | MED | — |
| inset-005 ios | f 0.8995 → P ≈ 0.965 | MED | — |
| inset-006 ios | f 0.8987 → P ≈ 0.965 | MED | — |
| inset-014 ios / android | f 0.9238 / f 0.923 → P | MED-LOW | — |

- **Movers:** text-decoration-color android f 0.6164 → ≈ 0.67; counter-list-item and
  counter-reset-increment-overflow-underflow android; subelements-003 android; two filter-effects h1s.
- **Must not move:** block-in-inline iOS / web, inset-011 ×2, inset-001…004/009/015/016/024 natives, subelements-002,
  all web, the fixture net.

**Risks:**
- the abspos `<h1>`'s wrap width (005/006 need "fox" on line 2);
- `16ch` re-resolving against 26 px mono;
- stems shared with the lists/markers and layout families (plan-build assigns them);
- the gate re-runs the fold, which is sound only while admission ignores FontSize/FontWeight (§4 D of the brief).

**GO.** GO-SMALL is U1 leaf-only: +1 Android HIGH.

## 2. #2 — web-root-separator (full brief: `web-root-separator.md`)

**Targets:**
- web f: box-sizing-007 0.9036, -008 0.8943, -022 0.9442, position-absolute-semi-replaced-stretch-other 0.941. The
  natives pass 007/008 at 0.9845 / 0.9726.
- DEGENERATE web P: box-sizing-010/011/014…019 (×8, pixel-identical) 0.9713, -020/021/024/025 (×4) 0.9599, -013
  0.9538, semi-replaced-stretch-input 0.9596.

**Defect as seen** (`web-root-separator.geometry.out.txt`): the second inline-level ROOT atom starts 5 px early on web.
- x146 against the ref's x151 (007/008);
- x187 against x192 (semi-replaced);
- or the two atoms merge into one run, x16–155 / x16–275 against x16–85 + x91–160 / x16–145 + x151–280 (010/013/020/022).

The natives sit on the ref's x151 / x91.

**Mechanism:**
- The extractor stamps `meta.role: "ws-after"` (`extract-fixture.mjs` :4883 / :4905).
- The web harness replays it only BETWEEN CHILDREN: `ComponentRenderer.tsx` `HARNESS_OPTIONS.renderChildSeparator`
  :1099, invoked by `NodeRenderer.ts` :366-376.
- The composed canvas maps ROOTS flush (`ComposedCaptureGallery.tsx` `flowRoots.map` :1070 / `canvasRoots.map` :1079).
- The natives pack root atoms with `rootAtomGapPx` / `ROOT_ATOM_GAP_PX` 4.5 (`InlineBlockAtom.swift:577`,
  `InlineBlockAtom.kt:604`).

**Census** (`web-root-separator.census.out.txt`): 27 documents carry a root-level inline-level `ws-after` pair. Their web
cells are 20 P, 6 f and 1 unscored. Native cells cannot move: no native file is touched.

**Ownership:**
- New `apps/web-harness/src/ui/ComposedRootSeparator.ts` plus its vitest.
- The 2 call sites in `ComposedCaptureGallery.tsx`.
- **Seam:** one hunk in `apps/web-harness/src/sdui/ComponentRenderer.tsx`, a pure lift plus export of the WWS predicate.

**Geometry probe:** `web-root-separator.geometry.py`. Today every web row is WRONG and every ref row OK; afterwards every
web row must be OK.

**Predictions** (`web-root-separator.replay.out.txt`, scorer's own SSIM; the shipped column reproduces every gate score):

| cell | from → to | confidence | floor |
|---|---|---|---|
| box-sizing-007 | f 0.9036 → P 0.985 | HIGH | 0.975 |
| box-sizing-008 | f 0.8943 → P 0.974 | HIGH | 0.965 |
| box-sizing-022 | f 0.9442 → P 0.970 | MED-HIGH | 0.96 |
| semi-replaced-stretch-other | f 0.941 → P 0.967 | MED | — |

- **P → P:** 0.9713 → 0.982 (×8), 0.9599 → 0.982 (×4), 0.9538 → 0.970, 0.9596 → 0.966.
- **Must not fall:** block-in-inline-015-print web P 1, static-inside-inline-block P 0.9803,
  appearance-auto-input-non-widget-001 P 0.9873, attr-style-sharing-1 P 0.9939, baseline-with-orthogonal-flow-001
  P 0.9854, all natives.

**Risks:**
- an at-risk pass whose composed DOM diverges from the ref (stop rule: any web drop > 0.002 is looked at);
- the abspos static-position shift;
- the table-body canvas must decline;
- `ComposedCaptureGallery.tsx` is over size (0(d)), so it gets call sites only.

**GO.**

## 3. The rest — why not now (one line each)

- **4(f) hanging punctuation** — the iOS thin pass `hanging-punctuation-inline-001 ios P 0.9555` (android f 0.9452, web P
  0.9849).
  - The picture is NOT a hanging defect. iOS draws the orange 2-line paragraph OVER the blue line because
    `ComponentRenderer.swift` :5078 `singleLineText = !displayText.contains { $0.isWhitespace }` pins a space-less CJK
    run to ONE line box while TextKit wraps it to two.
  - Radius: `queue-scout-text-web.cjk-pin.census.out.txt`, 14 documents (iOS 4 P / 7 f / 3 unscored).
  - Not now: it is a seam pin in the wrapping neighbourhood the hyphenate-character lane will own. Queue it, or hand it to
    that lane.
  - `hanging-punctuation: last` itself has two-platform scope and the bound tests fail on web too (block-bound-001
    0.8415 / 0.7361 / 0.745; inline-bound-001 0.8467 / 0.6789 / 0.6176).
- **0(m) `::first-letter` dropped at parse time** — first-letter cells are 83 P / 19 f.
  - Of the 19 f, the exclude-*-marker ×9 and skip-marker ×3 are marker interactions (lists/markers ground), and
    insert-text-node-dynamic ×3 is dynamic.
  - Only `first-letter-005` ×3 (web f 0.9248 / ios f 0.9054 / android f 0.9224) is plainly the drop. It ALSO needs `&pound;`
    decoded: the extractor leaves it literal, and it is the only undecoded named reference in all 1435 per-test IR docs
    (scan executed).
  - F3 is an L-size extractor-seam lane for ≤ 3 flips. Not now; bundle the `&pound;` singleton into any extractor-seam
    lane.
- **4(c) block-ellipsis-032** — android f 0.9457 (ios P 0.9899, web P 0.9834).
  - Android paints the red `.hangs` band and places the ellipsis after it. The ref removes hanging white space first
    (css-overflow-4 §4), and web paints the band too.
  - Two waves of missed predictions and no mechanism traced in the hour. Not now: it needs a device probe before
    anyone predicts it again.
- **4(b) / 4(b′) residue beyond lane #1** — subelements-003 (ios f 0.9043 / android f 0.9084, web P 0.9987).
  - It needs a per-range decoration-colour draw off `TextLayoutResult` on Compose (`SpanStyle` has no decoration colour)
    plus a Compose propagation channel. That is L effort, MED-LOW.
  - `subelements-002 ios f 0.7975` is a different defect: both abspos lines overprint at one y because iOS leaves the
    `Top {v:1|3, u:EM}` insets unresolved. That is layout ground.
  - inset-007/008 natives (f 0.8732 / 0.8579, 0.8762 / 0.8637) already carry the baked face. Their residue is the
    Times New Roman → native serif face. inset-012/013 fail on all three.
  - Not this wave.
- **4(d) / 4(d′) line-clamp** — line-clamp-001…007 android are all P 0.9701–0.982. (d′)'s `<br>` arm has 0 corpus movers
  (S3's mutation). NO-GO; docs-only.
- **5 web-tail named items** — closed or out of family:
  - contain-body-* all P (web 1, ios 0.9992–0.9996, android 0.9667–0.9989);
  - contain-html-overflow-002 P ×3 (1 / 0.9996 / 0.9989);
  - contain-content-004 web P 0.9985, natives f 0.8094 / 0.8286 (abspos containing block → the layout scout).
  - The live web tail is lane #2 plus the three untracked singletons in §4.
- **2(c¹) armenian** — the native cells are unscored (`NATIVE_FONT_PARITY_REFUSED_TESTS`; only web P 0.9981 is scored).
  Not measurable → NO-GO.
- **11(a) counter-list-item** — ios f 0.7864 / android f 0.7307. Lists/markers family (and a lane-#1 android mover).
- **11(c) broken-symbols** — P ×3 (1 / 0.9976 / 0.9972). Done.
- **11(d) discard-multicol-003** — web f 0.8901 (natives P 0.9966 / 0.9551). The web paints the `column-span: all`
  "Spanner 1" row the ref discards. Untraced web singleton; queue it.
- **11(e) hyphenate-character-005** — ios f 0.9428 / android f 0.9474. The hyphenate-character family.
- **11(f) currentcolor-001/-002** — P ×3 (1 / 0.999 / 0.9982). Done.
- **11(h) T8 attr-style-sharing-1/-3** — natives f 0.9445/0.9447 and 0.9515/0.9513. Inline-tag boxing, a layout-adjacent
  M lane.
- **11(h) T9 gradient-{de,in}creasing-hue-lch** — natives f 0.9582–0.9584. Gradient interpolation, not text.
- **11(h) T10 fixup-dynamic-anonymous-*** — inline-table-001/-002 natives f 0.944–0.9456, anonymous-table-001 ios f
  0.9451. The table families.
- **11(h) T11 position-absolute-center-007** — natives f 0.9451/0.9445. The layout scout.
- **11(h) B counter-reset-reversed-list-item(-start)** — web f 0.938 ×2. Lists/markers.
- **11(h) C shadow-DOM `@counter-style`** — access-from / shadow-dom-part f 0.9925 ×3 each, override-in f
  0.9816 / 0.9808 / 0.9805, fallbacks-in f 0.8821 ×3. An extractor shadow-tree walk (M, extractor seam). Not now.

## 4. Queue corrections found while scouting (for the BACKLOG refill)

- **4(b′):** the "one line of code away" mechanism is stale for inset-005/-006/-014.
  - Both natives fold those hosts at wave53-final.
  - What fails is the UA heading face. Lane #1, BACKLOG 0(g).
- **0(g):** the iOS gate "stand down for a heading with children" is now actively wrong for FOLDED hosts. Its own banner
  says to delete it when an inline formatting context lands; one has.
- **Untracked web-only fails, natives PASS** (queue them under 5):
  - **The lane-#2 family**, as above.
  - **`css-color/border-color-currentcolor` web f 0.9038** (ios P 0.9922 / android P 0.9914).
    - Picture: web paints the four 3D borders GREY where ref and natives paint green.
    - Wire: `BorderTopColor {"original":"currentColor"}`. The web side emits `currentColor` (`borders/sides/_shared.ts`),
      so the box's computed `color` at paint time is not the declared green.
    - Traced only that far.
  - **`css-sizing/aspect-ratio/abspos-016` web f 0.9043** (ios P 0.9974 / android P 0.9967). Picture: web stretches the
    abspos aspect-ratio box to the full 358 px. The ref has a 100×100 square.
  - **`css-tables/colspan-004` web f 0.999** (natives P 0.9847 / 0.9873).
- **`text-decoration-subelements-002` ios f 0.7975** is an iOS em-inset defect (`top: 1em` / `3em` with `original`-only
  data). It is not decoration. Route it to layout.

## 5. Artifacts (all under `tools/titan/results/wave54-plan/`)

- `ua-heading-face.md`
- `ua-heading-face.census.mjs`, `.census.json`, `.census.out.txt`
- `ua-heading-face.geometry.py`, `.geometry.out.txt`
- `web-root-separator.md`
- `web-root-separator.census.mjs`, `.census.out.txt`
- `web-root-separator.replay.mjs`, `.replay.out.txt`
- `web-root-separator.geometry.py`, `.geometry.out.txt`
- `queue-scout-text-web.cjk-pin.census.mjs`, `.out.txt`
