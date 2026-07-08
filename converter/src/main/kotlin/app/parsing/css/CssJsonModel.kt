package app.parsing.css

import kotlinx.serialization.Serializable

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
    val role: String? = null
)

/**
 * Root document containing all CSS components in JSON format
 */
@Serializable
data class CssComponents(
    val components: Map<String, CssComponent>
)