# Layout fixtures (Phase 7)

CSS fixtures exercising every value variant accepted by the layout parsers in
`src/main/kotlin/app/parsing/css/properties/longhands/layout/`.

This phase's theme is **child-in-parent divergences** — every fixture styles
both the parent (flex/grid/positioned container) and at least one, usually
2–4, visible coloured children so cross-platform differences in the
layout-of-children are directly measurable by the screenshot harness.

Run via:

```bash
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/properties/layout/<file>.json -o /tmp/p7"
```

All 48 fixtures convert with `(0 generic)` on every component.

## Coverage map

### Flexbox (9 fixtures, 73 components)

| Fixture | Properties / variants |
|---|---|
| flex-display.json | display: flex, inline-flex, grid, inline-grid, block, inline-block, none, flow-root |
| flex-direction.json | flex-direction: row, row-reverse, column, column-reverse |
| flex-wrap.json | flex-wrap: nowrap, wrap, wrap-reverse (with overflowing children) |
| flex-justify-content.json | justify-content: flex-start, flex-end, center, space-between, space-around, space-evenly, start, end, left, right, normal, stretch |
| flex-align-items.json | align-items: stretch, flex-start, flex-end, center, baseline, start, end, self-start, self-end, normal |
| flex-align-content.json | align-content: stretch, flex-start, flex-end, center, space-between, space-around, space-evenly (wrapped multi-line container) |
| flex-grow-shrink-basis.json | flex-grow 0/1/asymmetric, flex-shrink 0/1/2, flex-basis auto/px/%/0/content |
| flex-order.json | order 0/-1/2/3 rearrangement cases |
| flex-align-self.json | align-self: auto, stretch, flex-start, flex-end, center, baseline, start, end, self-start, self-end |

### Grid (17 fixtures, 98 components)

| Fixture | Properties / variants |
|---|---|
| grid-display.json | display: grid, inline-grid, subgrid (columns) |
| grid-template-columns.json | fr, px, %, auto, mixed, minmax, repeat(N, …), repeat(auto-fill, minmax), repeat(auto-fit, minmax), fit-content(), named lines `[name]` |
| grid-template-rows.json | same families as columns |
| grid-template-areas.json | 3×3, with spans, with `.` empty cells, 2-col asymmetric, `none` |
| grid-auto-columns.json | px, auto, minmax, fr, multi-track, min-content, max-content |
| grid-auto-rows.json | px, auto, minmax, fr, min-content |
| grid-auto-flow.json | row, column, row dense, column dense |
| grid-area.json | named area, 2-value lines, 4-value lines, span, auto |
| grid-column-start-end.json | integer, negative integer, span N, named line, auto |
| grid-row-start-end.json | same |
| grid-justify-items.json | start, end, center, stretch, normal |
| grid-align-items.json | start, end, center, stretch, baseline |
| grid-justify-self.json | start, end, center, stretch (overriding parent justify-items) |
| grid-align-self.json | start, end, center, stretch (overriding parent align-items) |
| grid-justify-tracks.json | normal, start, end, center, stretch, space-between, multi-value |
| grid-align-tracks.json | same keyword set as justify-tracks plus multi-value |
| masonry-auto-flow.json | pack, next, ordered, definite-first, pack definite-first, pack ordered, next definite-first, next ordered |

### Position (5 fixtures, 26 components)

| Fixture | Properties / variants |
|---|---|
| position-keywords.json | position: static, relative, absolute, fixed, sticky |
| position-top-left.json | top/left: px, %, 0, auto, negative, calc() |
| position-right-bottom.json | right/bottom: same families |
| inset-logical.json | inset-block-start/end, inset-inline-start/end: px, %, 0, auto |
| z-index.json | auto, 0, positive, negative |

### Advanced (7 fixtures, 59 components)

| Fixture | Properties / variants |
|---|---|
| anchor-positioning.json | anchor-name (none/single/multiple), anchor-scope (all/none), position-anchor (named/auto), position-area, inset-area |
| offset-path.json | none, path(…), ray(angle), ray(angle size), circle(), circle(… at …), ellipse(), polygon() |
| offset-distance.json | 0, 25%, 50%, 100%, px |
| offset-rotate.json | auto, auto <angle>, reverse, <angle>, <rad> |
| offset-anchor.json | auto, center, left/top combined, percentage pair, px pair |
| offset-position.json | auto, normal, center, left/top, percentage pair, px pair |
| position-try.json | position-try none/named/multiple, position-try-fallbacks, position-try-options (flip-block/flip-inline/flip-start), position-try-order (normal/most-width/most-block-size), position-visibility (always/anchors-visible/no-overflow), position-fallback (none/named) |

### Root (4 fixtures, 19 components)

