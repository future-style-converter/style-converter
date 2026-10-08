# web-out-of-flow-hyphen-box — wave-54 family brief (BACKLOG obligation 0(c))

Evidence run: the gate of record `tools/titan/runs/wave53-final` (dev tip 7cce3b22). Every score below comes from
`node tools/titan/results/wave52-gate/cells.mjs` over the named runs. Every PNG named below was opened, and its box and ink
extents were measured with PIL (orange = r>200, 120<g<200, b<80; ink = every channel < 200, the wave-53 L2 skeptic-D1
rule). Nothing was built or run on a device, simulator or browser, because the wave54-open gate was live. The executed
look-ups are committed beside this file:

| file | what it is |
|---|---|
| `web-out-of-flow-hyphen-box.census.mjs` → `.census.json`, `.census.out.txt` | the corpus census (§6) |
| `web-out-of-flow-hyphen-box.geometry.py` → `.geometry.out.txt` | the family's geometry probe (§8) |
| `web-out-of-flow-hyphen-box.replay.py`, `.replay-score.mjs` → `.replay.out.txt`, `web-out-of-flow-hyphen-box-replay/*.png` | the composited replay of the fix's picture, scored with the gate's own `diffWebVsRef` |
| `web-out-of-flow-hyphen-box.cf-hyphenation.py` → `.cf-hyphenation.out.txt` | macOS CoreFoundation hyphenation of the split word (§4) |
| `web-out-of-flow-hyphen-box.wptfyi.json` | wpt.fyi results for the four hyphens span / out-of-flow tests, aligned runs at WPT 30d4d7d411, fetched 2026-10-08 |

**Summary.**
- **The defect.** On the web, boxes **4 and 5** of `hyphens-out-of-flow-002` (both `high<span abspos>abspos</span>way`)
  render as one 26-px line `highway` that overflows the right border. Box 4 is not the only one: the wave-53 probe stops
  at its first wrong box. Boxes 6 and 7 then sit 40 px above the ref. The natives are right.
- **What the web runtime does.** It renders the wire's `meta.runs` faithfully: the text piece `high`, then the abspos
  member at its run slot, then the text piece `way`. In the harness each piece is wrapped in its own `<span>`.
- **Why the box collapses.** Once the out-of-flow member sits inside the word, the hyphenator on this host never sees a
  word with the `high|way` point. CoreFoundation was asked both ways a line breaker can ask (each fragment alone, or the
  whole run with U+FFFC where the member sits). Both answers reproduce the capture box by box: boxes 2, 3, 6 and 7 still
  hyphenate, boxes 4 and 5 get no point, and `highway` (63.68 px) does not fit 6ch (60.56 px).
- **The fix (W1).** It is the web twin of wave-53 L2 F2. A **paint-inert** out-of-flow member that splits a word under
  `hyphens: auto` is rendered after the joined word, not inside it. That is box 7's runs shape, and box 7 is right in the
  same capture.
- **Measured outcome.** The replay scores **1** (0 mismatched px). The census reach is 4 hosts, all in this one test.
  No seam and no native file is touched.

## 1. Target cells

| cell | platform | wave52-ship | wave53-open | wave53-probe | **wave53-final** |
|---|---|---|---|---|---|
| css-text/hyphens/hyphens-out-of-flow-002 | **web** | f 0.9411 | f 0.9411 | f 0.9411 | **f 0.9411** |

- **It has never moved.** `cells.mjs 'hyphens-out-of-flow-002 web'` gives f 0.9411 on all 13 runs checked:
  wave35-webmap, wave47-final, wave48-final, wave49-final, wave50-final, wave51-open, wave51-fix, wave52-open,
  wave52-final, wave52-ship, wave53-open, wave53-probe and wave53-final. The defect predates every lane since wave 35.
- **Context only, not targets (must not move; §7):**
  - `hyphens-out-of-flow-002`: ios P 0.9971, android P 0.9943 (`wave53-final`). Android became faithful with wave-53
    L2 F2: it was P 0.982 at wave53-open and DEGENERATE.
  - `hyphens-out-of-flow-001`: web P 1, ios P 0.9971, android P 0.9943.
  - The anchor `hyphens-span-002`: web P 1, ios P 0.9971, android P 0.9943.
