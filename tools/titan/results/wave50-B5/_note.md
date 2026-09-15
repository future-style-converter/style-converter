# wave-50 lane B5 — BACKLOG queue 1(b) + 1(c), diagnosed

Evidence base: `tools/titan/runs/wave49-final/` (gitignored run dir) and the
frozen refs at
`tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`.
No devices this wave: every number below is a JVM pin, a vitest render, a
headless-Chrome layout measurement, or a PNG read of the frozen captures.

---

## 1(b) `css-writing-modes/direction-upright-002` — DIAGNOSED. It is not the
## upright text-flow model.

wave49-final: web f 0.5826 [390x2805] · iOS f 0.5758 [390x2544] · Android
f 0.5946 [390x2316], all against a 390x954 ref.

**The ref packs the eleven `body > div` floats into wrapped rows; we stack
them one per row.** Ink-band read of the four rasters (rows carrying any
non-white pixel):

| | bands | shape |
|---|---|---|
| ref 390x954 | 10 | 3 prose lines, then TWO float groups (y124..501, hr at y518, y544..921) |
| web 390x2805 | 13 | 3 prose lines + 10 separately-banded divs, 32 px apart |
| iOS 390x2544 | 13 | same |
| Android 390x2316 | 13 | same |

The 32 px gaps are the floats' own non-collapsing 1em margins, so the boxes
**are** floating — each is simply too WIDE to share a line. Chromium lays the
first float out at **98x150**; the harness lays it out at **364x230**, i.e.
its content box clamps at the 358 px ICB inline size.

### Why the box is 3.7x too wide — three independent modelling gaps

Measured in headless Chrome on the harness's OWN composed DOM
(`renderToStaticMarkup` of every root through the harness ComponentRenderer
in `?wpt=1` mode, injected into a replica of `ComposedTestCanvas`'s
canvas+ICB markup **via XHTML parsing**, so the HTML parser's table
foster-parenting cannot move nodes the real React-DOM harness keeps in
place). Replica-vs-real fidelity: replica 3045 px against the real capture's
2805 px (+8.6%, font-weight metrics) — good enough to attribute deltas, not
to quote as a score.

| variant | composed canvas H | first float |
|---|---|---|
| as shipped | 3045 | 364x230 |
| `colgroup`/`col` at their UA display | 3042 | 330x266 |
| `ruby`/`rt` at their UA display | 2769 | 308x230 |
| both (seam **S1**) | 2766 | 192x266 |
| both + the extractor's 100x100 `<col>` stamp removed (seam **S2**) | **1864** | 192x184 |
| Chromium, the frozen ref's own source | **954** | **98x150** |

1. **`col` / `colgroup` / `ruby` / `rt` have no display model on any
   platform.** They are outside the harness's `TAG_ALLOWLIST`, so they map to
   `<div>` and become BLOCK boxes. css-display-3 2.4 gives them internal
   layout displays instead (`table-column`, `table-column-group`, `ruby`,
   `ruby-text`); css-tables-3 2.1 keeps column boxes out of the table's
   content flow, and css-ruby-1 2 puts an annotation alongside its base, not
   after it. **In a vertical writing mode the block axis is HORIZONTAL
   (css-writing-modes-4 3), so every invented block box widens the element
   physically** — which is why this is a css-writing-modes bug and why all
   three platforms blow the canvas by the same 2.4–2.9x.
