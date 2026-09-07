# tools/titan/fixtures — vendored TITAN wire, for pins that must run everywhere

`tools/titan/runs/` is a gitignored run artifact: every gate rewrites it and
old runs are pruned. Unit tests that pinned a claim against "the live
per-test IR" of a pruned run (wave20-final, wave21-gate, wave42-final,
wave43-final) skip-guarded on the run directory — and once the directory
was gone they skipped on EVERY sweep, on every machine, forever
(retrospective finding A8#3). The files here are the byte-verbatim
per-test / combined IR those pins need, committed so the pins execute on a
hermetic checkout and on CI.

Rules:

- **Verbatim.** Every component is byte-identical to the run it was copied
  from (the copy step diffed each file against wave48-final as well: all
  THIRTEEN per-test files are identical across wave47/48/49-final —
  re-verified 2026-09-05 by `shasum -a 256` for the css-grid addition). Do
  not hand-edit; re-vendor from a newer run and update the pin's numbers.
- **Provenance in the path.** `per-test-ir/<run>/<section>/<file>.json`
  mirrors `tools/titan/runs/<run>/sections/<section>/per-test-ir/`.
- **Subsets are labelled.** `combined-ir/css-text.wave49-final.12-doc-
  subset.json` is the wave49-final css-text `out/tmpOutput.json` (48 tests,
  687 KB) filtered — with `split-combined-ir.mjs`'s own `testKeyOf` — to 12
  tests: the 8 `boundary-shaping-001..008` face carriers plus 4 undeclared
  controls (`hyphens-none-shy-on-2nd-line-001` and `hyphens-auto-001`: no
  FontFamily at all; `hyphens-manual-013`: generic `monospace`;
  `boundary-shaping-010`: generic `sans-serif`, same WPT directory as the
  carriers). The envelope (`irVersion`, `minReaderVersion`, `fontFaces`) is
  unchanged.

Consumers (each loads by a repo-root-relative path, no build-system
resource wiring):

| fixture | pin |
|---|---|
| `per-test-ir/wave49-final/css-ui/wpt__css-ui__appearance-auto-001.json` | `runtimes/swiftui/Tests/…/InlineAtomFlowTests.swift` (fix-3 fold guard + 4/4/7/1 row partition) |
| `per-test-ir/wave49-final/css-grid/wpt__css-grid__abspos__descendant-static-position-002.json` | `runtimes/swiftui/Tests/…/FloatRowPackingTests.swift` (the wave-20 fix-6 RTL double-mirror pin). Its stem carries the WPT `abspos/` subdirectory, which the wave-20 spelling (`wpt__css-grid__descendant-static-position-002`) did not — that rename is why finding A8#3 recorded the document as unrecoverable. |
| `per-test-ir/wave49-final/css-cascade/wpt__css-cascade__scope-pseudo-element.json` | `apps/android-harness/app/src/test/…/ComposedRootInlineFlowTest.kt` and `apps/ios-harness/StyleConverterTestTests/ComposedRootInlineFlowTests.swift` (B8 pass-preservation) |
| `per-test-ir/wave49-final/CSS2/wpt__CSS2__abspos__static-inside-inline-block.json` | `apps/ios-harness/StyleConverterTestTests/ComposedRootInlineFlowTests.swift` (the wave-34 target document) |
| `per-test-ir/wave49-final/css-multicol/*.json` (9 files: the 7 spanner+abspos tests the wave49-final section flags, plus 2 non-prone controls) | `tools/visual/capture-divergence.test.mjs` |
| `combined-ir/css-text.wave49-final.12-doc-subset.json` | `tools/titan/split-combined-ir.test.mjs` (face scoping) |

The composition-test convert used by `tools/visual/expected-captures.test.mjs`
lives beside that test's other inputs at
`tools/visual/fixtures/composition-test.ir.json` (a fresh
`:converter:run` of `fixtures/composition-test.json`, 42 components).
