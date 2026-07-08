# IR v1 — 01: The envelope

**Status: normative.** This section documents what the converter *actually
emits today* (IR v1). It is machine-checked by `schema/ir-v1.schema.json`
and the golden fixtures under `schema/conformance/fixtures/`.

Implementing code:

- `converter/src/main/kotlin/app/irmodels/IRDocument.kt` — `IRDocument`,
  `IRComponent`, `IRSelector`, `IRMedia`, and the custom
  `IRComponentSerializer` that controls field omission.
- `converter/src/main/kotlin/app/irmodels/IRPropertySerializer.kt` — the
  `{type, data}` property envelope.

## Document

```json
{ "components": [ <component>, ... ] }
```

- `components` is **always an array** on the wire, even though the
  authoring-side input keys components by name (see 03-children.md for the
  map→array flattening rule, which applies at every nesting level).
- No other top-level keys exist in v1. There is **no version field** —
  v1 is implicit (see 05-versioning.md).
- Validators reject unknown top-level keys (`additionalProperties: false`).

## Component

Emitted by `IRComponentSerializer.serialize` in this key order:

| key | type | presence | rule (from the serializer) |
|---|---|---|---|
| `id` | string | always | non-empty; converter generates `<lowercased-name>-<NNN>` (`CssParsing.convertToIR`), extractor children keep WPT ids like `color-001__1__0` |
| `name` | string | always | component/class name; for flattened children the input map key becomes the name verbatim |
| `properties` | array of property envelopes | always | may be `[]` (every declaration was invalid and dropped) |
| `selectors` | array of selector buckets | omit-when-empty | present ⇒ non-empty |
| `media` | array of media buckets | omit-when-empty | present ⇒ non-empty |
| `children` | array of components | omit-when-null-or-empty | present ⇒ non-empty; **always an array** (03-children.md) |
| `_text` | string | omit-when-null | empty string `""` is a legal emitted value (means "extracted, was empty" — see the serializer comment at `IRDocument.kt`) |
| `_role` | string | omit-when-null | only emitted value today: `"body-root"` (04-metadata-fields.md) |

`_tag` and `_pseudo` are part of the wire vocabulary (the runtimes read
them — `runtimes/web/src/core/ir/IRModels.ts`) but are **not currently
emitted by the converter**; see 04-metadata-fields.md for the honest
status of each underscore field.

## Property envelope

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
  02-values.md; leaf strictness arrives with v2 (05-versioning.md).
- Two flattening passes shape `data` before emission
  (`IRPropertySerializer`): single-field unwrap (`{"value": X}` → `X`)
  and deep-flatten of `{type-discriminator + single object field}`
  (`{"type":"length","length":{"px":10}}` → `{"type":"length","px":10}`).
  Consequences of these passes are called out per-shape in 02-values.md.

## Selector / media buckets

```json
{ "condition": "hover",             "properties": [ <property>, ... ] }
{ "query": "(min-width: 768px)",    "properties": [ <property>, ... ] }
```

- `condition` stores the pseudo-class **without** the leading colon
  (`:hover` → `"hover"`), per `IRSelector` KDoc in `IRDocument.kt`.
- `query` is the raw media query string, parentheses included.
- Both buckets are strict: exactly the two keys shown.
