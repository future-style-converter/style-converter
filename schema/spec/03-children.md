# IR v2 — 03: Flat list, slot & placement (the composition contract)

**Status: normative for IR v2** (the default wire since the v2 freeze).
The legacy v1 nested-children contract this section replaces is preserved
at the end for the deprecation window; `schema/ir-v1.schema.json` remains
its machine check.

Implementing code:

- flattener: `converter/src/main/kotlin/app/parsing/IRFlattener.kt`
  (pre-order walk, slot stamping, duplicate-id error)
- wire codec: `converter/src/main/kotlin/app/irmodels/IRWireV2.kt`
  (`IRComponentV2Serializer` — flat-only, hard error on `children`)
- scope classification: `converter/src/main/kotlin/app/irmodels/PropertyScope.kt`
- machine check: `schema/ir-v2.schema.json` + the goldens under
  `schema/conformance/fixtures/v2/` (notably `slot-composition.json` and
  `placement-claims.json`)

## 1. The flat list

A v2 document's `components` is a **flat array**: every component at
every composition depth is a standalone entry.

```json
{
  "irVersion": 2,
  "minReaderVersion": 2,
  "components": [
    { "id": "card-001",  "name": "Card",  "properties": [ … ] },
    { "id": "thumb-007", "name": "Thumb", "properties": [ … ],
      "slot": { "parent": "card-001" } }
  ]
}
```

- **`children` does not exist in v2.** A v2 document containing a
  `children` key anywhere is a **hard validation error**
  (`additionalProperties: false` in the schema; the converter's v2
  reader throws on it).
- **Sibling-order rule:** the relative order of entries sharing the same
  `slot.parent` in the flat array IS the composition order (DOM order /
  Compose emission order / SwiftUI subview order). The flattener produces
  this via pre-order traversal, so v1→v2 conversion is order-stable.
  Item-scoped `order` re-sorts *visually* at layout time; array order
  still governs paint-order ties and accessibility order — exactly CSS
  semantics.
- **Duplicate `id` → convert-time error.** Slot references would become
  ambiguous, so `IRFlattener` refuses the document outright (never a
  warning).

## 2. The child→parent reference: `slot` (no underscore)

```json
"slot": { "parent": "card-001", "name": "content" }
```

- `slot.parent` (required inside the object): the `id` of the container
  this component composes into. `slot.name` is optional, defaults to
  `"content"` (and is omitted on the wire at the default); it is reserved
  for future multi-slot containers (scaffold header/body/footer). Roots
  omit `slot` entirely.
- **Not underscore-prefixed — by rule.** The `_` prefix is reserved for
  *droppable renderer hints* (metadata a consumer may ignore without
  correctness loss — see 04-metadata-fields.md). `slot` is structural
  composition data that **MUST round-trip** through every reader. (The
  v1 audit showed Android's `ignoreUnknownKeys` silently dropped `_role`;
  that failure mode is forbidden for `slot`.)
- **Consumers:** ONLY composers — the preview harness on each platform,
  and later an SDUI shell that chooses to use it. The style engines
  themselves **MUST ignore `slot`** — engines are composition-agnostic by
  contract.
- **Dangling `slot.parent`** (no component with that id in the document):
  the composer treats the entry as a **root and emits a warning**. Never
  a crash, never a dropped component. (Goldens carry no dangling refs —
  the converter cannot produce them.)

### Mode B — zero-slot documents (documented, first-class)

A valid v2 document may carry **no `slot` fields at all**: composition is
supplied entirely externally (an SDUI backend layout tree, or host-app
code passing children). Because container output is an open slot (§3),
Mode B requires zero engine changes — it is the SDUI production mode;
Mode A (slots) is the preview/harness mode.

## 3. Style ownership: PropertyScope

Every property has exactly one ownership scope
(`converter/src/main/kotlin/app/irmodels/PropertyScope.kt`; mirrored by
each platform `PropertyRegistry` so routing is machine-checkable by
`tools/visual/coverage-audit.mjs`):

