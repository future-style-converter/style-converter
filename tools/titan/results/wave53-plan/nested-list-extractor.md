# Wave-53 brief — family `nested-list-extractor` (BACKLOG 2(f))

Paths are relative to the gate worktree `.claude/worktrees/trusting-bohr-bd6fbf`. Gate of record: `wave52-ship`
(dev tip cdb8a845). Nothing was built, captured or run on a device for this brief. The opening gate was live while it was
written. Two kinds of measurement were made: the four PNGs were decoded once each, and the extractor ran in-process
(pure node, `nice -n 19`, about 2 s) on scratch COPIES of `extract-fixture.mjs` and `counter-bake.mjs`.
Census + reproduction scripts sit beside this file: `nested-list-extractor.census.{mjs,json}`, `.wire-census.py`,
`.differential.sh` (+ `.patch-extractor.py` / `.patch-bake.py`, which apply the proposed change to scratch copies only).

## 1. Target cells

| cell | wave51-fix | wave52-calib | wave52-final | **wave52-ship** |
|---|---|---|---|---|
| `css-lists/counter-reset-reversed-nested` web | P 0.9506 | P 0.9506 | P 0.9506 | **P 0.9506** |
| `css-lists/counter-reset-reversed-nested` ios | P 0.9551 | P 0.9551 | f 0.9424 | **P 0.9508** |
| `css-lists/counter-reset-reversed-nested` android | P 0.9509 | P 0.9509 | f 0.9425 | **P 0.9509** |

All three cells pass today with the wrong picture. The wave52-final dip came from the stray native `1.`…`5.` marker,
which aaf676c5 removed (`ListItemMarkerGate`, BACKLOG 2(f) second half). That is why the iOS cell reads 0.9508 now
against its earlier 0.9551. This brief is about the first defect, which is still open.

## 2. The picture (390×600, ink = luminance < 128)

Reference (`tools/wpt/refs/9b5435e5…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-lists/counter-reset-reversed-nested.png`):
six rows on a 20 px pitch. Rows 1, 2 and 6 sit at x57 and the nested rows at x97, which is the UA `padding-inline-start: 40px`
of the inner `<ol>`. There is no vertical gap around the nested list (`ol ol { margin-block: 0 }`).

| ink band | reference | web capture | iOS capture | Android capture |
|---|---|---|---|---|
| y36-47 | `3.` x57-68 · `Three` x75-117 | `12.` x57-74 · `Three` x82-124 | `12.` x57-74 · `Three` x82-124 | `12.` x57-76 · `Three` x84-125 |
| y56-67 | `2.` x57-68 · `Two` x76-105 | `11.` x57-71 · `Two` x79-108 | **`Two` x57-86, no marker** | `11.` x57-76 · `Two` x84-113 |
| y76-87 | `11.` **x97**-111 · `Eleven` x120-166 | `10.` x57-74 · `Eleven` x83-129 | `10.` x57-74 · `Eleven` x82-129 | `10.` x57-76 · `Eleven` x85-131 |
| y96-107 | `9.` **x97**-108 · `Nine` x116-148 | `9.` x57-68 · `Nine` x76-108 | `9.` x57-67 · `Nine` x76-108 | `9.` x57-67 · `Nine` x76-107 |
| y116-130 | `8.` **x97**-108 · `Eight` x116-151 | `8.` x57-68 · `Eight` x76-111 | `8.` x57-67 · `Eight` x76-111 | `8.` x57-67 · `Eight` x76-110 |
| y136-147 | `1.` x57-65 · `One` x73-101 | **blank** | **blank** | **blank** |
| ink px | 1157 | 1045 | 915 | 861 |

All three platforms are wrong in the same three ways:
- The list is flat: rows 3-5 sit 40 px left of the reference.
- The last item (`1. One`) is missing.
- Rows 1-3 carry the wrong numbers: 12/11/10 where the reference has 3/2/11. Rows 4-5 match by coincidence.

iOS has a fourth defect: row 2 paints `Two` with no `::before`. The fix below does not touch that cause by itself
(see §4 step 5).

## 3. The wire (`tools/titan/results/wave52-gate/per-test-ir/css-lists/wpt__css-lists__counter-reset-reversed-nested.json`, sha-256 e1dd5c58… = the wave52-ship copy)

