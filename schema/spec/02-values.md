# IR v1 — 02: Value shapes

**Status: normative for emitters, documented-not-enforced for validators.**
The JSON Schema is deliberately permissive below the property envelope
(`data: true`); these are the shapes the serializers **actually emit
today**. Golden fixtures under `schema/conformance/fixtures/` pin one
fixture per shape family.

Implementing code: `converter/src/main/kotlin/app/irmodels/ValueTypes.kt`
(all primitive serializers), `converter/src/main/kotlin/app/irmodels/properties/effects/ClipPathSerializers.kt`
(the asymmetric-by-design ShapeRadius pattern),
`converter/src/main/kotlin/app/irmodels/IRPropertySerializer.kt` (flattening).

## Lengths — `IRLengthSerializer`

Dual storage: normalized pixels when the unit is absolute, original
value+unit when it isn't `px`:

| CSS input | wire form |
|---|---|
| `16px` | `{"px": 16.0}` |
| `12pt` | `{"px": 16.0, "original": {"v": 12.0, "u": "PT"}}` |
| `2em`  | `{"original": {"v": 2.0, "u": "EM"}}` (no `px` — relative unit) |

`u` values are the `IRLength.LengthUnit` enum names (`PX`, `PT`, `EM`,
`REM`, `PERCENT`, `VW`, … — see `ValueTypes.kt` for the full list).
`null` pixels means *runtime-dependent* — `em`, `%`, `vw`, `var()`,
`calc()` cannot be pre-computed (project-wide rule, see CLAUDE.md).

**Context-dependent keyword/percentage escapes** (per-value-type
serializers in `ValueTypes.kt`):

- `PaddingValue` / `MarginValue` / `SizeValue` etc. emit percentages as a
  **raw number** (`"padding-left": "10%"` → `"data": 10.0`) and keywords
  as a **raw string** (`"margin-left": "auto"` → `"data": "auto"`).
- Expressions emit `{"expr": "calc(10px + 5%)"}`.

## Colors — `IRColorSerializer` (sRGB dual storage)

```json
{"srgb": {"r": 0.2, "g": 0.6, "b": 0.86}, "original": <representation>}
```

- `srgb` components are 0–1 floats; `a` omitted when `1.0`
  (`SRGBSerializer`). `srgb` itself is omitted for dynamic colors
  (`currentColor`, `var()`, `color-mix()` …) that cannot resolve at parse
  time.
- `original` dispatches by structure (`ColorRepresentationSerializer`):
  hex/named/`transparent`/`currentColor` → raw string; rgb → `{r,g,b[,a]}`
  with 0–255 ints; hsl → `{h,s,l[,a]}`; modern spaces carry a `type` tag
  (`{"type":"oklch","l":…,"c":…,"h":…}`, `lab`, `hwb`, `color`,
  `color-mix`, `light-dark`, `relative`).
- **Lab-family channel scales** (pinned wave-48; the percentage-mapping
  tables of css-color-4 §9.1–§9.4 for `lab()`/`lch()`/`oklab()`/`oklch()`):
  `lab`/`lch` carry `l` on the spec's **0–100** scale (`lab(50% …)` →
  `"l": 50.0`), while `oklab`/`oklch` carry `l` on the canonical **0–1**
  scale (`oklab(86.64% …)` → `"l": 0.8664`). Chroma/axis channels are
  **absolute** values on the spec's reference ranges — authored `100%`
  maps to ±125 for `lab` `a`/`b`, 150 for `lch` `c`, ±0.4 for `oklab`
  `a`/`b`, and 0.4 for `oklch` `c`. Readers that re-emit these originals
  as CSS text may rely on this scale (the web runtime's typed-stop path
  does); an ok-space `l` on a 0–100 scale is a pre-wave-48 wire defect,
  not a valid encoding — readers should treat it as unrepresentable and
  fall back to `srgb`.
- Flattening side effect: single-color properties whose Kotlin class also
  has a discriminator (e.g. `CaretColor`) emit
  `{"type": "color", "srgb": …, "original": …}` — readers must not choke
  on the extra `type` key.

## ShapeRadius — the asymmetric-by-design pattern

`ClipPathSerializers.kt` / `ShapeRadiusSerializer`. CSS
`<shape-radius>` is `<length-percentage> | closest-side | farthest-side`
— two structurally distinct forms, dispatched on JSON node kind:

- keyword → **raw string**: `circle(farthest-side)` →
  `{"type": "circle", "r": "farthest-side"}`
- length → **IRLength object** — but note the deep-flatten pass: because
  `{"type":"circle","r":{"px":40}}` has exactly one non-type object field,
  it is inlined to `{"type": "circle", "px": 40.0}` (the `r` key
  disappears). With two radii (`ellipse`) no flattening occurs:
  `{"type": "ellipse", "rx": "closest-side", "ry": "farthest-side"}`.

Readers MUST accept both node kinds per axis. Polygon points are
`{"x": <number>, "y": <number>}` where a **raw number means percentage**
(legacy compat, `IRLengthPercentageSerializer`) and an object is an
IRLength.

## Angles / times — `IRAngleSerializer` / `IRTimeSerializer`

Same dual-storage idiom as lengths, with normalization always present:

