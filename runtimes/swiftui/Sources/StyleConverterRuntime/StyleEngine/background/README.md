<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/background/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# background/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/background/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- BackgroundAttachment
- BackgroundClip
- BackgroundImage
- BackgroundOrigin
- BackgroundPositionBlock
- BackgroundPositionInline
- BackgroundPosition
- BackgroundPositionX
- BackgroundPositionY
- BackgroundRepeat
- BackgroundSize

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
