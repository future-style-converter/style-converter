# Phase 10 "long tail" fixtures

CSS fixtures exercising every property category not covered by Phases 2–9.
Each category lives at `fixtures/properties/<category>/longtail.json` and
parses cleanly with zero `generic` rows.

Run any fixture via:

```bash
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/properties/<cat>/longtail.json -o /tmp/p10"
```

## Coverage map

| Category | Fixture | Components | Notes |
|---|---|---:|---|
| scrolling | `scrolling/longtail.json` | 60 | scroll-behavior / snap / margin / overscroll / scrollbar / anchor / clip-margin / start / start-target / marker-group / target-group |
| svg | `svg/longtail.json` | 87 | fill(+rule/opacity), stroke(+opacity/width/dash*/linecap/linejoin/miterlimit), stop-color/opacity, flood-*, lighting-color, color-interpolation(-filters), color-rendering, marker(-start/mid/end/side), paint-order, shape-rendering, vector-effect, buffered-rendering, enable-background, cx/cy/r/rx/ry/x/y/d |
| speech | `speech/longtail.json` | 45 | aural — volume, speak(-as), pause(-before/after), rest(-before/after), cue(-before/after), voice-* (family/rate/pitch/range/stress/volume/duration/balance), pitch(-range), richness, stress, speech-rate, azimuth, elevation |
| rendering | `rendering/longtail.json` | 34 | color-rendering, color-interpolation(-filters), content-visibility, field-sizing, forced-color-adjust, print-color-adjust, image-orientation, image-resolution, input-security, interpolate-size, zoom |
| print | `print/longtail.json` | 32 | bleed, bookmark-{label,level,state,target}, footnote-{display,policy}, leader, marks, page, size |
| regions | `regions/longtail.json` | 25 | flow-into/from, region-fragment, continue, copy-into, wrap-{flow,through,before,after,inside} |
| interactions | `interactions/longtail.json` | 37 | cursor (+url/fallback), pointer-events, user-select, touch-action, resize, interactivity, caret, caret-shape |
| performance | `performance/longtail.json` | 21 | contain, will-change, contain-intrinsic-size/width/height/block-size/inline-size |
| columns | `columns/longtail.json` | 22 | column-{count,width,gap,rule-style,rule-width,span,fill} |
| paging | `paging/longtail.json` | 25 | break-{before,after,inside}, page-break-{before,after,inside}, margin-break |
| table | `table/longtail.json` | 14 | border-collapse, border-spacing, caption-side (all 6 incl logical), empty-cells, table-layout |
| shapes | `shapes/longtail.json` | 20 | shape-outside (none/shape-box/circle/ellipse/polygon/inset/url), shape-margin, shape-padding, shape-image-threshold, shape-inside |
| rhythm | `rhythm/longtail.json` | 12 | block-step{,-align,-insert,-round,-size} (line-height-step / line-grid / line-snap already in typography) |
| navigation | `navigation/longtail.json` | 10 | nav-{up,down,left,right}, reading-order |
| images | `images/longtail.json` | 17 | image-rendering, object-fit, object-position, object-view-box |
| appearance | `appearance/longtail.json` | 15 | appearance, appearance-variant, color-adjust, image-rendering-quality |
| counters | `counters/longtail.json` | 10 | counter-{increment,reset,set} |
| lists | `lists/longtail.json` | 17 | list-style-{type,position,image} including symbols() and custom-ident |
| container | `container/longtail.json` | 10 | container, container-name, container-type |
| math | `math/longtail.json` | 8 | math-{style,shift,depth} including `auto-add` and `add(N)` |
| experimental | `experimental/longtail.json` | 8 | presentation-level, running, string-set |
| content | `content/longtail.json` | 15 | content keywords/strings/counter/counters/attr/url/url-alt/multiple/gradient |
| global | `global/longtail.json` | 5 | `all: initial | inherit | unset | revert | revert-layer` |

**Totals: 23 fixture files, 549 components, 0 generic rows across the
entire suite.**

## Categories intentionally not fixtured (duplicate of earlier phases)

All of these are covered by Phase 2–9 fixtures elsewhere in
`fixtures/properties/`; re-fixturing would only duplicate coverage:

- **color** — Phase 4: `colors/{accent-caret,background-blend,blend-modes,color-*,isolation,opacity}.json` cover `color`, `background-color`, `opacity`, `accent-color`, `caret-color`, `color-scheme`, `background-blend-mode`, `mix-blend-mode`, `isolation`. Phase 10 picks up `forced-color-adjust` / `print-color-adjust` / `color-rendering` / `color-interpolation` / `color-interpolation-filters` via `rendering/longtail.json` and `svg/longtail.json`.
- **typography / rhythm overlap** — `typography/line-grid.json`, `typography/line-snap.json`, `typography/line-height-step.json` already cover those rhythm-adjacent properties. Phase 10 rhythm fixture covers only the remaining `block-step*` family.
- **typography / print overlap** — `typography/orphans-widows.json` owns `orphans` and `widows`. Phase 10 print fixture skips them.
- **content / typography overlap** — `typography/quotes.json` already covers the `quotes` longhand.
- **effects / shapes overlap** — `shape-rendering` is fixtured under `svg/longtail.json` (its parser lives with the SVG set). The effects phase already owns all mask/clip-path properties.

