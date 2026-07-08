# background/ — web style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/background/`. Each IR
property in that directory gets a `{Property}Config.ts`,
`{Property}Extractor.ts`, and `{Property}Applier.ts` here.

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

Empty — properties migrate in one-at-a-time from `../StyleBuilder.ts` as
they're implemented per the `testing/ROLLOUT.md` phase plan.
