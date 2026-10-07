# spaceless-soft-hyphen — wave-53 family brief (BACKLOG 4(g); obligation 0(b))

Evidence run: `tools/titan/runs/wave52-ship` (dev tip cdb8a845). Every score below comes from `cells.mjs` over
`wave51-fix → wave52-calib → wave52-ship`. Every PNG named below was opened at full size, and box and ink extents were measured
with a short PIL script (orange = r>200, 120<g<200, b<80). Nothing was built or run, because the wave53-open gate was live.
The census is in `spaceless-soft-hyphen.census.json`, next to this file: 1435 per-test IR docs scanned, 17 of them carry U+00AD.
Its method is committed as `spaceless-soft-hyphen.census.py` (added at plan-skeptic review; the original "1494" was an
`ls …/per-test-ir/ | wc -l` artifact that also counted 30 directory headers and 29 blank lines).

**Summary.** A soft hyphen in a run with no space is a break opportunity that nobody takes on Android, and the same root
breaks iOS in a different way:
- **Android.** `PreBreakPipeline.preBreak` declines every space-less run. Its comment says the wave-21 whole-run gate "owns
  that case". It does not own this case: `DecorationOps.hasSoftWrapOpportunity` counts U+00AD as an opportunity, so the
  wave-21 gate leaves `softWrap` on. Compose maps `hyphens: manual` to `Hyphens.None`, and under that Minikin ignores U+00AD.
  It therefore makes an emergency break at the box edge (`highwa` / `y`).
- **iOS.** The twin guard (`transformedText.contains(" ") || hyphenLocale != nil`) declines the same runs. TextKit then takes
  the soft hyphen on its own, but the composed line-box pin counts ONE line (`singleLineText`, because U+00AD is not
  whitespace). The box is 26 px tall where the ref box is 46 px.
- **The out-of-flow span is a second, separate mechanism.** The abspos `<span>` is refused by the inline fold
  (`member-prop:Position`). Each text run then renders as its own stacked `Text`, so the word is split into two
  paragraphs at the span's position. No line breaker ever sees one word. This confirms the reviewers' observation and
  replaces their guessed mechanism (see §4).

## 1. Target cells

| cell | platform | wave51-fix | wave52-calib | **wave52-ship** | label (cell-review.json) |
|---|---|---|---|---|---|
| css-text/hyphens/hyphens-span-001 | android | f 0.9166 | f 0.9166 | **P 0.9532** | DEGENERATE |
| css-text/hyphens/hyphens-out-of-flow-001 | android | f 0.9218 | f 0.9218 | **P 0.9685** | DEGENERATE |
| css-text/hyphens/hyphens-out-of-flow-002 | android | f 0.9291 | f 0.9291 | **P 0.982** | DEGENERATE |
| css-text/hyphens/hyphens-span-001 | ios | f 0.8558 | f 0.8558 | **f 0.8652** | honest fail (not reviewed) |
| css-text/hyphens/hyphens-out-of-flow-001 | ios | f 0.8862 | f 0.8862 | **f 0.8935** | honest fail (not reviewed) |

- **Why the three Android cells flipped in wave 52.** The `ch` fix (1(e), aaf676c5) together with M-A (ab-ma.txt) widened
  the boxes to the reference's 6ch. The text did not change.
- **Context only, not targets.** web: span-001 P 1, out-of-flow-001 P 1, out-of-flow-002 **f 0.9411**. The web capture
  of -002 paints boxes 4 and 5 as one overflowing `highway`, which is a web-harness runs issue outside this family.
  ios: out-of-flow-002 P 0.9971.
- **Anchor for "correct".** css-text/hyphens/hyphens-span-002 has the same geometry, with `hyphens: auto` + `lang=en`.
  It scores android P 0.9943 and ios P 0.9971.

## 2. The picture (390×600, all boxes x 21..87 → content box x 24..84 = 61 px = 6ch)

