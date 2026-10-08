# compose-wpt-content-box-cb — wave 54 family brief (from the layout queue scout)

This is a read-only planning brief, written 2026-10-08 while `wave54-open` was running. Nothing was built or run for it.

**Evidence**
- Gate of record: `wave53-final`.
- Cells: `node tools/titan/results/wave52-gate/cells.mjs … wave53-open wave53-final` → `queue-scout-layout.cells.txt`.
- Pictures: wave53-final captures compared with the frozen refs. Every PNG below was opened.
- Pixels: `compose-wpt-content-box-cb.geometry.py` → `.geometry.out.txt`.
- Census: `queue-scout-layout.census.mjs` → `queue-scout-layout.census.json` (`cbborder`, `cbborderVerticalText`).

**Queue items.** BACKLOG 0(b) is CLOSED: `position-relative-006` is P ×3 at wave53-final. This lane is the remaining Compose
containing-block defect in that neighbourhood. It is also the red-square class 0(k): the six abspos-autopos cells are
red-ink passes in `queue-scout-layout.red-square.json`.

**Verdict in one line: GO-SMALL (S, Android only).** Under WPT capture, Compose sizes a box `box-sizing: content-box` (the UA
default it emulates). But `DynamicValueResolver.childContainingBlock` still treats the declared width as a BORDER box and
subtracts padding and border from it. Every out-of-flow percentage size under a padded or bordered parent therefore comes
out 20–30 px short.

The fix is one WPT-gated condition plus a one-argument seam hunk. It covers 10 Android cells: 1 MED-HIGH flip and
9 DEGENERATE / short-box passes made faithful. It can fold into the oof-containing-block lane as a second unit; the files
are disjoint.

---

## 1. Target cells (`wave53-open → wave53-final`, unchanged; android column, web and iOS for reference)

| cell | android | web / ios | the Android picture (probe, canvas px) |
|---|---|---|---|
| css-flexbox/abspos/abspos-autopos-htb-ltr | P 0.9966 R | P 0.999 / P 0.9974 | green 70×80 at x16–85, y88–167 (5 600 px) in a 100×100 red box; 4 400 red px (ref: green 100×100, no red) |
| …-vlr-ltr / …-vrl-ltr | P 0.9966 R ×2 | P 0.999 / P 0.9974 | identical: green x16–85, y88–167 |
| …-htb-rtl | P 0.9869 R | P 0.999 / P 0.9974 | green x66–135, y88–167 (4 800 px), 6 000 red px |
| …-vlr-rtl / …-vrl-rtl | P 0.9966 R ×2 | P 0.999 / P 0.9974 | green x36–105, y108–187 (5 600 px), 4 400 red px |
| filter-effects/backdrop-filter-nested-border-radius-clip-3 | **f 0.9574** | P 0.9921 / P 0.9921 | inverted backdrop green 160×60 at x36–195, y36–95 (9 438 px); pink backdrop source showing (4 360 px) (ref: green 200×100 at x36–235, y36–135) |
| …-nested-border-radius-clip | P 0.9649 | P 0.9936 / P 0.9936 | green x46–225, y26–105 (14 238 px), pink 5 156 px (ref x26–225, y26–125) |
| …-nested-border-radius-clip-2 | P 0.9689 | P 1 / P 0.9998 | green x46–225, y26–105, pink 5 210 px |
| …-nested-border-radius-clip-4 | P 0.977 | P 0.9964 / P 0.9981 | green x42–185, y26–89, pink 3 385 px (ref x26–185, y26–105) |

iOS and web draw every one of these right: `GEOMETRY OK` on all web rows and all iOS rows except the three `*-rtl`
autopos rows. Those have their own 10-px iOS offset (green x16–105, 1 000 red px, P 0.9974). That offset is NOT this lane;
it is recorded in queue-scout-layout.md §4.

## 2. The defect as seen

In every picture the out-of-flow box sized `width: 100%; height: 100%` is exactly the parent's padding plus border SHORT on
each axis:
- abspos-autopos: 100 − 20 − 10 = 70 wide and 100 − 5 − 15 = 80 tall;
- the nested clips: 200 − 2×10 = 180, compounded twice in clip-3 to 160.

The red box (autopos) or the pink backdrop source (nested clips) that the box is meant to cover shows through. The in-flow
`100%` siblings are the right size: the pink source spans the full 200 px in clip / clip-2. That is the discriminator: only
the out-of-flow percentage path is wrong.

