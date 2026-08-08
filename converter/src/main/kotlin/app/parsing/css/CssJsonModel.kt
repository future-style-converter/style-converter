package app.parsing.css

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Represents a CSS property value in JSON format
 */
@Serializable
data class CssPropertyValue(
    val value: String
)

/**
 * Represents a CSS selector in JSON format
 */
@Serializable
data class CssSelector(
    val selector: String,
    val properties: Map<String, CssPropertyValue>
)

/**
 * Represents a CSS media query in JSON format
 */
@Serializable
data class CssMedia(
    val query: String,
    val properties: Map<String, CssPropertyValue>
)

/**
 * Represents a CSS component in JSON format.
 *
 * Supports nested children for SDUI container components, plus an optional
 * `text` payload threaded through from the WPT extractor (see
 * tools/titan/extract-fixture.mjs). The extractor writes the field as
 * `_text` on the input JSON; the parser strips the leading underscore at
 * the boundary to keep this Kotlin identifier idiomatic. The IR serializer
 * re-adds the underscore on the output side
 * (see IRComponentSerializer in IRDocument.kt).
 *
 * `children` stays a Map here because that's the shape WPT inputs ship in
 * (each child keyed by its stable extractor-assigned name like
 * `abspos-in-opacity-000__1__0`); IR conversion flattens it to a List so
 * the web/iOS/Android renderers can index positionally.
 *
 * `role` mirrors the WPT extractor's `_role` marker (see
 * tools/titan/extract-fixture.mjs:1417). Today the only value emitted is
 * `"body-root"` (a synthetic component aggregating html/body-scoped CSS so
 * the renderers can treat the root-element paint differently from a normal
 * styled descendant). Same naming convention as `_text` — leading
 * underscore on the wire, idiomatic Kotlin identifier in the model.
 */
@Serializable
data class CssComponent(
    val properties: Map<String, CssPropertyValue>? = null,
    val selectors: List<CssSelector>? = null,
    val media: List<CssMedia>? = null,
    val children: Map<String, CssComponent>? = null,
    val text: String? = null,
    val role: String? = null,
    // Originating HTML tag from the extractor's `_tag` hint (lowercase,
    // non-generic tags only). v1 emission drops it (historical behavior);
    // IR v2 forwards it as `meta.sourceTag` — see IRWireV2.kt.
    val tag: String? = null,
    // Opaque `_pseudo` payload ({before?, after?, marker?} component-
    // shaped slots) forwarded verbatim. Pseudo nodes never flatten
    // (design §4.2); v2 wire name is `pseudos`. Not @Serializable-typed
    // deeper than JsonObject on purpose — the extractor owns the shape.
    val pseudos: JsonObject? = null,
    // wave-20 W1: opaque `_attrs` payload — the extractor's widget-identity
    // attributes for form/widget tags (present-in-source keys among
    // type/value/checked/multiple/size/alt/min/max/selected/disabled; see
    // tools/titan/extract-fixture.mjs widgetAttrsFor). Forwarded VERBATIM
    // as IR v2 `meta.attrs` (IRWireV2.kt) — the converter never interprets
    // the values, exactly the `_pseudo` opacity contract.
    val attrs: JsonObject? = null,
    // wave-22 lane DECOR: opaque `_decorations` payload — the extractor's
    // ORDERED (outermost-first) per-line decoration list for a COLLAPSED
    // inline run: [{line, color?}, …] where `line` is exactly one
    // css-text-decor-3 §2.1 keyword and `color` is the CSS colour token AS
    // AUTHORED ("blue", "#00f", "rgb(0,0,255)"); absent = currentColor
    // (§2.2 initial). Forwarded VERBATIM as IR v2 `meta.decorations` — the
    // converter does NOT normalize the colour tokens to the IR sRGB leaf,
    // exactly the `_attrs` opacity contract: each runtime resolves the
    // token with its own CSS token parser at decode time (see
    // schema/spec/04-metadata-fields.md and the DecorationWire twins).
    val decorations: JsonArray? = null,
    // wave-27 lane CBAKE: the extractor's `_markerText` hint — the RESOLVED
    // list-marker string for one `<li>` (representation + suffix, e.g.
    // "١٨٦٠." or "ՔՋՂԹ."), computed at extract time from css-counter-styles-3
    // §6 because the two inputs a native cannot see — an unmodelled counter
    // style and `<ol start>` — are both statically known there. A plain
    // string, forwarded VERBATIM as IR v2 `meta.markerText`; the converter
    // never re-derives or validates it (the `_attrs` opacity contract).
    val markerText: String? = null,
    // wave-37 lane W4 (THE LANG WIRE): the extractor's `_lang` hint — the
    // element's COMPUTED content language (HTML §3.2.6.2: own `lang`, else
    // the nearest ancestor's, else the `<html>`/`<body>` chain's), already
    // resolved because the flat v2 component list has no parent edge a reader
    // could walk. A plain string, forwarded VERBATIM as IR v2 `meta.lang` —
    // NOT canonicalised (BCP-47 matching is case-insensitive and
    // subtag-truncating per RFC 4647, so consumers lowercase at lookup), the
    // same `_attrs` opacity contract every other meta hint rides.
    val lang: String? = null,
    // wave-32 lane R: opaque `_runs` payload — the extractor's ORDERED
    // inline-content list for an element whose own text INTERLEAVES with
    // kept element children: [{text}|{child}, …] in document order, where
    // `child` is the id of one of this component's children. It replaces
    // the single `_text` string as the authoritative content order for
    // readers that understand it (`_text` stays on the wire, carrying the
    // pre-wave-32 concatenation, so a runs-unaware reader is byte-for-byte
    // unchanged). Forwarded VERBATIM as IR v2 `meta.runs` — the converter
    // never re-orders, re-splits or validates the entries, exactly the
    // `_decorations` opacity contract (schema/spec/03-children.md §"Inline
    // runs" and schema/spec/04-metadata-fields.md).
    val runs: JsonArray? = null
)

