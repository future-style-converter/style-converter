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
    val decorations: JsonArray? = null
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
    val keyframes: Map<String, List<CssKeyframeStop>>? = null
)