- **Ref (all three tests).** Every box has border-box y 108..153 (46 px) and a pitch of 51 px. Line 1 `high‐` has ink
  x 25..61. Line 2 `way` has ink x 24..54.
- **span-001, android.** All 9 boxes keep the ref geometry (y 108..153, …, 516..561), but each reads `highwa` / `y`:
  - line 1 ink runs x 25..77, overrunning the ref's x 61 by 16 px;
  - line 2 ink is x 24..32, a lone `y`;
  - there is no hyphen glyph in any box.
- **out-of-flow-001, android.**
  - Boxes 1, 2, 6 and 7 read `highwa` / `y` (L1 x 25..77, L2 x 24..32).
  - Box 3 (y 210..255) reads `h` / `ighway` (L1 x 25..31, L2 x 25..77).
  - Boxes 4 and 5 read `high` / `way` with no hyphen (L1 x 25..55, L2 x 25..53).
- **out-of-flow-002, android.**
  - Boxes 1, 2 and 7 are correct: `high‐` / `way`, L1 x 25..61. Android's hyphen is light anti-aliased ink, so it only
    shows up at a luminance threshold of 200.
  - Box 3 reads `h` / `ighway`. Boxes 4 and 5 read `high` / `way` with no hyphen. Box 6 reads `highwa` / `y`.
- **span-001 and out-of-flow-001, ios.**
  - The glyphs are right: `high-` with ink x 25..60 at y 111..130.
  - Every box is 26 px tall (y 108..133, i.e. 3 + 20 + 3) and the pitch is 31 px.
  - Line 2 `way` paints at y 134..138, outside the box, under the next box's top border.
  - The last box ends at y 381 (span-001) or 319 (-001), where the ref ends at y 561 or 459.
- **out-of-flow-002, ios.** Correct, with 46 px boxes.

## 3. The wire (`wave52-ship/sections/css-text/per-test-ir/…`)

- **hyphens-span-001.** All nine boxes are LEAF components with no `runs`, because the extractor flattened the unstyled
  spans. Each has `"text": "high­way"` and the properties `Width {"type":"length","original":{"v":6,"u":"CH"}}` and
  `Hyphens "MANUAL"` (component `…hyphens-span-001__1-301` … `__9-309`).
- **hyphens-out-of-flow-001.**
  - Box 1 is a leaf with `"text":"high­way"`.
  - Boxes 2–7 are runs hosts. For example, box 3 (`…__3-273`) has
    `"runs":[{"text":"h"},{"child":"hyphens__hyphens-out-of-flow-001__3__0"},{"text":"igh­way"}]`.
  - The member is `{"sourceTag":"span"}`, `"text":"abspos"`, with properties
    `[{"type":"Position","data":"ABSOLUTE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}}]`.
  - The run splits for boxes 2…7 are: `[child,"high­way"]`, `["h",child,"igh­way"]`, `["high",child,"­way"]`,
    `["high­",child,"way"]`, `["high­wa",child,"y"]` and `["high­way",child]`.
- **hyphens-out-of-flow-002.** Same shape with `"text":"highway"` (no U+00AD), `Hyphens "AUTO"` and `meta.lang "en"`.
  The runs split at `h|`, `high|`, `high|` and `highwa|`.

## 4. Mechanism (traced; file:line under cdb8a845)

**A. span-001 android, and the flat or one-piece boxes 1, 2 and 7 of out-of-flow-001.** The path, in order:

1. The leaf or stacked run reaches `PlaceholderContent`
   (`runtimes/compose/…/core/renderer/ComponentRenderer.kt`).
2. `SoftHyphenPolicy.displayString` keeps U+00AD, because the mode is `manual`.
3. `effectiveSoftWrap` (`:6624-6626`) is `softWrap && !(composed && !dict && !hasSoftWrapOpportunity(displayText))`.
   - `hasSoftWrapOpportunity` (`typography/DecorationOps.kt:393-410`) returns **true**, because `ch == '­'` is in its
     set (`:405`).
   - So the wave-21 whole-run gate does **not** turn `softWrap` off.