- **The browsers disagree on this test** (`web-out-of-flow-hyphen-box.wptfyi.json`; aligned runs at WPT 30d4d7d411):

  | browser | `hyphens-out-of-flow-002` | `hyphens-span-002` |
  |---|---|---|
  | Edge 157 (Windows) | P | P |
  | Safari 253 preview (macOS) | P | F |
  | Firefox 159a1 (Linux) | F | P |
  | Chrome 157 canary (Linux) | F | F |

  Chrome on Linux also fails span-002, which needs only the dictionary point, so that run says nothing about the
  out-of-flow member. No macOS Chrome run exists on wpt.fyi.

## 2. The picture (390×600; every box x 21..87 → content box x 24..84 = 61 px ≈ 6ch)

Measured on `tools/titan/runs/wave53-final/sections/css-text/{screenshots,ios-screenshots,android-screenshots}/wpt__css-text__hyphens__hyphens-out-of-flow-002.png`
and the ref `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text/hyphens__hyphens-out-of-flow-002.png`.

- **Ref.** Seven boxes at y 108, 159, 210, 261, 312, 363 and 414. Each is 46 px (3 + 2×20 + 3) on a 51-px pitch. Line 1
  `high‐` has ink x 25..61 and line 2 `way` has ink x 24..54.
- **Web.**
  - Boxes 1, 2 and 3 (y 108/159/210) and boxes 6 and 7 are 46 px and read `high‐` / `way` with exactly the ref's ink
    (x25–61 / x24–54).
  - **Boxes 4 and 5 are 26 px** (y 261..286 and 292..317). Each holds ONE line, `highway`, with ink **x25–86**. The `y`
    paints over the right border (content edge x84, border x85–87).
  - So box 6 sits at y323 and box 7 at y374, **40 px above** the ref's y363 / y414. That shift is most of the SSIM loss:
    `diffWebVsRef` divergentPx 5395, refDroppedPct 0.8457.
- **iOS and Android.** All seven boxes are 46 px with `high‐` / `way` (ink right edges x61 / x53).
- **Web boxes 3 and 6 are right, but not identical to box 7** (pixel diff of the 46-px crops). Box 3 differs at x56–63,
  y+13..15: the hyphen. Box 6 differs at x46–55, y+30..43: `way`. In both, the separately shaped fragment has its own
  sub-pixel origin. `hyphens-out-of-flow-001` web shows the same two diffs.

## 3. The wire (`wave53-final/sections/css-text/per-test-ir/wpt__css-text__hyphens__hyphens-out-of-flow-002.json`)

- **Hosts.** Every box is a host with `Width {"type":"length","original":{"v":6,"u":"CH"}}`, `Hyphens "AUTO"`, orange
  3-px borders, 5-px margins and `meta.lang "en"`. No `Display` (a block `div`) and no float anywhere in the document.
- **Box 1** is a leaf `"text":"highway"`. Boxes 2–7 are runs hosts:

  | box | host | `meta.runs` |
  |---|---|---|
  | 2 | `__2-285` | `[child, "highway"]` |
  | 3 | `__3-287` | `["h", child, "ighway"]` |
  | 4 | `__4-289` | `["high", child, "way"]` |
  | 5 | `__5-291` | `["high", child, "way"]` |
  | 6 | `__6-293` | `["highwa", child, "y"]` |
  | 7 | `__7-295` | `["highway", child]` |

- **The member** (e.g. `hyphens__hyphens-out-of-flow-002__4__0-290`) is `{"sourceTag":"span","lang":"en"}`,
  `"text":"abspos"`, with properties exactly `[Position "ABSOLUTE", Color {"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}]`.
- **The wire is faithful** to the source `tools/wpt/css/css-text/hyphens/hyphens-out-of-flow-002.html`
  (`span { position: absolute; color: transparent }`, `div { width: 6ch; hyphens: auto }`, every div `lang=en`). The
  extractor is not at fault.

## 4. Mechanism (traced; file:line under 7cce3b22)

**A. What the web runtime emits for box 4.** The path, in order:

1. `NodeRenderer` (`runtimes/web/src/renderer/NodeRenderer.ts:339`) calls
   `resolveRuns(component.meta?.runs, node.children, component.id)`.
2. `resolveRuns` (`runtimes/web/src/renderer/InlineRuns.ts:60-149`) returns the entries in WIRE ORDER,
   `[text "high", child 0, text "way"]`, as spec 03 §4.1 rules 1–2 require.
3. `NodeRenderer.ts:344-350` paints each `{text}` entry through `options.renderText`, and the member through
   `renderChild` AT ITS SLOT.
