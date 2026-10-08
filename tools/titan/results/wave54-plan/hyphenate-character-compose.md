# hyphenate-character-compose — wave-54 family brief (BACKLOG "Next-wave obligations" 0(b))

Evidence run: `tools/titan/runs/wave53-final` (dev tip 7cce3b22). Every score below comes from
`node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave53-open wave53-final` or from the gate's own composed
metric (`inject-wpt-block.mjs` `diffWebVsRef`) run by a script in this directory. Every PNG named below was opened.
Line extents and line tops were measured with PIL by `hyphenate-character.bands.py` (output in `.bands.out.txt`) and by
`hyphenate-character.geometry.py`. Nothing was built, run or captured, because the wave-54 opening gate is live on this
host. All files written by this miner are in `tools/titan/results/wave54-plan/`:
- `hyphenate-character.census.mjs` → `.census.json` / `.census.out.txt` (1435 per-test IR docs);
- `hyphenate-character.b2-census.mjs` → `.b2-census.out.txt`;
- `hyphenate-character.replay.py` + `.replay-b.py` + `.replay-score.mjs` → `.replay.out.txt` (pictures in
  `hyphenate-character-replay/`);
- `hyphenate-character.replay-b-radius.py` + `-score.mjs` → `.replay-b-radius.out.txt`;
- `hyphenate-character.drift.py` → `.drift.out.txt`;
- `hyphenate-character.geometry.py` → `.geometry.wave53-final.txt` / `.geometry.replays.txt`.

**Summary.** The obligation as written ("Compose does not honour `hyphenate-character`") is true, but it is the
smallest of four defects. Fixing it alone flips **nothing**: the best replay of "the glyph fixed, everything else as
captured" scores 0.9474. Here is what the pictures and the code show:
- **The CONVERTER loses the value before any runtime sees it.**
  - `hyphenate-character: ""` (tests -001 and -002) is rejected by `HyphenateCharacterPropertyParser`
    (`if (stringValue.isEmpty()) return null`, `:26`). It reaches the wire as `Generic {_unmapped: true}`.
  - `"\2022"` (-003) and `"\00a0\0640"` (-005) are never unescaped. The wire carries a literal backslash, and the web
    runtime faithfully paints `im\2022` (web -003 f 0.8812).
- **Neither native threads the value.**
  - Compose bakes U+2010 in `PreBreakPipeline`.
  - iOS passes `AutoHyphenation.defaultHyphenCharacter` at `ComponentRenderer.swift:5011`, and its
    `HyphenateCharacterApplier` is an identity function.
  - Compose's `tookSoftHyphenBreak` cannot even see a taken break when the string is `""`: `count()` returns 0 (`:236`).
  - The string's WIDTH decides the breaks: `real` fits 4.5ch only when the string is `""`, and `tial/-/` overflows
    6.5ch. So the fix must measure with the string; substituting the glyph afterwards would not be enough.
- **The EXTRACTOR adds a blank line before word groups 3 and 4 on all three platforms.** `buildNode` seeds
  `childLineCtx.hasInline` once from `ownText` (`extract-fixture.mjs:11706`) and never re-arms it for text interleaved
  between children. So a `<br>` that ENDS a text line gets the 20 px "blank line" height.
- **The preamble `<b>` is lost** (`inline-run-merged`). This is the corpus-wide convention and is not ours.

## 1. Target cells

| cell (css-text/hyphens/…) | platform | wave53-open | **wave53-final** | wave-53 label |
|---|---|---|---|---|
| hyphenate-character-001 | android | f 0.9136 | **f 0.9301** | DEGENERATE by construction (`expectations.degenerateByConstruction`) |
| hyphenate-character-003 | android | f 0.9168 | **f 0.9292** | same |
| hyphenate-character-004 | android | f 0.9041 | **f 0.905** | same |
| hyphenate-character-001 | ios | f 0.9287 | **f 0.9287** | same |
| hyphenate-character-003 | ios | f 0.9337 | **f 0.9337** | same |
| hyphenate-character-004 | ios | f 0.9134 | **f 0.9134** | same |

**In the family but not in the obligation** (cells.mjs, wave53-final):
- web: -001 f 0.935, -003 f 0.8812, -004 f 0.9249;
- -002 (`hyphens: auto`, same `""`, same ref): web f 0.92, ios f 0.9261, android f 0.9223;
- -005: web P 0.974, ios f 0.9428, android f 0.9474;
- hyphenate-limit-chars-001: web f 0.9068, ios f 0.8919, android f 0.8581.

The scores are reproduced exactly from the captures by `hyphenate-character.replay-score.mjs`, column `capture` in
`.replay.out.txt`.

## 2. The picture (390×600; the 16px-monospace `<div>` is 4.5ch / 5.5ch / 6.5ch = 43.2 / 52.8 / 62.4 px wide)

