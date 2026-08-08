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
> **wave-27 exercised it a third time**: the extractor's `_markerText`
> (the *resolved* list-marker string for one `<li>` — see the counter-
> style bake banner in `tools/titan/counter-style-bake.mjs`) forwards
> verbatim as `meta.markerText`, same additive rule. Contract below.
> **wave-32 exercised it a fourth time**: the extractor's `_runs` (the
> ordered inline-content list of an element whose own text interleaves
> with its kept children — see the `_runs` banner in
> `extract-fixture.mjs`) forwards verbatim as `meta.runs`, same additive
> rule. Its full contract lives in 03-children.md §4.1; the converter-hop
> note is below.
> **wave-37 exercised it a fifth time**: the extractor's `_lang` (the
> element's *computed* content language — see the LANG WIRE banner in
> `extract-fixture.mjs`) forwards verbatim as `meta.lang`, same additive
> rule. Contract below.

## `meta.lang` — the computed content language

`lang` is the one piece of source state that changes how the *same*
declarations paint. Two mechanisms read it, and a wire without it runs
both on the consumer's default locale:

- **`quotes: auto`** (css-content-3 §2.2.1). The UA's
  `q::before { content: open-quote }` picks a CLDR quote pair keyed by
  the content language: French `«…»`, Japanese `「…」`, German `„…“`.
  With no language on the wire every `<q>` paints the root pair.
- **Generic-family font fallback.** `font: 32px serif` resolves to a
  *different face* under `lang="ja"` than under `lang="en"` — for the
  Latin runs in the same element too, so line breaking moves.

### The contract

- **Already resolved, never a walk.** The value is the element's computed
  content language per HTML §3.2.6.2 — own `lang`, else nearest
  ancestor's, else the document element chain's (`<body lang>` nearer,
  `<html lang>` farther). The flat v2 component list has no parent edge
  to walk, so resolving upstream is the only shape that works; it is also
  what lets a consumer set the attribute on one element without knowing
  anything about its neighbours.
- **Verbatim as authored, never canonicalised.** `lang="eN-Us"` ships as
  `eN-Us`. BCP-47 matching is case-insensitive and subtag-truncating
  (RFC 4647 §2.1 / §3.4), every consumer lowercases at *lookup*, and
  rewriting here would only make the fixture lie about its source.
- **Omitted when unknown.** A document that declares no language carries
  no key — which is exactly the "use your default locale" state, so a
  lang-free document's bytes are unchanged. `lang=""` is HTML's spelling
  of *explicitly* unknown (§3.2.6.2): it stops the resolution walk and
  still emits no key, so an empty attribute cannot fall through to an
  outer language.
- **Droppable, like every `meta` member.** A reader that ignores it
  paints in its default locale — the pre-wave-37 result. Nothing about
  layout *correctness* depends on it; fidelity does.
- **Consumers.** Web sets the `lang` attribute on the rendered element
  and lets the browser's own CLDR table and font fallback engage (the
  capture harness does this in WPT mode). The generated-content bake
  reads it to resolve `quotes: auto` into literal marks for the readers
  that have no UA quote engine — see
  `tools/titan/generated-content-bake.mjs`, whose CLDR table is derived
  from the corpus's own `css-content/quotes-0NN` references.

## `meta.runs` — why the converter never rewrites a `child` key

`meta.runs` entries name children. It would be easy to make the converter
resolve those names against the flattened list — rewriting them to the
minted ids the way `IRFlattener` already stamps `slot.parent` — and fail
loudly on a dangling one. It is the wrong place for both, and the field
is shaped so that neither is needed:

- **The converter re-ids, so the reference is the AUTHORING KEY.** The
  converter mints `<lowercased-name>-<NNN>` at the flatten boundary while
  the authoring key survives verbatim as the child's `name`
  (03-children.md §5 rule 1). Pinning `child` to the authoring key makes
  the payload correct on BOTH sides of the hop with zero converter
  involvement — and correct in the extractor-direct pipeline too, where
  key, `name` and `id` are one string. Rewriting instead would turn an
  *opaque forwarded hint* into a field the converter co-owns, the exact
  coupling `meta.attrs` and `meta.decorations` were designed to avoid.
- **Dangling is already a defined, non-fatal state.** 03-children.md §2
  makes a dangling `slot.parent` a composer-side warn; §4.1 rule 5 makes
  a dangling `runs.child` a renderer-side warn-and-skip. Both keep a
  malformed document *renderable*, which is the whole point of putting
  the field in the droppable-hints group.

