package app.parsing.css.properties.longhands.shapes

import app.irmodels.IRProperty
import app.irmodels.properties.shapes.ShapeOutsideProperty
import app.irmodels.properties.shapes.ShapeOutsideValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.UrlParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parses the CSS `shape-outside` property.
 *
 * Syntax:
 * - none
 * - <shape-box> (margin-box, border-box, padding-box, content-box)
 * - <basic-shape> (circle(), ellipse(), polygon(), path())
 * - <image> (url())
 *
 * Examples:
 * - "none"
 * - "margin-box"
 * - "circle(50%)"
 * - "polygon(0 0, 100% 0, 100% 100%)"
 * - "url(shape.png)"
 */
object ShapeOutsidePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return ShapeOutsideProperty(ShapeOutsideValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return ShapeOutsideProperty(ShapeOutsideValue.Raw(trimmed))
        }

        // Check for none
        if (lower == "none") {
            return ShapeOutsideProperty(ShapeOutsideValue.None)
        }

        // Check for shape-box values
        val shapeBoxValue = when (lower) {
            "margin-box" -> ShapeOutsideValue.MarginBox
            "border-box" -> ShapeOutsideValue.BorderBox
            "padding-box" -> ShapeOutsideValue.PaddingBox
            "content-box" -> ShapeOutsideValue.ContentBox
            else -> null
        }
        if (shapeBoxValue != null) {
            return ShapeOutsideProperty(shapeBoxValue)
        }

        // Check for url() - image
        val url = UrlParser.parse(trimmed)
        if (url != null) {
            return ShapeOutsideProperty(ShapeOutsideValue.ImageUrl(url))
        }

        // Check for basic-shape functions
        if (lower.startsWith("circle(") ||
            lower.startsWith("ellipse(") ||
            lower.startsWith("polygon(") ||
            lower.startsWith("path(") ||
            lower.startsWith("inset(")
        ) {
            return ShapeOutsideProperty(ShapeOutsideValue.BasicShape(trimmed))
        }

        return ShapeOutsideProperty(ShapeOutsideValue.Raw(trimmed))
    }
}
