# IR v1 — 04: Underscore metadata fields

**Status: normative.** The underscore rule and the per-field emission
matrix below document current behavior, caveats included.

## The underscore rule

A leading underscore on a component key marks **renderer-only metadata**:
a hint that is *not* a CSS property and that a renderer may use to improve
fidelity, but that can be **dropped without breaking correctness** of the
style pipeline. The convention originates in the web IR model
(`runtimes/web/src/core/ir/IRModels.ts`, `_text` KDoc) and the WPT
extractor (`tools/titan/extract-fixture.mjs`).

Kotlin cannot idiomatically name fields with a leading underscore, so the
converter keeps idiomatic names (`text`, `role`) internally and adds the
underscore **at the JSON boundary** (`IRComponentSerializer`,
`converter/src/main/kotlin/app/irmodels/IRDocument.kt`).

## Field matrix (who writes it, who reads it, TODAY)

| field | extractor emits | converter forwards | web reads | compose reads | swiftui reads |
|---|---|---|---|---|---|
| `_text` | yes (`extract-fixture.mjs` — own text nodes) | **yes** (`CssParsing` reads → serializer re-emits, omit-when-null) | yes (`IRModels.ts`) | yes (`IRModels.kt` `_text`) | yes (`IRModels.swift` `_text`) |
| `_role` | yes (only `"body-root"`, synthetic body component) | **yes** (wired for swarm-003; omit-when-null) | **no** (field missing from `IRModels.ts`) | **no** (dropped by `ignoreUnknownKeys`) | **no** (no CodingKey — silently dropped) |
| `_tag` | yes (non-generic lowercase tags only) | **no — dropped** (`CssParsing.parseComponent` never reads it) | yes | yes | yes |
| `_pseudo` | yes (`{before?, after?, marker?}` component-shaped slots) | **no — dropped** | yes (`PseudoElements`) | no | no |
| `_lossy` / `_lossyReasons` | yes (extractor diagnostics) | no — dropped | no (tooling-only) | no | no |

Two honest gaps fall out of the matrix, both **current-behavior, kept as
caveats** rather than silently papered over:

1. **The converter is a lossy hop for `_tag` and `_pseudo`.** Fixtures
   that flow extractor → converter → renderer lose both hints;
   only pipelines that hand extractor output straight to the web renderer
   see them. Closing this belongs to the same work item as the `_role`
   forwarding fix (which already landed).
2. **`_role` reaches the wire but no runtime model reads it yet.**
   The renderer-side body-root handling is tracked follow-up work
   (swarm-003 backdrop-filter-root investigation).

## The Android `ignoreUnknownKeys` caveat

The Android harness decodes IR with
`Json { ignoreUnknownKeys = true }`
(`apps/android-harness/app/src/main/java/com/styleconverter/test/ui/ComponentListScreen.kt`,
`.../screenshot/ScreenshotCaptureScreen.kt`). Consequences, documented as
**current behavior with caveat**:

- Any underscore field the Compose model doesn't declare (`_role`,
  `_pseudo`) is *silently* dropped — consistent with the "droppable hint"
  rule, but with **no logged warning**, so drift between the wire and the
  model is invisible at runtime.
- The same setting also swallows misspelled or future **envelope** keys,
  which 05-versioning.md classifies as an error. Until the v2 freeze
  tightens this, Android is the most permissive reader of the three —
  conformance relies on the schema check in CI, not on the Android
  decoder, to catch envelope violations.

## Rules for adding a new `_field`

1. Underscore prefix on the wire; idiomatic name inside Kotlin with the
   prefix added at the JSON boundary (follow the `_text`/`_role` pattern
   in `IRComponentSerializer`).
2. Omit-when-null so fixtures that don't use it stay byte-stable
   (the BASELINE=1 327-pair contract).
3. String-or-object payloads only; renderers MUST render correctly with
   the field absent.
4. Update this matrix and add a golden fixture pinning the emission.
