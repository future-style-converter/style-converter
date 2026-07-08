package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `line-break` property.
 *
 * ## CSS Property
 * **Syntax**: `line-break: auto | loose | normal | strict | anywhere`
 *
 * ## Description
 * Controls how to break lines of Chinese, Japanese, or Korean (CJK) text when wrapping.
 * Specifies the strictness of line breaking rules.
 *
 * ## Examples
 * ```kotlin
 * LineBreakProperty(lineBreak = LineBreak.Auto)
 * LineBreakProperty(lineBreak = LineBreak.Strict)
 * LineBreakProperty(lineBreak = LineBreak.Anywhere)
 * ```
 *
 * ## Platform Support
 * - **CSS**: Full support
 * - **Compose**: `LineBreak.Paragraph` for some control
 * - **SwiftUI**: Limited control
 *
 * @property lineBreak The line break behavior
 * @see [MDN line-break](https://developer.mozilla.org/en-US/docs/Web/CSS/line-break)
 */
@Serializable
data class LineBreakProperty(
    val lineBreak: LineBreak
) : IRProperty {
    override val propertyName = "line-break"
}

/**
 * Represents line-break values.
 */
@Serializable
enum class LineBreak {
    /**
     * Break text using the default line break rule.
     */
    AUTO,

    /**
     * Break text using the least restrictive line break rule.
     * Typically used for short lines, such as in newspapers.
     */
    LOOSE,

    /**
     * Break text using the most common line break rule.
     */
    NORMAL,

    /**
     * Break text using the most stringent line break rule.
     */
    STRICT,

    /**
     * Allows breaking anywhere, even in the middle of words.
     * Useful for preventing text overflow.
     */
    ANYWHERE
}
