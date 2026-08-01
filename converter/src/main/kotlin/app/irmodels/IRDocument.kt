package app.irmodels

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * Represents a CSS pseudo-class selector (e.g., :hover, :active, :focus) with its properties.
 *
 * ## Purpose
 * Captures conditional styles that apply based on element state or user interaction.
 *
 * ## Examples
 * - `:hover` - Mouse hover state
 * - `:active` - Active/pressed state
 * - `:focus` - Focused state
 * - `:disabled` - Disabled state
 *
 * @property condition The selector condition (e.g., "hover", "active", "focus", "disabled")
 * @property properties List of CSS properties that apply when the condition is met
 */
@Serializable
data class IRSelector(
    val condition: String,
    val properties: MutableList<@Serializable(with = IRPropertySerializer::class) IRProperty>
)

/**
 * Represents a CSS media query with its properties.
 *
 * ## Purpose
 * Captures responsive styles that apply based on screen size, device type, or other media features.
 *
 * ## Examples
 * - `(min-width: 768px)` - Tablet and larger screens
 * - `(max-width: 480px)` - Mobile screens
 * - `(prefers-color-scheme: dark)` - Dark mode preference
 *
 * @property query The media query string (e.g., "(min-width: 768px)")
 * @property properties List of CSS properties that apply when the query matches
 */
@Serializable
data class IRMedia(
    val query: String,
    val properties: MutableList<@Serializable(with = IRPropertySerializer::class) IRProperty>
)

/**
 * IR v2 child→parent composition reference (schema/spec/03-children.md).
 *
 * ## Purpose
 * In the flat-list IR v2 wire format, components no longer nest: every
 * component is a standalone entry in the document's flat `components`
 * array, and a child points at its container via this `slot` object.
 * The reference lives on the CHILD (never the parent) so containers stay
 * composition-agnostic — a container's payload carries zero knowledge of
 * what arrives in its open slot.
 *
 * ## Wire shape (IR v2 only — v1 documents never carry `slot`)
 * ```json
 * "slot": { "parent": "card-001", "name": "content" }
 * ```
 * `name` is omitted on the wire when it equals the default `"content"`;
 * it is reserved for future multi-slot containers (scaffold
 * header/body/footer). Roots omit the whole `slot` object.
 *
 * ## NOT underscore-prefixed — deliberately
 * The `_` prefix is reserved for droppable renderer hints (spec 04).
 * `slot` is structural composition data that MUST round-trip through
 * every reader; a consumer that drops it has corrupted the document.
 *
 * @property parent The `id` of the container component this child composes into.
 * @property name The slot name within the parent; `"content"` is the default single slot.
 */
@Serializable
data class IRSlot(
    val parent: String,
    val name: String = "content"
)

/**
 * Represents a single UI component with its styles, selectors, and media queries.
 *
 * ## Purpose
 * Encapsulates all styling information for one component, including:
 * - Base properties (default styles)
 * - Selector-based properties (state-dependent styles like :hover)
 * - Media query properties (responsive styles)
 * - Nested children (for SDUI container components)
 *
 * ## Architecture
 * A component maps to:
 * - **Compose**: A `@Composable` function
 * - **SwiftUI**: A `View` struct
 * - **CSS**: A CSS class or element
 *
 * ## SDUI Support
 * The `id` and `children` fields enable Server-Driven UI:
 * - `id` uniquely identifies each component instance
 * - `children` allows nesting for container layouts (Grid, Flex, Stack)
 *
 * @property id Unique identifier for this component instance (e.g., "button-001", "card-123")
 * @property name The component type/class (e.g., "Button", "Card", "LoginScreen")
 * @property properties Base CSS properties that always apply
 * @property selectors Conditional properties based on pseudo-class selectors
 * @property media Responsive properties based on media queries
 * @property children Nested child components (null for leaf components)
 * @property text Optional element text content carried through from extraction.
 *
 * Populated by the WPT extractor (tools/titan/extract-fixture.mjs) for fixtures
 * whose styled element has an inner text node — e.g. the green sentence in
 * `<p class=test>Test passes if this text is green</p>`. Mirrors the TypeScript
 * `IRComponent._text` field in runtimes/web/src/core/ir/IRModels.ts; that
 * leading underscore (a renderer-only metadata convention reserved for fields
 * that aren't CSS properties) is added by the serializer, NOT stored here.
 *
 * Optional / nullable to preserve backward compatibility with existing
 * non-WPT fixtures (visual-test.json, fixtures/properties/&#42;) that never carry
 * text content (the slash-asterisk in that path is HTML-encoded only because
 * a literal close-comment marker would terminate this KDoc block early). The
 * serializer omits the field entirely when null so the 327-pair BASELINE=1
 * regression on visual-test.json stays byte-stable.
 *
 * @property role Optional renderer-only role marker carried through from the
 *   WPT extractor. Today the only value emitted is `"body-root"` (set at
 *   tools/titan/extract-fixture.mjs:1417 for synthetic components that
 *   aggregate html/body-scoped CSS so renderers can treat the root-element
 *   paint differently from a styled descendant). Mirrors the same wire
 *   convention as `text`: leading underscore on the JSON side (`_role`),
 *   idiomatic identifier on the Kotlin side. Serializer omits the field
 *   when null so the BASELINE=1 byte-stable contract is preserved for
 *   every non-body-root fixture. Wired in for swarm-003
 *   backdrop-filter-root-element investigation — without this field the
 *   Kotlin convert pipeline silently dropped the extractor's marker
 *   before it could reach any platform renderer.
 */
