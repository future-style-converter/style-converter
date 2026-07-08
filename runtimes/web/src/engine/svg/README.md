# svg/ — web style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/svg/`. Each IR
property in that directory gets a `{Property}Config.ts`,
`{Property}Extractor.ts`, and `{Property}Applier.ts` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- BufferedRendering
- Cx
- Cy
- D
- EnableBackground
- FillOpacity
- Fill
- FillRule
- FloodColor
- FloodOpacity
- LightingColor
- MarkerEnd
- MarkerMid
- Marker
- MarkerSide
- …

## Status

Empty — properties migrate in one-at-a-time from `../StyleBuilder.ts` as
they're implemented per the `testing/ROLLOUT.md` phase plan.