4. In the harness, `HARNESS_OPTIONS.renderText` (`apps/web-harness/src/sdui/ComponentRenderer.tsx:991`) is
   `(text) => <span>{text}</span>`. The member is an inline-allowlisted `span`, so `renderEmptyContent` (`:967`) gives it
   bare text `abspos`, and `decorateProps` (`:892-894`) stamps `lang="en"`.
5. The DOM is therefore
   `<div lang="en" style="…width:6ch;hyphens:auto">` `<span>high</span>`
   `<span class="sc-…" lang="en" style="position:absolute;color:…">abspos</span>` `<span>way</span>` `</div>`.
6. Box 7 differs only in order: `<span>highway</span><span …abspos…>`.

**B. Why box 4 is one 26-px line.** The causes that are ruled out:

- **Not `hyphens` itself.** `HyphensApplier.ts:13` emits `{ hyphens: 'auto' }`, and boxes 1, 2 and 7 hyphenate in the
  same capture.
- **Not an inline-block.** The host is a block box, the pieces are inline `<span>`s, and the member is `position:
  absolute`.
- **Not a float.** The -002 wire has none; the census finds 3 floats in runs corpus-wide, none mid-word.
- **Not a word that fits.** Measured from `apps/web-harness/public/fonts/Inter-Regular.ttf` (hmtx, 16 px, GPOS kerning
  not applied): `highway` = 63.680 px and `0` = 10.094 px, so 6ch = 60.562 px. `highway` overflows by about 3.1 px and
  NEEDS a break.

**What remains: the hyphenator never sees a word with the `high|way` point.** The macOS system hyphenator
(`CFStringGetHyphenationLocationBeforeIndex`, locale `en`) was asked in
`web-out-of-flow-hyphen-box.cf-hyphenation.py`, under both ways a line breaker can hand it the split word:

| box | runs split | (1) fragments alone | (2) whole run, U+FFFC at the member | capture |
|---|---|---|---|---|
| 1 | `highway` | `high-way` (point 4) | — | right |
| 2 | `M|highway` | `highway` → point 4 | point 5 at before-index 6–8 | right |
| 3 | `h|M|ighway` | `ighway` → `igh-way` (3) | point 5 at before-index 6–8 | right |
| **4, 5** | **`high|M|way`** | **`high`: none; `way`: none** | **none at any index** | **one line, 26 px** |
| 6 | `highwa|M|y` | `highwa` → `high-wa` (4) | point 4 at before-index 5–6 | right |
| 7 | `highway|M` | `highway` → point 4 | point 4 at before-index 5–7 | right |

- **Both models reproduce the capture box by box.** A split that leaves the dictionary point inside a fragment
  (`ighway`, `highwa`) still hyphenates. The only split that puts the point exactly at the member (`high|way`) loses it.
  Then no soft wrap opportunity is left in `highway`, `overflow-wrap: normal` forbids an emergency break, the line
  overflows, and the box is 20 + 6 = 26 px.
- **The manual twin `-001` is web P 1 with the same DOM shapes.** Its U+00AD opportunities are characters inside the
  fragments. They are not dictionary look-ups, so a split cannot lose them. That is why the defect is `auto`-only.

**Which layer owns the split: NOT YET DECIDED. The lane's step 0 settles it (§5).**
- Model (2) is the leading one, and it is a guess about Chromium internals that has not been checked against Chromium
  source (none is on disk). It assumes LayoutNG keeps an out-of-flow object as U+FFFC in the text content.
- Under model (2) the raw WPT page holds the same text content as our DOM, so **host Chromium would fail the raw test
  too**. That is branch R: a Chromium-on-macOS (CoreFoundation) behaviour that the web runtime reproduces faithfully.
- Edge on Windows passes the raw test. It shares the line breaker but not the hyphenation backend, which is consistent
  with model (2).
- If instead the raw page passes on the host, the harness's per-piece `<span>` wrapper is the trigger (branch H). Our DOM
  differs from the raw page only by those wrappers and the member's `lang` / `class` / inline `style`.

## 5. The fix

**Rejected: W2, bare text nodes for every runs piece** (drop the harness `renderText` `<span>` for runs). Its reach is
**264 runs hosts in 129 tests** (census `w2Reach`), so every composed web runs host would change. It only helps under
branch H. And the `<span>` is a deliberate harness calibration (`ComponentRenderer.tsx:987-991`, the swarm-002 Bug 1
shape). This is not a one-cell change.