2. **The extractor stamps 100x100 on the first `<col>` of each
   `<colgroup>`** — the `matchedRules === 0 && no props && no ownText && no
   kept children` "empty node" placeholder in `tools/titan/extract-fixture.mjs`
   (the same class as BACKLOG queue 5(a)'s `<hr>` stamp). 10 components in
   this test. On a `<col>` it does not add a placeholder, it PINS the table's
   column width.
3. **Residual after 1+2 (192 vs the ref's 98), all in the harness's per-text
   content wrapper.** Box-by-box against Chromium: table 56 vs 26 (the
   colgroup boxes still carry the `display:block; padding:4px` content span,
   and the `<td>` texts are ALSO emitted as inline spans inside the `<tr>` —
   the doubled-item-text class of BACKLOG queue 2(c)); `div.flex` 28 vs 20 and
   `div.grid` 28 vs 20 (the 4 px content-span padding on each side); the ruby
   div 74 vs 26, because each `<rt>`'s content is an `inline-block` span and
   an inline-block cannot be ruby-annotation content (css-ruby-1 2), so every
   annotation takes its own block-axis column instead of riding over its base.

**Prediction, with numbers: S1+S2 do NOT flip this cell.** Two 192 px floats
still do not fit the 358 px ICB, so the one-per-row stacking survives; the
canvas goes 2805 -> ~1750 (scaling the replica's 3045 -> 1864 onto the real
capture) against a 954 ref. Flipping it needs residual (3) as well — the
content-wrapper geometry, which is item (A)'s territory (harness chrome drawn
outside the paint chain), not this lane's.

### Where the value actually is: `CSS2/borders/border-conflict-style-107`

Web pixel mismatch against the frozen ref (+/-8 per channel), base -> S1:
**39.276% -> 10.431%**. Its natives are the same over-tall shape
(iOS 390x1404, Android 390x1204, against a 390x600 ref) at f 0.6564 / f 0.3262
— the lowest Android score of any carrier. Full per-test table, including the
seven currently-PASSING cells the seam touches (all measured FLAT or BETTER on
web): `carriers.json`.

---

## 1(c) `css-text-decor/text-decoration-inset-025` — the Android column is a
## BLANK CAPTURE, not a tall render

The Android PNG is **390x9470 of `rgba(0,0,0,0)` — one distinct colour, zero
opaque pixels, 14483 bytes**. It is the ONLY fully transparent capture in the
entire wave49-final corpus (`blank-captures.json`; predicate = every capture
whose file is smaller than area/40 bytes, decoded and checked for a single
pixel with alpha > 8). The 7042 px `css-view-transitions/far-away-capture`
Android capture directly above it is opaque white, so the boundary is the
emulator's ~8192 px GPU render-target limit that
`runtimes/compose/.../core/renderer/VerticalRunIntrinsics.kt`'s own banner
already records for direction-upright-002's 66404 px canvas:
`GraphicsLayer.toImageBitmap()` rasterises a transparent bitmap above it.

**So the cell's 0.0005 is not a measurement of a render.** It is scored like
any other row: `divergence: "unknown"`, `perChannelSsim.a 0.0001`,
`labDeltaE: null`. `ScreenshotManager.DEGENERATE_CANVAS_PX` (8192) logs the
canvas on-device, but nothing in `tools/` fails — the only mention of the
shape in the pipeline is `inject-wpt-block.mjs`'s
`computeColorComposite` comment ("labDeltaE is null for degenerate pairs
(all-transparent, metric error)"), which *excuses* it. This is BACKLOG
obligation #3's degenerate-pass problem in its starkest form: a capture that
drew nothing at all, scored.

**The 9470 px layout blow-up itself is still undiagnosed and could not be
diagnosed this wave.** It needs Compose layout, and
`runtimes/compose/build.gradle.kts` has no Robolectric — the JVM suite can
only exercise pure helpers. What is known: the container is a `columns: 2`
multicol with 12 `inline-size: fit-content` children whose `<u>`/`<span>`
chain carries unresolved `em` padding/margin/border; web lays it out at 750,
iOS at 600, the ref at 1230; the Android/web height ratio is 12.63x, an
outlier (next worst in the corpus is 4.67x).

---

## What this lane landed, and what it did not

**Landed** (lane ownership): overflow-safe intrinsic sums in
`runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/VerticalRunIntrinsics.kt`
(`sumClampedIntrinsic`, `INTRINSIC_SUM_CAP` = 262142 =
`IntrinsicChannel.packableCap(Constraints.Infinity)`) — BACKLOG queue item 8's
"upright intrinsic Int-overflow at ~2.1e9 px summed advances, coerceAtMost
candidate". Two `Constraints.Infinity` advances sum to **-2** as an Int and a
negative intrinsic makes Constraints throw, killing the whole capture
composition. Pinned in `VerticalRunIntrinsicsTest` (9 tests; mutation proof:
replacing the helper body with the pre-fix `values.sum()` turns exactly the
two new pins red). **Zero corpus carriers — no cell is predicted to move.**

**Deferred as verified seam patches** (both outside lane ownership):

* `seam-S1-web-ua-boxless-display.patch` — new
  `apps/web-harness/src/sdui/UaBoxlessDisplay.ts` + a 1-line hook in
  `ComponentRenderer`'s `decorateStyles` + 6 pins. **Applied, measured and
  reverted in this tree**: the live hook reproduced the forced-display A/B
  exactly (2766 / 1864), the new suite is 6/6 green, mutation-proved (3 of 6
  red when the map is neutralised), the whole web-harness suite is 282/282
  green with it applied, and `git apply --check` passes against the current
  tree. Owner: whoever owns the harness ComponentRenderer (decided PR (A)/(B)
  territory).
* `seam-S2-extractor-col-placeholder.patch` — one predicate term
  (`!NON_BOX_GENERATING_TAGS.has(node.tag)`) on the empty-node placeholder in
  `tools/titan/extract-fixture.mjs`, generated against HEAD. It IS applied in
  the wave-50 tree (lane B1 rebased it into the file it owns); this entry's
  original "NOT applied" status is superseded.

  **CENSUS CORRECTED — wave-50 fix lane F3, on skeptic S2's defect 1. This
  entry used to say "exactly ONE test carries a 100x100 stamp on
  `col`/`colgroup` (direction-upright-002 …), so no passing cell can move".
  Both halves were wrong.** The census was taken over wave49-final's
  **per-test IR**, which is not the path the predicate runs on. Re-derived on
  the **static walker** over all 1435 gate tests (8 of them carry a
  `<col>`/`<colgroup>` at all), it is **TWO** tests:

  | test | stamped | wave49-final cells |
  |---|---:|---|
  | `css-writing-modes/direction-upright-002` | 10 `<col>` | web f 0.5826 · iOS f 0.5758 · Android f 0.5946 |
  | `css-tables/border-collapse-dynamic-col-001` | 3 `<col>` | **web P 1.0000** · iOS f 0.9404 · **Android P 0.9812** |

  The second stamps components
  `border-collapse-dynamic-col-001__0__0__{0,1,2}`, each of which loses
  `Width {px:100}` + `Height {px:100}` under this seam (3 `<col>`s in the
  test, 4 in its `-ref`). It was invisible to an IR census because it ran
  `[post-load: extracted+structure]` in wave49-final and its `<col>`s carry
  browser-computed props there. That is not a safe proxy: in that same run
  post-load **bailed 49×** and **declined 9×**
  (`grep -ho 'post-load: [a-z+ -]*' tools/titan/runs/wave49-final/sections/*/extract.log | sort | uniq -c`),
  so bail-to-static is live and any test can arrive on the static path at a
  gate. **So TWO CURRENTLY-PASSING CELLS are at risk from this seam** and the
  gate must be told to watch them.

  **And the same fact read the other way (wave-50 fix lane F6, on skeptic
  S2's differential): three of the 17 extractor-changed documents do NOT take
  the static path at the gate, so no static differential in this wave predicts
  their gate result.** `css-pseudo/active-selection-057` and this very
  `css-tables/border-collapse-dynamic-col-001` run post-load-extracted
  (`[post-load: extracted]` and `[post-load: extracted+structure]` on their
  wave49-final `extract.log` lines), and `css-counter-styles/counter-suffix`
  runs through `[bidi-bake: baked — 2 roots, 4 runs] [stale-runs dropped: 2]
  [counter-bake: baked — 10 markers, 2 declined]`. At the gate, read
  counter-suffix's NEW `extract.log` line first.

  The seam also shipped with **no test pin** (S2 defect 2: emptying the set
  left `node --test tools/titan/extract-fixture*.test.mjs` at 489/489 green).
  It now has one: `tools/titan/extract-fixture-col-placeholder.test.mjs`, 4
  pins on verbatim markup from both carriers, mutation-proved twice — all
  four go red when the set is emptied, and the negative pin goes red when the
  set is widened to `['col','colgroup','div','hr']`.

* **TWIN PICTURES in `carriers.json` (skeptic S5).** `ch-units-vrl-003` and
  `-004` are byte-identical in BOTH capture and reference (capture sha256
  `dff77bf618a6af42…`, ref `145456241a41d1ea…`), and so are `-007` and `-008`
  (capture `5abdd80f47a7c43f…`, ref `06334eb635cf5b51…`). Their **four rows
  are two measurements**; counting them as four independent carriers
  double-counts the evidence. Full digests are in `carriers.json`'s
  `_correction_wave50_F3`.

**Reported, unowned**: there is no blank-capture guard anywhere in `tools/`.
The check that can be made to fail: a composed capture of a WHITE-canvas
document with zero opaque pixels is never an honest render — fail it as a
capture failure (the exit-7 family) rather than scoring it. It is provable by
mutation against the one committed instance named in `blank-captures.json`.
