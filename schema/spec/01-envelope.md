# IR — 01: The envelope

**Status: normative.** This section documents the wire envelope in both
supported versions:

- **IR v2** — the flat-list slot/placement wire the converter emits **by
  default** since the v2 freeze. Machine-checked by
  `schema/ir-v2.schema.json` and the goldens under
  `schema/conformance/fixtures/v2/`.
- **IR v1** — the legacy nested wire, still produced by the deprecated
  `--emit-ir v1` flag for one deprecation window (05-versioning.md).
  Machine-checked by `schema/ir-v1.schema.json` and the goldens under
  `schema/conformance/fixtures/`.

Implementing code:

- `converter/src/main/kotlin/app/irmodels/IRWireV2.kt` — the v2 document
  envelope + `IRComponentV2Serializer`.
- `converter/src/main/kotlin/app/parsing/IRFlattener.kt` — nested→flat
  (pre-order, slot stamping, duplicate-id error).
- `converter/src/main/kotlin/app/irmodels/IRDocument.kt` — the shared IR
  model + the legacy v1 `IRComponentSerializer`.
- `converter/src/main/kotlin/app/irmodels/IRPropertySerializer.kt` — the
  `{type, data}` property envelope (identical in both versions).

## v2 document

```json
{
  "irVersion": 2,
  "minReaderVersion": 2,
  "components": [ <component>, ... ]
}
```

- `irVersion` states what the writer produced; `minReaderVersion` states
  the oldest reader that can safely consume it. Both are **mandatory**
  and both are `2` today. A reader MUST refuse a document whose
  `minReaderVersion` exceeds what it implements (05-versioning.md).
- A document **without** `irVersion` IS a v1 document (deprecation
  window only).
- `components` is a **flat array** — every component at every
  composition depth is a standalone entry; composition is expressed by
  the child-side `slot` reference (03-children.md). May be empty.
- `keyframes` (optional, **additive minor-revision key** — wave 8):
  document-level named keyframe sets,
  `name → [{offset: 0..1, properties: [{type,data}…]}]`, offset-sorted.
  Omit-when-empty so pre-motion documents stay byte-identical. Full
  contract: 07-animations.md §1; golden: `v2/keyframes.json`.
- No other top-level keys exist. Validators reject unknown top-level
  keys (`additionalProperties: false`).

## v2 component

Emitted by `IRComponentV2Serializer.serialize` in this key order
(`variables` — added as a sanctioned additive minor revision — sits
between `properties` and `selectors`):

| key | type | presence | rule (from the serializer) |
|---|---|---|---|
| `id` | string | always | non-empty; unique across the WHOLE document (duplicate = convert error); converter generates `<lowercased-name>-<NNN>` in pre-order |
| `name` | string | always | component/class name; for components flattened from the authoring map the map key becomes the name verbatim |
| `properties` | array of property envelopes | always | may be `[]`; ITEM-scoped placement claims live here on the CHILD (03-children.md §3) |
| `variables` | object | omit-when-empty | **additive minor-revision key** — CSS custom-property definitions declared on this component: `"--name" → raw value` verbatim (names case-sensitive, values untyped token streams per css-variables-1 §2; the empty string is legal). Keys match `^--.`; present ⇒ non-empty. `var()` *references* stay unresolved inside normal property envelopes — resolution is a runtime concern (02-values.md, custom-properties section) |
| `selectors` | array of selector buckets | omit-when-empty | present ⇒ non-empty (unchanged from v1) |
| `media` | array of media buckets | omit-when-empty | present ⇒ non-empty (unchanged from v1) |
| `slot` | object | omit for roots | `{parent, name?}` — structural, MUST round-trip; `name` omitted at the default `"content"` (03-children.md) |
| `text` | string | omit-when-null | v2 rename of `_text`; empty string `""` is a legal emitted value ("extracted, was empty") |
| `pseudos` | object | omit-when-null | v2 rename of `_pseudo`; `{before?, after?, marker?}` extractor-shaped payload forwarded verbatim, never flattened |
| `meta` | object | omit-when-empty | droppable hints grouped: `{sourceTag?, role?, attrs?, decorations?}` — v2 home of v1 `_tag`/`_role`, plus the wave-20 `attrs` widget-identity object (v2 home of the extractor's `_attrs`; present-in-source keys among type/value/checked/multiple/size/alt/min/max/selected/disabled, typed per the extractor contract) and the wave-22 `decorations` array (v2 home of `_decorations`: a collapsed inline run's ordered outermost-first `[{line, color?}]` list, colour tokens AS AUTHORED — see 04-metadata-fields.md). Present ⇒ non-empty and strict |

