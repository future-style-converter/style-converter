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
| `trees/flex-row.json` | tree | 4 | 17 |
| `trees/flex-column.json` | tree | 4 | 17 |
| `trees/grid-2col.json` | tree | 4 | 20 |
| `trees/block-flow.json` | tree | 4 | 12 |
| `trees/inheritance-color.json` | tree | 4 | 12 |
| `trees/inheritance-typography.json` | tree | 4 | 12 |
| `trees/nested-3level.json` | tree | 3 | 18 |
| `trees/mixed-direction.json` | tree | 3 | 17 |
| **total** | | **272** | **367** |