@Serializable(with = IRComponentSerializer::class)
data class IRComponent(
    val id: String,
    val name: String,
    val properties: MutableList<@Serializable(with = IRPropertySerializer::class) IRProperty>,
    val selectors: List<IRSelector> = emptyList(),
    val media: List<IRMedia> = emptyList(),
    val children: List<IRComponent>? = null,
    val text: String? = null,
    val role: String? = null,
    // --- IR v2 additions (see IRWireV2.kt for the v2 serializer) ---
    // Child→parent composition reference. Stamped by IRFlattener during
    // the v2 flatten; ALWAYS null on the nested (v1 / in-memory authoring)
    // tree. The legacy v1 serializer below ignores it entirely so the
    // deprecated `--emit-ir v1` output stays byte-identical to the
    // pre-v2 converter.
    val slot: IRSlot? = null,
    // Originating HTML tag from the WPT extractor's `_tag` input hint
    // (lowercase, e.g. "ol"). v1 dropped this at the converter hop (spec
    // 04 field matrix); v2 forwards it inside `meta.sourceTag`. Ignored
    // by the v1 serializer (matching the historical dropped behavior).
    val tag: String? = null,
    // wave-20 W1: widget-identity attributes from the extractor's `_attrs`
    // hint — an OPAQUE object (present-in-source keys among type/value/
    // checked/multiple/size/alt/min/max/selected/disabled; strings,
    // booleans, numbers per the extractor's wire contract). v2 forwards
    // it VERBATIM inside `meta.attrs` (spec 05 sanctions new omit-when-
    // absent meta keys as v2-additive); the v1 serializer ignores it so
    // deprecated `--emit-ir v1` bytes stay frozen.
    val attrs: JsonObject? = null,
    // wave-22 lane DECOR: the ORDERED (outermost-first) per-line decoration
    // list from the extractor's `_decorations` hint — an OPAQUE array of
    // {line, color?} entries where `line` is one css-text-decor-3 §2.1
    // keyword and `color` is the CSS colour token AS AUTHORED (absent =
    // currentColor, the §2.2 initial). v2 forwards it VERBATIM inside
    // `meta.decorations` (spec 05 additive meta-key rule, the same lane
    // `meta.attrs` used); the v1 serializer ignores it so deprecated
    // `--emit-ir v1` bytes stay frozen. Colour NORMALIZATION deliberately
    // does NOT happen here — see schema/spec/04-metadata-fields.md for why
    // the runtimes resolve the token themselves.
    val decorations: JsonArray? = null,
    // Generated-content payload from the extractor's `_pseudo` input
    // ({before?, after?, marker?} component-shaped slots). Carried as an
    // opaque JsonObject — pseudo nodes have no independent lifecycle and
    // are never flattened (design §4.2), so the converter forwards the
    // payload verbatim. v2 wire name: `pseudos`. Ignored by the v1
    // serializer (matching the historical dropped behavior).
    val pseudos: JsonObject? = null,
    // CSS custom-property definitions declared on this component
    // (`--name: value`), extracted by CustomPropertyParser. Names keep
    // their exact case (css-variables-1 §2: custom property names are
    // case-sensitive) and values are the RAW declaration strings verbatim
    // (untyped until substitution — no normalization is possible before a
    // var() reference gives the value a type). ADDITIVE IR v2 envelope
    // key `variables` (schema/spec/01-envelope.md; omit-when-empty);
    // ignored by the legacy v1 serializer so `--emit-ir v1` output stays
    // byte-identical. Resolution order for var() references is specified
    // in schema/spec/02-values.md (element → slot-parent chain → fallback).
    val variables: Map<String, String>? = null
)