- **Ref -001** (`tools/wpt/refs/…/css-text/hyphens__hyphenate-character-001.png`). The preamble's "no visible hyphens
  appear" is bold. The four word groups are `im/ple/men/ta/tion` · `ini/tial/iza/tion` · `real/iza/tion` ·
  `hy/phen/ation`, with no hyphen glyph. Lines sit on a 19.2 px pitch. Each group is separated by ONE blank line, so the
  group tops are at y 110 / 224 / 319 / 395.
- **Android -001** (`…/wave53-final/sections/css-text/android-screenshots/wpt__css-text__hyphens__hyphenate-character-001.png`):
  - Every break carries a `-`: `im-/ple-/men-/ta-/tion`, `ini-/tial-/iza-/tion`.
  - Group 3 has four lines (`re-/al-/iza-/tion`) where the ref has three. `real-` is 5ch, which does not fit 4.5ch.
  - Groups 3 and 4 start at y 345 / 458, against the ref's 319 / 395. That is TWO blank lines before each of them.
  - The preamble is regular weight.
  - The wave-53 movers sheet `wave53-gate/final/sheets-movers/movers-02.png` row 2 shows the wave-53 gain: wave53-open
    painted the emergency breaks `impl/emen/tati/on`.
- **Web -001 is the same picture as Android**, hyphens included, because the property never reached the wire.
- **iOS -001** shows the same hyphens and the same extra blank lines. Group 2 additionally reads `ini-/tial/-/iza-/tion`
  with a LONE `-` line: the pre-broken line `tial‐` is 48 px wide against 43.2 px, and TextKit re-wraps it (§4.C).