4. `PreBreakPipeline.preBreak` (`typography/wrapping/PreBreakPipeline.kt:166`) runs
   `if (text.indexOf(' ') < 0) return identity` before the fold. The F5 taken-soft-hyphen trigger (`:187`) is never reached.
   - The comment at `:161-165` assumes the wave-21 gate owns every space-less run. For a run that carries U+00AD, step 3
     shows that it does not.
5. The `Text` renders the raw string with `softWrap = true` (`ruleBSoftWrap`, `:7080`) and the default `Hyphens.None`.
   - `TextWrapApplier.kt:176` / `:187` map `MANUAL` to `Hyphens.None`.
   - `styledTextStyle` only gets `Hyphens.Auto` when `AutoHyphenation.engaged` (auto + a language tag).
   - Minikin ignores U+00AD under `Hyphens.None`. This was measured in wave 37 (`SoftHyphenPolicy.kt:30-39`: an IR with the
     shys deleted captured byte-identical).
   - Minikin then makes an emergency break at the 61 px edge, giving `highwa` / `y`.
6. **Ruled out: a fold or measurer defect.** `GreedyLineBreaker.open` already splits a lone overflowing word at its first
   usable op (`WordBreakOpportunities.split`, `overflowFallback = true`). So the fold would return `["high‐","way"]` and
   `tookSoftHyphenBreak` would fire. The guard is the only thing in the way.

**B. out-of-flow boxes 3–6 on Android (-001 and -002).** The path, in order:

1. `InlineRunFold.fold` reaches the glyph-member arm for the `abspos` span.
2. `InlineSpanRing.admit` (`typography/inline/InlineSpanRing.kt:366`, `else -> Refused("member-prop:${prop.type}")`) refuses
   the member on its first property, `Position`.
3. `InlineRunFold.kt:470` returns `Bailed("member-prop:Position")`. The JVM suite already pins this bail on the verbatim
   box-3 wire: `InlineRunFoldTest.kt:152`, "hyphens-out-of-flow-001 - an abspos member bails to the stacked fallback".
4. `ComponentRenderer.kt:3905-3920` takes the stacked wave-32 fallback. Every `Entry.Text` becomes its own
   `PlaceholderContent`, that is a separate paragraph in a `Column`, and the abspos child is mounted between them.
5. That is why `h` and `ighway` are two blocks. It is not a break opportunity inside one layout:
   - `ighway` fits by itself (ink x 25..77), so it stays on one line;
   - in -002, `high` and `way` are both short, so no dictionary is consulted.

**What this means for the reviewers' guess.** Their observation is right: the word is cut at the span. Their mechanism is
not: the span is not "a line-break point" in one text layout. It is the fold refusing an out-of-flow member, which causes
stacked paragraphs.

Box 6 of -001 needs no break at all: `high­wa` fits. The `highwa` / `y` there is the stack, not Minikin.

**C. iOS span-001 and out-of-flow-001 share root A, with a different symptom.** The path, in order:

1. In `Renderer/ComponentRenderer.swift:4921`, the pre-break `broken` closure is guarded by
   `transformedText.contains(" ") || hyphenLocale != nil`. A `manual` run with no language and no space declines, so
   `displayText = "high\u{AD}way"`.
2. TextKit honours U+00AD unconditionally (`SoftHyphenPolicy.swift` banner) and draws `high-` / `way`.
3. At `:5028`, `singleLineText = !displayText.contains { $0.isWhitespace }` is **true**, because U+00AD is category Cf and
   not whitespace.
4. So `lineCountIsExact` (`:5097`) is true, and `pinnedBoxHeight` (`:5099`) pins 1 × 20 px. With the 3 px borders top and
   bottom that is the measured 26 px box.