/**
 * Custom serializer for IRComponent that omits empty/null fields.
 *
 * Serialization strategy:
 * - `id` and `name` are always included
 * - `properties` is always included
 * - `selectors` is omitted if empty
 * - `media` is omitted if empty
 * - `children` is omitted if null or empty (and serialized as a JSON array,
 *   matching the TS `IRComponent[]` shape the web renderer consumes)
 * - `_text` is omitted if null (preserves backward-compat byte stability for
 *   non-WPT fixtures like fixtures/visual-test.json)
 * - `_role` is omitted if null (same byte-stability contract as `_text` —
 *   only WPT body-root components actually carry a value today)
 *
 * Why `_text` / `_role` (with the leading underscore) on the wire but `text`
 * / `role` in Kotlin: the underscore is the IR convention for renderer-only
 * metadata that isn't a CSS property (see
 * runtimes/web/src/core/ir/IRModels.ts comments). Kotlin field names
 * can't start with `_` without backticks, so we keep the Kotlin identifier
 * idiomatic and add the prefix at the JSON boundary here.
 *
 * Note: Uses simple descriptor since we serialize directly to JSON. The
 * children + text + role fields are handled dynamically in serialize() to
 * keep the descriptor flat (children would otherwise need a recursive
 * descriptor).
 */
object IRComponentSerializer : KSerializer<IRComponent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRComponent") {
        element<String>("id")
        element<String>("name")
        element<List<IRProperty>>("properties")
        element<List<IRSelector>>("selectors", isOptional = true)
        element<List<IRMedia>>("media", isOptional = true)
        // children + _text descriptors omitted: children is recursive (handled
        // dynamically below) and _text is just an optional string written
        // alongside without a separate schema entry.
    }

    override fun serialize(encoder: Encoder, value: IRComponent) {
        require(encoder is JsonEncoder) { "This serializer only works with JSON" }
        val json = encoder.json

        val element = buildJsonObject {
            put("id", value.id)
            put("name", value.name)
            put("properties", json.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(IRPropertySerializer),
                value.properties
            ))
            // Only include selectors if non-empty
            if (value.selectors.isNotEmpty()) {
                put("selectors", json.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(IRSelector.serializer()),
                    value.selectors
                ))
            }
            // Only include media if non-empty
            if (value.media.isNotEmpty()) {
                put("media", json.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(IRMedia.serializer()),
                    value.media
                ))
            }
            // Only include children if non-null and non-empty.
            // Output is always an ARRAY (List<IRComponent>), even if input
            // was a name->component map — flattening happens in
            // CssParsing.convertToIR so the web renderer's
            // `IRComponent[]` contract is upheld here.
            if (!value.children.isNullOrEmpty()) {
                put("children", json.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(IRComponentSerializer),
                    value.children
                ))
            }
            // Only include _text if non-null. Empty string IS preserved (it
            // means "extractor saw this element and it had no text" — which
            // is meaningfully different from "we never looked"); only `null`
            // suppresses the field. This keeps visual-test.json's existing
            // tmpOutput.json byte-identical because that fixture's Kotlin
            // text field is null for every component.
            if (value.text != null) {
                put("_text", value.text)
            }
            // Only include _role if non-null. Same byte-stability reasoning
            // as _text — only WPT body-root components have a value here
            // today, so every other fixture's tmpOutput.json stays
            // byte-for-byte identical. Renderers branch on the presence /
            // value of this marker (see swarm-003 backdrop-filter-root
            // investigation for the renderer-side body-root viewport-paint
            // follow-up). Stored as a plain JSON string primitive.
            if (value.role != null) {
                put("_role", value.role)
            }
        }

        encoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): IRComponent {
        require(decoder is JsonDecoder) { "This serializer only works with JSON" }
        val obj = decoder.decodeJsonElement().jsonObject
        val json = decoder.json

        // Recurse into children (array shape — see serialize() for why).
        // Round-trip support so tools reading tmpOutput.json get the full
        // tree back, not just a top-level stub. Property deserialization
        // itself is still a stub (see IRPropertySerializer); only the
        // structural fields (id, name, children, text) round-trip cleanly
        // for now, which is enough for the FIX-A2 use case.
        val childrenList = obj["children"]?.jsonArray?.map { childEl ->
            json.decodeFromJsonElement(IRComponentSerializer, childEl)
        }

        return IRComponent(
            id = obj["id"]?.jsonPrimitive?.content ?: "",
            name = obj["name"]!!.jsonPrimitive.content,
            properties = mutableListOf(), // Property deserialization not implemented (see IRPropertySerializer)
            selectors = emptyList(),
            media = emptyList(),
            children = childrenList,
            // Read `_text` (the on-the-wire name with leading underscore) and
            // map it back to the Kotlin `text` field. Missing field → null
            // (no _text was extracted), explicit JSON null → null, empty
            // string → "" (preserved as a meaningful "extracted, was empty"
            // signal — matches serialize() symmetry).
            text = obj["_text"]?.let { el ->
                if (el is JsonNull) null else el.jsonPrimitive.content
            },
            // Same symmetry for `_role`: missing → null, explicit null →
            // null, any string content → preserved verbatim. The only
            // emitted value today is `"body-root"` but we treat the field
            // as an open-ended String so future role markers (e.g.
            // `"html-root"`, `"viewport-overlay"`) don't require another
            // schema change.
            role = obj["_role"]?.let { el ->
                if (el is JsonNull) null else el.jsonPrimitive.content
            }
        )
    }
}

