<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/appearance/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# appearance/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/appearance/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- Appearance
- AppearanceVariant
- ColorAdjust
- ImageRenderingQuality

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
