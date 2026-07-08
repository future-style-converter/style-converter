# layout/advanced/ — web style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/layout/advanced/`. Each IR
property in that directory gets a `{Property}Config.ts`,
`{Property}Extractor.ts`, and `{Property}Applier.ts` here.

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

Empty — properties migrate in one-at-a-time from `../StyleBuilder.ts` as
they're implemented per the `testing/ROLLOUT.md` phase plan.
