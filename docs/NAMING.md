# Naming glossary

The project vocabulary, one line each. Use these words in code, docs,
commit messages, and PR titles — consistently.

| term | meaning |
|---|---|
| **reader** | An input-side converter frontend that parses a source format into IR — today there is exactly one: the CSS reader in `converter/` (`--from css`). |
| **writer** | An output-side generator that emits static source code (Compose / SwiftUI / Tailwind) from IR — roadmap only; none exist today (`--to ir` is the sole target). |
| **runtime** | A library that renders IR **live** on a platform: `runtimes/web` (npm `@style-converter/web`), `runtimes/compose` (AGP `com.styleconverter.runtime`), `runtimes/swiftui` (SwiftPM `StyleConverterRuntime`). |
| **harness** | A per-platform capture app in `apps/` that loads an IR document, renders each component with its runtime, and screenshots it for comparison — not a product. |
| **fixture** | A JSON test input under `fixtures/` (CSS envelope), e.g. `fixtures/properties/<category>/<property>.json` with one component per value variant. |
| **golden** | A hand-authored IR document in `schema/conformance/fixtures/` that pins one wire-shape family; decoded byte-for-byte by all four codebases. |
| **conformance** | The machine check that IR documents match the wire contract: `node schema/conformance/run.mjs [--emit]` plus the per-codebase golden-decode test suites. |
| **config** | The typed value struct for one property on one platform — what was extracted, ready for rendering (`{Property}Config`). |
| **extractor** | The `IRProperty → Config` function for one property on one platform; must handle every value flavor its parser produces (`{Property}Extractor`). |
| **applier** | The `Config → platform output` function: CSS declaration (web), Compose `Modifier` (android), SwiftUI modifier (ios) (`{Property}Applier`). |
| **baseline** | A committed reference PNG in `tools/visual/baseline/`; `BASELINE=1 ./test-all.sh` fails on regressions against it, `UPDATE_BASELINE=1` refreshes it. |

Two rules worth their own lines:

- **The underscore-prefix wire rule** — a leading `_` on an IR JSON key
  (`_text`, `_role`, …) marks renderer-only metadata: not a CSS property,
  droppable without breaking correctness. Normative text:
  `schema/spec/04-metadata-fields.md`.
- **"Engine" is retired — say "runtime".** Older docs and code called the
  renderers "style engines" (and internal paths like
  `runtimes/web/src/engine/` still carry the name). In prose, the product
  is a *runtime*; a "runtime style engine" is tolerated where it aids
  transition, bare "engine" is not.