```
{"id":"counter-reset-reversed-nested__0__1-383", … "slot":{"parent":"wpt__css-lists__counter-reset-reversed-nested__0-381"},"text":"Two",
 "pseudos":{"before":{"properties":{"counter-increment":"foo -1","content":"\"11\" \". \""},"_text":"11. ",…}},
 "meta":{"sourceTag":"li","runs":[{"text":"Two "},{"child":"counter-reset-reversed-nested__0__1__0"}]}}
{"id":"counter-reset-reversed-nested__0__1__0-384", … "properties":[{"type":"CounterReset","data":[{"name":"reversed(foo)"}]}],
 "slot":{"parent":"counter-reset-reversed-nested__0__1-383"},"meta":{"sourceTag":"ol"}}          ← the nested <ol>, EMPTY
{"id":"counter-reset-reversed-nested__0__2-385", … "slot":{"parent":"wpt__css-lists__counter-reset-reversed-nested__0-381"},"text":"Eleven",
 "pseudos":{"before":{…,"_text":"10. "}}, "meta":{"sourceTag":"li","role":"ws-after"}}             ← slotted under the ROOT <ol>
```
`Nine`, which carries `CounterSet foo 10`, has `_text` `"9. "`. `Eight` has `"8. "`. Both are slotted under the root, and there
is no component for `One`.

## 4. Mechanism — traced and reproduced

The unmodified `extractFixture()` was run in-process on this test. It reproduces the wire exactly: a flat tree, `12. / 11. / 10. / 9. / 8.`, and no `One`.

1. **`tools/titan/extract-fixture.mjs` `walkChildren` (l.1716) ignores list nesting.** For `<li>Two` (an
   `AUTO_CLOSE_TRIGGERS` key, `li: new Set(['li'])` l.1954), l.1821-1833 take the FIRST `<li\b` opener after the open tag as
   the implied close. That opener is `<li>Eleven`, which sits inside the nested `<ol>`. The same-name pairing loop does find
   Two's real `</li>`, but l.1867 prefers the implicit close because it comes first. Two's body therefore becomes
   `"Two\n    <ol>\n      "`, an unclosed `<ol>` that is emitted as an empty child. The walk then resumes at `<li>Eleven`,
   one level too high, so Eleven, Nine and Eight become items of the root list.
2. **The last `<li>` is dropped by the same jump.** The walk now meets the nested list's `</ol>` at root level, and l.1792
   (`if (html.startsWith('</', i)) break;`) ends the fragment there. `</li>` and `<li>One</li>` are never read.
3. `scanOwnText` (l.3313) repeats the same first-match at l.3560-3570 (the wave-50 B4 agreement). That produces Two's
   `meta.runs` `[{text:"Two "},{child: <empty ol>}]`.
4. **Counters are computed by the counter-tree bake, not by any runtime.** `tools/titan/counter-bake.mjs` `bakeCounters` →
   `walk()` resolves every `counter()` to a string. `generated-content-bake.mjs` then writes `_text`, and every runtime paints
   that `_text` verbatim:
   - web: `runtimes/web/src/renderer/NodeRenderer.ts:272` `beforeNode`
   - Compose: the `ContentApplier.ContentWithPseudoElements` Row wrapper
   - iOS: `PseudoTextFold` folds it into `text`

   Over the flat tree the root `reversed(foo)` collects −1 from Three, Two and Eleven. The `.set` item then stops the
   §4.4.2 walk at `setCounter` l.226, giving `implied = 3 + 10 = 13`, which paints 12, 11, 10, 9, 8.
5. iOS row 2: `runtimes/swiftui/.../StyleEngine/content/PseudoTextFold.swift:65` refuses every component carrying
   `meta.runs` ("pseudo text not folded"), so Two's `11. ` is never painted. Compose paints it through the Row wrapper, and
   web paints the span.

**A second, independent defect in the bake — it would show the moment the tree is right.** With the extractor fixed, the
bake reads **3 / 2 / 10 / 9 / 8 / 1** (measured). The reference reads 3 / 2 / **11** / 9 / 8 / 1. The inner `reversed(foo)`
walk goes like this:
- Eleven's `::before` steps −1, so `num = 1`.
- The `.set` `<li>` sets 10 but does not step the counter itself (its `::before` does).
- The bake stops at `1 + 10 = 11`.

