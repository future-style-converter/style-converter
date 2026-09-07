# Effects fixtures (Phase 8)

CSS fixtures exercising every value variant accepted by the effects-category
parsers in `converter/src/main/kotlin/app/parsing/css/properties/longhands/effects/`
(and the `FilterProperty` parser, which lives there even though its IR model
is under `irmodels/properties/color/`).

Run via:

```bash
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/properties/effects/<file>.json -o /tmp/p8"
```

Fixture count: `ls fixtures/properties/effects/*.json | wc -l` (25 at
authoring, Phase 8; the directory has grown since — the coverage map below
covers the original 25). Every fixture converted with `(0 generic)` on every
component when the phase shipped; that claim has not been re-run since.

## Coverage map

### Clip / visibility / overflow (7 fixtures, 51 components)

| Fixture | Components | Properties / variants |
|---|---:|---|
| clip-path-basic-shapes.json | 14 | `clip-path`: none, inset(1), inset(4), inset round, circle(radius), circle(% at %), circle(px at px), ellipse(2), ellipse(at), polygon(triangle), polygon(hexagon), rect(), xywh(), xywh round |
| clip-path-path.json | 3 | `clip-path: path('M ...')` with basic rect, curve, diamond path data |
| clip-path-ref-and-box.json | 10 | `clip-path`: url(#id), 7 geometry-box keywords (margin/border/padding/content/fill/stroke/view), shape+box, box+shape |
| clip-rule.json | 2 | nonzero / evenodd |
| clip-legacy.json | 2 | `clip`: auto, `rect(t,r,b,l)` (deprecated, absolute-positioned) |
| visibility.json | 3 | visible / hidden / collapse |
| overflow.json | 17 | overflow 5-keyword + 2-value form; overflow-x/y/block/inline longhands × key variants |

### Filters (3 fixtures, 36 components)

| Fixture | Components | Properties / variants |
|---|---:|---|
| filter-functions.json | 22 | `filter`: none, blur(px)×2, brightness (pct + num), contrast (pct + num), grayscale×2, sepia, invert, saturate×2, hue-rotate (deg + turn), opacity(), drop-shadow (rgba+blur, no-blur, negative), 3 chain variants |
| filter-url.json | 2 | `filter: url(#id)`, `filter: url(file.svg#id)` |
| backdrop-filter.json | 12 | Same function list as filter, applied as backdrop |

### Masks (15 fixtures, 89 components)

| Fixture | Components | Properties / variants |
|---|---:|---|
| mask-image.json | 11 | none, url(), linear-gradient (default + angle + `to right`), radial-gradient (default + circle + ellipse), conic-gradient, repeating-linear-gradient, comma-separated multi |
| mask-mode.json | 3 | alpha / luminance / match-source |
| mask-repeat.json | 6 | repeat / no-repeat / repeat-x / repeat-y / round / space |
| mask-position.json | 13 | center, keyword pairs, pct pair, length pair, center center; `mask-position-x` 4 variants; `mask-position-y` 3 variants |
| mask-size.json | 8 | auto / cover / contain / single-length / single-pct / two-lengths / length+auto / pct+pct |
| mask-origin.json | 6 | content-box / padding-box / border-box / fill-box / stroke-box / view-box |
| mask-clip.json | 7 | same list as mask-origin plus no-clip |
| mask-composite.json | 4 | add / subtract / intersect / exclude |
| mask-type.json | 2 | luminance / alpha (SVG) |
| mask-border-source.json | 3 | none / url() / url(file.svg#id) |
| mask-border-slice.json | 6 | number, %, 2-value, 4-value, bare `fill`, value+fill |
| mask-border-width.json | 5 | auto, length, number, 2-value, 4-value |
| mask-border-outset.json | 5 | 0, length, number, 2-value, 4-value |
| mask-border-repeat.json | 6 | stretch / repeat / round / space / two-value × 2 |
| mask-border-mode.json | 2 | luminance / alpha |

## Totals

- 25 fixture files
- 142 components total
- 0 `generic` rows across the entire suite

## Parser gaps / notes for platform-agent handoff

All properties convert cleanly, but several variants degrade to an opaque
storage that platform appliers must be defensive about:

- **`FilterPropertyParser` / `BackdropFilterPropertyParser`**: any unknown
  filter function or any `var()`/`calc()` inside the value falls back to a
  `FilterValue.Raw(string)` variant. Our fixtures stay on the typed-function
  path. `url(#id)` is explicitly supported and produces a `UrlReference`.
- **`ClipPathPropertyParser`**: supports circle/ellipse/polygon/inset/path/
  rect/xywh. `polygon()` **does not** parse an optional leading `nonzero`/
  `evenodd` winding-rule token — we omit that variant (see `clip-rule` for the
  global toggle). `path()` accepts an optional leading `evenodd` keyword per
  spec, but the parser treats the entire content as the path string, so we
  only fixture the plain `path('…')` form. The `url(…)` form accepts an
  optional trailing geometry-box keyword (see basic-shapes / ref-and-box).
- **`ClipPropertyParser`** (legacy) accepts only `auto` and `rect(t,r,b,l)`
  with **comma-separated** values, unlike modern clip-path's space-separated
  rect().
- **`MaskImagePropertyParser`**: radial/conic gradients parse shape/position
  partially — `at <position>` inside gradients is recognized in radial
  (discarded), but explicit size (`closest-side`, etc.) is not fixtured
  because the parser ignores those tokens.
- **`MaskRepeatPropertyParser`** does not accept the two-value form like
  `mask-repeat: round space` — only single keywords. The two-value form is
  however accepted by `MaskBorderRepeatPropertyParser`.
- **`MaskSizePropertyParser`** strips trailing commas defensively; used in
  `mask` shorthand contexts. Not relevant for the longhand fixtures.
- **`MaskBorderSlicePropertyParser`**: accepts bare `fill` (→ Keyword("fill"))
  and slice-values with inline `fill`. Multi-value with fill stored under
  `Values(... , hasFill=true)`.
- **`MaskBorderOutsetPropertyParser` / `MaskBorderWidthPropertyParser`**
  accept unitless numbers (interpreted as multiples of border-width for
  outset, lengths in spec-defined units for width). Both also accept lengths
  and multi-value forms. Platform appliers should distinguish the `Number` vs
  `Length` branches — they are **not** interchangeable.
- **`OverflowXPropertyParser`/`OverflowYPropertyParser`/`OverflowBlock...`/
  `OverflowInline...`** accept the same 5-keyword set (visible / hidden / clip
  / scroll / auto). The `overflow` shorthand expander splits into x+y
  longhands.

## Properties not directly fixtured (intentional)

- **`overflow` shorthand** — covered via the `overflow.json` fixture which
  includes both the shorthand and all four longhands.
- **`mask` shorthand** — expander exists but is out of scope for Phase 8;
  every mask longhand is fixtured above.
- **`mask-border` shorthand** — same rationale.
- **`clip-path` with `evenodd`/`nonzero` winding tokens inside
  polygon(…)/path(…)** — the parser does not accept these inline; use
  `clip-rule` instead.