**Rejected: W3, drop the member** (Compose F2 parity). The DOM would lose the element: its accessible text, and any
`getBoundingClientRect` a consumer takes. Moving it is enough, and costs less.

**Chosen: W1, keep the word whole and move the paint-inert out-of-flow member to the word's end.**

- **Owned files: a new pure module `runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts` (≤ 100 lines), used by
  `InlineRuns.ts`.** It holds two functions.
  - `isInertOutOfFlowMember(node: ComposedNode)` is a byte-for-byte restatement of Compose's
    `InertOutOfFlowMember.admits`
    (`runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt:89`). It is
    true only when all of these hold:
    - the member has no children, no `meta.runs` and no `meta.decorations`;
    - it has a `Position` `ABSOLUTE`|`FIXED`;
    - it has a `Color` whose `srgb.a` is 0;
    - it has no other property type except `Hyphens`.
  - `joinWordsAroundInertOutOfFlow(entries, children, hyphensAuto)` rewrites each `text P, child M, text N` into
    `text P+N, child M`, but only when all of these hold:
    - `hyphensAuto` is true;
    - M passes `isInertOutOfFlowMember`;
    - P ends with a non-whitespace character and N starts with one (mid-word).

    It works left to right, so several members in one word all move after it in their original order. It returns the
    count joined.
- **`InlineRuns.ts` change.**
  - `resolveRuns` gains an optional fourth argument `{ hyphensAuto?: boolean }` and applies the join after validation,
    before `unreferenced` is computed. The member stays `claimed`, so rule 4 cannot paint it twice.
  - `RunsPlacement` gains `joinedOutOfFlowMembers: number` (default 0). It is the web twin of Compose
    `Folded.droppedOutOfFlowMembers`: the deviation from spec 03 §4.1 rule 1 is counted, never silent.
