# layout/position/ — web style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/layout/position/`. Each IR
property in that directory gets a `{Property}Config.ts`,
`{Property}Extractor.ts`, and `{Property}Applier.ts` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- Bottom
- InsetBlockEnd
- InsetBlockStart
- InsetInlineEnd
- InsetInlineStart
- Left
- Position
- Right
- Top
- ZIndex

## Status

Empty — properties migrate in one-at-a-time from `../StyleBuilder.ts` as
they're implemented per the `testing/ROLLOUT.md` phase plan.
