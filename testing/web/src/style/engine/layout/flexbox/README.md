# layout/flexbox/ — web style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/layout/flexbox/`. Each IR
property in that directory gets a `{Property}Config.ts`,
`{Property}Extractor.ts`, and `{Property}Applier.ts` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- AlignContent
- AlignItems
- AlignSelf
- BoxOrient
- Display
- FlexBasis
- FlexDirection
- FlexGrow
- FlexShrink
- FlexWrap
- JustifyContent
- Order

## Status

Empty — properties migrate in one-at-a-time from `../StyleBuilder.ts` as
they're implemented per the `testing/ROLLOUT.md` phase plan.
