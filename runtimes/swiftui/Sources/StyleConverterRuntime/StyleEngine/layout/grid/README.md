<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/layout/grid/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# layout/grid/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/layout/grid/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- AlignTracks
- GridArea
- GridAutoColumns
- GridAutoFlow
- GridAutoRows
- GridAutoTrack
- GridColumnEnd
- GridColumnStart
- GridRowEnd
- GridRowStart
- GridTemplateAreas
- GridTemplateColumns
- GridTemplate
- GridTemplateRows
- JustifyItems
- …

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
