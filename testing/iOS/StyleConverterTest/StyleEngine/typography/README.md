<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/typography/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# typography/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/typography/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- AlignmentBaseline
- BaselineShift
- BaselineSource
- BlockEllipsis
- CaretColor
- Direction
- DominantBaselineAdjust
- DominantBaseline
- FontDisplay
- FontFamily
- FontFeatureSettings
- FontKerning
- FontLanguageOverride
- FontMaxSize
- FontMinSize
- …

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