| Fixture | Properties / variants |
|---|---|
| clear.json | clear: none, left, right, both, inline-start, inline-end |
| float.json | float: none, left, right, inline-start, inline-end |
| overlay.json | overlay: none, auto |
| reading-flow.json | reading-flow: normal, flex-visual, flex-flow, grid-rows, grid-columns, grid-order |

### Integration / divergence fixtures (6 fixtures, 23 components)

| Fixture | Purpose |
|---|---|
| flex-parent-with-margin-children.json | Flex container with margin-bearing children — common source of per-platform divergence (margin collapse rules differ) |
| flex-gap-vs-margin.json | Visual comparison of gap-spacing vs margin-spacing under the same flex layout |
| grid-with-gap-and-padding.json | Grid container with gap + inner padding + child margins — layered spacing chain |
| absolute-positioned-children.json | Relative parent, absolutely positioned children at all 4 corners, center, inset-all, negative coords, mixed with static sibling |
| nested-flex-grid.json | Flex parent containing grid children and grid parent containing flex children — sizing chain |
| fixed-sticky-in-scroll.json | Sticky-top, sticky-bottom, and fixed-positioned children inside a scrollable container |

## Totals

- 48 fixture files
- 295 components total
- 0 `generic` rows across the entire suite
- 0 parser errors

## Parser gaps / notes for the platform-agent handoff

All properties listed in the scope convert cleanly, but a few behaviours are
worth flagging for platform implementers because they are **parser-accepted
but runtime-divergent** or **semantically degraded**:

- `DisplayPropertyParser` maps `-webkit-box`, `-webkit-flex`, `-ms-flexbox`
  and the two-value `block flex` / `block grid` forms to plain `flex` /
  `grid`. No fixture exercises these vendor forms — assumed legacy and out of
  scope for Phase 7.
- `GridTemplateColumns/Rows` parser falls back to a `Expression`-typed IR
  node for anything with `min(`, `max(`, `clamp(`, or parenthesis depth > 2
  (see `isComplexExpression`). Our fixtures avoid those deliberately so no
  component ends up stored as an opaque string; platform renderers that want
  to support those cases will need extra work.
- `OffsetPropertyParser` (the shorthand) only recognises `path('…')` as the
  path component. Other shapes (`ray`, `circle`, `polygon`) in the shorthand
  form are not parsed — but they *are* parsed when passed directly to
  `offset-path`. Our `offset-path.json` exercises every shape variant; the
  `offset` shorthand itself is not separately fixtured because all its
  sub-parts are covered by the longhand fixtures.
- `PositionAnchorPropertyParser` accepts *any* non-`auto` string as an
  anchor name (no `--` prefix validation). Platform renderers will need to
  decide how to handle names without the CSS custom-property prefix.
- `AnchorName` parser accepts comma- *and* space-separated lists. Our fixture
  uses the comma-separated form, which is the CSSWG-spec form.
- `MasonryAutoFlow`, `JustifyTracks`, `AlignTracks`, `InsetArea`,
  `PositionArea` all degrade to a `Raw(string)` variant for unknown tokens
  instead of returning `null`. None of our fixtures hit those paths (every
  variant we use is in the parser's known-keyword set), but platform appliers
  should be defensive about the `Raw` case.
- `FlexBasisPropertyParser` accepts `fit-content` as a bare keyword but does
  not accept `fit-content(<length>)` — only `GridTemplate`'s track parser
  does. Not fixtured as `fit-content(…)` for flex-basis.
- `position-try-options` requires at least one recognised option
  (`flip-block` / `flip-inline` / `flip-start`) and returns `null`
  otherwise — unknown tokens are silently dropped. Our fixture uses both
  single and combined recognised forms.

## Properties not directly fixtured (intentional)

- **`Offset` shorthand** — covered by its component longhands
  (`offset-path`, `offset-distance`, `offset-rotate`, `offset-anchor`,
  `offset-position`).
- **`GridTemplate` shorthand** — covered by `grid-template-columns`,
  `grid-template-rows`, `grid-template-areas`.
- **`GridAutoTrack`** (the generic single-track property) — the parser is
  only registered as a helper for the `auto` subset of grid-auto-columns /
  grid-auto-rows; the user-facing properties are the concrete ones and both
  are fixtured.
- **`BoxOrient`** — legacy `-webkit-box-orient`; the parser exists but it is
  not registered under a CSS property name that flows through
  `PropertyParserRegistry` into `convert`, so a fixture can't exercise it
  without parser changes, which this phase explicitly forbids. Noted for a
  future parser-registration pass.

## Child-in-parent coverage summary

Every fixture contains at least one parent with visible styled children. The
integration group (6 files, 23 components) focuses specifically on divergence
hot-spots: margin-vs-gap, absolute corners, nested flex-in-grid, sticky in
scrollable containers, and mixed positioning schemes. These are the fixtures
platform agents should watch most closely when SSIM dips below 0.95.