- **Ref -003.** Bullets: `im•/ple•/men•/ta•/tion`, `ini•/tial•/iza•/tion`, `real•/iza•/tion`, `hy•/phen•/ation`.
  - Android and iOS paint the same breaks with `-` instead of `•`, plus the two extra blank lines (groups 3/4 at
    y 322/419 Android, 319/415 iOS, against the ref's 299/375).
  - Web paints `im\2022` and so on: literal backslash text, ink to x 81–100, and only four lines in group 1.
- **Ref -004.** `im/-/`, `ple/-/`, `men/-/`, `tation`; `ini/-/`, `tial/-/` (overflowing to x 81), `iza/-/`, `tion`;
  `re/-/`, `al/-/`, `iza/-/`, `tion`; `hy/-/`, `phen/-/` (overflowing), `ation`.
  - Both natives paint `imple-/menta-/tion` (three lines) and `real-/iza-/tion`, because a 1ch `-` lets more letters fit
    than the 3ch `/-/`.
  - **Web -004 is already right in glyphs and breaks.** It differs from the ref only by the two extra blank lines
    (groups 3/4 at y 303/418 against 281/375) and the non-bold preamble, and it still scores f 0.9249. That cell is the
    proof that the glyph alone cannot flip these tests.
- `hyphenate-character.geometry.py wave53-final` (`.geometry.wave53-final.txt`) prints GEOMETRY OK on all three ref rows
  and GEOMETRY WRONG on all nine capture rows. The only web -004 reason is `offset: group 3 line 1 top y303 vs ref y281`.

## 3. The wire (`wave53-final/sections/css-text/per-test-ir/…`)

- **-001 host `…-001__1-177`.**
  - Properties: `FontSize 16`, `FontFamily [monospace]`, `LineHeight {multiplier 1.2, original normal}`,
    `Width {v 4.5, u CH}`, `Hyphens "MANUAL"`, and
    **`Generic {propertyName "hyphenate-character", rawValue "\"\"", _unmapped true}`**.
  - `meta.runs`: `[im­ple­men­ta­tion, br__0, br__1, " ini­tial­iza­tion", br__2, br__3, " re­al­iza­tion", br__4,
    br__5, " hy­phen­ation"]`.
  - The six `br` children (`meta.role line-break`) carry Height **0, 20, 20, 20, 20, 20**. In the ref only br__0, br__2
    and br__4 end a text line; br__1, br__3 and br__5 are the blank lines. The correct heights are therefore
    0, 20, 0, 20, 0, 20.
- **-002** is the same, with `Hyphens "AUTO"`, the same Generic and the same br heights.
- **-003 / -004.** Typed `HyphenateCharacter {"type":"string","value":"\\2022"}` (a literal backslash and `2022`) and
  `{"type":"string","value":"/-/"}`. Same br heights.
- **-005.** `HyphenateCharacter {"type":"string","value":"\\00a0\\0640"}` sits on the bidi-baked host `…-005__1-219`.
  Its runs are child components with `WhiteSpace PRE` and no `HyphenateCharacter`.
- **The preamble `<p>`** is a leaf (`"text": "…but no visible hyphens appear."`) with no runs. The manifest gives
  `lossyReasons ["inline-run-merged"]`: `extract-fixture.mjs` merges `<b>` into the text (INLINE_MERGE_TAGS, `:2483`).

## 4. Mechanism (traced; file:line at 7cce3b22)

**A. Converter: the value never survives.** `converter/…/longhands/typography/HyphenateCharacterPropertyParser.kt`:
- `:24` strips the quotes with `removeSurrounding` and decodes nothing (css-syntax-3 §4.3.7 "consume an escaped code
  point" is not implemented).
- `:26` `if (stringValue.isEmpty()) return null`. css-text-4 §6.3 `<string>` admits the empty string, and test 001
  asserts it ("no visible hyphens").
- The null makes the property fall back to `Generic _unmapped`.
- `ContentPropertyParser.unescapeString` (`:285`) is no help: it handles only `\" \' \\ \n \t \r`, and its `\n` → LF is
  not CSS either.

**B. Web is faithful to a wrong wire.**
- `runtimes/web/src/engine/typography/HyphenateCharacterExtractor.ts:23` re-escapes `\` to `\\`, so CSS receives
  `"\\2022"` and Chromium paints `\2022`. Fixing the wire fixes web.
- The `Generic` is not emitted, so -001 and -002 web fall back to `auto`, which paints `-`.
- The web runtime needs no change.

**C. Natives.** Both bake the UA hyphen into the pre-break, measure with it, and never read the property.
- **Compose.** The runs host bails `br-stacked-equivalent` (`typography/inline/InlineRunFold.kt:546`), so every text run
  is a stacked `PlaceholderContent(properties = component.properties)` (`core/renderer/ComponentRenderer.kt:3946-3949`).
  The host's `HyphenateCharacter` IS in reach at the pre-break call (`:7072`), but `PreBreakPipeline.preBreak` has no
  string parameter:
  - `:185` `GreedyLineBreaker.lines(text, wrapWidthPx, measure)` uses the default `WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER`
    (U+2010). Note that `GreedyLineBreaker.lines` and `WordBreakOpportunities.split` already take `hyphenChar`.
  - `:202`/`:236` `tookSoftHyphenBreak` detects a taken U+00AD by COUNTING hyphenChars, and `count()` returns 0 for `""`.
    Threading `""` alone would therefore make the run decline. Minikin under `Hyphens.None` then ignores U+00AD and
    emergency-breaks: wave53-open's `impl/emen/tati/on`.
  - The only Compose reader of the property, `TextStyleApplier.extractHyphenateCharacter` (`TextStyleApplier.kt:1912`,
    DEFAULT U+00AD), is not on the render path.
- **iOS.**
  - `HyphenateCharacterApplier.swift` is `_ = cfg; _ = agg`.
  - `HyphenateCharacterExtractor.swift:19` reads `extractKeyword(prop.data)?.lowercased()`. That is the wrong shape for
    the `{type, value}` object on the wire, and it lowercases.
  - So `TextConfig` never carries the string, and `ComponentRenderer.swift:5011` passes
    `hyphenChar: AutoHyphenation.defaultHyphenCharacter`. The comment at `:5006-5010` names this gap.
- **iOS second defect (the lone `-` line).** `GreedyLineBreaker.hasUnbreakableOverflowingLine` (`GreedyLineBreaker.swift:485-495`)
  asks `!DecorationOps.hasSoftWrapOpportunity(line)`, and that function counts `-` and U+2010 as opportunities
  (`DecorationOps.swift:361`).
  - A pre-broken line that overflows only because of its own materialised hyphen (`tial‐`, and after the fix `tial/-/`
    and `phen/-/`) is therefore judged breakable.
  - `wptOverlongPreBrokenRun` (`:5123`) stays false, `.fixedSize(horizontal:)` (`:5318`) is not applied, and TextKit
    re-wraps the line at the box edge.
  - On Compose a fired run has `softWrap = false`, so the same line simply overflows, as in the ref.

**D. Extractor: the stray blank lines (a seam).** `tools/titan/extract-fixture.mjs`:
- `:11457-11459`: `brHeight = (lineCtx.hasInline || clear) ? '0px' : '20px'`.
- `:11466`: every br sets `hasInline = false`.
- `:11533`: element siblings re-arm `hasInline`.
- `:11706`: the children scope is seeded once with `{ hasInline: !!node.ownText }`.
- Text interleaved BETWEEN children (`node.runs` `{text}` entries) never re-arms. So br__2 and br__4, each of which ends
  a ` ini…tion` / ` re…tion` line, get 20 px.
- The body scope already does the right thing (`:11727-11733`, `bodyRuns.some(run => run.afterElemIndex === idx)`).
  The child scope lacks the twin.
- All three runtimes render a stacked `line-break` box at its Height: the gap appears on web, iOS and Android alike.

**E. What survives a br fix: per-group drift.** `hyphenate-character.drift.py` measured, line by line, the top offset
against the ref after subtracting the stray 20 px per br (`.drift.out.txt`):
- web and Android land **0 / +1 / +2 / +3 px** low on groups 1–4;
- iOS lands 0 / 0 / 0 / 0 (-003: g3 +20 → 0, g4 +40 → 0).

The cause is that the line-start br box is 20 px (the extractor's `REF_LINE_HEIGHT` 1.25 × 16), while the ref's blank
line is this host's line box, 1.2 × 16 = 19.2 px. iOS's zero is a compensation: its text pitch runs about 0.2 px/line
short (g1 offsets 0, 0, 0, −1, −1).

## 5. The fix (four units, one lane)

- **U1 — converter** (owns `HyphenateCharacterPropertyParser.kt` + a new
  `primitiveParsers/CssStringParser.kt` ≤ 200 lines).
  - Consume a CSS `<string>` per css-syntax-3 §4.3.5: matching quotes; escapes per §4.3.7, i.e. 1–6 hex digits plus one
    optional whitespace, U+0000 / surrogates / > U+10FFFF → U+FFFD, and `\` + any other code point → that code point.
  - Return `HyphenateCharacterValue.String("")` for `""`.
  - Unquoted input keeps today's acceptance.
  - Wire effect: 001/002 `Generic` → `{"type":"string","value":""}`; 003 → `"•"`; 005 → `" ـ"`. These are
    value changes on an existing `string` variant, so there is no new byte shape (schema leaves are permissive, and no
    conformance golden carries the property).
- **U2-Compose** (new `typography/wrapping/HyphenateCharacter{Config,Extractor,Applier}.kt`, new
  `typography/wrapping/SoftHyphenCuts.kt`, `PreBreakPipeline.kt` defaulted parameter only).
  - The Extractor reads `{type:"auto"}` → null (= U+2010, what Chromium paints) and `{type:"string",value}` verbatim,
    including `""`.
  - The Applier returns the string for the pre-break, and logs via `PropertyTracker.logOnce` when a non-auto value meets
    a dictionary run, where Minikin paints its own hyphen (-002 android; repo rule "no silent fallthroughs").
  - `preBreak(…, hyphenChar: String = DEFAULT_HYPHEN_CHARACTER)` threads the string to `GreedyLineBreaker.lines`.
  - "Took a soft-hyphen break" moves to `SoftHyphenCuts.took(text, lines, hyphenChar)`. It walks the lines against the
    source and asks whether any line boundary falls on a U+00AD, which works for `""` as well. Counting characters does
    not.
  - **Seam patch 1** (Compose `ComponentRenderer.kt:7072`, one argument):
    `hyphenChar = HyphenateCharacterApplier.preBreakString(HyphenateCharacterExtractor.extract(properties))`.
- **U2-iOS** (owns `HyphenateCharacter{Config,Extractor,Applier}.swift`, `TypographyAggregate.swift` +1 field,
  `Renderer/StyleBuilder.swift` +1 `TextConfig` field and +1 line beside `:525`, `GreedyLineBreaker.swift` one defaulted
  parameter, and a new `typography/wrapping/SpentHyphen.swift` ≤ 200 lines).
  - The Config carries `value: String?` verbatim.
  - The Applier writes `agg.hyphenateCharacter`.
  - `hasUnbreakableOverflowingLine(…, spentHyphen: String? = nil)` tests `hasSoftWrapOpportunity` on the line minus a
    trailing `spentHyphen` (css-text-3 §5.5: the taken opportunity is spent, and a break after the line-final hyphen
    breaks nothing).
  - **Seam patch 2** (iOS `ComponentRenderer.swift`):
    - `:5011` → `hyphenChar: textConfig.hyphenateCharacter ?? AutoHyphenation.defaultHyphenCharacter`;
    - `:5026` → `spentHyphen: textConfig.hyphenateCharacter`.
  - nil, meaning no declaration or `auto`, keeps every other run byte-identical by construction. Generalising the
    spent-hyphen rule to U+2010 is queued, not done here.
- **U3 — extractor re-arm (seam patch 3, `tools/titan/extract-fixture.mjs` `buildNode` children loop).** Before
  `buildNode(child, …, childLineCtx)`, set `childLineCtx.hasInline = true` when `node.runs` holds a non-whitespace
  `{text}` entry after the previous `{childIndex}` and before this child's. This is the child-scope twin of `:11733`
  (CSS 2.1 §9.5: a br preceded by inline content on its line ends that line).
- **U3b — optional, probe-decided (same seam).** A line-start br inside a host whose cascade declares `line-height` (the
  `font` shorthand resets it to `normal`) gets Height = that line box (`normal` → 1.2 × font-size, the converter's own
  mapping) instead of 20.
  - Census (`.b2-census.out.txt`): of the 76 line-start 20 px brs U3 keeps (32 docs), **12 in exactly 4 docs** sit in such
    a host: hyphenate-character-001…004.

## 6. Census (corpus radius)

**hyphenate-character carriers** (`.census.out.txt` (1)): 14 properties in **6 of 1435 docs**:
- -001 and -002: `Generic` `""`, rejected by the parser;
- -003: `\2022`, undecoded;
- -004: `/-/`;
- -005: `\00a0\0640`, undecoded, on a bidi-baked host whose `WhiteSpace PRE` runs have no `HyphenateCharacter`;
- hyphenate-limit-chars-001: `"-"` ×9, with `Hyphens AUTO`.

Every carrier declares the property on the text host itself, so no inheritance is needed this wave. The only other
typed property with an undecoded hex escape in the corpus is `css-view-transitions/escaped-name`
`ViewTransitionName "third\\000021"` (a different parser, untouched).

**U1 wire carriers:** 001, 002, 003, 005. **U2 reach:**
- Compose: the U+00AD pieces of 001/003/004 (piece 1 is space-less, 2–4 lead with a space).
- iOS: the same, plus the CF-dictionary fold of 002 and hyphenate-limit-chars-001.
- Not reached: 005 (`white-space: pre` declines before both guards).
- Compose 002 and hyphenate-limit-chars-001 are dictionary runs, identity by `!dictionaryHyphenation`.

**U3 radius** (`.census.out.txt` (2)): **54 brs in 18 docs**, all statically extracted (no post-load, no bidi bake), and
29 currently passing cells (web 11, iOS 10, Android 8):
- css-text: hyphenate-character-001…004;
- css-cascade: revert-layer-006, revert-val-001/-002 (P everywhere);
- css-overflow: block-ellipsis-002/-004/-005/-006 (P everywhere);
- filter-effects: backdrop-filter-clip-rect, -edge-clipping, -paint-order (web/iOS P, Android f) and -plus-filter;
- f only: css-multicol balance-grid-container and column-height-009, css-masking clip-path-filter-order.

`filter-effects/backdrop-filter-basic-blur` (ring-fenced) is NOT a carrier.

**What the radius looks like:**
- I opened backdrop-filter-paint-order; the top crop of ref / web / android is
  `hyphenate-character-replay/backdrop-paint-order-top.png`. Web and Android insert the same stray blank line before
  "No dark/black…", which the ref does not have.
- In plus-filter the only stray br is the `<p>`'s trailing one, followed by an abspos stage, so nothing visible moves.
- In revert-val-001, revert-val-002 and revert-layer-006 the brs sit in green-on-green text inside a 100 px
  `overflow: hidden` box. Eight lines are ≥ 100 px either way, so the result is invariant.

**`<b>` (W, not ours):** 357 tests carry `inline-run-merged`. 331 / 310 / 314 of their 353 scored cells pass (web / iOS /
Android; node over wave53-final manifests). A pass that keeps W is the corpus's standing convention.

## 7. Ownership and seams

**Lane-owned files (disjoint):**
- Converter: `HyphenateCharacterPropertyParser.kt`, new `CssStringParser.kt`, new `HyphenateCharacterPropertyParserTest.kt`.
- Compose: new `HyphenateCharacter{Config,Extractor,Applier}.kt` and `SoftHyphenCuts.kt` under `typography/wrapping/`;
  `typography/wrapping/PreBreakPipeline.kt` (a defaulted parameter only — 239 lines); `PreBreakPipelineTest.kt`; new
  `HyphenateCharacterExtractorTest.kt`.
- iOS: `StyleEngine/typography/wrapping/HyphenateCharacter{Config,Extractor,Applier}.swift`, new `SpentHyphen.swift`;
  `StyleEngine/typography/TypographyAggregate.swift` (+1 field); `Renderer/StyleBuilder.swift` (+1 field +1 line);
  `StyleEngine/typography/GreedyLineBreaker.swift` (one defaulted parameter — 566 lines);
  `Tests/…/GreedyLineBreakerTests.swift`, `TypographyTests.swift` (the `:311` pin moves to the wire's object shape).

**Seams (patches, never direct edits):**
- (1) Compose `core/renderer/ComponentRenderer.kt:7072`, one argument;
- (2) iOS `Renderer/ComponentRenderer.swift:5011`/`:5026`, two arguments;
- (3) `tools/titan/extract-fixture.mjs` `buildNode` children loop: U3 re-arm, plus a separable U3b hunk, with a node
  test in a new `tools/titan/extract-fixture-br-line-context.test.mjs`.
- No web-runtime file and no web-harness `ComponentRenderer.tsx` change.

**Docs** (orchestrator / docs lane):
- The new Compose `HyphenateCharacterApplier.kt` moves `coverage-audit.mjs`'s `real` Android 15 → 16
  (`CLAUDE.md:347`, `docs/STATUS.md:38`). The converter test count moves too. `doc-staleness-check.sh` will fail
  otherwise.
- BACKLOG 0(b)'s hyphenate-character line is rewritten from §9.

**Overlap check:**
- `web-out-of-flow-hyphen-box` (sibling brief) works in the same `css-text/hyphens` section but on web-runtime files.
  It shares the probe section, not files.
- `StyleBuilder.swift` must not be claimed by another lane this wave.

## 8. Geometry probe — `hyphenate-character.geometry.py` (written, run)

**What it checks.** One line per (test ∈ 001/003/004, platform ∈ ref/web/ios/android), ending in `→ GEOMETRY OK` or
`→ GEOMETRY WRONG (<why>)`. The why carries up to three named reasons:
- `lines:`: per-group line counts against the ref;
- `offset:`: a line top more than 4 px off;
- `glyph:`: the line's right edge (±3 px) or the ink height of its last 5-px cell (±2 px). This tells "" from `-` from
  `•` from `/-/`.

**How it measures.** Lines are segmented on glyph cores (max-channel < 110). The end cell is measured at < 200, which
keeps Android's grey hyphen (wave-53 L2 skeptic D1). The preamble is not read.

**Self-check.** Each ref row must print OK (exit 1 otherwise). `--replay <tag>` reads the planning replays.

**Proven able to fail on each axis** (`.geometry.replays.txt`):

| arm | output |
|---|---|
| wave53-final | 9/9 WRONG |
| GB, GBd | 9/9 OK |
| G | `offset:` only, all 9 |
| B | `glyph:` on every native row and on web -001/-003; web -004 OK |

**Limitation.** The ±4 px tolerance does not catch the 1–3 px drift of §4.E. That is a score effect, not a wrong
picture.

## 9. Predictions (wave53-final → the wave-54 closing gate)

**Replay basis** (`.replay.out.txt`, gate metric). Arms:
- `G` = string fixed, B and W left;
- `GB` = string and br fixed;
- `GBd` = GB plus the measured drift;
- `GBn` = the iOS risk arm for U3b;
- `B` = the br fix alone, on the platform's own pixels.

Calibration: web -004's actual capture (glyph right) is 0.9249 where its `G` model says 0.9307. Every number below
therefore subtracts **0.006** raster allowance from a ref-pixel model.

**Core = U1 + U2 + U3:**

| cell | wave53-final | → predicted | tier | floor |
|---|---|---|---|---|
| hyphenate-character-001 ios | f 0.9287 | **P ≈ 0.979** (GBd 0.9852) | MED-HIGH | ≥ 0.965 |
| hyphenate-character-003 ios | f 0.9337 | **P ≈ 0.977** (GBd 0.9831) | MED-HIGH | ≥ 0.965 |
| hyphenate-character-004 ios | f 0.9134 | **P ≈ 0.979** (GBd 0.9855) | MED (needs the spent-hyphen hunk, or TextKit re-wraps `tial/-/`) | — |
| hyphenate-character-001 web | f 0.935 | P ≈ 0.960 (GBd 0.9665) | MED-LOW | — |
| hyphenate-character-003 web | f 0.8812 | P ≈ 0.956 (GBd 0.9617) | MED-LOW | — |
| hyphenate-character-004 web | f 0.9249 | 0.947–0.966 (GBd 0.9534; B on own pixels 0.966) | LOW (coin flip) | — |
| hyphenate-character-001 android | f 0.9301 | ≈ 0.957 (GBd 0.9627) | LOW-MED | — |
| hyphenate-character-003 android | f 0.9292 | ≈ 0.953 (GBd 0.9589) | LOW | — |
| hyphenate-character-004 android | f 0.905 | ≈ 0.945 (GBd 0.9509), stays f | LOW | — |
| hyphenate-character-002 web | f 0.92 | ≈ 0.96 (Chromium's en breaks equal the shys; B alone 0.9426) | LOW-MED | — |
| hyphenate-character-002 ios / android | f 0.9261 / f 0.9223 | mover / stays f (Android: Minikin paints its own hyphen — wall, logged) | LOW | — |

**+U3b (A/B arm on the probe):**
- web / android 001, 003, 004 → ≈ 0.976–0.981 (GB − 0.006), MED.
- iOS **risk**: if its compensation stops, iOS falls to ≈ 0.947–0.958 (GBn 0.9526–0.9637 − 0.006). The probe decides.
  Keep U3b only if no iOS target loses.

**U3-radius movers (P today; expected up):**
- block-ellipsis-002 web P 0.9868 → ≈ 1.0 and iOS P 0.9813 → ≈ 0.993 (`.replay-b-radius.out.txt`), MED-HIGH / MED.
  Android P 0.9884 → identical or up (only two lines painted, the clamp interplay is unknown), LOW.
- block-ellipsis-004 / -005 / -006 web P 0.9737 / 0.9735 / 0.9735 → ≈ 0.998, MED-HIGH. iOS and Android show no stray
  gap, so they are expected byte-identical (MED).
- backdrop-filter-clip-rect / -edge-clipping / -paint-order:
  - web P 0.959 / 0.9595 / 0.9595 and iOS P 0.956 / 0.9578 / 0.9578 → up, MED;
  - Android f 0.9315 / 0.929 / 0.929 → up, a flip is possible, LOW.
- f movers, LOW: clip-path-filter-order, balance-grid-container, column-height-009.

**Must not move (byte-identical, or decoded-pixel identical across the host):**
- hyphenate-character-005: web P 0.974, ios f 0.9428, android f 0.9474. The wire changes under U1, but `white-space: pre`
  means no break is taken.
- revert-layer-006, revert-val-001, revert-val-002: P 0.999 / 0.9974 / 0.9967 on web / iOS / Android. Their wire changes
  under U3; the pixels are invariant (§6). HIGH.
- backdrop-filter-plus-filter: web P 1, android P 0.9709, ios f 0.9499. The wire changes; the trailing br has nothing
  below it in flow. MED.
- hyphenate-limit-chars-001: web f 0.9068 and android f 0.8581 identical; ios f 0.8919 is a LOW mover (U+2010 →
  U+002D in the dictionary fold).
- The U2 default-argument population. These cells are all android / ios:

  | test (css-text/hyphens/…) | android | ios |
  |---|---|---|
  | hyphens-manual-010 | 0.9869 | 0.9874 |
  | hyphens-manual-011 | 0.9952 | 0.9951 |
  | hyphens-manual-012 | 0.9952 | 0.9951 |
  | hyphens-manual-013 | 0.9945 | 0.994 |
  | hyphens-manual-inline-010 | 0.9819 | 0.9833 |
  | hyphens-manual-inline-011 | 0.9927 | 0.993 |
  | hyphens-manual-inline-012 | 0.9803 | 0.9905 |
  | hyphens-none-011 | 0.9869 | 0.9874 |
  | hyphens-none-012 | 0.978 | 0.9913 |
  | hyphens-none-013 | 0.979 | 0.9926 |
  | hyphens-span-001 | 0.9937 | 0.9968 |
  | hyphens-span-002 | 0.9943 | 0.9971 |
  | hyphens-out-of-flow-001 | 0.9943 | 0.9971 |
  | hyphens-out-of-flow-002 | 0.9943 | 0.9971 |
  | hyphens-auto-control | 0.9732 | 0.9731 |
  | hyphens-vertical-001 | f 0.8819 | f 0.8801 |

  Also in this population: css-overflow block-ellipsis-014 (0.9736 / 0.9793) and block-ellipsis-028 (0.9855 / 0.9913).
- `filter-effects/backdrop-filter-basic-blur`: not a carrier. Report plainly if it moves.
- Web: everything outside the U1 wire carriers and the U3 radius.

**DEGENERATE-by-construction, honestly.**
- **Wave 53.** The label was right: U+2010 was baked on every platform, so any flip carried the wrong glyph.
- **After U1 + U2** the glyph is right by construction. Whether a flip is faithful is then decided per cell by the
  probe's `glyph:` axis, not by a blanket label. The residuals are drift (score only) and the lost `<b>` (the corpus
  convention: 357 tests, 955 passing cells carry it). Neither makes a pass degenerate.
- **The label STILL applies if U3 lands without U1 + U2.** Replay `B`:
  - -003 ios → **0.9566 with hyphens where the ref has bullets**, a degenerate pass;
  - -001 web → 0.9496, borderline;
  - -003 android → 0.9495, borderline.
- **So:** drop `degenerateByConstruction` for 001/003/004 only when U1 + U2 + U3 land together, and require
  `GEOMETRY OK` before any of these cells is written as a gain.
- **Web -004 is not degenerate under U3 alone.** Its glyph is already right (B replay 0.966, probe OK).

## 10. Verification pins (each with the mutation that must turn it red)

**Converter (`HyphenateCharacterPropertyParserTest`)** — mutations: drop the decoding (the `\2022` pin goes red);
restore `isEmpty() → null` (the `""` pin goes red).

| input | expected |
|---|---|
| `""` | `String("")` |
| `"\2022"` | `"•"` |
| `"\00a0\0640"` | `" ـ"` |
| `"\2022 x"` | `"•x"` (one whitespace after a hex escape is consumed) |
| `'/-/'` | `"/-/"` |
| `auto` | `Auto` |

**Compose `PreBreakPipelineTest`** (mono measurer, CH = 19.2; inputs verbatim from the per-test IR runs):

| # | input | width / string | expected | mutation that turns it red |
|---|---|---|---|---|
| a | `im­ple­men­ta­tion` | 4.5·CH, `""` | fired, `"im\nple\nmen\nta\ntion"` | restore character-counting → identity |
| b | ` re­al­iza­tion` | 4.5·CH, `""` | `"real\niza\ntion"` | — (the width case: U+2010 gives `re‐/al‐/iza‐/tion`) |
| c | piece 1 | 5.5·CH, `"•"` | `"im•\nple•\nmen•\nta•\ntion"` | — |
| d | ` ini­tial­iza­tion` | 6.5·CH, `"/-/"` | `"ini/-/\ntial/-/\niza/-/\ntion"` (line 2 overflows) | — |
| e | default argument | — | every existing `assertSame` identity pin stays green | — |

**Compose Extractor:**
- `{"type":"string","value":""}` → `""`;
- `{"type":"auto"}` → null;
- `/-/` verbatim. Mutation: lowercase or keyword path.

**iOS:**
- `GreedyLineBreaker.lines(" ini\u{AD}tial\u{AD}iza\u{AD}tion", 6.5·CH, hyphenChar: "/-/")` → 4 lines;
- `hasUnbreakableOverflowingLine(["tial/-/"], 6.5·CH, spentHyphen: "/-/") == true` and `== false` with nil
  (mutation: drop the strip);
- Extractor on the object shape → `"/-/"`.
- The seam patch must apply clean and keep the pins green.

**Seam 3 (node --test):**

| fixture | expected br heights |
|---|---|
| verbatim 001 `<div>` | `[0,20,0,20,0,20]` (today `[0,20,20,20,20,20]`) |
| revert-val-001 | all 0 |
| B-RC1's five body-level brs | 20s, unchanged |
| empty-span-001 | unchanged |
| U3b: 001 | `[0,19.2,0,19.2,0,19.2]` |

Mutation: drop the re-arm.

**Wire census after seam 3:** exactly 54 brs in 18 docs change (U3), plus 12 in 4 (U3b). Any other wire diff is a leak.

**Suites, after the gate releases the host:**
- `./gradlew :converter:test`;
- the focused Compose `--tests '*PreBreakPipelineTest' '*GreedyLineBreakerTest' '*HyphenateCharacter*'`, then the full
  runtime suite;
- Catalyst `GreedyLineBreakerTests`, `TypographyTests`, then the full suite;
- `node --test tools/titan/*.test.mjs`;
- `node schema/conformance/run.mjs --emit`.

**Device probe before the closing gate:** sections css-text, css-overflow, filter-effects and css-cascade.
- A/B arms: drop-U1, drop-U2-compose, drop-U2-ios, drop-U3, ±U3b.
- Read with `hyphenate-character.geometry.py <probe-run>` and `score-gate.mjs`.

## 11. Risks and recommendation

**Risks:**
1. **Drift caps web/Android (MED).** Without U3b, web and Android land 0.945–0.967: coin flips. U3b fixes them in the
   replay, but may cost iOS its zero-drift compensation (GBn). Only the device probe can choose.
2. **iOS re-wrap of a line that overflows by its own hyphen string (MED).** If the spent-hyphen hunk is dropped, -004
   ios stays f, with `tial/-` / `/`-style lines.
3. **U+2022 face (LOW-MED).** If the pinned monospace lacks U+2022, the fallback advance enters the fit. Measure and
   paint share it, but the breaks could differ from the ref.
4. **U3 is a corpus-wide extractor change (MED).** 18 docs and 29 passing cells.
   - The replays and pictures all move toward the ref.
   - Android block-ellipsis-002 is unknown.
   - The backdrop-filter cells sit at 0.956–0.96, so watch them.
5. **Sequencing (HIGH impact).** U3 without U1 + U2 manufactures a degenerate -003 ios pass (§9).
6. **Out of scope, stated.**
   - The lost `<b>` (W).
   - -002 android: a Minikin dictionary-hyphen wall, logged.
   - -005 natives: the bidi bake would have to bake ` ـ` into the run.
   - Generalising the iOS spent-hyphen rule to U+2010.
   - `TextStyleApplier.extractHyphenateCharacter`'s dead DEFAULT U+00AD.
7. **Docs drift (LOW).** Android `real` 15 → 16 and the converter test count must be updated, or the doc-staleness CI
   job goes red.

**Recommendation: GO** — one lane, effort M, units U1 + U2-Compose + U2-iOS + U3, with U3b as a pre-registered,
probe-decided A/B arm.
- **What it delivers:**
  - The property honoured end-to-end: converter, web via the wire, both natives (css-text-4 §6.3).
  - The child-scope br-height defect fixed: 18 docs, all moving toward the ref.
  - Three MED-HIGH faithful iOS flips, plus two to six MED-LOW web / Android flips depending on U3b.
- **What it costs:** every unit is pinnable on verbatim wire with a mutation, and the probe separates glyph from
  geometry.
- **GO-SMALL**, if the orchestrator wants the radius small: U1 + U2 only. Flips: zero (the best replay is 0.9474). It
  ships the correct glyph and removes the degenerate-by-construction trap for whoever lands U3 later.
- **Not recommended:** U3 alone, which yields the degenerate -003 ios pass.