/**
 * Root IR model representing an entire style document.
 *
 * ## Purpose
 * The top-level container for the Intermediate Representation of a complete style definition.
 * This is the output of the parsing phase and the input to the generation phase.
 *
 * ## Processing Pipeline
 * ```
 * JSON Input
 *   ↓
 * CSS Parsing (CssParsing.kt)
 *   ↓
 * IRDocument (this class)
 *   ↓
 * Code Generation (ComposeGenerator, SwiftUIGenerator, etc.)
 *   ↓
 * Platform Output
 * ```
 *
 * ## Usage Example
 * ```kotlin
 * val document = IRDocument(
 *     components = listOf(
 *         IRComponent(
 *             name = "Button",
 *             properties = mutableListOf(
 *                 PaddingTopProperty(IRLength(16f, LengthUnit.DP)),
 *                 BackgroundColorProperty(IRColor.Hex("#007AFF"))
 *             ),
 *             selectors = listOf(
 *                 IRSelector(
 *                     condition = "hover",
 *                     properties = mutableListOf(
 *                         BackgroundColorProperty(IRColor.Hex("#0051D5"))
 *                     )
 *                 )
 *             ),
 *             media = listOf(
 *                 IRMedia(
 *                     query = "(min-width: 768px)",
 *                     properties = mutableListOf(
 *                         PaddingTopProperty(IRLength(20f, LengthUnit.DP))
 *                     )
 *                 )
 *             )
 *         )
 *     )
 * )
 * ```
 *
 * @property components List of all components in the document
 * @property keyframes Document-level named keyframe sets (wave 8 — see
 *   IRKeyframeStop below and schema/spec/07-animations.md). Marked
 *   @Transient so the plugin-generated serializer (the DEPRECATED
 *   `--emit-ir v1` path) can never leak the field into the frozen v1
 *   bytes; the v2 codec (IRWireV2.encodeDocument) reads it directly and
 *   emits the additive `keyframes` envelope key, omit-when-empty.
 */
@Serializable
data class IRDocument(
    val components: List<IRComponent>,
    // Fully qualified: plain @Transient would be ambiguous between
    // kotlinx.serialization.Transient (wanted) and kotlin.jvm.Transient.
    @kotlinx.serialization.Transient
    val keyframes: Map<String, List<IRKeyframeStop>>? = null
)

/**
 * One typed keyframe stop inside a document-level named keyframe set
 * (schema/spec/07-animations.md — the wire twin of one `@keyframes` rule).
 *
 * ## Wire shape (IR v2 additive key, spec 05 minor-revision process)
 * ```json
 * "keyframes": { "fade": [ { "offset": 0.0, "properties": [{type,data}…] }, … ] }
 * ```
 *
 * @property offset Resolved fractional position in [0, 1] — `from`/`0%` → 0,
 *   `to`/`100%` → 1, `50%` → 0.5 (css-animations-1 §4.2 keyframe selectors).
 *   The converter emits each set SORTED ascending by this value (stable for
 *   equal offsets) so runtimes can interpolate between adjacent entries
 *   without re-sorting.
 * @property properties The stop's declarations as typed IR properties —
 *   parsed by the exact same PropertiesParser pipeline as component base
 *   properties (shorthand expansion, color→sRGB, length→px, …). Animatable
 *   tier + interpolation rules are the runtimes' contract (spec 07 §2); the
 *   wire just carries the typed values.
 */
@Serializable
data class IRKeyframeStop(
    val offset: Double,
    val properties: MutableList<@Serializable(with = IRPropertySerializer::class) IRProperty>
)
