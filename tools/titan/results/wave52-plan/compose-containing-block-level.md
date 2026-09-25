# compose-containing-block-level — wave 52 plan brief

Read-only planning lane, 2026-09-25. Evidence: gate run `wave51-fix` (`tools/titan/runs/wave51-fix/sections/<sec>/{manifest.json,android-screenshots,ios-screenshots,screenshots(web),per-test-ir}`),
refs under `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`.
Paths are repo-relative; every `file:line` is the Compose runtime under
`runtimes/compose/src/main/java/com/styleconverter/runtime/`. Census beside this brief:
`percent-spacing-census.mjs` / `.json`.

## 1. Queue items covered (docs/BACKLOG.md › "Ranked queue (wave 51+)" › item 0)

- **(b″) PRIMARY** — "The SAME level defect exists in `spacing/MarginApplier.kt` and
  `spacing/PaddingApplier.kt`" … "Port them onto `ElementContainingBlock.containingBlockFor`
  with an A/B, or state the divergence."
- **(b′) SECONDARY** — "The CSS 2.1 §10.1 republish for NON-BLOCK-CONTAINER ancestors is a
  real and SEPARATE gap, still open, in `DynamicValueResolver.childContainingBlock`."
- **(c) COSTED, recommend HOLD** — "`css-transforms/css-transform-3d-transform-style`
  (android) passes at 0.9577 against iOS's 0.9574 and is FRAGILE."

## 2. Target cells, cheapest first (every PNG below was opened and measured with pngjs)

| # | cell (wave51-fix) | platform | ssim | visible in the capture PNG | what the ref shows |
|---|---|---|---|---|---|
| 1 | css-sizing/abspos-auto-sizing-fit-content-percentage-001, -002, -003, -004 | android | P 0.9984 ×4 | one green square [16,68]-[115,167] 100×100, n=10000 green px, no red — identical on all four | identical: green 100×100 at [16,68]-[115,167], no red |
| 1c | the same four (controls — a Compose port cannot move them) | web / ios | P 1.0000 / P 0.9992 | same square | same |
| 2 | css-position/position-relative-002 / -008 | android | P 0.9967 / P 0.9984 | (b′) carriers; level-insensitive, no PNG change expected | — |
| 3 | css-transforms/css-transform-3d-transform-style | android | P 0.9577 | blue 200×100 [116,76]-[315,175] with green 100×100 CENTRED on it [166,76]-[265,175] → blue\|green\|blue | green 100×100 [116,76]-[215,175] then blue 100×100 [216,76]-[315,175] → green\|blue; **bold** "green"/"blue"/"red" in the `<p>` |
| 3 | same | ios / web | P 0.9574 / P 0.9841 | iOS: identical blue\|green\|blue; web: green\|blue exactly as the ref, residual = plain vs bold text | same ref |
| 3′ | filter-effects/backdrop-filter-3d-transform-perspective | android | P 0.9868 | blue RECTANGLE 70×100 [51,36]-[120,135] n=7000 | blue KEYSTONED trapezoid 73×105 [47,32]-[119,136] n=7280 |
| 3′ | filter-effects/backdrop-filter-nested-3d-transform-perspective | android | P 0.9749 | blue rectangle 70×100 n=7000 (iOS: 50×100 n=5000) | trapezoid 72×112 [46,30]-[117,141] n=7260 |

Row 1 is the whole (b″) target: the four cells are **passing-WRONG** — the picture is
byte-equal to the ref, the used margin/padding is not (§3). Zero flips predicted; correctness only.

## 3. Mechanism, with file:line

**The level defect (b″).** `MarginApplier.apply` enters a `Modifier.composed { }` lane for
any PERCENT side (`MarginApplier.kt:85-99`) and reads `val cb = LocalContainingBlock.current`
at `MarginApplier.kt:93`; `PaddingApplier.apply` does the same at `PaddingApplier.kt:53-70`,
read at `:58`. A `composed` factory materialises where the chain is handed to a layout
node: `ComponentRenderer.RenderComponentContent` (`core/renderer/ComponentRenderer.kt:2130`),
invoked from `inheritanceWrappedContent` (`:1983`) INSIDE
`CompositionLocalProvider(… LocalContainingBlock provides childContainingBlock …)` (`:2004`).
Both appliers therefore read the block this element publishes for its CHILDREN. The
correctly-levelled channel already exists: `:2021-2022` `LocalElementContainingBlock provides
containingBlock`, where `containingBlock` is the `LocalContainingBlock.current` read at the
TOP of `RenderComponent` (`:1169`) — the block this element is laid out in (CSS 2.1 §10.1).
The percentage-inset lane already consumes it (`layout/position/PercentInsetPositioned.kt:73-77`
via `layout/position/ElementContainingBlock.kt:122-133`); the two spacing appliers do not.

