<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/print/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# print/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/print/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- Bleed
- BookmarkLabel
- BookmarkLevel
- BookmarkState
- BookmarkTarget
- FootnoteDisplay
- FootnotePolicy
- Leader
- Marks
- Page
- Size

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
