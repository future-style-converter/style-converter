package com.styleconverter.test.style.core.ir

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Root document containing all components.
 */
@Serializable
data class IRDocument(
    val components: List<IRComponent>
)

/**
 * A single UI component with its styles.
 *
 * @property id Unique identifier for SDUI (e.g., "button-001")
 * @property name Component type/class name (e.g., "Button", "Card")
 * @property properties List of CSS properties as IR
 * @property selectors State-based styles (hover, focus, etc.)
 * @property media Responsive breakpoint styles
 * @property children Nested child components for containers
 * @property _text Optional inner-text content carried from the WPT
 *   extractor. Renderer draws this verbatim instead of the placeholder
 *   name when present; also rendered as a leading inline node alongside
 *   children for mixed-content layouts (Bug 1, swarm-002 css-text-decor
 *   text-decoration-decorating-box-thickness-001).
 * @property _tag Optional originating HTML element tag (lowercase: 'ol',
 *   'li', 'p', etc.). Lets the renderer wire up tag-default behaviour —
 *   list-marker prefix on <li> children of <ol>/<ul>, paragraph spacing
 *   on <p>, table layout on <tr>/<td>, etc. (Bug 2, swarm-002
 *   css-counter-styles css3-counter-styles-101). Both fields are
 *   nullable so legacy fixtures (visual-test.json) deserialize
 *   unchanged and the 327-pair baseline stays stable.
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
    val _tag: String? = null
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