**What the two levels say on the carriers** (IR verbatim,
`per-test-ir/wpt__css-sizing__abspos-auto-sizing-fit-content-percentage-00{1..4}.json`):
`.abs` = `Position="ABSOLUTE"` + `BackgroundColor` only → `childContainingBlock`
(`core/variables/DynamicValueResolver.kt:207-249`) publishes `(null, null)`; `.child` =
`Width {px:100}`, `Height {px:100}`, `MarginLeft=-50` / `MarginRight=-50` / `PaddingLeft=50` /
`PaddingRight=50` (a BARE NUMBER is the PERCENT wire, `core/types/LengthValue.kt:132-136`) →
its OWN published block is `(100, 100)` (the bare-number padding is not subtracted: `pxOf`
needs a `JsonObject`, `:215-216`). Element level → `parentWidthPx = null` +
`percentIndefiniteAsZero` → **0 px** (`spacing/SpacingResolve.kt:89-90`, pin P11). Child
level (today) → **−50 px / +50 px**.

**Why the picture is still right (passing-wrong).** Under WPT capture the child's
`box-sizing` defaults to content-box and `SizingExtractor.contentBoxInflation` resolves the
padding band with `SpacingContext(percentIndefiniteAsZero = true)` and NO parent width
(`sizing/SizingExtractor.kt:220-234`) → band 0 → border box 100. `LayoutFacade.applyToModifier`
chains margin OUTSIDE sizing (`layout/LayoutFacade.kt:181-187`) and `StyleApplier` chains
padding innermost at step 8 (`StyleApplier.kt:783`), so the 50 px padding sits INSIDE the
fixed 100 px box (content 50, not 100) and the −50 px margin rides `Modifier.offset`
(`MarginApplier.kt:203-207`), which translates without resizing. `.abs` is measured unbounded
by `absposOverflowMeasure` (`ComponentRenderer.kt:4652-4658`) and wraps the child's 100×100
layout box → the green `.abs` paints 100×100 at the ref's bbox. The child paints nothing and
has no children (census: `paints=false children=false` on all four), so the 50 px content
shortfall and the −50 px shift are invisible. Two lanes of one runtime disagree about one
percentage (frame band 0, applier 50): that is the defect, independent of score.

