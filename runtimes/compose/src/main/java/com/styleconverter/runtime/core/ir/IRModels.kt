package com.styleconverter.runtime.core.ir

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Root document containing all components.
 */
@Serializable
data class IRDocument(
    val components: List<IRComponent>
)

/**
 * The IR v2 child→parent composition reference (schema/spec/03-children.md).
 *
 * Carried by a CHILD component in a flat v2 document; roots omit it.
 * `slot` is STRUCTURAL data that MUST round-trip (spec 05 tolerance rule 4)
 * — unlike the droppable `meta` hints. Only composers (the harness's
 * SlotComposer, later an SDUI shell) may read it; the style engine itself
 * is composition-agnostic by contract and never consults slot.
 *
 * @property parent The `id` of the container this component previews inside.
 * @property name Slot name within the parent; the wire omits it when it
 *   equals the documented default "content" (reserved for future
 *   multi-slot containers — scaffold header/body/footer).
 */
@Serializable
data class IRSlot(
    val parent: String,
    val name: String = "content"
)

/**
 * A single UI component with its styles.
 *
 * @property id Unique identifier for SDUI (e.g., "button-001")
 * @property name Component type/class name (e.g., "Button", "Card")
 * @property properties List of CSS properties as IR
 * @property selectors State-based styles (hover, focus, etc.)
 * @property media Responsive breakpoint styles
 * @property children Nested child components for containers. v1 wire only:
 *   IR v2 documents are FLAT (children on the wire is a hard decode error —
 *   see IRDocumentDecoder); in-memory the field is (re)populated by
 *   SlotComposer from the v2 `slot` refs so the renderer sees one tree
 *   shape regardless of wire version.
 * @property _text Optional inner-text content carried from the WPT
 *   extractor. Renderer draws this verbatim instead of the placeholder
 *   name when present; also rendered as a leading inline node alongside
 *   children for mixed-content layouts (Bug 1, swarm-002 css-text-decor
 *   text-decoration-decorating-box-thickness-001). v2 wire name: `text`
 *   (IRDocumentDecoder maps it onto this field; the in-memory name keeps
 *   the v1-era spelling so the renderer and its pinning tests are
 *   untouched by the wire rename).
 * @property _tag Optional originating HTML element tag (lowercase: 'ol',
 *   'li', 'p', etc.). Lets the renderer wire up tag-default behaviour —
 *   list-marker prefix on <li> children of <ol>/<ul>, paragraph spacing
 *   on <p>, table layout on <tr>/<td>, etc. (Bug 2, swarm-002
 *   css-counter-styles css3-counter-styles-101). Both fields are
 *   nullable so legacy fixtures (visual-test.json) deserialize
 *   unchanged and the 327-pair baseline stays stable. v2 wire name:
 *   `meta.sourceTag`.
 * @property slot v2 composition reference (see [IRSlot]); null for roots
 *   and for every component of a v1 document.
 * @property pseudos v2 generated-content payload (wire rename of the
 *   authoring `_pseudo`): an opaque component-shaped {before?, after?,
 *   marker?} object. Pseudo nodes never flatten (design §4.2) — they have
 *   no independent lifecycle. Decoded and retained so the contract
 *   round-trips; the Compose renderer does not consume it yet (its
 *   ::before/::after support still rides the selectors channel — TODO
 *   route pseudos into ContentApplier when the web/iOS ordering contract
 *   is pinned cross-platform).
 * @property role v2 `meta.role` droppable hint (only value emitted today
 *   is "body-root"). v1 wire spelled it `_role` and this model dropped it
 *   (the documented spec-04 caveat); the v2 decoder closes that gap.
 */
@Serializable
data class IRComponent(
    val id: String,
    val name: String,
    val properties: List<IRProperty> = emptyList(),
    val selectors: List<IRSelector> = emptyList(),
    val media: List<IRMedia> = emptyList(),
    val children: List<IRComponent>? = null,
    val _text: String? = null,
    val _tag: String? = null,
    val slot: IRSlot? = null,
    val pseudos: JsonObject? = null,
    val role: String? = null
)

/**
 * A CSS property in IR format.
 *
 * Uses generic JsonElement for data to handle all 446+ property types flexibly.
 * Specific property handling is done in StyleApplier.
 */
@Serializable
data class IRProperty(
    val type: String,
    val data: JsonElement
)

/**
 * Pseudo-class selector styles (e.g., :hover, :focus).
 */
@Serializable
data class IRSelector(
    val condition: String,
    val properties: List<IRProperty>
)

/**
 * Media query styles (e.g., min-width: 768px).
 */
@Serializable
data class IRMedia(
    val query: String,
    val properties: List<IRProperty>
)