## 3. Mechanism (traced; 7cce3b22)

- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/DynamicValueResolver.kt` `childContainingBlock`
  (:207–249) takes the declared px or percentage `Width/Height` and unconditionally subtracts `Padding*` and
  `Border*Width` (:238–247).
- Its own pin states the assumption: `DynamicValueResolverTest.kt` :413–429, "border-box width minus padding and border
  bands" (300 → 276).
- Under WPT capture, `SizingExtractor` resolves an unset `box-sizing` to CONTENT_BOX
  (`sizing/SizingApplier.kt` `effectiveBoxSizing` :311–316), and `SizingApplier` INFLATES the frame by the bands. The box
  is drawn content-box. The Android red container is 100 px wide inside its white borders, as the ref's is.
- Its children's containing block is computed border-box. The two halves disagree.
- The consumer that turns this into pixels is the out-of-flow percentage path: `ComponentRenderer.kt`
  `resolveOutOfFlowPercentSizes(animatedProperties, absposCb)` (:1260–1272 → :4985).
- In-flow percentage widths stay on the layout-time `fillMaxWidth` path. The resolver leaves `%` sizes untouched; the row
  just above the containing-block pins, `DynamicValueResolverTest.kt` ~:400–411, says "percentage width stays on the
  layout-time fillMaxWidth path". This is why only abspos boxes shrink.
- The call is `ComponentRenderer.kt` :1933–1934, `DynamicValueResolver.childContainingBlock(effectiveProperties,
  containingBlock)`. `wptCaptureModeForStretch` (:1251) is already a `remember` key of that block.
- Spec: CSS 2.1 §10.1 / §10.2. A percentage resolves against the containing block. For a content-box parent that is the
  declared width; for an abspos child it is the padding box (css-position-3 §2.1).
- iOS is right through `ContainingBlockBasis.paddingBox`.

## 4. The fix

- **F1.** `childContainingBlock(properties, parent, wptCaptureMode: Boolean = false)` subtracts the bands only when the
  EFFECTIVE box-sizing is not CONTENT_BOX. Use the same `SizingApplier.effectiveBoxSizing(declared, wptCaptureMode)` the
  sizing path uses, so the two cannot disagree.
- **Gate it on `wptCaptureMode`** so that the dark stage, with no BoxSizing wire, keeps the frozen border-box reading and
  every committed baseline stays byte-identical. An EXPLICIT `content-box` on the dark stage is the same latent
  inconsistency. Leave it as a named limitation, breadcrumbed `ContainingBlock[dark-stage-content-box-bands]`; the dark-stage
  fixtures with `content-box` include `fixtures/properties/sizing/box-sizing.json`.
- **F2 (optional, 0 carriers, pin only).** An abspos child's base is the padding box (content + padding). Every carrier has
  zero padding on the abspos's own parent, so F1 alone produces the ref's pictures. Land F2 only with its own pin, or queue it.
- **Seam:** one hunk, `ComponentRenderer.kt` :1934, adding `wptCaptureMode = wptCaptureModeForStretch`. It is delivered as
  `tools/titan/results/wave54-<lane>/seam-1.patch` against 7cce3b22.

## 5. Corpus radius (census over 1435 docs)

- **`cbborder`: 11 containers in 11 tests.** These are content-box (no `BoxSizing BORDER_BOX`) boxes with a px or percentage
  Width/Height, a non-zero padding or border band, and a direct child carrying a percentage size or inset.
  - abspos-autopos ×6: the child is ABSOLUTE, so they are carriers.
  - backdrop-filter-nested-border-radius-clip ×4: the chain reaches an ABSOLUTE `#backdrop-filter`, so they are carriers.
  - `css-images/cross-fade-target-alpha`: its percentage child is STATIC (in-flow, `fillMaxWidth`), so it is a
    **must-not-move control** (android P 0.9957).
- **No percentage margin, padding, `calc()` or pseudo-element carrier** sits under such a parent. These are the other
  readers of the channel: `ElementContainingBlock`, `Margin/PaddingApplier`, `PercentInsetPositioned`, `ContentApplier`
  (counted with the same census predicate).
