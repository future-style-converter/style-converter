# fixtures/fidelity/ — PROVENANCE

**Generated. Do not hand-edit.** Every file in this directory (including
`manifest.json` and this document) is emitted by a deterministic generator:

```bash
node tools/visual/gen-fidelity.mjs
```

- **Tool**: `tools/visual/gen-fidelity.mjs`
- **Seed**: `20260708` (mulberry32; the constant lives in the generator)
- **Inputs**: the harvested value variants of `fixtures/properties/<category>/*.json`
  and the IR property catalogue under `converter/src/main/kotlin/app/irmodels/properties/`.
- **Determinism pin**: `node --test tools/visual/gen-fidelity.test.mjs` fails if a
  regeneration is not byte-identical to the committed files.

Why no in-file marker: the CSS-side component envelope only tolerates
`properties/selectors/media/children/_text/_role` (CssParsing.parseComponent) and
the IR wire schema rejects unknown envelope keys (schema/spec/01-envelope.md), so a
`"_generated"` key inside fixtures would be a contract violation. Provenance lives
here and in `manifest.json` instead.

## Suites

- `<category>.combos.json` — intra-category clusters + cross-category standard
  recipes, 3–6 declarations per component.
- `pairwise/pairs-NN.json` — category-PAIR components: all 66 unordered pairs of
  the 12 visually-strongest categories (background, borders, color, effects,
  images, layout, lists, shapes, sizing, spacing, transforms, typography),
  4 components per pair, 2–3 harvested declarations from each side — hunts
  cross-category interaction bugs single-category combos cannot reach.
- `trees/*.json` — hand-designed parent→child templates (flex/grid/block flow +
  inheritance), PRNG-colored leaves.
- `placement/*.json` — IR v2 slot/placement contract stress: children carrying
  explicit placement claims (grid-area names, line numbers + spans, order
  permutations, align-self/justify-self overrides, z-index stacking among
  absolutely-positioned siblings, mixed claimed+unclaimed auto-flow interleave,
  and a dangling area claim). Authored CSS-side nested per
  schema/spec/03-children.md; the converter flattens to the v2 flat+slot form.
- `tokens/*.json` — dynamic-value suite (wave 6): custom-property definitions
  (`--name` declarations → the IR v2 component `variables` key), var()
  references consumed across the slot-parent chain (with shadowing), missing-
  var vs fallback vs nested-fallback chains, and calc()/relative-unit
  arithmetic (%−px against definite parents, em against inherited font-size
  chains, nested calc, calc-consuming-var). Components are built so
  resolution SUCCESS vs FAILURE changes visible pixels (spec 02
  custom-properties section).

## Inventory

| file | kind | components | nodes |
|---|---|---:|---:|
| `animations.combos.json` | combos | 11 | 11 |
| `appearance.combos.json` | combos | 4 | 4 |
| `background.combos.json` | combos | 6 | 6 |
| `borders.combos.json` | combos | 17 | 17 |
| `color.combos.json` | combos | 5 | 5 |
| `columns.combos.json` | combos | 5 | 5 |
| `container.combos.json` | combos | 4 | 4 |
| `counters.combos.json` | combos | 4 | 4 |
| `effects.combos.json` | combos | 11 | 11 |
| `experimental.combos.json` | combos | 4 | 4 |
| `images.combos.json` | combos | 4 | 4 |
| `interactions.combos.json` | combos | 6 | 6 |
| `layout.combos.json` | combos | 22 | 22 |
| `lists.combos.json` | combos | 4 | 4 |
| `math.combos.json` | combos | 4 | 4 |
| `navigation.combos.json` | combos | 4 | 4 |
| `paging.combos.json` | combos | 5 | 5 |
| `performance.combos.json` | combos | 5 | 5 |
| `print.combos.json` | combos | 6 | 6 |
| `regions.combos.json` | combos | 6 | 6 |
| `rendering.combos.json` | combos | 7 | 7 |
| `rhythm.combos.json` | combos | 4 | 4 |
| `scrolling.combos.json` | combos | 15 | 15 |
| `shapes.combos.json` | combos | 4 | 4 |
| `sizing.combos.json` | combos | 5 | 5 |
| `spacing.combos.json` | combos | 11 | 11 |
| `speech.combos.json` | combos | 11 | 11 |
| `svg.combos.json` | combos | 13 | 13 |
| `table.combos.json` | combos | 4 | 4 |
| `transforms.combos.json` | combos | 6 | 6 |
| `typography.combos.json` | combos | 25 | 25 |
| `pairwise/pairs-01.json` | pairwise | 44 | 44 |
| `pairwise/pairs-02.json` | pairwise | 44 | 44 |
| `pairwise/pairs-03.json` | pairwise | 44 | 44 |
| `pairwise/pairs-04.json` | pairwise | 44 | 44 |
| `pairwise/pairs-05.json` | pairwise | 44 | 44 |
| `pairwise/pairs-06.json` | pairwise | 44 | 44 |
| `trees/flex-row.json` | tree | 4 | 17 |
| `trees/flex-column.json` | tree | 4 | 17 |
| `trees/grid-2col.json` | tree | 4 | 20 |
| `trees/block-flow.json` | tree | 4 | 12 |
| `trees/inheritance-color.json` | tree | 4 | 12 |
| `trees/inheritance-typography.json` | tree | 4 | 12 |
| `trees/nested-3level.json` | tree | 3 | 18 |
| `trees/mixed-direction.json` | tree | 3 | 17 |
| `placement/grid-areas.json` | placement | 3 | 13 |
| `placement/grid-lines.json` | placement | 3 | 13 |
| `placement/flex-order.json` | placement | 3 | 15 |
| `placement/self-alignment.json` | placement | 3 | 15 |
| `placement/z-stack.json` | placement | 3 | 11 |
| `placement/mixed-claims.json` | placement | 3 | 18 |
| `tokens/token-theme.json` | tokens | 3 | 11 |
| `tokens/token-fallbacks.json` | tokens | 3 | 9 |
| `tokens/calc-units.json` | tokens | 3 | 10 |
| **total** | | **563** | **746** |

