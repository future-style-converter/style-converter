package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRAngle
import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.GeometryBox
import app.irmodels.properties.layout.advanced.OffsetPathProperty
import app.irmodels.properties.layout.advanced.OffsetPathValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object OffsetPathPropertyParser : PropertyParser {
    private val geometryBoxKeywords = mapOf(
        "border-box" to GeometryBox.BORDER_BOX,
        "padding-box" to GeometryBox.PADDING_BOX,
        "content-box" to GeometryBox.CONTENT_BOX,
        "margin-box" to GeometryBox.MARGIN_BOX,
        "fill-box" to GeometryBox.FILL_BOX,
        "stroke-box" to GeometryBox.STROKE_BOX,
        "view-box" to GeometryBox.VIEW_BOX
    )

    private val raySizeKeywords = setOf(
        "closest-side", "closest-corner", "farthest-side", "farthest-corner", "sides"
    )

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return OffsetPathProperty(OffsetPathValue.Keyword(lowered))
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return OffsetPathProperty(OffsetPathValue.Raw(trimmed))
        }

        // Check for geometry-box keyword alone
        geometryBoxKeywords[lowered]?.let { box ->
            return OffsetPathProperty(OffsetPathValue.GeometryBoxValue(box))
        }

        // Check for shape + geometry-box (e.g., "circle(50%) content-box")
        val parts = lowered.split(Regex("\\s+"))
        if (parts.size >= 2) {
            val lastPart = parts.last()
            geometryBoxKeywords[lastPart]?.let { box ->
                val shapePart = parts.dropLast(1).joinToString(" ")
                if (shapePart.startsWith("circle(") || shapePart.startsWith("ellipse(") ||
                    shapePart.startsWith("polygon(") || shapePart.startsWith("inset(") ||
                    shapePart.startsWith("rect(") || shapePart.startsWith("xywh(")) {
                    return OffsetPathProperty(OffsetPathValue.ShapeWithBox(shapePart, box))
                }
            }
        }

        return when {
            lowered == "none" -> OffsetPathProperty(OffsetPathValue.None)

            lowered.startsWith("path(") && trimmed.endsWith(")") -> {
                val pathData = trimmed.removePrefix("path(").removeSuffix(")").trim()
                    .removeSurrounding("\"").removeSurrounding("'")
                OffsetPathProperty(OffsetPathValue.PathString(pathData))
            }

            lowered.startsWith("url(") && trimmed.endsWith(")") -> {
                val url = trimmed.removePrefix("url(").removeSuffix(")").trim()
                    .removeSurrounding("\"").removeSurrounding("'")
                OffsetPathProperty(OffsetPathValue.Url(url))
            }

            lowered.startsWith("ray(") -> parseRay(lowered)

            lowered.startsWith("circle(") -> parseCircle(lowered)

            lowered.startsWith("ellipse(") -> parseEllipse(lowered)

            lowered.startsWith("polygon(") -> parsePolygon(lowered)

            lowered.startsWith("inset(") -> parseInset(lowered)

            lowered.startsWith("rect(") -> parseRect(lowered)

            lowered.startsWith("xywh(") -> parseXywh(lowered)

            else -> OffsetPathProperty(OffsetPathValue.Raw(trimmed))
        }
    }

    /**
     * Parse ray(angle [size] [contain] [at position])
     * Examples: ray(45deg), ray(45deg closest-side), ray(45deg closest-side contain)
     */
    private fun parseRay(value: String): OffsetPathProperty {
        val params = extractFunctionParams(value) ?: return OffsetPathProperty(OffsetPathValue.Raw(value))
        val tokens = params.split(Regex("\\s+")).toMutableList()

        if (tokens.isEmpty()) return OffsetPathProperty(OffsetPathValue.Raw(value))

        // First token should be an angle
        val angle = AngleParser.parse(tokens[0]) ?: return OffsetPathProperty(OffsetPathValue.Raw(value))
        tokens.removeAt(0)

        var size: String? = null
        var contain = false
        var position: String? = null

        // Parse remaining tokens
        val remaining = tokens.toMutableList()
        while (remaining.isNotEmpty()) {
            val token = remaining.first()
            when {
                token in raySizeKeywords -> {
                    size = token
                    remaining.removeAt(0)
                }
                token == "contain" -> {
                    contain = true
                    remaining.removeAt(0)
                }
                token == "at" -> {
                    // Rest is position
                    position = remaining.joinToString(" ")
                    remaining.clear()
                }
                else -> remaining.removeAt(0) // Skip unknown tokens
            }
        }

        return OffsetPathProperty(OffsetPathValue.Ray(angle, size, contain, position))
    }

    /**
     * Parse circle(radius [at position])
     * Examples: circle(50%), circle(50% at center)
     */
    private fun parseCircle(value: String): OffsetPathProperty {
        val params = extractFunctionParams(value) ?: return OffsetPathProperty(OffsetPathValue.Raw(value))

        val atIndex = params.indexOf(" at ")
        return if (atIndex != -1) {
            val radius = params.substring(0, atIndex).trim()
            val position = params.substring(atIndex + 4).trim()
            OffsetPathProperty(OffsetPathValue.Circle(radius, position))
        } else {
            OffsetPathProperty(OffsetPathValue.Circle(params.trim(), null))
        }
    }

    /**
     * Parse ellipse(rx ry [at position])
     * Examples: ellipse(50% 30%), ellipse(50% 30% at center)
     */
    private fun parseEllipse(value: String): OffsetPathProperty {
        val params = extractFunctionParams(value) ?: return OffsetPathProperty(OffsetPathValue.Raw(value))

        val atIndex = params.indexOf(" at ")
        val radiiPart = if (atIndex != -1) params.substring(0, atIndex).trim() else params.trim()
        val position = if (atIndex != -1) params.substring(atIndex + 4).trim() else null

        val radii = radiiPart.split(Regex("\\s+"))
        if (radii.size >= 2) {
            return OffsetPathProperty(OffsetPathValue.Ellipse(radii[0], radii[1], position))
        }

        return OffsetPathProperty(OffsetPathValue.Raw(value))
    }

    /**
     * Parse polygon([fill-rule,] point, point, ...)
     * Examples: polygon(0% 0%, 100% 0%, 100% 100%), polygon(evenodd, 50% 0%, 100% 100%, 0% 100%)
     */
    private fun parsePolygon(value: String): OffsetPathProperty {
        val params = extractFunctionParams(value) ?: return OffsetPathProperty(OffsetPathValue.Raw(value))

        val parts = params.split(",").map { it.trim() }
        if (parts.isEmpty()) return OffsetPathProperty(OffsetPathValue.Raw(value))

        // Check if first part is a fill-rule
        val firstPart = parts[0]
        val fillRule = if (firstPart == "nonzero" || firstPart == "evenodd") firstPart else null
        val points = if (fillRule != null) parts.drop(1) else parts

        return OffsetPathProperty(OffsetPathValue.Polygon(fillRule, points))
    }

    /**
     * Parse inset(offsets [round radius])
     * Examples: inset(10%), inset(10% round 20px)
     */
    private fun parseInset(value: String): OffsetPathProperty {
        val params = extractFunctionParams(value) ?: return OffsetPathProperty(OffsetPathValue.Raw(value))

        val roundIndex = params.indexOf(" round ")
        return if (roundIndex != -1) {
            val offsets = params.substring(0, roundIndex).trim()
            val borderRadius = params.substring(roundIndex + 7).trim()
            OffsetPathProperty(OffsetPathValue.Inset(offsets, borderRadius))
        } else {
            OffsetPathProperty(OffsetPathValue.Inset(params.trim(), null))
        }
    }

    /**
     * Parse rect(top right bottom left [round radius])
     * Examples: rect(10px 20px 30px 40px), rect(10px 20px 30px 40px round 5px)
     */
    private fun parseRect(value: String): OffsetPathProperty {
        val params = extractFunctionParams(value) ?: return OffsetPathProperty(OffsetPathValue.Raw(value))

        val roundIndex = params.indexOf(" round ")
        val valuesPart = if (roundIndex != -1) params.substring(0, roundIndex).trim() else params.trim()
        val borderRadius = if (roundIndex != -1) params.substring(roundIndex + 7).trim() else null

        val values = valuesPart.split(Regex("\\s+"))
        if (values.size >= 4) {
            return OffsetPathProperty(OffsetPathValue.Rect(values[0], values[1], values[2], values[3], borderRadius))
        }

        return OffsetPathProperty(OffsetPathValue.Raw(value))
    }

    /**
     * Parse xywh(x y width height [round radius])
     * Examples: xywh(10px 20px 100px 100px), xywh(10px 20px 100px 100px round 10px)
     */
    private fun parseXywh(value: String): OffsetPathProperty {
        val params = extractFunctionParams(value) ?: return OffsetPathProperty(OffsetPathValue.Raw(value))

        val roundIndex = params.indexOf(" round ")
        val valuesPart = if (roundIndex != -1) params.substring(0, roundIndex).trim() else params.trim()
        val borderRadius = if (roundIndex != -1) params.substring(roundIndex + 7).trim() else null

        val values = valuesPart.split(Regex("\\s+"))
        if (values.size >= 4) {
            return OffsetPathProperty(OffsetPathValue.Xywh(values[0], values[1], values[2], values[3], borderRadius))
        }

        return OffsetPathProperty(OffsetPathValue.Raw(value))
    }

    private fun extractFunctionParams(value: String): String? {
        val start = value.indexOf("(")
        val end = value.lastIndexOf(")")
        if (start == -1 || end == -1 || end <= start) return null
        return value.substring(start + 1, end).trim()
    }
}