- **CONTAINER-scoped** (declared on the container; its layout policy for
  whatever arrives; never mentions children): `display`,
  `flex-direction`, `flex-wrap`, `flex-flow`, `justify-content`,
  `align-items`, `align-content`, `place-content`, `place-items`,
  `justify-items`, `gap`/`row-gap`/`column-gap`,
  `grid-template-columns/rows/areas`, `grid-template`, `grid`,
  `grid-auto-columns/rows/flow`, `align-tracks`/`justify-tracks`,
  `masonry-auto-flow`, `column-*` (multicol), and the CSS Gap
  Decorations L1 family — `row-rule`/`row-rule-width|style|color`,
  `column-rule-break`/`row-rule-break`,
  `column-rule-inset`/`row-rule-inset`, `rule-overlap` (declared on the
  container, painted in the container's own gutters).
- **ITEM-scoped** (declared on the child; packaged as parent-data by the
  runtimes; activates only when it lands in a matching container):
  `align-self`, `justify-self`, `place-self`, `order`, `flex-grow`,
  `flex-shrink`, `flex-basis`, `flex`, `grid-area`,
  `grid-column(-start/-end)`, `grid-row(-start/-end)`, `z-index`, and
  `position:absolute/fixed` + `top/right/bottom/left/inset*`.
- **SELF-scoped:** everything else (the ~530 remaining properties).

**Wire decision (frozen):** scope is **not emitted on the wire**. The v2
wire carries properties as plain `{type, data}` envelopes on whichever
component declared them; ITEM claims simply live on child components
(see `fixtures/v2/placement-claims.json`). The runtimes do placement
routing — each container's layout implementation reads the arriving
children's item claims and consumes ONLY the block matching its own
container kind, applying CSS initial values for absent fields. Item
props for a container kind never landed in are inert by design (logged
at debug level by PropertyTracker, no error, no visual effect — same as
`grid-area` on a flex child in a browser).

## 4. Edge cases (frozen with the contract)

1. **Text (`text`):** stays ON the component as leaf content — text is
   content, not a sibling component, so it does not flatten into the
   list. The leading text run renders before injected children —
   **unless** the component also carries `meta.runs` (§4.1), the ordered
   inline-content list that supersedes that placement. (Before wave-32
   the interleaved case had no wire at all and this clause deferred it
   to "a future optional `contentRuns` field"; `meta.runs` IS that
   field, landed under the 05-versioning.md additive meta-key rule.)
2. **Generated content (`pseudos`):** never flattened. Pseudo components
   have no independent lifecycle, can never be composed externally, and
   per CSS spec `::before`/`::after` of a grid/flex container ARE items
   of that container — so the runtime treats them as implicit first/last
   entries in the container's open slot (marker → before → text →
   [injected children] → after). They remain embedded in the host's
   payload.
3. **z-order across siblings the parent doesn't know:** `z-index` is
   ITEM-scoped. Compose `Modifier.zIndex()` and SwiftUI `.zIndex()` both
   operate purely among siblings of whatever parent they end up in; CSS
   is native. Ties resolve by flat-array order (§1), matching CSS
   painting order.
4. **Absolutely-positioned children escaping flow:** `position:
   absolute/fixed` + insets are ITEM-scoped. Contract: the containing
   block is the **immediate slot parent** — legal because whether the
   parent is a containing block (`position: relative`) is the parent's
   OWN property, requiring no child knowledge. Escaping through a
   non-positioned parent to a distant ancestor is explicitly
   **UNSUPPORTED** in v2 (affected WPT titan cases get a not-applicable
   rule).

### 4.1 Inline runs: `meta.runs` (the anonymous-run box)

`text` is ONE string and the flat list has ONE sibling order, so a
component can express `run + children` or `children + run` — never
`run / child / run`. The moment a kept child sits BETWEEN two text
nodes (`the quick <u>brown</u> fox` with a styled `<u>`), the reading
order the browser paints is not the order the wire describes. Through
wave-31 the extractor said so out loud and shipped the concatenation
(the `inline-run-reordered` lossy reason, 1,203 components across 472
fixture files). `meta.runs` is the wire that fixes it.