css-lists-3 §4.4.2 (the "Instantiating counters" algorithm) has two relevant steps. Step 3.3 says *"If el sets this counter
with counter-set, then add that integer value to num and break this loop"*. Step 4 then adds lastNonZeroIncrementNegated
anyway. That gives 1 + 10 + 1 = 12, so Eleven paints 11.

The bake's stop branch skips step 4. The result matched the spec on every existing pin only because each of those setters
(`<li value>`) also steps the counter itself, so its own step stood in for the step-4 term.

The natives' `lists/ListOrdinal.{kt,swift}` header already describes this term ("ADD ITS VALUE AND STOP; finally add the
last non-zero incrementNegated"), so the bake disagrees with its own twin. The bake banner's "KNOWN OUTLIER"
`li-value-reversed-019` (walk 4, ref 5) is the same omission: 1 + 3 + 1 = 5.

## 5. The fix

- **(A) Seam patch — `tools/titan/extract-fixture.mjs`.** Add one helper and call it from both trigger sites (l.1821-1833
  and l.3560-3570), so the two scans keep agreeing. The helper skips trigger openers that lie inside a list container opened
  after the `<li>`. This is HTML §13.2.6.4.7 ("in body", start tag `li`): the implied close stops at a special element other
  than address/div/p. Measured draft (in `.patch-extractor.py`):
  ```js
  const IMPLIED_CLOSE_SCOPE = { li: ['ol', 'ul', 'menu', 'dir'] };
  export function findImpliedClose(html, from, tagName, triggers) {   // depth-0 trigger, else -1
    const boundary = IMPLIED_CLOSE_SCOPE[tagName] ?? [];
    const re = new RegExp(`<(/?)(${[...new Set([...triggers, ...boundary])].join('|')})\\b`, 'gi');
    re.lastIndex = from; let depth = 0, m;
    while ((m = re.exec(html)) !== null) {
      const tag = m[2].toLowerCase();
      if (boundary.includes(tag)) { if (!m[1]) depth++; else if (depth > 0) depth--; continue; }
      if (!m[1] && depth === 0 && triggers.has(tag)) return m.index;
    }
    return -1;
  }
  ```
  When no boundary opens before the first trigger, the result is the old first match, so the change is identity by
  construction. Name the rest of the special category (blockquote, table, …) as a KNOWN GAP: the census found 0 carriers.
- **(B) `tools/titan/counter-bake.mjs`** (lane-owned).
  - `setCounter` stop branch:
    `inst.implied = inst.negSum + value + (inst.lastNegBy === who ? 0 : inst.lastNeg)`.
  - `bumpCounter` records `lastNegBy = who` on every non-zero step.
  - `who` is the walk's `nodeIdx`, or `"<nodeIdx>::<pseudo>"` inside `pseudoSet`.
  - Spec: css-lists-3 §4.4.2 steps 3.3 + 4.
  - Rewrite the KNOWN OUTLIER paragraph: the spec explains -019.
- **(C) iOS `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/content/PseudoTextFold.swift`** (lane-owned).
  - When `meta.runs` is present and `runs[0]` is a text run, fold the `::before` inline run into BOTH `runs[0].text` and
    `text`. Which one gets painted depends on which path engages, the run plan or the leading label, so both must carry it.
  - Keep the logged refusal for `runs[0]` = child, and for `::after`.
  - Spec: CSS 2.1 §12.1 / css-pseudo-4 §4.1. `::before` is the element's first inline content, and CSS 2.1 §9.2.1.1 places
    it in the anonymous block box with "Two", ahead of the block `<ol>`.
- **(D) Compose `runtimes/compose/src/main/java/com/styleconverter/runtime/content/PseudoTextFold.kt`** (lane-owned).
  - Apply the same fold.
  - Also drop `before` from the folded component's `pseudos`. `PseudoBucketExtractor.extractBeforeAfterConfig` at
    `ComponentRenderer.kt:1804` reads the folded component (`PseudoBoxFold.resolve(pseudoTextFold.component)`, l.1024), so it
    then builds no `Row { before, content }` wrapper. **No ComponentRenderer hunk is needed.**
  - Without (D), Compose would lay the content column (`Two` plus the nested list) out to the RIGHT of the `2. ` Text.
    Predicted effect: nested rows near x116 instead of x97, offset by the `2. ` advance (19 px in the reference).

The natives already handle the nested `<ol>` geometry, so nothing else is needed there:
- **Indent:** `ListStyleUaRule` gives every `ol` its 40 px.
- **No vertical gap:** neither plan builder emits UA margins under a parent with own text (Compose `blockCollapsePlanFor`'s
  `_text` bail, iOS `MarginCollapsePlanner` GATE 3).