- `45deg` → `{"deg": 45.0}`; `0.5turn` → `{"deg": 180.0, "original": {"v": 0.5, "u": "TURN"}}`
- `300ms` → `{"ms": 300.0}`; `0.3s` → `{"ms": 300.0, "original": {"v": 0.3, "u": "S"}}`

## Numbers, keywords, enums

- `IRNumber` / `IRPercentage` → raw JSON number (no wrapper).
- `IRKeyword` → raw JSON string.
- Kotlin enum values serialize as their **UPPER_SNAKE names** unless a
  `@SerialName` overrides them: `display: flex` → `"data": "FLEX"`,
  `visibility: hidden` → `"data": "HIDDEN"`.
- Font weight: keyword inputs keep dual storage
  (`bold` → `{"weight": 700, "original": "bold"}`), numeric inputs
  flatten to a raw number (`700` → `"data": 700`).

## Custom properties, `var()` substitution, and dynamic expressions

**Status: normative (IR v2 additive minor revision — see 05-versioning.md
change process).** Goldens: `schema/conformance/fixtures/v2/variables-basic.json`,
`variables-inheritance.json`, `calc-mixed.json`.

### Definitions — the component-level `variables` map

A custom-property **declaration** (`--name: <value>`) does NOT become a
`{type, data}` property envelope. The converter lifts it into the
component-level `variables` map (01-envelope.md v2 component table):

```json
"variables": { "--brand-bg": "#0f62fe", "--space-2": "12px" }
```

Two rules carry over verbatim from css-variables-1 §2 and are pinned by
`CustomPropertyParserTest`:

- **Names are case-sensitive** (`--Main` ≠ `--main`) — the only names in
  the whole IR that never pass through lowercase normalization.
- **Values are raw, untyped token streams** until substitution: no px /
  sRGB / degree normalization is possible, so the wire carries the
  declaration value byte-for-byte (the empty string is a legal value —
  `--x:;` is valid CSS). Emission order is authoring order.

### References — the preservation contract

A `var()` **reference** (and any `calc()` / relative-unit expression)
stays inside its normal property envelope, **unresolved**, under the
project-wide null+original rule (`null` means runtime-dependent): the
normalized slot is absent/null and the ORIGINAL expression survives
verbatim — carrier shape varies per value family (`{"expr": …}`,
`{"original": …}`, raw string), but the bytes of the expression never
change. Pinned by `VarCalcPreservationTest` across the core visual
properties, including nested fallbacks (`var(--a, var(--b, 4px))`) and
mixed-unit calc (`calc(100% - 24px)`, `calc(2em + 4px)`,
`calc(var(--space-2) * 2)`).

### Resolution order (runtime semantics)

Substitution happens in the **runtimes**, never in the converter. For a
`var(--x, <fallback>)` reference on component `C`:

1. **Element scope:** if `C.variables` defines `--x`, use that value.
2. **Slot-parent chain:** otherwise walk `C.slot.parent` transitively
   (child → container → … → root, the same chain style inheritance
   follows in 03-children.md) and use the first definition found.
   Roots (no `slot`) end the walk.
3. **Fallback:** otherwise, if the reference carries a fallback
   (css-variables-1 §3 — fallbacks may nest arbitrarily), resolve the
   fallback by the same rules.
4. **Guaranteed-invalid:** otherwise the reference is
   *invalid at computed-value time* (css-variables-1 §3): the declaration
   behaves as `unset` (inherited properties inherit; others take their
   initial value). Never render the literal `var(…)` string; log via
   PropertyTracker (no silent fallthroughs).

Scope note for this revision: `variables` is a **base-declaration**
surface only — custom properties inside selector/media buckets are
dropped by the parser (logged), pending a future revision if a use case
appears.

## Known defects (fix at the v2 freeze, do NOT rely on)

1. **The phantom `"u"` discriminator.** Several legacy *deserializers*
   in `ValueTypes.kt` (`PaddingValueSerializer`, `MarginValueSerializer`,
   `ScrollPaddingValueSerializer`, `BorderRadiusValueSerializer`,
   `PositionValueSerializer`, `SizeValueSerializer`,
   `AnimationRangeValueSerializer`) detect the Length branch via
   `element.containsKey("u")` — but **no serializer ever emits a
   top-level `"u"` key** (`IRLengthSerializer` nests it under
   `"original"`). Round-tripping a relative length through those readers
   therefore mis-classifies it as a percentage. v1 documents the *emitted*
   forms above as the contract; the reader bug is scheduled for the v2
   freeze.
2. **Fully-qualified class names leak into nested discriminators.** Some
   plugin-generated sealed types serialize their kotlinx discriminator as
   the full class path, e.g. `FlexGrow` emits
   `"type": "app.irmodels.properties.layout.flexbox.FlexGrowProperty.FlexGrowValue.Number"`.
   Readers must treat nested `type` strings as opaque. v2 will pin short
   discriminators.
3. **`Generic` envelope.** Valid-but-unparsed declarations emit
   `{"type": "Generic", "data": {"propertyName": …, "rawValue": …, "_unmapped": true}}`.
   Invalid property names are dropped entirely (PropertiesParser step 1)
   and never reach the wire.
