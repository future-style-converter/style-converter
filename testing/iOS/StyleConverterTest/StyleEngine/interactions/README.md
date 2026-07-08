<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/interactions/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# interactions/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/interactions/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- Caret
- CaretShape
- Cursor
- Interactivity
- PointerEvents
- Resize
- ScrollBehavior
- TouchAction
- UserSelect

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