- **Measured evidence:** in wave52-ship `counter-list-item-2/-3`, the nested item's content starts at x97 on all three
  platforms, matching the reference, and its row follows at y76, a 20 px pitch with no gap.

## 6. Blast radius (cells the closing gate watches)

- **Source census** (`nested-list-extractor.census.mjs`, 1435 corpus tests): **1** test has an `<li>` whose first trigger
  lies inside a nested list, `counter-reset-reversed-nested`, with text before the nested list. There are **0** without
  text before, and **0** `dd`/`dt` carriers. The two other nested-list shapes are not carriers, because their `<li>` close
  explicitly before the nested list:
  - `counter-list-item-2`: `<ol>` as a sibling of `<li>`, web/ios/android P 0.9972 / 0.9939 / 0.9923.
  - `counter-list-item-3`: `<div><ol>` inside `<ol>`, P 1 / 0.9965 / 0.9947.
- **Differential extraction** over all 1435 tests (`.differential.sh`, in-process, base vs patched copies):
  - (A) alone: 1 fixture changes.
  - (B) alone: 1 fixture changes.
  - (A)+(B): 1 fixture changes.
  - In every case the changed fixture is `counter-reset-reversed-nested`, and 1434 are byte-identical.
- **Native fold census** (`.wire-census.py`, 1435 wave52-ship docs): 1 component carries `meta.runs` plus a `::before`
  `_text`, the target's `Two`.
  - The only other `runs`+`pseudos` component, `display-contents-dynamic-before-after-001__1__3`, has no `_text`, so the
    fold is identity there. Its cells read P 0.9898 / 0.9888 / 0.9879.
- **Passing today:** all 3 carrier cells (P 0.9506 / 0.9508 / 0.9509).
- **Carrier set for `control-check.mjs`:**
  - web, ios and android: `{wpt__css-lists__counter-reset-reversed-nested}`.
  - Rule, evaluated on the post-fix run's own per-test IR: a component with `meta.sourceTag` ∈ {ol, ul, menu, dir, li}
    whose slot ancestors include an `li`, OR a component with `meta.runs` and `pseudos.before._text`.
  - On wave52-ship this matches only that document.
- **Outside the corpus** (not gate cells), the change also moves 5 `css-lists/li-value-reversed-*` tests.
  - (A) moves 007a, 007b, 009a and 009b. (B) moves 008b and 009b.
  - `008b` is a direct check of (B): after the fix its four lists read 10/8/6/5/3, 10/7/5/4/−4, 3/5/7/9 and −7/−4/−3/5.
    Those are the numbers in `li-value-reversed-008-ref.html`. The current bake reads 9/7/5/5/3, 8/5/5/4/−4, 3/5/7/9 and
    −3/0/1/5.
  - Separately, a `counters()` prefix `3.` / `3.99.` appears on sibling lists. That is untraced and out of scope.

## 7. Predictions

Every target row is labelled **picture-correctness (P→P)**, with zero scorer flips.

| cell | prediction |
|---|---|
| `counter-reset-reversed-nested` web | P 0.9506 → **P ≥ 0.995 (HIGH)**. After (A)+(B) the DOM equals the reference's own DOM (real `<ol>`/`<li>`, browser UA sheet). Sibling web cells: `-multiple` / `-display-none` P 1. |
| `counter-reset-reversed-nested` ios | P 0.9508 → **P ≥ 0.99 (MED-HIGH)** with (C). Without (C): still P, but row 2 stays unmarked `Two` at x57 (MED). |
| `counter-reset-reversed-nested` android | P 0.9509 → **P ≥ 0.99 (MED)** with (D). Without (D): rows 3-5 shift right by the `2. ` advance (pass LOW-MED). |

**Must not move** (57 cells, all byte-identical; listed in `census.json` `mustNotMove`):
- every other `css-lists/counter-reset-reversed-*` ×3
- `counter-list-item`, `-2`, `-3`, `-slot-order` ×3
- `css-display/display-contents-dynamic-before-after-001` ×3

More broadly, every capture outside the carrier set must be byte-identical (the control).

## 8. Verification plan

