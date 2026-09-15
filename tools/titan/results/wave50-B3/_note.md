# wave50-B3 — the Compose `::after` drop (BACKLOG queue 2(a)), and the corrected mechanism for 2(b)

Lane B3, wave 50. **No device gate ran this wave**; every number below is a
measurement of the frozen `wave49-final` captures or a JVM pin, never a new
render. Deltas attribute against `corpus-v6.15`.

---

## 1. Queue 2(a) — `counter-cjk-decimal` renders ZERO ink on Android

**Diagnosed, fixed, and the fix is a PATCH in this directory** (`content/`
and `core/renderer/` are outside lane B3's ownership list, so the code is
handed over rather than applied — see §4).

### Mechanism (not a counter bug, and not a font bug)

Compose composes an ordinary element's generated content as a **wrapper
AROUND the host**: `ContentApplier.ContentWithPseudoElements` builds
`Row { before, content, after }`. CSS 2.1 §12.1 / css-pseudo-4 §4.1 put the
`::after` box **INSIDE** the originating element, as its last child — which
is what web does (`NodeRenderer` emits the pseudo span as a positional
child) and what iOS does (`PseudoTextFold.swift` folds it into the host's
text run).

Outside the host box the `::after` has to share the line with the host's own
block box, and in composed-WPT capture that box takes
`Modifier.fillMaxWidth()` (`ComponentRenderer.kt`, the `blockFlowWidth` val —
CSS 2.1 §10.3.3 stretch fit). A Compose `Row` measures unweighted children in
order against the space its predecessors left, so the host consumes the whole
line and the `::after` `Text` is measured at `maxWidth = 0`: it still occupies
the row (its zero-width layout wraps one grapheme per line) but **draws
nothing**.

### The four capture signatures that prove it

All paths under `tools/titan/runs/wave49-final/sections/`.

