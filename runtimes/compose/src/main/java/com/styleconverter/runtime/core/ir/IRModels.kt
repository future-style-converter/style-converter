package com.styleconverter.runtime.core.ir

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Root document containing all components.
 *
 * @property keyframes Document-level named @keyframes sets (additive IR v2
 *   minor revision — schema/spec/07-animations.md §1.2). Keys are the
 *   lowercased custom-ident names `AnimationName` references; values are
 *   the offset-sorted stop lists. null when the wire omitted the key
 *   (omit-when-empty rule) and for every v1 document — the legacy path
 *   never carried keyframes, so the default keeps v1 decode byte-stable.
 */
@Serializable
data class IRDocument(
    val components: List<IRComponent>,
    val keyframes: Map<String, List<IRKeyframeStop>>? = null
)

/**
 * One resolved keyframe stop (spec 07 §1.2).
 *
 * @property offset The RESOLVED fraction in [0, 1] — the converter already
 *   mapped `from`/`to`/percent selectors, so readers never re-parse them.
 *   Stops arrive sorted ascending (stable for equal offsets); readers MAY
 *   rely on sortedness and MUST NOT reorder.
 * @property properties Standard typed `{type, data}` property envelopes —
 *   byte-identical semantics to component properties, including the
 *   unknown-type tolerance rule (skip + log at use, never at decode).
 */
@Serializable
data class IRKeyframeStop(
    val offset: Double,
    val properties: List<IRProperty> = emptyList()
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
 * Wave-20 wire contract (lane W2) — the form/widget attribute capsule the
 * extractor emits as `meta.attrs` for the widget tags (a, button, input,
 * textarea, select, option, meter, progress). ONLY present-in-source
 * attributes among the pinned ten appear; types are pinned by the wire
 * contract: strings except checked/multiple/selected/disabled (booleans)
 * and min/max/value-on-meter-progress (numbers where numeric — hence the
 * separate [valueNumber] channel so a numeric wire `value` survives
 * without stringly re-parsing). Droppable meta hint like `sourceTag`:
 * ignoring it loses fidelity, never correctness.
 */
@Serializable
data class IRAttrs(
    val type: String? = null,      // input type ("checkbox", "range", …)
    val value: String? = null,     // string-form value attribute
    val valueNumber: Double? = null, // numeric wire value (meter/progress)
    val checked: Boolean? = null,  // checkbox/radio checked presence
    val multiple: Boolean? = null, // select multiple presence (listbox)
    val selected: Boolean? = null, // option selected presence
    val disabled: Boolean? = null, // disabled presence (not yet painted)
    val size: String? = null,      // select/input size attribute
    val alt: String? = null,       // image-input alt text
    val min: Double? = null,       // meter/progress/range min
    val max: Double? = null        // meter/progress/range max
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
 * @property attrs Wave-20 widget-identity capsule (see [IRAttrs]) — v2
 *   wire name `meta.attrs`, riding the meta channel exactly like `_tag`
 *   (the documented precedent). Null for every v1 document and for
 *   components the extractor didn't tag (non-widget elements).
 * @property variables CSS custom-property definitions declared on this
 *   component ("--name" → RAW declaration value, verbatim). Additive IR
 *   v2 envelope key (schema/spec/01-envelope.md): names are
 *   case-SENSITIVE and values stay untyped token streams until var()
 *   substitution (css-variables-1 §2). null when the wire omitted the
 *   key. Resolution (element → slot-parent chain → fallback →
 *   guaranteed-invalid, schema/spec/02-values.md) is the style engine's
 *   job — the decoder only round-trips the map.
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
    val attrs: IRAttrs? = null,
    val slot: IRSlot? = null,
    val pseudos: JsonObject? = null,
    val role: String? = null,
    val variables: Map<String, String>? = null
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
