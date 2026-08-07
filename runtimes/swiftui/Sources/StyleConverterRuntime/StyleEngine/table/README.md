<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/table/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# table/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/table/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- BorderCollapse
- BorderSpacing
- CaptionSide
- EmptyCells
- TableLayout

## Shared decision tables (not per-property triplets)

Table layout is not expressible as one property → one applier: a single
`border-spacing` is spent by the table box AND by every row below it, and a
collapsed border is decided by six elements at once. Those cross-box rules
live here as pure modules, each byte-parallel with a Kotlin twin under
`runtimes/compose/src/main/java/com/styleconverter/runtime/table/`, and each
named after its spec section rather than after a property:

| file | spec | twin |
|---|---|---|
| `TableBoxTree.swift` | css-tables-3 §2.1 — internal table roles | `TableBoxTree.kt` |
| `TableSeparatedTracks.swift` | CSS 2.1 §17.6.1 — separated-borders tracks + used `border-spacing` | `TableSeparatedTracks.kt` |
| `CollapsedBorderConflict.swift` | CSS 2.1 §17.6.2 / §17.6.2.1 — collapsed-border conflict resolution | `CollapsedBorderConflict.kt` |

Their SwiftUI adapter is `../../Renderer/TableSeparatedLayout.swift`; the
renderer gate is `ComponentRenderer.tableTrackPlan`.

`CollapsedBorderConflict` is decided but NOT YET PAINTED — see its header
for the three things still between it and a correct collapsed table.

## Status

The per-property triplets (`TableConfig`/`TableExtractor`/`TableApplier`)
cover the keyword properties above. Cross-box table LAYOUT arrived in
wave 34 as the shared modules listed above.
