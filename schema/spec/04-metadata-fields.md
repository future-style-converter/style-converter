# IR v1 — 04: Underscore metadata fields

**Status: normative for the v1 wire (deprecation window).** The
underscore rule and the per-field emission matrix below document v1
behavior, caveats included.

> **IR v2 note (the current default wire):** v2 emits **no** underscore
> component keys. `_text` became the structural `text`, `_pseudo` became
> `pseudos`, and `_tag`/`_role` grouped into `meta: {sourceTag?, role?}`
> (droppable hints). Two v1 gaps closed at the freeze: the converter now
> forwards `_tag` (→ `meta.sourceTag`) and `_pseudo` (→ `pseudos`)
> instead of dropping them — see 01-envelope.md and 03-children.md. The
> underscore convention itself survives only for future droppable hints;
> in v2 the sanctioned extension point is a new key inside `meta`.
> **wave-20 exercised that extension point**: the extractor's `_attrs`
> (widget-identity attributes for form/widget tags — see
> `tools/titan/extract-fixture.mjs` `widgetAttrsFor` for the tag set,
> key allow-list, and value typing) forwards verbatim as `meta.attrs`,
> an additive omit-when-absent meta key per 05-versioning.md.
> **wave-22 exercised it again**: the extractor's `_decorations` (the
> ordered per-line decoration list of a *collapsed* inline run — see the
> `_decorations` banner in `extract-fixture.mjs`) forwards verbatim as
> `meta.decorations`, same additive rule. Its contract is spelled out
> below.

## `meta.decorations` — why the colour token is NOT normalized

`meta.decorations` is `[{line, color?}, …]`, ordered **outermost-first**
(css-text-decor-3 §2.1 propagation order), one line keyword per entry.
It is **authoritative when present**: it is the complete line set for the
run, so a reader that honours it must ignore the component's own
`text-decoration-line` flags. The flat `text-decoration*` longhands that
survive on a collapsed component are a root-wins merged bag kept for
readers that drop `meta` — they paint a *subset*, never a superset, so
neither reader double-paints. **Present-but-empty is still
authoritative**: a reader that filters the list down to nothing (every
entry an unknown keyword) must paint **nothing**, not fall back to the
flat flags.

Unlike every CSS *property* value, `color` here is the **authored CSS
token** (`"blue"`, `"#00f"`, `"rgb(0, 0, 255)"`), not the normalized sRGB
leaf of 02-values.md. That is deliberate and it is the `meta.attrs`
opacity contract, not an oversight:

- `meta` members are **extractor-owned payloads forwarded verbatim**. The
  converter's property pipeline normalizes *declarations*; it has no
  declaration here, only a hint object it is contractually opaque to.
- The three runtimes already own a CSS colour-token parser for exactly
  this situation (post-`var()` substitution): `CSSTokenParser.color` on
  iOS, `ValueExtractors.parseCssColorLiteral` on Compose, the browser
  itself on web. Resolution therefore happens **once per runtime, at
  decode time**, in the same code path a substituted `var()` colour takes.
- Consequence, stated honestly and **measured, not hypothetical**: a
  token outside those parsers' families (hex, `transparent`, the basic
  named set, plus `rgb()`/`rgba()` on iOS only) resolves to *no colour*
  on the natives. The runtimes **log** it (`PropertyTracker` / logcat) —
  never silent — and the painter then substitutes the run's merged
  `text-decoration-color` leaf, else the text colour. (That substitute is
  *not* strictly §2.2's `currentColor`; it is right for the outermost
  entry and is the root's colour for any other.) Web has no such gap: the
  token goes straight into a `text-decoration-color` declaration the
  browser parses.

  The corpus already contains a case:
  `fixtures/wpt/css-text-decor/text-decoration-style-multiple.json` ships
  `coral` and `skyblue`, which **neither** native table has, so its
  overline paints coral on Android and iOS while web paints skyblue.
  `crimson` resolves on Compose but not iOS; `rgb()` on iOS but not
  Compose. The converter itself resolves all of them
  (`converter/src/main/kotlin/app/irmodels/ColorConversion.kt` carries the
  full 148-name css-color-4 table) — the loss is the *price* of the
  verbatim-forward decision above, not a parser bug, and closing it means
  either widening both runtime token parsers or adding a normalized
  sibling field to the entry (a v3-gated wire change). Pinned by
  `SkepticDecorWireSeamTest.kt` / `SkepticDecorWireSeamTests.swift`.

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
