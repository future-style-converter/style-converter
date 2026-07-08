<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/navigation/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# navigation/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/navigation/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- NavDown
- NavLeft
- NavRight
- NavUp
- ReadingOrder

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