-002 ios passes because its language tag satisfies the guard (`hyphenLocale != nil`), so the run is pre-broken to
`high‐\nway` and pinned to 2 lines. That is the proof of the fix's shape on this platform.

## 5. The fix

**Rejected: direction 2 of 4(g), "honour U+00AD under manual via `Hyphens`".**
- Compose's `Hyphens` only has `None` / `Auto`. `Auto` switches on Minikin's hyphenator for the paragraph locale, which is
  the DEVICE locale unless `localeList` is set (`TextWrapApplier.kt` wave-40 banner).
- So every word of a `manual` run without a soft hyphen would gain dictionary breaks, which §5.3 `manual` forbids.
- Avoiding that needs a pattern-less locale hack, which also changes Han face selection.
- None of it can be pinned on the JVM.
- Against the wave-21 gate it changes nothing: that gate is already off for these runs.

**Chosen: direction 1, a taken soft hyphen fires for a space-less run.** Three parts, each with its own A/B arm.

- **F1 (Compose; owns `typography/wrapping/PreBreakPipeline.kt`).**
  - Narrow the decline at `:166` to `if (text.indexOf(' ') < 0 && text.indexOf('­') < 0) return identity`, and
    rewrite the `:161-165` comment: the wave-21 gate owns space-less runs with NO soft-wrap opportunity. A U+00AD run has
    one, so nobody else claims it.
  - Space-less runs without U+00AD keep the identity instance, so wave 21's population is byte-stable by construction.
  - Spec: css-text-3 §5.3 (`manual`: U+00AD is an opportunity, and the UA paints the hyphenate character when it breaks
    there) and §5.5 (`overflow-wrap: normal` forbids the emergency break).
  - Seam: none. The call site (`ComponentRenderer.kt:7031`) already pairs `fired` with `softWrap = false`.
- **F1-iOS (seam hunk).**
  - Move the precondition into a pure helper in a lane-owned file, for example
    `StyleEngine/typography/wrapping/SoftHyphenPolicy.swift`:
    `static func admitsPreBreak(_ t: String, dictionary: Bool) -> Bool { t.contains(" ") || t.unicodeScalars.contains("\u{AD}") || dictionary }`.
  - Deliver `seam-1.patch` replacing the condition at `ComponentRenderer.swift:4921` with that call.
  - The fold then returns `high‐\nway`, `preBroken` makes `lineCountIsExact` count 2 lines, and the box pins at 40 + 6 px.
  - Spec: css-text-3 §5.3, CSS 2.1 §10.8 (line-box height).
- **F2 (Compose; owns `typography/inline/InlineRunFold.kt`).**
  - Before the glyph-member arm, admit a **paint-inert out-of-flow member** and drop it from the merged string. The member
    must satisfy all of:
    - tag in `TEXT_MEMBER_TAGS`;
    - no children, runs or decorations;
    - properties ⊆ {`Position` ABSOLUTE|FIXED, `Color` with alpha 0, `Hyphens`, border colours}.
  - Count dropped members in a new `Outcome.Folded.droppedOutOfFlowMembers`, default 0.
  - The merge then yields `high­way` (-001; F1 fires) and `highway` (-002; Minikin `Hyphens.Auto` + `en` already
    paints `high‐/way` for box 1).
  - Spec: css-text-3 §5.1 line-breaking details: out-of-flow elements introduce no soft wrap opportunity. The test asserts
    it as "the presence of an out of flow element has no effect on manual hyhenation".
  - Stated loss: the dropped box is not mounted. It paints nothing by the predicate, and its static position is unmodelled.
  - Seam: optional one-line `seam-2.patch` (Compose `ComponentRenderer.kt` fold breadcrumb, `:3821-3852`) to print the new
    count. Without it the breadcrumb still logs the fold but not the drop.

## 6. Blast radius (census JSON beside this file)