**Shape.** An array, in DOCUMENT order, of entries that are each
*exactly one* of:

| entry | meaning |
|---|---|
| `{"text": "…"}` | an anonymous inline run — a bare text node at this position |
| `{"child": "<key>"}` | the child component with that authoring key sits at this position |

**Which key?** `child` carries the child's **authoring key** — the map
key the producer assigned — *not* its `id`. This is the one place
`meta.runs` deviates from `slot.parent`, and it is deliberate: the
converter **mints** ids at the flatten boundary
(`<lowercased-name>-<NNN>`, §5 rule 1), so a producer-written id names
nothing after the hop. The authoring key does round-trip — it becomes
the child's `name`. Readers resolve against `name` first and MAY fall
back to `id`; in the extractor-direct pipeline (extractor output handed
straight to the web renderer, no converter) the two are the same string,
so both resolutions agree. The alternative — teaching `IRFlattener` to
rewrite `runs.child` alongside `slot.parent` — was rejected because it
would make the converter a co-owner of an otherwise opaque hint (see
04-metadata-fields.md).

**Rules, all frozen with the key:**

1. **Order is the payload.** The array is the component's inline content
   in document order. Nothing else on the component encodes it.
2. **Authoritative when present.** A reader that honours `runs` paints
   the entries in order and MUST NOT *also* paint `text`, nor paint a
   referenced child a second time from the sibling walk.
3. **Additive, not a break.** `text` stays on the wire carrying the
   pre-wave-32 concatenation, so a reader that ignores `meta.runs`
   behaves exactly as it did before the key existed — the same lossy
   result, no decode failure. This is why the key rides `meta`
   (droppable hints) and not the structural envelope.
4. **Unreferenced children still render**, after the runs, in flat-list
   sibling order. The emitter references every kept child today; the
   rule exists so a partial `runs` can never make a box disappear.
5. **Dangling `child` keys are a renderer-side warn-and-skip**, mirroring
   the dangling-`slot.parent` rule (§2). The converter never resolves
   them — `meta.runs` is opaque all the way through the hop.
6. **Whitespace at a run boundary is meaningful** (it is the inter-run
   word space). Only the FIRST run is leading-trimmed and only the LAST
   run trailing-trimmed: the same CSS Text §4.1 trim the whole-element
   `text` gets, applied to the ends of the *sequence* instead of to
   every entry.
7. **An anonymous run is an INLINE box, not a block.** Renderers must
   emit it as a bare text node in the content walk — never as a
   synthesized child component with a placeholder box, because a block
   box inside an inline box splits it (CSS 2.1 §9.2.1.1) and would
   re-break the very line box the key exists to preserve.

## 5. The authoring input stays nested — forever

`CssComponent.children: Map<String, CssComponent>` remains the
authoring/extractor input shape (children keyed by stable extractor ids
like `color-001__1__0`). The converter permanently flattens at the wire
boundary:

1. The child's **map key becomes its `name`** (unchanged from v1); `id`
   is generated by the converter's pre-order counter
   (`<lowercased-name>-<NNN>`, zero-padded, shared across the document).
2. `IRFlattener` walks the parsed tree pre-order, appends each component
   to the flat list, stamps `slot.parent` with the parent's id, and
   strips the nested list.
3. Because v1's id counter was already depth-first pre-order, flat-array
   order and every generated id are **identical** between a v1 and a v2
   emission of the same input.

## Appendix: the legacy v1 contract (deprecation window only)

The v1 wire ships children **nested**, as an array of full components at
every depth (map-in / array-out): the input map key becomes the child's
`name`, `children` is omitted when null/empty and never empty when
present, and renderers consume the array positionally. `--emit-ir v1`
still produces this shape byte-identically; it is removed, along with
this appendix, at the end of the deprecation window
(see 05-versioning.md).
