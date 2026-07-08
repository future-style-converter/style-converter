<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/layout/advanced/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# layout/advanced/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/layout/advanced/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- AnchorName
- AnchorScope
- InsetArea
- OffsetAnchor
- OffsetDistance
- OffsetPath
- OffsetPosition
- Offset
- OffsetRotate
- PositionAnchor
- PositionArea
- PositionFallback
- PositionTryFallbacks
- PositionTryOptions
- PositionTryOrder
- …

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