**Why element level is the CSS-correct answer.** CSS 2.1 §8.3/§8.4: a margin/padding
percentage "is calculated with respect to the width of the generated box's containing
block" — the block the element is laid out in, never its own content box. css-sizing-3
§5.2.1 (cyclic percentage contributions): when that block depends on the element (the
fit-content abspos here) the percentage is **zero** for the intrinsic contribution — what the
tests assert (`<meta name=assert>`: "abspos elements with fit-content sizing disallows
percent-size resolution in its children") and what the frame-inflation lane already does.
The child-level read only coincides because the child is the SOLE content of the fit-content
box; with parent `Width 400` and child `Width 200; margin-left: 10%` the levels give 40 px vs
20 px — the general defect.

**(b′) §10.1 republish.** `childContainingBlock` publishes a content box for EVERY
component (`DynamicValueResolver.kt:207-249`); CSS 2.1 §10.1 says a static/relative box's
containing block is "the content edge of the nearest **block container** ancestor box", so an
inline `<span>` or a `<tbody>` should hand its OWN block down unchanged. Corpus scan (1435
docs, 11 bare-number inset + spacing carriers): parents are 9× `div`/block, 1× `span`
(position-relative-002 `__1__0-195`, `meta.sourceTag "span"`, no `Display`), 1× `tbody`
(position-relative-008 `__1__0-217`); both level-insensitive (pins L4/L5 in
`layout/position/PercentInsetContainingBlockLevelTest.kt`) — the republish moves nothing.

**(c) transform-style.** `transforms/OrthographicFlatten.kt:57-83` states it: every element
is flattened alone (`TransformApplier.kt:713-717`), so the preserve-3d parent `rotateY(-60deg)`
and child `rotateY(120deg)` (IR `TransformStyle="PRESERVE_3D"` on `__1__1-150`) multiply to
|cos60·cos120|·400 = 100 px of green centred, where css-transforms-2 §4.1.3 composes once:
|cos 60|·400 = 200 px, of which the ref shows the FRONT half (z>0) over the parent and hides
the back half behind the opaque blue — green\|blue. The wire is already decoded elsewhere
(`layout/position/TransformContainingBlock.kt:174-175` `preserves3D`) but no channel reaches
a Modifier (`TransformMatrixPathApplier.kt:26-33`, `TransformMatrixComposer.kt:74-78`,
`TransformConfig.kt:96-98` all name the seam).

## 4. Proposed fix

**(b″) — port, Compose only, two files + one additive param.**
- `spacing/MarginApplier.kt:93` and `spacing/PaddingApplier.kt:58`: replace
  `val cb = LocalContainingBlock.current` with
  `val cb = ElementContainingBlock.containingBlockFor(element = ElementContainingBlock.LocalElementContainingBlock.current, ambient = LocalContainingBlock.current, breadcrumb = ElementContainingBlock.SPACING_UNPUBLISHED_BREADCRUMB)`
  (import `com.styleconverter.runtime.layout.position.ElementContainingBlock`). Keep
  `ctx.copy(parentWidthPx = cb.widthPx, percentIndefiniteAsZero = true)` and the
  `LocalWptCaptureMode` gate untouched — outside WPT capture nothing changes, so every
  committed baseline stays byte-identical by construction (`MarginApplier.kt:95-97`,
  `PaddingApplier.kt:66-68`).
- `layout/position/ElementContainingBlock.kt:122-133`: add `breadcrumb: String =
  UNPUBLISHED_BREADCRUMB` and `SPACING_UNPUBLISHED_BREADCRUMB =
  "Spacing[containing-block-level-unpublished]"` so the fallback leg (list markers, widget
  shims — paths that never pass `:2022`) is named per lane, not mis-filed under `Inset[…]`.
  Update the file header: the channel now has three readers.
- Fix the stale comment `MarginApplier.kt:79-82` ("this is what makes `margin-left: -50%` …
  collapse to 0 like the browser ref") — it describes the level the code never read.
- **A/B.** (A) `wave52-open` (unmodified tree) vs (B) the ported tree on the same quiet host:
  `node tools/titan/score-gate.mjs wave52-open <B-run>` must report **0 gained / 0 lost /
  0 movers**, and the four `css-sizing` Android PNGs must be byte-identical (`cmp`) — the
  used value moves from −50/+50 px to 0 px on a box that paints nothing. Record both APK
  sha1s (standing constraint). Fixture net `BASELINE=1 ./test-all.sh` exit 0 ×8.

**(b′) — decide, pin, then a one-hunk renderer seam (optional this wave).** At
`ComponentRenderer.kt:1886-1890` gate the publish:
`if (BlockContainer.establishes(effectiveProperties, component._tag)) childContainingBlock(...) else containingBlock`
(pass-through = republish the inherited block). New `layout/position/BlockContainer.kt`:
declared `Display` wins (INLINE / CONTENTS / TABLE_ROW / TABLE_ROW_GROUP / TABLE_HEADER_GROUP /
TABLE_FOOTER_GROUP / TABLE_COLUMN(-GROUP) → false; BLOCK / INLINE_BLOCK / FLEX / INLINE_FLEX /
GRID / INLINE_GRID / TABLE_CELL / LIST_ITEM / FLOW_ROOT → true — flex/grid containers ARE
their items' containing block, css-flexbox-1 §4 / css-grid-1 §9.1), else the UA display of
`meta.sourceTag` (`span b i em strong a code label` and `tbody thead tfoot tr colgroup col`
→ false; everything else → true). Abspos descendants are out of scope (their block is the
nearest POSITIONED ancestor's padding box, css-position-3 §3.1 — not modelled today either).
Predicted movement: none. A zero-score modelling repair, so it ranks after (b″); if the wave
is short, land only `BlockContainer.kt` + pins and leave the seam as a patch under
`tools/titan/results/wave52-<lane>/`.

**(c) — HOLD.** Do not touch the cell in wave 52; put it on the score-gate watchlist as a
tripwire. Cost of a transform-style-aware channel: (1) renderer seam — a
`LocalPreserve3DContext` (accumulated 4×4 of the nearest preserve-3d ancestor chain + its
`perspective`/`perspective-origin`) provided in the `:1983-2030` block when
`TransformContainingBlock.preserves3D` is true, RESET when `transform-style` is flat
(§4.1.2): **S**. (2) `TransformMatrixPathApplier.apply` (`:37-60`) pre-multiplies
`TransformMatrixComposer.ctm` by the inherited matrix — needs the child's placement inside
the ancestor (an `onPlaced` channel) for the pivot, and `takesMatrixPath` must claim it: **M**.
(3) OCCLUSION — the ref hides the child's back half behind the parent's own background;
Compose paints children after the parent's `drawBehind` and has no depth buffer, so the z<0
half must be painted by the parent BEFORE its background: a bespoke compositor, **XL**.
Without (3) the composed-once projection paints green over the whole [116..315] — still 100
of 200 columns right, so **no gain is predicted and 0.9577 may drop**. The channel's only
measurable payoff is the two `filter-effects` ancestor-`perspective` carriers (row 3′:
70×100 rectangle vs 72-73×105-112 keystone), both already P — ≤ +0.01 each, and the backdrop
two-pass sampler would then have to follow the homography. Real preserve-3d compositing is
the actual fix; treat the cell as a candidate to lose.

## 5. Blast radius — how a builder censuses it

`node tools/titan/results/wave52-plan/percent-spacing-census.mjs` (read-only over `RUN_DIR`,
default `wave51-fix`): for every component with a
`(Margin|Padding)(Top|Right|Bottom|Left|BlockStart|BlockEnd|InlineStart|InlineEnd)` whose
data is a BARE NUMBER or `{type:"percentage"}`, it transcribes `childContainingBlock` for the
slot-parent (element level) and for the component itself (child level), applies
`percentBasePx`'s P10/P11 rule, and prints both used px with the three gate verdicts. Result
on 1435 docs: **4 carriers in 4 tests, cb differs on 4, used px differs on 4, all four
`paints=false children=false`** — the whole population is row 1. The shape histogram (in the
JSON) shows every other non-px spacing value is em/ch/auto/initial/calc; no
`{original:{u:"PERCENT"}}` exists. Currently-PASSING cells at risk are exactly the four
Android cells of row 1 (only if the port is mis-levelled — `element`/`ambient` swapped keeps
today's numbers) plus the 390-sheet fixture net if the `LocalWptCaptureMode` gate were
disturbed. iOS/web cells cannot move (Compose code).

## 6. Predicted flips

- **(b″): none.** All four stay `android P 0.9984`; the change is the used value
  (−50/+50 → 0 px) of an unpainted, childless box. Confidence **high**. Falsified by: any of
  the four moving off 0.9984, ANY other Android cell moving in the A/B, a byte difference in
  the four PNGs, or the fixture net leaving exit 0.
- **(b′): none.** Confidence **high** (L4/L5 pins + the 11-carrier parent scan). Falsified by
  position-relative-002/-008 Android moving off 0.9967 / 0.9984.
- **(c): untouched.** If the channel lands without occlusion, predict
  `css-transform-3d-transform-style android` ≤ 0.9577 (all-green over the blue) and ≤ +0.01
  on the two `filter-effects` carriers. Confidence **medium** (bbox arithmetic, not a render).

## 7. Pins a builder must write (JUnit4 JVM, VERBATIM corpus payloads) — and their mutation

1. **S1 level discriminator** (`spacing/PercentSpacingContainingBlockLevelTest.kt`): the
   verbatim `.abs`/`.child` payloads → `childContainingBlock(abs, ROOT)` = `(null,null)`,
   `childContainingBlock(child, that)` = `(100,100)`. Dies if the resolver starts subtracting
   bare-number padding or reading `Position`.
2. **S2 used value**: `resolveToDp(Relative(-50, PERCENT), SpacingContext(parentWidthPx=null,
   percentIndefiniteAsZero=true))` = 0 dp and with `100f` → −50 dp; same for `+50`. Mutating
   `percentBasePx`'s tri-state (`SpacingResolve.kt:89-90`) turns it red.
3. **S3 wiring pin** (source-level, the `WptBoxSizingDefaultTest.kt:135` /
   `SeamReachabilityTest.kt` idiom — this module has no Compose UI test): read both applier
   sources; assert each `composed {` body calls `ElementContainingBlock.containingBlockFor(`
   with `LocalElementContainingBlock.current` and that the ONLY `LocalContainingBlock.current`
   is the `ambient =` argument. Reverting either read → red; run once against pre-port bytes
   as the negative control and record the failing line in the lane note.
4. **S4 generalisation**: parent `Width {px:400}` / child `Width {px:200}` + `MarginLeft 10` →
   `MarginApplier.resolvedInsets(cfg, ctx.copy(parentWidthPx = containingBlockFor(element=(400,null), ambient=(200,null)).widthPx, percentIndefiniteAsZero=true))`
   = 40 dp; the child-level 20 dp must NOT be produced. Swapping the arguments → red.
5. **S5 breadcrumb**: `containingBlockFor(null, ambient, SPACING_UNPUBLISHED_BREADCRUMB)` marks
   `Spacing[…]` not `Inset[…]`; the published path marks neither (mirror of L1/L2 in
   `PercentInsetContainingBlockLevelTest.kt:57-97`). Wrong constant → red.
6. **S6 lane consistency**: on the 003 payload, `SizingExtractor`'s frame band (P13, base 0)
   and the ported padding context both yield 0 → content box 100. Reverting the applier level
   gives 50 ≠ 0 → red.
7. **(b′) B1–B4** (`layout/position/BlockContainerTest.kt`): `span`/`tbody`/`tr` → false,
   `div`/`td`/`li` → true, `Display INLINE` → false, `FLEX`/`GRID`/`INLINE_BLOCK` → true,
   declared `Display` beats the tag; plus a corpus pin that the republished block on
   position-relative-002 `(100,100)` still resolves `Top=-100` → −100 px (same as L4).

## 8. File ownership the lane needs

Owned (write): `spacing/MarginApplier.kt`, `spacing/PaddingApplier.kt`,
`layout/position/ElementContainingBlock.kt` (additive param + constant), new tests under
`runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/` (plus
`layout/position/BlockContainer.kt` + its test if (b′) is staffed). Read-only:
`core/variables/DynamicValueResolver.kt`, `spacing/SpacingResolve.kt`,
`sizing/SizingExtractor.kt`. **Shared-renderer seam**: (b″) needs NONE — the provider at
`ComponentRenderer.kt:2021-2022` already exists (the wave-50 B2 seam); (b′) needs one hunk at
`ComponentRenderer.kt:1886-1890`, delivered as a verified patch under
`tools/titan/results/wave52-<lane>/` for the integrator (wave skill's shared-file rule).
`content/ContentApplier.kt:368-371` re-provides the element channel for pseudo boxes and needs
no change. Cross-runtime: iOS passes the same four at P 0.9992 through
`runtimes/swiftui/…/StyleEngine/spacing/PaddingConfig.swift:95-104`'s `containingBlockWidthPx`
channel — not audited for level here; web is the browser. No twin change is proposed.

## 9. What I could not verify (no gradle/device allowed)

- The materialisation point of the two spacing `composed {}` lanes is inferred from the
  identical mechanism proven for insets (B2, gate-confirmed: position-relative-006 android
  f 0.9966 → wave51-fix P 0.9967) and from S3's STATIC probe
  (`tools/titan/results/wave50-S3/probe-output/percent-spacing-levels.txt`: elemW=null,
  childW=100 on all four); no runtime probe read the CompositionLocal inside the applier.
- The "passing-wrong" chain (padding inside the 100 px box, `.abs` wrapping the child) is
  read from `LayoutFacade.kt:181-187`, `StyleApplier.kt:783`, `SizingExtractor.kt:220-234`,
  `ComponentRenderer.kt:4652-4658` — not executed. A device breadcrumb dump would settle it.
- Whether `PropertyTracker` breadcrumbs surface in the Android capture logs (post-port "no
  `Spacing[…]` breadcrumb on the four carriers" check); the iOS twin's level for percent
  spacing; the "all-green" outcome of a transform-style channel without occlusion
  (arithmetic, not rendered); the SSIM split between flattening and bold-text residual.
- The census is a static transcription (calc/var sizes, `display: contents`, the wave-33
  `absHeightPx` side channel are not modelled); none applies to the 4 carriers.