- **Side reader:** `VerticalTextFlowLayout.kt` :172–176 reads `LocalContainingBlock.heightPx` for the upright fallback
  budget under WPT. `cbborderVerticalText` finds 6 text runs, all in `css-gaps/flex/flex-gap-decorations-006` (ios f 0.8266 /
  android f 0.8223; a priced-and-declined writing-mode defect). Watch it: it may move and stays f.
- Post-load-extracted documents carry an explicit `BoxSizing CONTENT_BOX` on every box, but no percentage-sized children
  under banded parents (the census found none). F1 is therefore inert there too.

## 6. Ownership

- **Owned:** `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/DynamicValueResolver.kt`, the
  `childContainingBlock` signature plus one condition. The new band rule goes in a new
  `core/variables/ContainingBlockBands.kt` of ≤ 60 lines if the condition grows past three lines.
- **Pins:** `DynamicValueResolverTest.kt`.
  - The existing 300 → 276 row stays: it is the dark-stage semantics.
  - New WPT rows on the verbatim payloads:
    - abspos-autopos-htb-ltr container → (100, 100);
    - nested-clip `#outer` → (200, 100);
    - `#middle` under it → (200, 100);
    - an explicit `BORDER_BOX` under WPT → still subtracted.
- **Seam:** `ComponentRenderer.kt` :1934, one named argument, as a patch.
- **Disjoint from the other families:** no other wave-54 brief names `DynamicValueResolver` as owned (`ua-heading-face.md`
  only cites its position). The table-body family owns `table/`.

## 7. Geometry probe — `tools/titan/results/wave54-plan/compose-wpt-content-box-cb.geometry.py [run]`

It decodes 40 PNGs and exits 1 if a ref row fails. The rule:
- the strict-green bbox equals the ref's ±1 px;
- the failure ink (strict red, or the pink backdrop source) equals the ref's (0) within 2 % of the green mass.

At wave53-final:
- all 10 ref rows and all 10 web rows print `GEOMETRY OK`;
- iOS prints OK on 7 rows and WRONG on the 3 `*-rtl` autopos rows (their own offset);
- Android prints WRONG on all 10, for example
  `backdrop-filter-nested-border-radius-clip-3 android … → GEOMETRY WRONG (green (36, 36, 195, 95) vs ref (36, 36, 235, 135))`.

After the lane, all 10 Android rows must print `→ GEOMETRY OK`.

## 8. Predictions

| cell (android) | from → to | confidence | floor |
|---|---|---|---|
| backdrop-filter-nested-border-radius-clip-3 | f 0.9574 → **P ≈0.99** (iOS twin of the same geometry: 0.9921) | MED-HIGH | 0.96 |
| …-nested-border-radius-clip / -2 / -4 | P 0.9649 / 0.9689 / 0.977 → P ≈0.99 (iOS 0.9936 / 0.9998 / 0.9981), short box → faithful | MED-HIGH | 0.98 |
| abspos-autopos-htb-ltr / -vlr-ltr / -vrl-ltr | P 0.9966 → P 0.9967 (sim: green repainted 100×100 → 0.9967), DEGENERATE → FAITHFUL | HIGH | 0.995 |
| abspos-autopos-htb-rtl / -vlr-rtl / -vrl-rtl | P 0.9869 / 0.9966 / 0.9966 → P ≈0.9967, FAITHFUL | MED: the rtl static-position arithmetic runs with the corrected size for the first time | — |

**Must not move:**
- every iOS and web cell;
- `cross-fade-target-alpha` android P 0.9957;
- every Android cell outside these 10 tests, except the watched `flex-gap-decorations-006` android f 0.8223;
- every dark-stage fixture PNG, which F1's gate keeps byte-identical. The fixture net must exit 0 with no ledger edit.

## 9. Risks

- **R1 — a second Compose consumer of the same channel moves.** VerticalTextFlowLayout's budget (006) is the only one the
  census finds. It is watched, not predicted.
- **R2 — the Compose backdrop two-pass and rounded clip** may anti-alias differently from iOS at the right size. That is why
  the nested clips are MED-HIGH and not HIGH.
- **R3 — the seam hunk shares a 30-line region with no other wave-54 hunk** that this scout knows of. Re-cut it at
  integration if compose-table-body-cell's patch touches :1925–1940.

**Recommendation: GO-SMALL**, effort S. Fold it into the oof-containing-block lane as unit 2, or staff it alone. It gives
1 flip at MED-HIGH, 9 cells made faithful, and 1 seam hunk.
