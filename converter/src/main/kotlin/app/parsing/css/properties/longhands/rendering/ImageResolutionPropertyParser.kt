package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.ImageResolutionProperty
import app.irmodels.properties.rendering.ImageResolutionValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object ImageResolutionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return ImageResolutionProperty(ImageResolutionValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return ImageResolutionProperty(ImageResolutionValue.Raw(trimmed))
        }

        // Handle "from-image" keyword
        if (lower == "from-image") {
            return ImageResolutionProperty(ImageResolutionValue.FromImage)
        }

        // Parse dppx value (e.g., "2dppx", "300dpi", "118dpcm")
        val dppxValue = when {
            lower.endsWith("dppx") -> lower.removeSuffix("dppx").toDoubleOrNull()
            lower.endsWith("dpi") -> {
                val dpi = lower.removeSuffix("dpi").toDoubleOrNull()
                dpi?.let { it / 96.0 }  // Convert DPI to DPPX
            }
            lower.endsWith("dpcm") -> {
                val dpcm = lower.removeSuffix("dpcm").toDoubleOrNull()
                dpcm?.let { it * 2.54 / 96.0 }  // Convert DPCM to DPPX
            }
            else -> lower.toDoubleOrNull()
        }

        return if (dppxValue != null) {
            ImageResolutionProperty(ImageResolutionValue.Dppx(dppxValue))
        } else {
            ImageResolutionProperty(ImageResolutionValue.Raw(trimmed))
        }
    }
}
