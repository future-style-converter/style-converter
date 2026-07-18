package app.parsing.css.properties.longhands.effects

import app.irmodels.*
import app.irmodels.properties.effects.MaskImageProperty
import app.irmodels.properties.effects.MaskImageValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.PercentageParser
import app.parsing.css.properties.primitiveParsers.UrlParser

/**
 * Parser for the `mask-image` CSS property.
 *
 * Supports:
 * - none: No mask image
 * - url(): Image from URL
 * - linear-gradient(): Linear gradient
 * - radial-gradient(): Radial gradient
 * - conic-gradient(): Conic gradient
 * - Repeating variants
 *
 * Multiple images can be specified as comma-separated values.
 */
object MaskImagePropertyParser : PropertyParser {

    override fun parse(value: String): MaskImageProperty? {
        // CASE-PRESERVATION CONTRACT (mirrors BackgroundImagePropertyParser):
        // keywords/function names are ASCII case-insensitive (CSS Syntax L3
        // §4.3) but url() payloads are case-sensitive author bytes (base64
        // data URIs, case-sensitive paths). Keep the ORIGINAL bytes and
        // lower per-layer copies only for matching — the old whole-value
        // lowercase corrupted data-URI payloads on the wire. The v2 schema
        // is permissive at property-data leaves (schema/spec/05-versioning.md),
        // so restoring true bytes is a bug fix, not a wire-shape change.
        val trimmed = value.trim()

        // Split the ORIGINAL bytes; the splitter is paren-aware and
        // case-agnostic, so layers keep the author's casing.
        val imageStrings = splitByComma(trimmed)
        if (imageStrings.isEmpty()) return null

        val images = imageStrings.mapNotNull { parseImage(it.trim()) }
        if (images.size != imageStrings.size) return null

        return MaskImageProperty(images)
    }

    // Receives one layer in ORIGINAL author bytes; dispatches on a lowered
    // copy. url() payloads come from the original; gradient bodies parse
    // from the lowered copy — every gradient token (color keywords, hex,
    // angle units, directions) is case-insensitive per CSS Images L3/L4.
    private fun parseImage(value: String): MaskImageValue? {
        // Lowered copy used ONLY for prefix matching and gradient parsing.
        val lower = value.lowercase()
        return when {
            lower == "none" -> MaskImageValue.None
            // url(): UrlParser matches the function name case-insensitively
            // and returns the payload from the original bytes untouched.
            lower.startsWith("url(") -> parseUrl(value)
            lower.startsWith("linear-gradient(") -> parseLinearGradient(lower, repeating = false)
            lower.startsWith("repeating-linear-gradient(") -> parseLinearGradient(lower, repeating = true)
            lower.startsWith("radial-gradient(") -> parseRadialGradient(lower, repeating = false)
            lower.startsWith("repeating-radial-gradient(") -> parseRadialGradient(lower, repeating = true)
            lower.startsWith("conic-gradient(") -> parseConicGradient(lower, repeating = false)
            lower.startsWith("repeating-conic-gradient(") -> parseConicGradient(lower, repeating = true)
            else -> null
        }
    }

    private fun parseUrl(value: String): MaskImageValue? {
        val url = UrlParser.parse(value) ?: return null
        return MaskImageValue.Image(url)
    }

    private fun parseLinearGradient(value: String, repeating: Boolean): MaskImageValue? {
        val funcName = if (repeating) "repeating-linear-gradient" else "linear-gradient"
        val content = extractFunctionContent(value, funcName) ?: return null

        val parts = splitByComma(content)
        if (parts.isEmpty()) return null

        var angle: IRAngle? = null
        var colorStopStart = 0
        val firstPart = parts[0].trim()

        AngleParser.parse(firstPart)?.let {
            angle = it
            colorStopStart = 1
        } ?: run {
            if (firstPart.startsWith("to ")) {
                angle = parseDirectionToAngle(firstPart)
                if (angle != null) colorStopStart = 1
            }
        }

        val colorStops = parts.drop(colorStopStart).mapNotNull { parseColorStop(it.trim()) }
        if (colorStops.isEmpty()) return null

        return MaskImageValue.LinearGradient(angle, colorStops, repeating)
    }

    private fun parseDirectionToAngle(direction: String): IRAngle? {
        return when (direction.lowercase()) {
            "to top" -> IRAngle.fromDegrees(0.0)
            "to top right", "to right top" -> IRAngle.fromDegrees(45.0)
            "to right" -> IRAngle.fromDegrees(90.0)
            "to bottom right", "to right bottom" -> IRAngle.fromDegrees(135.0)
            "to bottom" -> IRAngle.fromDegrees(180.0)
            "to bottom left", "to left bottom" -> IRAngle.fromDegrees(225.0)
            "to left" -> IRAngle.fromDegrees(270.0)
            "to top left", "to left top" -> IRAngle.fromDegrees(315.0)
            else -> null
        }
    }

    private fun parseRadialGradient(value: String, repeating: Boolean): MaskImageValue? {
        val funcName = if (repeating) "repeating-radial-gradient" else "radial-gradient"
        val content = extractFunctionContent(value, funcName) ?: return null

        val parts = splitByComma(content)

        // Check for shape/size/position (e.g., "circle" at start)
        var shape: MaskImageValue.GradientShape? = null
        var colorStopStart = 0
        val firstPart = parts[0].trim()

        when {
            firstPart.startsWith("circle") -> {
                shape = MaskImageValue.GradientShape.CIRCLE
                colorStopStart = 1
            }
            firstPart.startsWith("ellipse") -> {
                shape = MaskImageValue.GradientShape.ELLIPSE
                colorStopStart = 1
            }
        }

        val colorStops = parts.drop(colorStopStart).mapNotNull { parseColorStop(it.trim()) }
        if (colorStops.isEmpty()) return null

        return MaskImageValue.RadialGradient(shape, null, null, colorStops, repeating)
    }

    private fun parseConicGradient(value: String, repeating: Boolean): MaskImageValue? {
        val funcName = if (repeating) "repeating-conic-gradient" else "conic-gradient"
        val content = extractFunctionContent(value, funcName) ?: return null

        val parts = splitByComma(content)
        val colorStops = parts.mapNotNull { parseColorStop(it.trim()) }
        if (colorStops.isEmpty()) return null

        return MaskImageValue.ConicGradient(null, null, colorStops, repeating)
    }

    private fun parseColorStop(value: String): MaskImageValue.ColorStop? {
        val parts = value.split("""\s+""".toRegex())
        if (parts.isEmpty()) return null

        val color = ColorParser.parse(parts[0]) ?: return null
        val position = if (parts.size > 1) PercentageParser.parse(parts[1]) else null

        return MaskImageValue.ColorStop(color, position)
    }

    private fun extractFunctionContent(value: String, funcName: String): String? {
        if (!value.startsWith("$funcName(") || !value.endsWith(")")) return null
        return value.substring(funcName.length + 1, value.length - 1)
    }

    private fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when (char) {
                '(' -> { depth++; current.append(char) }
                ')' -> { depth--; current.append(char) }
                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