**U+00AD census** (`spaceless-soft-hyphen.census.py`). 17 of the 1435 per-test IR docs carry U+00AD. They hold 24
space-less U+00AD strings, counting each leaf's own `text` (15) and each runs host's `meta.runs[i].text` piece (9). A runs
host's own `text` field mirrors its merged pieces and is NOT counted: those 6 mirrors (all in hyphens-out-of-flow-001)
would make it 30. iOS labels can read a host's `text`; all 6 mirrors read `high­way`, the string of the 9
hyphens-span-001 leaves and of out-of-flow-001's box-1 leaf, so they add occurrences but no new string shape:

| test (css-text/hyphens/…) | strings | Compose F1 outcome (traced) | iOS F1 reached? |
|---|---|---|---|
| hyphens-span-001 | 9 leaves `high­way` | **fires** → `high‐\nway` | yes |
| hyphens-out-of-flow-001 | box 1 leaf; boxes 2 and 7 run pieces `high­way` | **fires** | yes (one label per box, per the picture) |
| hyphens-out-of-flow-001 | `igh­way`, `high­wa` | enter the pipeline, fit on one line → identity | — |
| hyphens-out-of-flow-001 | `­way`, `high­` | enter the pipeline, no usable op → identity | — |
| hyphenate-character-001 / -003 / -004 | run piece 1 `im­ple­men­ta­tion` | **fires** with U+2010 baked; the `{text, br}` hosts bail `br-stacked-equivalent` | yes, if the run label gets a wrap width |
| hyphens-auto-control | 3 leaves `fragilistic­expiali` (`AUTO`, `en-us`) | enter the pipeline → identity (dictionary veto) | no (already admitted) |
| hyphenate-character-005 | `قىل­`, `white-space: pre` | declined before the guard | no |
| hyphens-vertical-001 | `hyphen­ation`, vertical-rl | declined before the guard (`wrapWidthPx = -1`) | **maybe** (the iOS wrap width is not vertical-gated) |

- **Hyphenate-character note.** The fired string bakes U+2010 even though the hyphenate-character wire says `""`, `•` or
  `/-/`. That is the 4(f) note-only fallthrough. Threading `HyphenateCharacter` is out of scope.
- **F2 census.** The corpus has 21 runs hosts with an abspos/fixed member. Exactly **12 pass the inert predicate, all in
  hyphens-out-of-flow-001 / -002**. The other 9 still bail, and are the F2 must-not-move controls:
  - static-inside-inline-001/2/3: tagless, with `BackgroundColor`;
  - hypothetical-inline-alone-on-second-line: visible `Line 2`, with `PaddingLeft`;
  - ch-unit-001: `BackgroundColor`;
  - between-float-and-text;
  - position-absolute-semi-replaced-stretch-other;
  - first-letter-list-item-dynamic-001;
  - abspos-container-change-dynamic-001.

**Carrier sets** (the stems control-check may see change; every other capture must be byte-identical; full JSON in
`carrierSets`):
- android: `wpt__css-text__hyphens__hyphens-span-001`, `…hyphens-out-of-flow-001`, `…hyphens-out-of-flow-002`,
  `…hyphenate-character-001`, `…-003`, `…-004`, `…hyphens-auto-control` (reached, expected byte-identical)
- ios: `…hyphens-span-001`, `…hyphens-out-of-flow-001`, `…hyphenate-character-001`, `…-003`, `…-004`, `…hyphens-vertical-001`
- web: none

**Passing today inside the carrier sets:**
- android: span-001, out-of-flow-001 and out-of-flow-002 (all three degenerate), plus auto-control P 0.9732.
- ios: none. The ios carriers are all f: span-001 0.8652, out-of-flow-001 0.8935, hyphenate-character-001 0.9287,
  -003 0.9337, -004 0.9134, vertical-001 0.8801.

## 7. Predictions (wave52-ship → closing gate)