- **Unit pins on verbatim payloads**, with the mutation that must turn each one red:
  1. Extractor pin: `extractFixture('css/css-lists/counter-reset-reversed-nested.html')`.
     - Asserts: the root `<ol>` has three `<li>` (Three, Two, One); Two has one `<ol>` child holding Eleven, Nine and Eight;
       the `::before` `_text` reads `3. / 2. / 11. / 9. / 8. / 1.`.
     - Mutation: make `findImpliedClose` return the old first match. The pin must go red.
  2. Run-proto pin through `extractOwnTextMerged` / `buildRunProto` on `<li>A<ol><li>B</li></ol></li>tail`.
     - Asserts: exactly one element entry.
     - Mutation: revert site 2 (l.3560-3570) only. The pin must go red with two entries.
  3. Bake pin in `counter-bake.test.mjs`: the test's tree, with `counter-set` on the element and the increment on `::before`.
     - Asserts: `3. 2. 11. 9. 8. 1.`
     - Second pin: the `li-value-reversed-008b` list 1 shape (`counter-increment: list-item 0; counter-set: list-item 5`),
       asserting 10 8 6 5 3.
     - Mutation A: drop the `+ lastNeg` term. Both new pins go red.
     - Mutation B: always add the term. The existing `counter-list-item` (32…) and `li-value-reversed-013` (5,3,2) pins go red.
  4. iOS `PseudoTextFoldTests` (Catalyst): the post-fix `Two` component verbatim.
     - Asserts: `runs[0].text == "2. Two "` and `text` starts with `"2. "`.
     - Mutation: restore the runs refusal.
     - Control pin: `display-contents-dynamic-before-after-001__1__3` verbatim comes back unchanged.
  5. Compose `PseudoTextFoldTest` (JVM): the same payload.
     - Asserts: `runs[0]` is prefixed, the folded `pseudos` lacks `before`, and `extractBeforeAfterConfig(...)?.before == null`.
     - Mutation: keep `before`. The pin must go red (double paint).
- **Focused suites, after the gate:**
  - `node --test tools/titan/extract-fixture*.test.mjs tools/titan/counter-bake.test.mjs tools/titan/generated-content-bake.test.mjs`
  - Compose: `:runtime:testDebugUnitTest --tests '*PseudoTextFold*' --tests '*ContentApplier*' --tests '*ListOrdinal*'`
  - SwiftUI Catalyst: `-only-testing:StyleConverterRuntimeTests/PseudoTextFoldTests`
- **Wire differential under the gate's own extract flags:** re-extract all sections pre/post and require exactly one
  per-test IR document to change. The in-process run above did not exercise `--post-load` / `--bidi-bake` / `--vt-bake`.
- **Probe sections for the closing gate:**
  - `css-lists` holds the target plus the 47 list controls.
  - `css-display` holds the runs+pseudos control.
  - `css-counter-styles` and `css-pseudo` are bake consumers with identical wire.
  - Run `control-check.mjs` with the carrier set in §6.

## 9. Risks and recommendation

- **The native fold path.** Whether iOS/Compose paint Two through the run plan or through the leading-`text` label depends
  on gates in the renderers. Folding into both `runs[0].text` and `text` removes that dependency; a pin per path is cheap.
- **Post-load documents** (serialized browser DOM) go through the same `walkChildren`. A script-built nested list would
  change too. The source census cannot see those; the gate-flag wire differential can.
- **Scope of (A).** Only the list containers bound the `li` scope. Other special elements (e.g. `<li><blockquote><li>`) keep
  the old behaviour. Name that gap; the census found 0 carriers.
- **(B) also changes pseudo-scoped `counter-set`s.** `pseudoSet` applies the set before the increment, the reverse of the
  element order. This is pre-existing and untouched, and has 0 corpus carriers.
- **Partial landing.** If (A)+(B) land without (C)+(D), the natives still pass, but with a different wrong picture. Land all
  four together, or label the native cells degenerate.

**Recommendation: GO-SMALL.** The mechanism is fully traced and reproduced in-process. The change is small (one seam patch,
one tools file, two native twins) and moves 1 document in a 1435-test corpus. The payoff is 3 P→P picture-correctness cells
with zero predicted flips, which is too little to justify a lane of its own. Fold it into the wave's lists/counters (or
pseudo-text) lane, and send the extractor hunk with the seam-patch batch. If no such lane exists, it stands alone as an S
lane. The ring-fenced `filter-effects/backdrop-filter-basic-blur` is untouched.
