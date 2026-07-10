# Golden IR fixtures (v1 in this directory, v2 under `v2/`)

Hand-authored IR documents, one **shape family** each. Every byte shape
here was verified against actual converter output (run the probe:
`./gradlew :converter:run --args="convert --from css --to ir -i <input> -o <out>"`)
— these are not aspirational documents, they pin what the code emits
today. JSON has no comments, so the commentary lives here.

## Layout

- **This directory** — the 12 IR **v1** goldens (legacy nested wire,
  still produced by the deprecated `--emit-ir v1`). They stay
  validatable for the whole deprecation window (spec 05), then become
  historical record.
- **`v2/`** — the IR **v2** goldens (flat list + slot, `irVersion`
  envelope, `text`/`pseudos`/`meta` names): a 1:1 v2 mirror of each v1
  golden (same filename — same shape family, flattened + renamed) plus
  two v2-only composition goldens:

| v2-only fixture | shape family pinned | sentinel expectations |
|---|---|---|
| `v2/slot-composition.json` | 3-level composition via child-side `slot` refs (spec 03 §1–2): pre-order flat array, contiguous subtrees, roots slot-free, default slot name omitted | 6 components; `cell__0__0-003` slot-walks to root in 2 hops; sibling bands share `slot.parent` in source order |
| `v2/placement-claims.json` | ITEM-scoped placement claims as plain `{type,data}` envelopes on children (spec 03 §3 — no scope byte on the wire): grid area/line/span claims, flex grow/shrink/basis + align-self + order, z-index + position/insets | grid host declares templates, children declare Grid\*/Flex\*/ZIndex/Position claims; converter emitted, not hand-typed |

Both v2-only goldens are **verbatim converter output** (the authoring
inputs live in the freeze PR description); regenerate by re-running the
converter, never by hand-editing.

Consumed by:

- `schema/conformance/run.mjs` — every v1 fixture must validate against
  `schema/ir-v1.schema.json`; every `v2/` fixture against
  `schema/ir-v2.schema.json`.
- `converter/src/test/kotlin/app/schema/SchemaConformanceTest.kt` —
  asserts the converter still *emits* these shapes from CSS input.
- `runtimes/web/tests/conformance.test.ts` — decodes every fixture
  through `buildStyles` (the real web IR path).
- `runtimes/compose/src/test/java/com/styleconverter/runtime/schema/SchemaConformanceTest.kt`
  — decodes every fixture through the runtime `IRDocument` model.
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/ConformanceTests.swift`
  — decodes every fixture through `JSONDecoder` + `IRValue`.

| fixture | shape family pinned | sentinel expectations |
|---|---|---|
| `lengths-all-forms.json` | IRLength dual storage (spec 02): px-only `{"px":16}`, absolute dual `{"px":16,"original":{v,u:"PT"}}`, relative original-only (EM/REM, no `px`), plus the PaddingValue escapes — percent as raw number, `auto` as raw string | PaddingTop → 16px on every platform; EM data has no `px` key; `PaddingLeft` data is the number `10.0` |
| `colors.json` | IRColor sRGB dual storage: hex/named → string original, rgb/hsl → structural original, oklch → `type`-tagged original, `transparent` (srgb a=0), `currentColor` (dynamic: **no** srgb) | BackgroundColor srgb.r ≈ 0.2039; AccentColor has `original` but no `srgb` |
| `shape-radius.json` | ShapeRadius asymmetric-by-design (spec 02): circle length deep-flattens to `{"type":"circle","px":40}` (no `r` key!), circle keyword keeps `"r":"farthest-side"`, ellipse two-keyword, polygon raw-number percent points | `farthest-side` survives verbatim; polygon point x=50 is a bare number |
| `text-and-role.json` | `_text`/`_role` omit-when-null (spec 04): populated, empty-string `_text` (legal!), and fully absent | `_text` round-trips; `_role:"body-root"` present in raw JSON; absent fields stay null |
| `children-nesting.json` | array-out children contract (spec 03): two-level nesting, full-component children, omit-when-empty (`LeafSibling` has no `children` key) | grandchild `_text` reachable at depth 2; child `name` keeps the extractor-map key `child__0` |
| `selectors-media.json` | selector/media buckets (spec 01): `condition` without leading colon, raw `query` string, omit-when-empty on the sibling | hover bucket holds a full property envelope; second component has neither key |
| `opacity-and-numbers.json` | number-family data objects: Opacity `{alpha, original}`, ZIndex integer, LineHeight multiplier, and the FlexGrow FQ-class-name discriminator wart (spec 02 known defect 2) | alpha 0.5; nested FQ `type` string treated as opaque, no crash |
| `transform-list.json` | Transform `{"type":"functions","list":[…]}` with per-fn shapes (`x:{px}`, `a:{deg}`, bare-number scale) + Rotate angle dual storage | rotate deg 45 inside the list; Rotate 0.5turn → deg 180 |
| `unknown-property-tolerance.json` | versioning rule 1 (spec 05): unknown `type` MUST be skipped without crashing; includes object-data unknown, primitive-data unknown, and the real `Generic` envelope (`_unmapped: true`) | Width/PaddingTop still extract next to the unknowns on every platform |
| `property-envelope-edge.json` | envelope edges: primitive string/number data, enum-name strings (`NONE`/`HIDDEN`), empty-object data, array data (TransitionDelay), FQ-discriminator list (AnimationDuration), empty `properties` list | decode survives `data:{}` and `properties:[]`; TransitionDelay ms 150 |
| `background-gradient.json` | BackgroundImage array-of-layers with linear-gradient: angle `{deg}`, stops with color dual storage and explicit `"position": null` leaves | two stops; `null` leaf inside data is legal |
| `font-weight-keywords.json` | FontWeight keyword dual storage (`bold`→`{weight:700,original:"bold"}`, `normal`→400) vs numeric raw-primitive `700` | weight 700 extracted from both the object and the primitive form |

Editing rules: a fixture change means the **wire contract changed** —
that is a v2-freeze event, not a casual PR (spec 05). Adding a new
fixture (new shape family) is fine; keep it 2–4 components, hand-written,
mirroring verified converter output.