## Parser gaps and platform-handoff notes

One-line notes per property family, aimed at the platform-agent (Android /
iOS / Web) who will write the Config/Extractor/Applier triplets.

### Scrolling

- **`OverflowClipMargin`** — accepts `<box>`, `<length>`, or `<box> <length>`. Single-value lowercase-only; mixing case (e.g. `Content-Box`) falls through to length parser and returns null.
- **`ScrollbarGutter`** — only accepts `auto` or any value containing `stable`; "both-edges" is looked up by substring, not position-sensitive.
- **`ScrollbarColor`** with 2 tokens: both must be valid CSS colors — if the first parses as a color but the second doesn't, the parser returns null (no partial).
- **`ScrollMarkerGroup`** only accepts `none | before | after`; spec also lists `<name>` tokens but parser rejects them.
- **`ScrollTargetGroup`** accepts `none` or anything else (wraps in `Named(...)`) — **never returns null**. Appliers must validate themselves.
- **`ScrollStart`** / **`ScrollStart{X,Y,Block,Inline}`** accept keywords OR length/percentage; `%` routes through `IRPercentage`, other units through `IRLength`.
- **`OverscrollBehavior`** two-token form silently accepts only the X/Y pair; 3+ tokens or axis-specific keywords are not valid.

### SVG

- **`StrokeWidth`** uses `LengthParser` so it rejects unitless values (`stroke-width: 3` is invalid here). The fixture uses `em` and `px` only.
- **`FillOpacity`** / **`StrokeOpacity`** range-check `0..1`; out-of-range returns null (no clamp).
- **`Fill`** / **`Stroke`** have distinct `None`, `ContextFill`, `ContextStroke`, `UrlReference(url, fallbackColor?)`, `ColorValue` variants; `Stroke` additionally has a `Raw` catch-all for unparseable text, `Fill` returns null instead.
- **`StrokeDasharray`** tries lengths → numbers → raw mixed. Comma-or-whitespace separated. `none` becomes a distinct variant.
- **`StrokeDashoffset`** additionally accepts unitless numbers and percentages (besides lengths and global keywords).
- **`ColorInterpolation*`** / **`ColorRendering`** / **`ShapeRendering`** are case-sensitive after lowercasing, but accept the CSS-spec camelCase tokens (`sRGB`, `linearRGB`, `optimizeSpeed`, etc.) because they're lowercased first.
- **`EnableBackground`** accepts only `accumulate` or `new [x y w h]?`. 2/3/4-value forms return null — only 1 or 5 tokens work.
- **`Marker{Start,Mid,End}`** — `none` is stored as `null` (not a sentinel); appliers checking `value == null` should treat that as "none".
- **`Cx/Cy/R/Rx/Ry/X/Y`** always return a property (raw fallback) — they never fail. `D` returns null only for empty strings, otherwise raw path string.
- **`FloodOpacity`** is the only opacity parser that accepts `%`.

### Speech / aural

Parse-only on every platform — no mobile/desktop/web audio pipeline emits
CSS speech properties. Appliers should no-op with a `PropertyTracker` TODO.
Notes:

- **`Volume`** and **`VoiceVolume`** share `VolumeValue` sum type (keyword, percentage, number, raw, global).
- **`SpeakAs`** accepts any whitespace-separated tokens unchecked — even invalid ones become strings.
- **`VoiceFamily`** splits on comma, no validation.
- **`VoiceRate`/`VoicePitch`/`VoiceRange`/`VoiceStress`/`SpeechRate`/`Pitch`** are stored as raw trimmed strings — no keyword validation at all.
- **`VoiceBalance`/`PitchRange`/`Richness`/`Stress`** are plain numbers, no keyword fallback.
- **`Azimuth`** is the most complex: 12 named positions + `behind` modifier + angle fallback + Raw catch-all.
- **`Elevation`** 5 named positions or an angle — returns null for anything else.
- **`Cue*`** only accept `none` or `url(...)` — no other forms.

### Rendering / imaging

- **`ImageOrientation`** accepts `<angle> flip?` OR `none | from-image`. `flip` at end with no angle means 0deg flip.
- **`ImageResolution`** converts DPI and DPCM to DPPX internally; raw `<n>dppx` is stored unconverted.
- **`Zoom`** has four variants (Normal, Reset, Percentage, Number) — no length form.
- **`ForcedColorAdjust`** has a `Raw` catch-all (never returns null) — appliers must validate.
- **`ContentVisibility`**, **`FieldSizing`**, **`InputSecurity`**, **`InterpolateSize`**, **`PrintColorAdjust`** are strict enums.