- **`NodeRenderer.ts:339` change, one line.** Pass `{ hyphensAuto: styles.hyphens === 'auto' }`.
  - Only the host's OWN value is used. `hyphens` is inherited (css-text-3 §5.4), but the census finds **0** reached
    members whose `auto` is inherited (all 4 are own).
  - This is a recorded reach limit, noted in the code comment and the BACKLOG. Threading the inherited value
    (wave-52 L6's `inheritedListStyleType` pattern) would grow a 432-line file. If the builder threads it anyway, the
    census already proves it is a no-op on the corpus.
- **The result.** Boxes 3–6 become `["highway", child]`, box 7's runs, which renders right in the same capture. The
  member's DOM node and accessible text survive. Its static position moves from after `high` to after `highway`, and it
  paints nothing, by the predicate.
- **Spec.**
  - css-text-3 §5.1 (line-breaking details): out-of-flow elements do not introduce soft wrap opportunities. The test's
    assert is that their presence "has no effect on automatic hyphenation".
  - css-text-3 §5.4: `auto` hyphenates at the dictionary point.
  - CSS 2.1 §10.3.7 / §10.6.4: only the static position changes, and it paints nothing.
- **Ungated fallback (W1-u)**, if another wave-54 lane owns `NodeRenderer.ts`. Drop the `hyphensAuto` gate. Then
  NodeRenderer needs no edit, because `resolveRuns` already has `children`. The reach grows to 8 members in 2 tests: it
  adds `-001` boxes 3–6, all `manual`. The replay `web-001-W1-exact.png` scores `-001` web **1**. The cost is that
  `-001` web joins the control-check carrier set.

**Step 0 (the lane runs it before writing code, after the gate releases the host; web only, about 1 minute).**
- **Setup.** A CDP probe in the lane dir, using the web-harness's puppeteer Chromium (`puppeteer ^25.4.0`). The page has
  Inter embedded via `capture-browser-ref.mjs` `interFontFaceCss()`, body `font-family: REF_FONT_STACK` and
  `line-height: 1.25`, at a 390-px viewport.
- **Variants.** Each is a `div lang=en` with a 3-px orange border, `width: 6ch` and `hyphens: auto`. Read
  `getBoundingClientRect().height` for each:
  - V0: raw markup `high<span abs>abspos</span>way`
  - V1: the harness shape `<span>high</span><span abs lang=en>abspos</span><span>way</span>`
  - V2: the package-default shape `high<span abs lang=en class style>abspos</span>way`
  - V3: the W1 shape `<span>highway</span><span abs>abspos</span>`
  - V4: `<span>high</span><span>way</span>`
  - V5: `highway`
  - V6: the raw WPT file `tools/wpt/css/css-text/hyphens/hyphens-out-of-flow-002.html` with the same font injection (all
    7 divs)
- **Pre-registered.** V1 = 26 (the capture), V3 = 46 and V5 = 46 (box 7 and box 1 of the capture). **Prediction, MED:
  V6 boxes 4 and 5 = 26 and V0 = 26 (branch R, model 2).**
- **Decision:**

  | step-0 result | branch | the lane does |
  |---|---|---|
  | V6 / V0 = 26 | **R**: Chromium-on-macOS fails the WPT test itself | W1 in the runtime, as above. A product-level workaround with precedent: wave-52 L6's `bakedMarkerPlan` for Chrome's own `::marker`. Record it as a platform behaviour in the BACKLOG. |
  | V6 = V0 = 46, V2 = 46, V1 = 26 | **H**: harness-induced; the runtime's own DOM is right | NOT W1. A seam patch to `apps/web-harness/src/sdui/ComponentRenderer.tsx` `renderText` instead (reach = the same 4 hosts): return the bare string when the host's runs carry a mid-word inert out-of-flow member. The general wrapper question (W2) goes to the BACKLOG. The prediction then rests on V2. |
  | V6 = 46, V2 = 26 | **H2**: an attribute or inline style of our member differs | Bisect V2 → V0 one attribute at a time. W1 still fixes it (V3), so land W1 and record the bisect result. |
  | V3 ≠ 46 | the premise is false | STOP. Re-plan. |

## 6. Blast radius (census: `web-out-of-flow-hyphen-box.census.mjs wave53-final`)

**The corpus.** 1435 per-test IR docs, 264 runs hosts in 129 tests, 521 non-empty text entries. **24 out-of-flow
members sit inside runs:** 5 abspos spaced, 3 float spaced, 8 abspos word-edge, 8 abspos mid-word.
- **12 are paint-inert** by the Compose predicate, all of them in `hyphens-out-of-flow-001` / `-002`. That matches
  wave-53 L2's F2 census.
- **Mid-word children under an effective `hyphens: auto`, of ANY kind: 4.** All 4 are -002 boxes 3, 4, 5 and 6. No
  other test in the corpus splits an `auto` word with any element.

| shape | reach | tests |
|---|---|---|
| **W1 (chosen; `auto` gate)** | **4 members**: hosts `…-002__3-287`, `__4-289`, `__5-291`, `__6-293` | **1**: css-text/hyphens/hyphens-out-of-flow-002 |
| W1-u (ungated fallback) | 8 members | 2: `-001`, `-002` |
| W2 (bare text for all runs; rejected) | 264 runs hosts | 129 |

**Rows the predicate refuses (byte-identical by construction; every one is web P except one):**

| test | split | web cell (wave53-final) | why W1 refuses it |
|---|---|---|---|
| CSS2/abspos/between-float-and-text | `"  "` / M / `" "`, and a float | P 0.999 | spaced; not inert |
| CSS2/abspos/hypothetical-inline-alone-on-second-line | `" "` / `<span>` M | P 1 | spaced; visible text |
| CSS2/abspos/static-inside-inline-001 / -002 / -003 | M / `" X"` | P 0.999 / P 0.963 / P 0.999 | spaced; not inert |
| css-cascade/all-prop-001 | float `<bdo>` / `" 321"` | P 0.9689 | a float |
| css-position/position-absolute-semi-replaced-stretch-other | `<label>` M / `"label"` | f 0.941 | word-edge; not inert |
| css-pseudo/first-letter-list-item-dynamic-001 | `"X"` / M, and a float | P 0.9999 | word-edge; not inert |
| css-tables/abspos-container-change-dynamic-001 | `"B"` / M | P 1 | word-edge; not inert |
| css-values/ch-unit-001 | `<span>` M / `"00000"` | P 0.9779 | word-edge; not inert |
| css-text/hyphens/hyphens-out-of-flow-001 (×6) | `h|M|igh­way` … | P 1 | `manual` (W1 gate) |
| -002 boxes 2 and 7 | M / `highway`, `highway` / M | (target) | word-edge: already box 7's shape |

**Carrier sets** (the stems the control-check may see change; every other capture must be byte-identical):
- web: `wpt__css-text__hyphens__hyphens-out-of-flow-002` (W1-u adds `…hyphens-out-of-flow-001`);
- ios: none; android: none.

The lane touches no Swift or Kotlin file. The schema/conformance golden `schema/conformance/fixtures/v2/inline-runs.json`
has one abspos run member, `runs-abspos-005`, but it is spaced and has a `Width` / `Height`, so it is not inert. W1
changes no wire byte: it is not a freeze event.

## 7. Predictions (wave53-final → the wave-54 closing gate)

**Target:**
- **css-text/hyphens/hyphens-out-of-flow-002 web: f 0.9411 → P 1 (HIGH; floor P 0.995).**
  - The basis is the replay `web-out-of-flow-hyphen-box-replay/web-W1-exact.png`. It is the wave53-final web capture
    with boxes 3–6 replaced by web box 7's 51-px block, and boxes 6–7 restored to the ref pitch.
  - It scores `diffWebVsRef` **ssim 1, pixelMismatchedCount 0** (fuzzyMaxChannelDelta 27; degenerate
    unexplainedInkPct 0.0064).
  - Sanity check: the same script reproduces the gate's 0.9411 / 0.9971 / 0.9943 / 1 on the four real captures
    (`web-out-of-flow-hyphen-box.replay.out.txt`).
  - Geometry: the -002 web row of `web-out-of-flow-hyphen-box.geometry.py` goes from
    `GEOMETRY WRONG (boxes 4,5 height 26 vs ref 46)` → `GEOMETRY OK`. The replay row `web*` already prints OK.
  - Under branch H the same cell and floor hold with confidence **MED-HIGH**: that picture rests on the V2 measurement
    instead of on box 7.

**Must not move (byte-identical captures):**
- **The natives:** `hyphens-out-of-flow-002` ios P 0.9971 / android P 0.9943; `hyphens-out-of-flow-001` ios P 0.9971 /
  android P 0.9943. No native file is touched.
- **`hyphens-out-of-flow-001` web P 1.** Not reached under the `auto` gate. Under W1-u it is a carrier, P 1 → P 1
  (HIGH; replay `web-001-W1-exact.png` = 1).
- **The anchor** `hyphens-span-002` web P 1; `hyphens-auto-control` web P 1; `hyphens-auto-inline-010` web P 0.9992.
- **The 10 other tests whose 12 out-of-flow run members W1 refuses, on web** (§6 table): between-float-and-text P 0.999,
  hypothetical-inline-alone-on-second-line P 1, static-inside-inline-001 / -002 / -003 P 0.999 / P 0.963 / P 0.999,
  all-prop-001 P 0.9689, position-absolute-semi-replaced-stretch-other f 0.941, first-letter-list-item-dynamic-001
  P 0.9999, abspos-container-change-dynamic-001 P 1, ch-unit-001 P 0.9779. All read on wave53-open and wave53-final
  alike.
- **Every other web capture.**

**Net: +1 web, 0 lost, 0 movers.**

## 8. Verification plan

**Geometry oracle (written, run, committed): `web-out-of-flow-hyphen-box.geometry.py [run] [--png <picture>]`.**
- **What it checks.** It reports EVERY wrong box, not the first one. The wave-53 `soft-hyphen.geometry.py` stops at box 4,
  so a one-box half-fix would read as a new failure on box 5, not as a regression. It also carries the must-not-move rows
  (-001 ×4, -002 natives) and the self-check anchor `hyphens-span-002` ×4. If a ref row or an anchor row is ever WRONG,
  it exits 1.
- **Today** (`.geometry.out.txt`, wave53-final + `--png web-W1-exact.png`): `hyphens/hyphens-out-of-flow-002 web … →
  GEOMETRY WRONG (boxes 4,5 height 26 vs ref 46)`. Every other row, and the replay row `web*`, prints `→ GEOMETRY OK`.
  Exit 0.
- **The line the closing gate must print:**
  `hyphens/hyphens-out-of-flow-002 web      heights [46, 46, 46, 46, 46, 46, 46] ink-right [(61, 54), (61, 54), (61, 54), (61, 54), (61, 54), (61, 54), (61, 54)] → GEOMETRY OK`.
  Every other row must keep printing OK.

**vitest pins** (new `runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx`). Every input is the verbatim
wave53-final wire.

| # | input → expected | mutation that turns it red |
|---|---|---|
| a | -002 host `__4-289` runs + member `__4__0-290`, `hyphensAuto` → entries `[text "highway", child]`, `joinedOutOfFlowMembers 1` | delete the join |
| b | -002 `__3-287` (`h|M|ighway`) and `__6-293` (`highwa|M|y`) → `[text "highway", child]` | require the dictionary point at the split (an over-narrow "fix boxes 4/5 only") |
| c | -002 `__2-285` / `__7-295` (word-edge) → plan unchanged, 0 joined | drop the mid-word test |
| d | -001 `__4` (`high|M|­way`, `hyphensAuto` false) → unchanged (W1-u: joined) | drop the `auto` gate |
| e | -002 `__4` with the member's `Color` alpha 1, or with an added `BackgroundColor` → unchanged | widen the predicate to "any abspos" |
| f | `renderToStaticMarkup(<NodeRenderer node={box4}/>)`, package default → `…>highway<span …>abspos</span></div>`, one member element, no `high` text node | paint the member at its wire slot |
| g | `isInertOutOfFlowMember` over the 24 census members (`.census.json`) → the same 12 trues as the Compose predicate | any predicate drift from `InertOutOfFlowMember.kt` |

The existing `InlineRuns.test.tsx` pins (wire order, bare text node, rule 2 / rule 4 / rule 5, byte-identity without
`runs`) must stay green unchanged.

**Suites** (single-writer rule: the lane runs focused suites, then the orchestrator runs ONE sweep):
`npm -w runtimes/web run test -- InertOutOfFlowWordJoin InlineRuns`, then the full `npm -w runtimes/web run test` and
`npm -w apps/web-harness run test`. The RendererParity golden has no runs host with an inert member, so it must stay
byte-identical.

**Closing gate:**
- **Probe section:** `css-text`, web column only. Every carrier and every hyphens must-not-move cell is in it. The 10
  other refused tests sit in `CSS2`, `css-cascade`, `css-position`, `css-pseudo`, `css-tables` and `css-values`; the control-check
  covers them.
- **Control-check:** the web carrier set above; the native sets are empty.
- **Device A/B arm:** drop W1, so the flip is attributed. The cell must return to f 0.9411.

## 9. Risks and recommendation

**Risks:**

1. **Layer (MED).** Step 0 can show branch H. The plan pre-registers it: then the fix is a 4-host harness seam patch, not
   W1. The cell and the picture are the same either way, but the DOM change lands in a different file.
2. **Spec 03 §4.1 rules 1–2 (LOW, a doc debt).** Under W1 a reader paints a run member out of its wire slot. Compose F2
   already does the stronger version (it drops the member), and neither is noted in `schema/spec/03-children.md`. No wire
   byte changes, so this is not a freeze event. Queue a one-paragraph renderer-latitude note covering both runtimes for
   the docs lane.
3. **Accessibility and static position (LOW, stated loss).** The member's accessible text `abspos` moves after the
   word, and its static position moves with it. Pixels cannot show this: the predicate requires zero ink and out-of-flow.
   A consumer reading the member's rect sees a different position.
4. **Inherited `hyphens: auto` not reached (LOW).** The gate reads the host's own value. 0 corpus members are affected;
   the limit is recorded in the code and the BACKLOG.
5. **Chromium drift (LOW).** If the host's puppeteer Chromium changes its out-of-flow / hyphenation handling, W1 becomes
   dead but stays harmless: box 7's shape renders right whatever the line breaker does with the member.
6. **Ring-fenced, not touched:** the harness `renderText` `<span>` wrapper (W2, 264 hosts) and the general "an element
   boundary inside an `auto` word" case (0 other corpus members). Both are BACKLOG entries, not this lane.

**Recommendation: GO-SMALL (one small web-only lane, effort XS; it can ride with another web lane).**
- It turns the last web cell of the hyphens out-of-flow pair (`-002` web f 0.9411, unchanged on all 13 runs checked) into a
  faithful pass. The replay scores 1.
- It needs one new pure module and an optional fourth argument in `InlineRuns.ts`. `NodeRenderer.ts` gets one line, or
  none under W1-u. There are no seams and no native files.
- Every behaviour is pinned on verbatim wire, with a mutation that turns each pin red. The geometry oracle is already
  written, and it fails today on exactly boxes 4 and 5.
- The census radius is 4 hosts in 1 test.
- It is "small" rather than "GO" because it is worth exactly one cell, and step 0 can move the change to the harness.