**`children` does not exist in v2** — its presence anywhere is a hard
validation error (03-children.md §1).

### The underscore rule after v2

v2 emits **no** underscore-prefixed component keys. `_text` became the
structural `text`; `_pseudo` became `pseudos`; `_tag`/`_role` grouped
into `meta`. The rule itself survives for future droppable hints
(04-metadata-fields.md), but the sanctioned extension point in v2 is a
new key inside `meta`, not a new top-level `_field`.

## Property envelope (identical in v1 and v2)

Every entry in any `properties` array is exactly:

```json
{ "type": "<IRTypeName>", "data": <any JSON value> }
```

- `type` is the Kotlin property class name with the `Property` suffix
  stripped (`PaddingTopProperty` → `"PaddingTop"`), computed by
  `IRPropertySerializer.getPropertyTypeName`. Unparsed-but-valid CSS
  declarations serialize as `type: "Generic"` with
  `data: {"propertyName": ..., "rawValue": ..., "_unmapped": true}`.
- `data` is **any JSON value**. Observed emitted kinds:
  - object — most properties (`{"px": 16.0}`, `{"srgb": {...}, "original": ...}`)
  - raw number — e.g. percentage padding (`"padding-left": "10%"` →
    `"data": 10.0`), numeric font-weight (`"data": 700`)
  - raw string — keywords (`"data": "auto"`) and enum names (`"data": "FLEX"`)
  - array — list-valued properties (`TransitionDelay`, `BackgroundImage`)
  - `null` and booleans occur as leaves *inside* objects
    (gradient stop `"position": null`; Generic `"_unmapped": true`)
- The envelope has **no other keys** (`additionalProperties: false`).
  The shape of `data` per property is documented (not enforced) in
  02-values.md; the v2 freeze added strictness ONLY for the composition
  structures (`slot`, `meta`) — full per-property leaf strictness
  remains future work.
- Two flattening passes shape `data` before emission
  (`IRPropertySerializer`): single-field unwrap (`{"value": X}` → `X`)
  and deep-flatten of `{type-discriminator + single object field}`
  (`{"type":"length","length":{"px":10}}` → `{"type":"length","px":10}`).
  Consequences of these passes are called out per-shape in 02-values.md.

## Selector / media buckets (identical in v1 and v2)

```json
{ "condition": "hover",             "properties": [ <property>, ... ] }
{ "query": "(min-width: 768px)",    "properties": [ <property>, ... ] }
```

- `condition` stores the pseudo-class **without** the leading colon
  (`:hover` → `"hover"`), per `IRSelector` KDoc in `IRDocument.kt`.
- `query` is the raw media query string, parentheses included.
- Both buckets are strict: exactly the two keys shown.

## Appendix: the v1 envelope (deprecation window only)

A v1 document is `{ "components": [ ... ] }` with **no version field**.
The v1 component emits, in order: `id`, `name`, `properties`,
`selectors` (omit-when-empty), `media` (omit-when-empty), `children`
(nested array of components, omit-when-null-or-empty), `_text`
(omit-when-null), `_role` (omit-when-null). `_tag` and `_pseudo` are
part of the v1 wire vocabulary (read by the runtimes) but were never
emitted by the converter — see 04-metadata-fields.md for that history.
`--emit-ir v1` reproduces this byte-identically until the flag is
removed.