**Target cells:**
- span-001 android: P 0.9532 → **P ≈ 0.994 (HIGH)**. F1 alone does it, and the result is the same picture as span-002
  android 0.9943.
- out-of-flow-001 android:
  - F1 alone: P 0.9685 → P ≈ 0.975 (MED). Boxes 1, 2 and 7 are fixed; 3–6 stay stacked.
  - F1 + F2: **P ≈ 0.994 (MED-HIGH)**.
- out-of-flow-002 android: P 0.982 → **P ≈ 0.994 with F2 (MED-HIGH)**. F1 alone leaves it byte-identical, because there is
  no U+00AD.
- span-001 ios: f 0.8652 → **P ≈ 0.99 (MED-HIGH)**. The glyphs are already right; only the 26 → 46 px box changes. Anchor:
  -002 ios is 0.9971.
- out-of-flow-001 ios: f 0.8935 → **P ≈ 0.99 (MED)**.

**Movers (f; no P→f possible).** If any of these flips to P it is DEGENERATE by construction (U+2010 where the ref paints
nothing, a bullet, or `/-/`) and must be looked at, not counted.
- hyphenate-character-001 / -003 android, f 0.9136 / 0.9168: up (MED). Piece 1 goes from 4 to 5 lines (-001) or 3 to 5
  lines (-003), matching the ref's 5. The groups below drop by about one line onto the ref rows.
- hyphenate-character-004 android, f 0.9041: small (LOW). It stays at 3 lines.
- The same three on ios (f 0.9287 / 0.9337 / 0.9134): LOW. TextKit already draws 5 lines there.
- hyphens-vertical-001 ios, f 0.8801: LOW, and only if it is reached.

**Must not move (byte-identical):**
- **auto-control:** hyphens-auto-control android P 0.9732 / ios P 0.9731.
- **Spaced U+00AD runs** (F5 unchanged; android / ios):
  - hyphens-manual-011: 0.9952 / 0.9951
  - hyphens-manual-012: 0.9952 / 0.9951
  - hyphens-manual-013: 0.9945 / 0.994
  - hyphens-manual-inline-011: 0.9927 / 0.993
  - hyphens-manual-inline-012: 0.9803 / 0.9905
  - hyphens-none-011: 0.9869 / 0.9874
  - hyphens-none-shy-on-2nd-line-001: 0.998 / 0.9988
  - css-overflow block-ellipsis-014: 0.9736 / 0.9793
  - css-overflow block-ellipsis-028: 0.9855 / 0.9913
- **Space-less runs without U+00AD:**
  - hyphens-span-002: 0.9943 / 0.9971
  - hyphens-punctuation-001 android: P 0.9981
- **F2 controls (android):**
  - static-inside-inline-001 / -002 / -003: P 0.9967 / 0.9617 / 0.9967
  - hypothetical-inline-alone-on-second-line: P 0.9852
  - first-letter-list-item-dynamic-001: P 0.9987
  - abspos-container-change-dynamic-001: P 0.9654
- **Web:** everything.

## 8. Verification plan

**JVM pins** in `PreBreakPipelineTest.kt`, using the existing `mono` measurer with CH = 19.2:

| # | pin (input → expected) | mutation that must turn it red |
|---|---|---|
| a | `"high­way"` (verbatim from `hyphens-span-001__1-301`), width 6·CH → `fired`, `"high‐\nway"` | restore the `:166` space-only guard |
| b | the same string at 8·CH → `assertSame` identity | drop the `!unbreakableOverflow && !tookSoftHyphen` early return |
| c | `"fragilistic­expiali"` (auto-control), `dictionaryHyphenation = true`, 12·CH → identity | remove `!dictionaryHyphenation &&` from `:187` |
| d | `"im­ple­men­ta­tion"` at 4.5·CH → `"im‐\nple‐\nmen‐\nta‐\ntion"` | names the hyphenate-character mover shape |