| cell | what the PNG shows | reading |
|---|---|---|
| `css-lists/counter-reset-reversed-pseudo-001` (android f 0.9884 · iOS P 0.9999) | Android ink bands at **y20-31** and **y60-71**, x17-33 / x17-34 ("B7", "B4"); iOS at y20-31 / y40-51, x17-53 / x17-56 ("B7A5", "B4A2") | the `::before` survives, the `::after` is gone — and the **40px Android row pitch vs iOS's 20px** is the two-glyph `::after` wrapping to 2 lines at width 0. The run WAS composed; it drew nothing. |
| `css-counter-styles/cjk-decimal/counter-cjk-decimal` (android f 0.9407 · iOS P 0.9934 · web P 1.0) | Android capture is **entirely white** — the whole 390x696 PNG is one colour, RGBA(255,255,255,255), no ink anywhere (`tools/titan/results/wave50-S6/blank-capture-census.txt`); the android-ref row's semantic presence is aCoveragePct **0.000** (exact) against bCoveragePct **0.816** — the ref side in that row's own 696-tall frame. **0.946 is the web-ref/ios-ref figure and was quoted here in error** (wave-50 skeptic S6-18, corrected by lane F4). Canvas 390x**696** vs the ref's 390x600 | every component is `::after`-ONLY (17 of them, `CounterReset` + `pseudos.after`), so nothing at all is left to paint; the canvas grows on the wrapped zero-width rows. |
| `css-display/display-contents-before-after-001` (android P 0.9712) | Android paints "P / A / S" — the **final S (the `::after`) is missing**; the ref reads PASS | a degenerate PASS: the cell scores over threshold while a quarter of the ref's glyphs are absent. |
| `css-contain/contain-content-011` (android f 0.9164) | the "25" is painted at **x≈118**, to the RIGHT of the 100px-wide host box, where web/iOS put it at x≈16 | the host declares `width: 100px`, so there is no squeeze and the same defect shows as **placement** instead. (This cell's dominant failure is unrelated — its two abspos green children escape the contained parent.) |

### Fix

`compose-pseudo-after-textfold.patch` — the Compose twin of iOS
`PseudoTextFold.swift` + `PseudoTextBridge.swift`, **`::after` only**:

* new `runtimes/compose/.../content/PseudoTextFold.kt` (151 lines) — the
  component rewrite: `_text = own text + baked ::after text`, the bucket's
  typed styling appended last.
* new `runtimes/compose/.../content/PseudoTextBridge.kt` (173 lines) — the
  pure declaration gate, byte-parallel with the Swift twin
  (`inertDeclarations` / `boxUnderContents` / the styling trio; anything
  else refuses the whole bucket, named through `PropertyTracker`).
* `PseudoBucketExtractor.kt` — new `afterFolded: Boolean = false` parameter
  so a folded bucket cannot ALSO render through the wrapper (one claim, two
  consumers — the same discipline `PseudoGeneratedBox.claim` already enforces
  for the block-box path).
* `ComponentRenderer.kt` — the fold runs in the same `remember` as
  `PseudoBoxFold`, and its verdict is threaded to the extractor seam. It is
  computed ONCE because the fold rewrites the `_text` its own styling gate
  reads.

**`::before` is deliberately NOT folded.** It is composed first in the same
Row, so it already lands adjacent to the host's content edge and renders
correctly today; 55 corpus tests carry a before-only bucket and moving them
is a device-A/B change, not a lane change. (The `::before` side does carry
the same *placement* defect — it sits outside the host's border box, so it
can never overlap the host's background — but no currently-failing cell is
carried by it.)

### Blast radius — replayed over all 1435 `wave49-final` per-test-ir documents

Exactly **13 tests / 78 components** change path. The 3 remaining
`::after`-bearing tests are refused by the declaration gate
(`css-cascade/scope-pseudo-element`, `css-anchor-position/anchor-function-chain-pseudo-elements`,
`css-anchor-position/anchor-position-multicol-002`) and do not move.

| test | android (wave49-final) | iOS | prediction |
|---|---|---|---|
| `css-counter-styles/cjk-decimal/counter-cjk-decimal` | f 0.9407 cov 0.000 | P 0.9934 | **FLIP to PASS** (all 17 rows fold; iOS is the same fold) |
| `css-values/attr-notype-fallback` | f 0.9584 cov 0.000, canvas 1032 | P 0.9993 | **FLIP to PASS** (3 fold, 3 have no `_text` and paint nothing on iOS either) |
| `css-values/attr-style-sharing-4` | f 0.9959 cov 0.000 | P 1.0000 | **FLIP to PASS** |
| `css-lists/counter-reset-reversed-pseudo-001` | f 0.9884 cov 0.105 | P 0.9999 | **FLIP to PASS** |
| `css-lists/counter-reset-reversed-pseudo-003` | f 0.9961 cov 0.057 | P 0.9999 | **FLIP to PASS** |
| `css-lists/counter-list-item` | f 0.7407 cov 1.513, canvas 1990 | f 0.7518 cov 4.625, canvas 959 | improves, does NOT flip: the 33 `" N"` runs come back and the canvas should collapse to ≈959; iOS fails the same cell on the bold `<h2>` (queue 0(g), `UAElementFontRule` has no Compose twin) |
| `css-display/display-contents-fieldset-002` | f 0.4567 | f 0.6092 | 14 fold; already a deep fail on both, direction unknown |
| `css-display/display-contents-dynamic-pseudo-insertion-001` | f 0.9450, cov 2.991 vs ref 0.121 | f 0.9120 | already failing and **over-inking 25x**; adding the `::after` could worsen it. Watch. |
| `css-contain/contain-content-011` | f 0.9164, cov 9.875 vs ref 5.815 | P 0.9854 | the "25" moves from x≈118 to x≈16; the dominant abspos-escape defect is untouched, so no flip predicted |
| `css-display/display-contents-before-after-001` | **P** 0.9712 cov 0.720 (ref 0.822) | P 0.9680 | **RISK CELL** — gains the missing trailing S, i.e. ink the ref has and Android lacks; expected to stay PASS |
| `css-display/display-contents-before-after-002` | **P** 0.9775 | P 0.9767 | same risk shape |
| `css-display/display-contents-before-after-003` | **P** 0.9713 | P 0.9691 | same risk shape |
| `css-display/display-contents-dynamic-before-after-001` | **P** 0.9898 | P 0.9888 | same risk shape |

**Net prediction: +5 Android cells, 4 currently-passing cells at risk**
(each ≥0.021 above threshold, each gaining ink the reference has).

#### Coupled with lane B2 on `display-contents-before-after-003`

Added by wave-50 lane F1 (skeptic S3). The risk cell
`css-display/display-contents-before-after-003` is changed by **two** wave-50
lanes at once, and neither lane's report said so: this lane's `::after` fold
folds its text, and **hunk 1 of lane B2's seam patch**
(`tools/titan/results/wave50-B2/componentrenderer-seam.patch`, the CSS 2.1
§10.3.9 shrink-to-fit gate) removes the block-auto-width fill from the SAME
component, `wpt__css-display__display-contents-before-after-003__3-024` — it is
on B2's 22-test blast radius
(`tools/titan/results/wave50-S3/probe-output/atomic-inline-census.txt`). It is
the only cell in the corpus carrying both changes.

Joint prediction: `wave49-final css-display/display-contents-before-after-003
android P 0.9713` — an at-risk pass with 0.0213 of head-room, receiving both an
ink gain (this lane) and a used-width change (B2). If it drops below 0.95, the
two changes must be A/B'd SEPARATELY before either is blamed: stub
`AtomicInlineShrinkToFit.suppressesBlockAutoWidth` to `false` (B2 off, B3 on),
re-run; restore it, then stub `PseudoTextFold.resolve` to the identity (B2 on,
B3 off), re-run. Both stubs switch off the MECHANISM. It must never be narrowed
by test name — docs/BACKLOG.md's standing ring-fence rule forbids
test-specific code, and an `if (id == "…-003")` carve-out would be exactly that.

### Verification done without a device

* `PseudoTextFoldTest` — 14 pins on **verbatim** `wave49-final` per-test-ir
  payloads (counter-cjk-decimal `__15`, counter-reset-reversed-pseudo-001
  `__0__0`, counter-list-item `__1__0__0`, contain-content-011 `__3`,
  scope-pseudo-element `__1__0`, attr-notype-fallback `__4`).
* Focused suites with the patch applied: `content.*` + `core.renderer.*` +
  `lists.*` + `counters.*` = **389 tests, 0 failures** (the 21 existing
  `PseudoBucketExtractorTest` pins included, so the new default parameter
  changed nothing).
* `node tools/visual/spec-cite-validate.mjs` — every citation in the new
  files resolves.
* **Mutation-proved** (each mutation applied, suite run, mutation reverted):
  1. `pseudos["after"]` → `pseudos["afterMUT"]` (never fold) ⇒ the 4 fold
     pins fail.
  2. drop `!afterFolded &&` from `PseudoBucketExtractor` ⇒ *"a folded after
     disappears from the wrapper config"* fails.
  3. widen the children refusal to `size > 99` ⇒ *"an after on a parent with
     children is refused, not mis-ordered"* fails.

---

## 2. Queue 2(b) — `css-lists/counter-004`: the BACKLOG's mechanism is WRONG

The queue records it as *"both-native zero-width wrap … lays the georgian
counter string one glyph per line"*. **There is no zero-width wrap in this
test, and no counter string is being broken.** Measured on the frozen
capture:

* `tools/titan/runs/wave49-final/sections/css-lists/android-screenshots/wpt__css-lists__counter-004.png`
  (390x**1784**) has **81 contiguous ink bands**; the ref (390x600) has **5**.
* Band x-widths: one band 341px wide (the heading), **two bands 51px wide**,
  and 78 bands 7–17px wide.
* The per-test IR has 80 inline `<span>` components under two parents: 40
  carrying a `pseudos.before` bucket (`content: "ა"` …) and 40 carrying a
  plain `text`. The two 51px bands are the two 5-glyph strings `ჵჰშჟთ`
  (components `counter-004__1__39` / `__2__39`) — **each rendered on ONE
  line**.

So the string that *could* have wrapped did not, and the two halves —
pseudo-generated and plain text — stack **identically**. The defect is that
**80 inline `<span>` siblings each become their own block line box**, i.e.
the inline-run wall already tracked as queue item **4(a)** ("the
br-stacked-equivalent wall — lifting it flips 29 hosts across 27 tests, ~16
currently passing: needs a device A/B, never a blind lift"). It is not a
marker defect, not a counter defect, and not fixable inside `lists/` or
`counters/`.

The §1 `::after` fold does **not** touch counter-004 (its buckets are all
`before`), and the iOS twin (lane B4) will find the same: iOS is 390x1704
with the same one-span-per-line shape, and web fails separately at 0.7779.

**Recommended BACKLOG edit:** move 2(b) under item 4(a) as a named carrier
(`css-lists/counter-004` android f 0.7531 / iOS f 0.7532, 80 spans → 80 line
boxes, canvas 1784/1704 vs ref 600) and delete the "zero-width wrap"
characterisation, which no capture supports.

---

## 3. Incidental finding for the orchestrator — RESOLVED before ship

Mid-wave, `node tools/visual/spec-cite-validate.mjs` failed on a file another
wave-50 lane (B2) added while this lane was running: `layout/position/
AtomicInlineShrinkToFit.kt` then attributed the shrink-to-fit table rule
(section 17.5.2) to css-tables-3, whose ED has no such section. It was not lane
B3's file, so this lane reported it rather than touching it.

It was fixed before ship: that line now cites CSS 2.1 section 17.5.2 — the
spec that really carries it — alongside css-tables-3 section 5, and the
validator passes on the file.

This paragraph deliberately writes the section numbers in PROSE, with no
section sign. The original note quoted the validator's error message verbatim
inside a fenced block, and `spec-cite-validate.mjs --include-data` scans note
files too: the quotation was itself parsed as a citation, so a note ABOUT a
fixed defect kept re-reporting it (wave-50 skeptic S1, finding 3). A report
that quotes a citation error must spell the number in a way the scanner cannot
parse, or the bug outlives its fix in the ledger.

---

## 4. Why this is a patch and not a landed change

Lane B3's ownership list is
`runtimes/compose/src/{main,test}/java/com/styleconverter/runtime/lists/**`
and `counters/**`. The defect's home turned out to be `content/` (the
generated-content bridge) and `core/renderer/ComponentRenderer.kt` — renderer
seams — so the wave rules require it be handed over as a verified patch
rather than applied in the shared tree. The tree was returned to its
pre-lane state after verification.

Apply with:

```bash
git apply tools/titan/results/wave50-B3/compose-pseudo-after-textfold.patch
```
