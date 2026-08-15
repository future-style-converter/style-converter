<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/content/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# content/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/content/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- Content
- Quotes

## Status

- `Content{Config,Extractor,Applier}.swift` — the registered triplet for the
  IR `Content` property (identity applier: `content` on an ELEMENT has no
  mobile analogue).
- `PseudoTextBridge.swift` + `PseudoTextFold.swift` (wave 42, lane W2) — the
  component-level consumer of the v2 `pseudos` bucket: baked
  `::before`/`::after` `_text` folds into `component.text` as leading /
  trailing inline runs (web parity: `runtimes/web/src/renderer/
  PseudoNodeRenderer.ts` + NodeRenderer's before/text/after order), gated to
  text-only buckets — box pseudos stay with `Renderer/RootPseudoBox.swift`,
  `pseudos.marker` with the list marker path. Seam: `ComponentRenderer.init`.
  Tests: `PseudoTextFoldTests.swift`.