- **Keep `declinesForASpacelessRunTheWholeRunGateOwns` (`:130`) green.** Deleting the guard outright turns it red: a single
  fired line is a new String instance, so `assertSame` fails. That pin is the over-widening mutation.

**JVM pins in `InlineRunFoldTest.kt`:**
- Invert `:152`. Verbatim `OUT_OF_FLOW_001_HOST3` → `Folded`, text `"high­way"`, `droppedOutOfFlowMembers == 1`.
  Mutation: remove the F2 arm.
- Add -002 box 4 verbatim → `Folded("highway")`.
- Negative pins, each still `Bailed`, on verbatim wire: ch-unit-001's span, hypothetical-inline-alone-on-second-line's
  `Line 2` span, and static-inside-inline-001's tagless member. Mutation: widen the predicate to "any abspos".

**Catalyst pins** (`SoftHyphenPolicyTests.swift` / `GreedyLineBreakerTests.swift`):
- `admitsPreBreak("high\u{AD}way", dictionary: false) == true`.
- `admitsPreBreak("Deoxyribonucleic", dictionary: false) == false`.
- `GreedyLineBreaker.lines("high\u{AD}way", maxWidth: 6, mono) == ["high‐","way"]`.
- Mutation: drop the U+00AD clause. The seam patch must apply clean and keep the pins green with it applied.

**Suites:**
- Focused Compose run: `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*PreBreakPipelineTest' --tests '*InlineRunFoldTest' --tests '*GreedyLineBreakerTest' --tests '*WordBreakOpportunitiesTest' --tests '*SoftHyphenPolicyTest' --tests '*AutoHyphenationTest')`.
- Then the full runtime suite, plus Catalyst `-only-testing:StyleConverterRuntimeTests/SoftHyphenPolicyTests`,
  `GreedyLineBreakerTests`, `WordBreakOpportunitiesTests` and `InlineRunFlowTests`, then the full Catalyst suite.
- All of this runs only after the gate releases the host.

**Closing gate:**
- Probe section: `css-text`. Every carrier is in it, except the must-not-move block-ellipsis pair in `css-overflow` and
  the F2 controls in `CSS2`, `css-pseudo`, `css-tables` and `css-values`.
- Control-check with the carrier sets above.
- Device A/B arms: drop-F1 (Compose), drop-F2, and drop-seam-1 (iOS), so each flip is attributed.

## 9. Risks and recommendation

**Risks:**
1. **Measurement margin (LOW).** `highway` ≈ 61 px against a 61 px content box. Minikin already judged that it does not
   fit, and the fold measures through the same `TextMeasurer` style. If the fold judged it fits, F1 would be identity and
   cost nothing.
2. **U+2010 baked where `hyphenate-character` says otherwise (MED, f cells only).** This is the existing 4(f) fallthrough,
   now reaching three more pieces.
3. **The iOS wrap width is not vertical-gated (LOW).** `hyphens-vertical-001` ios may move. If the builder sees it reach
   the closure, gate the U+00AD clause on horizontal runs to keep it byte-identical.
4. **F2 drops a box (LOW).** It is safe only because the predicate requires zero paint. The breadcrumb names it, and only
   the 12 out-of-flow hosts qualify.
5. **Ring-fenced, not touched:** filter-effects/backdrop-filter-basic-blur.

**Recommendation: GO (one lane, effort S).**
- It turns three DEGENERATE Android passes into faithful ones and two honest iOS fails into passes.
- It needs two small owned files (`PreBreakPipeline.kt`, `InlineRunFold.kt`), one Swift helper and one iOS seam hunk.
- Every change is pinnable on verbatim wire with a mutation that turns the pin red.
- If the orchestrator wants it smaller: F1 plus F1-iOS alone is GO-SMALL. It covers span-001 on both natives, boxes
  1/2/7 of -001, and the iOS -001 cell. -002 android then stays DEGENERATE and is recorded as such.