/**
 * One authored keyframe stop inside a named `@keyframes` set (wave 8 —
 * schema/spec/07-animations.md).
 *
 * Authoring shape mirrors css-animations-1 §4.2 keyframe rules:
 * `offset` is the keyframe selector as authored — `"from"`, `"to"`, or a
 * percentage string like `"50%"` (one selector per stop; comma-grouped
 * selectors are authored as separate stops). `declarations` is a plain
 * property→value map, the exact same shape as a component's base
 * `properties` map, so the converter can reuse PropertiesParser (shorthand
 * expansion + typed value parsing) verbatim for keyframe payloads.
 */
@Serializable
data class CssKeyframeStop(
    val offset: String,
    val declarations: Map<String, CssPropertyValue>
)

/**
 * Root document containing all CSS components in JSON format.
 *
 * `keyframes` (wave 8) is the DOCUMENT-level map of named keyframe sets —
 * the authoring twin of CSS `@keyframes <name> { … }` at-rules, which are
 * document-scoped in CSS too (they live beside rules, not inside them).
 * Optional/null so every pre-motion fixture parses identically; each set
 * is an authored-order list of stops (the converter sorts by resolved
 * offset at the IR boundary — see CssParsing).
 */
@Serializable
data class CssComponents(
    val components: Map<String, CssComponent>,
    val keyframes: Map<String, List<CssKeyframeStop>>? = null,
    val fontFaces: List<CssFontFace>? = null
)

/**
 * One authored `@font-face` declaration (wave 34 —
 * schema/spec/01-envelope.md §5).
 *
 * Authoring shape is already the WIRE shape: unlike every other authored
 * structure in this file, nothing here needs typing or normalization on the
 * way to the IR. `family` arrives unquoted, and `weight`/`style` are
 * css-fonts-4 §4.4/§4.5 DESCRIPTORS — a `font-weight` range (`400 700`) has
 * no numeric 100–900 equivalent and an oblique angle has no keyword one, so
 * the converter is a courier here, not a parser. See IRFontFace.
 *
 * `src` is a path to the font FILE relative to the producing pipeline's
 * corpus root, never a payload and never absolute (spec 01 §5).
 */
@Serializable
data class CssFontFace(
    val family: String,
    val src: String,
    val weight: String? = null,
    val style: String? = null
)