# speech/ — web style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/speech/`. Each IR
property in that directory gets a `{Property}Config.ts`,
`{Property}Extractor.ts`, and `{Property}Applier.ts` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- Azimuth
- CueAfter
- CueBefore
- Cue
- Elevation
- PauseAfter
- PauseBefore
- Pause
- Pitch
- PitchRange
- RestAfter
- RestBefore
- Rest
- Richness
- SpeakAs
- …

## Status

Empty — properties migrate in one-at-a-time from `../StyleBuilder.ts` as
they're implemented per the `testing/ROLLOUT.md` phase plan.
