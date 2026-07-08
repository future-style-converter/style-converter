# effects/mask/ — web style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/effects/mask/`. Each IR
property in that directory gets a `{Property}Config.ts`,
`{Property}Extractor.ts`, and `{Property}Applier.ts` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- MaskBorderMode
- MaskBorderOutset
- MaskBorderRepeat
- MaskBorderSlice
- MaskBorderSource
- MaskBorderWidth
- MaskClip
- MaskComposite
- MaskImage
- MaskMode
- MaskOrigin
- MaskPosition
- MaskPositionX
- MaskPositionY
- MaskRepeat
- …

## Status

Empty — properties migrate in one-at-a-time from `../StyleBuilder.ts` as
they're implemented per the `testing/ROLLOUT.md` phase plan.