So the hop is byte-verbatim: `_runs` → `CssComponent.runs` (opaque
`JsonArray`) → `IRComponent.runs` → `meta.runs`. The v1 serializer
ignores it, so `--emit-ir v1` bytes stay frozen.

## `meta.markerText` — why the marker string is resolved upstream

A list marker is a pure function of three inputs: the resolved
`list-style-type`, the counter origin (`<ol start>` / `<li value>`), and
the item's position. Only the first rides the IR as a property. The
other two had **no wire at all**, and a counter style outside a runtime's
own keyword table (`arabic-indic`, `cambodian`, …) silently degraded to
that runtime's `<ol>` UA default. `meta.markerText` closes both gaps with
one droppable string instead of three new channels.

- **Resolved upstream, not normalized.** css-counter-styles-3 §6 is a
  closed table and §2/§4/§7.1 are closed algorithms, so the extractor
  computes the representation, applies the §4 `range` gate and the
  §7.1.4 `fallback` chain, and appends the §3.1.5 `suffix`. The
  converter forwards the result verbatim — the same opacity contract
  `meta.attrs` and `meta.decorations` established.
- **The suffix carries no trailing space.** The spec's initial `suffix`
  is `"."` *followed by a space*; both native runtimes already supply
  that gap themselves (Compose renders `Text("$marker ")` with a 4dp end
  padding, SwiftUI an `HStack(spacing: 4)`), and their pre-existing
  tables emit `"1."`. Keeping the space would double it.
- **Authoritative when present**, exactly like `meta.decorations`: a
  reader that honours `markerText` must not *also* synthesise a marker
  from `ListStyleType`. Readers that drop `meta` keep their own table
  and paint a *worse* marker, never a doubled one.
- **Absence is meaningful and never silent.** The key is omitted for the
  ordinal-independent §6.1 bullets (out of scope by design — the
  runtimes already paint those), for `list-style-type: none`, for a
  `list-style-image` marker, and for any counter style the bake does not
  model. The last case additionally raises the extractor's
  `counter-style-unsupported` lossy reason, and a document with dynamic
  counters (`counter-increment`/`counter-reset`/`counter()`/
  `counters()`/`@counter-style`/`<script>`) bails whole under the same
  reason.
- **`meta.attrs` gained `start` in the same wave.** `ol`/`li` form a
  *disjoint* attr lane from the wave-20 widget tags (`start`, `value`,
  verbatim strings) so the web renderer — the one platform that paints a
  real `<ol>` and lets the browser synthesise `::marker` — gets native
  numbering instead of a baked string.

## `meta.attrs.src` — the replaced-element source (wave-36)

A third *disjoint* attr lane: `img`, `embed`, `object`, `video`. These
are **replaced elements**, and `object-fit` / `object-position` /
`object-view-box` are the CSS that decides how their content is scaled
into the box — so a wire that carries the properties but not the content
describes a scaling of nothing. `src` closes that.

- **One canonical key, whatever the markup spelled.** `src` on
  `img`/`embed`, `data` on `object` (HTML §4.8.7), `poster` on `video`
  (§4.8.9 — the poster frame is what a still capture paints, and it is
  object-fit-scaled like any other replaced content). Normalizing is in
  keeping with the lane's siblings, which already retype rather than
  mirror (`checked` → literal `true`, `min`/`max` → Number).
- **A path, never a payload.** The value is a **producer-relative path**
  (or a `data:` URI the author already inlined) — the same
  consumer-resolves contract the document-level `fontFaces[].src` uses
  (01-envelope.md §5), and for the same two reasons: only the consumer
  knows where its asset origin is, and inlining is quadratically
  expensive when a corpus reuses a handful of images across thousands of
  element references.
- **Absence is meaningful.** The key is omitted when the source
  attribute is absent, empty, or names something the producer could not
  deliver (remote URL, non-image format, missing file). A consumer that
  sees no `src` must fall back to its own policy — a placeholder, alt
  text, an empty box — never to a fabricated path.

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
| `_runs` (v2 `meta.runs`, wave-32) | yes (interleaved inline content only) | **yes** (opaque `JsonArray`, omit-when-absent) | yes (`NodeRenderer` content walk) | yes (`ComponentRenderer.RenderContent`) | yes (`ComponentRenderer.contentOrPlaceholder`) |

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