### Print / regions / paging / navigation / math (no-mobile-analog)

These are all "parse but no-op on mobile" per CLAUDE.md:

- **`Page`** always succeeds — `auto` or anything else becomes `Named(value)`.
- **`BookmarkLabel`** always succeeds — `attr(x)` → Attr, else Content(value).
- **`BookmarkTarget`** strict — only `self | url(...) | attr(...)`, else null.
- **`Size`** accepts named sizes (a3–a5, b4–b5, jis-b4/5, letter, legal, ledger) optionally followed by orientation, or `portrait`/`landscape` alone, or 1–2 lengths.
- **`Leader`** accepts `dotted | solid | space` or any string (quoted or not).
- **`MarginBreak`**, **`FootnoteDisplay/Policy`**, **`RegionFragment`**, **`Continue`**, **`WrapFlow/Through/Before/After/Inside`** are strict enums.
- **`FlowInto`/`FlowFrom`** accept anything (non-`none` → `Named`).
- **`CopyInto`** stores raw string unchecked.
- **`Nav{Up,Down,Left,Right}`** accept `auto` or any other string (strips leading `#`).
- **`PageBreak{Before,After,Inside}`** have `Raw` catch-alls and a `Keyword` global-keyword path.
- **`MathDepth`** has: `auto`, `auto-add`, `add(N)`, integer, Raw fallback, global keywords.

### Interactions

- **`Cursor`** with `url(...)` requires a fallback keyword after the URL; parser returns null if no valid keyword fallback is found.
- **`Caret`** shorthand parses whichever tokens it can and allows either/both color and shape to be absent — but **returns null if neither is present**.
- **`TouchAction`** multi-value: every token must be valid or the whole thing fails.
- **`PointerEvents`** has a `Raw` catch-all; unknown values become Raw.
- **`Appearance`** has a `Raw` catch-all; appliers must filter.

### Performance

- **`Contain`** rejects unknown keyword in a multi-value list (all-or-nothing).
- **`WillChange`** treats any non-`scroll-position`/`contents` token as a PropertyName — no validation that the name corresponds to an animatable CSS property.
- **`ContainIntrinsic*`** accept `none | auto | auto <length> | <length>`.

### Columns

- **`ColumnGap`** accepts `normal | <length-percentage>`; expression-like values fall into `Raw`.
- **`ColumnRuleWidth`** has keywords + length only (no `calc()` etc.).
- **`ColumnCount`** requires positive integer ≥ 1.

### Shapes

- **`ShapeOutside`** has `Keyword`, `Raw`, `None`, shape-box (4 variants), `ImageUrl`, `BasicShape(raw string)` — the basic-shape content isn't parsed, just detected and stored.
- **`ShapeMargin`** has a `Raw` catch-all (never fails) — length or % or Raw.
- **`ShapeInside`** / **`ShapePadding`** strict.
- **`ShapeImageThreshold`** range-checked `0..1`.

### Table / counters / lists / container / content / global / appearance / images / rhythm / experimental

These are mostly tight strict enums or straightforward keyword+value splits;
see individual parsers for gaps. Noteworthy:

- **`CaptionSide`** has a `Raw` catch-all — unknown keywords degrade to raw.
- **`BorderSpacing`** only accepts 1 or 2 tokens (not 3+).
- **`Counter{Increment,Reset,Set}`** pair tokens greedily: `a 1 b 2` → `[(a,1),(b,2)]`, `a b` → `[(a,1),(b,1)]` (for increment/reset) but `CounterSet` requires every name be followed by an integer, else null.
- **`ListStyleType`** has ~30 named keywords + `symbols(...)` function + quoted strings + any lowercase ident (falls through to CustomString).
- **`Container`** shorthand: bare `size`/`inline-size`/`normal` means type-only (name=null); bare other ident means name-only (type=normal); `name/type` form requires both sides.
- **`Content`** is huge — 10+ value kinds, quote-aware tokenizer, `url(...) / "alt"` syntax, multi-part lists.
- **`StringSet`** experimental parser stores `(name, rest-of-string)` — no structural parsing of the content list.

## Hand-off note for platform agents

For **speech**, **regions**, **navigation**, **math**, **print**, **paging**:
no-op these properties on every target. Register the extractor so
`PropertyRegistry.allRegistered()` reports coverage, but the applier should
be a no-op with a single-line "not applicable to <platform>" comment and a
`PropertyTracker.markNotApplicable(property)` call.

For **scrolling**, **svg**, **interactions**, **performance**, **columns**,
**table**, **shapes**, **lists**, **container**, **content**, **images**,
**appearance**: these have real cross-platform analogs. See per-property
Extractor notes in each Phase-10 PR when it opens.
