package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.InsetAreaProperty
import app.irmodels.properties.layout.advanced.InsetAreaKeyword
import app.irmodels.properties.layout.advanced.InsetAreaSpec
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object InsetAreaPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return InsetAreaProperty(InsetAreaSpec.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return InsetAreaProperty(InsetAreaSpec.Raw(trimmed))
        }

        val parts = lower.split(Regex("\\s+"))

        return when (parts.size) {
            1 -> {
                val kw = parseKeyword(parts[0])
                if (kw != null) {
                    InsetAreaProperty(InsetAreaSpec.Single(kw))
                } else {
                    InsetAreaProperty(InsetAreaSpec.Raw(trimmed))
                }
            }
            2 -> {
                val first = parseKeyword(parts[0])
                val second = parseKeyword(parts[1])
                if (first != null && second != null) {
                    InsetAreaProperty(InsetAreaSpec.Combined(first, second))
                } else {
                    InsetAreaProperty(InsetAreaSpec.Raw(trimmed))
                }
            }
            else -> InsetAreaProperty(InsetAreaSpec.Raw(trimmed))
        }
    }

    private fun parseKeyword(v: String): InsetAreaKeyword? = when (v) {
        "none" -> InsetAreaKeyword.NONE
        "top" -> InsetAreaKeyword.TOP
        "bottom" -> InsetAreaKeyword.BOTTOM
        "left" -> InsetAreaKeyword.LEFT
        "right" -> InsetAreaKeyword.RIGHT
        "start" -> InsetAreaKeyword.START
        "end" -> InsetAreaKeyword.END
        "center" -> InsetAreaKeyword.CENTER
        "span-all" -> InsetAreaKeyword.SPAN_ALL
        "span-top" -> InsetAreaKeyword.SPAN_TOP
        "span-bottom" -> InsetAreaKeyword.SPAN_BOTTOM
        "span-left" -> InsetAreaKeyword.SPAN_LEFT
        "span-right" -> InsetAreaKeyword.SPAN_RIGHT
        "span-start" -> InsetAreaKeyword.SPAN_START
        "span-end" -> InsetAreaKeyword.SPAN_END
        "span-x" -> InsetAreaKeyword.SPAN_X
        "span-y" -> InsetAreaKeyword.SPAN_Y
        "span-block-start" -> InsetAreaKeyword.SPAN_BLOCK_START
        "span-block-end" -> InsetAreaKeyword.SPAN_BLOCK_END
        "span-inline-start" -> InsetAreaKeyword.SPAN_INLINE_START
        "span-inline-end" -> InsetAreaKeyword.SPAN_INLINE_END
        "all" -> InsetAreaKeyword.ALL
        "block-start" -> InsetAreaKeyword.BLOCK_START
        "block-end" -> InsetAreaKeyword.BLOCK_END
        "inline-start" -> InsetAreaKeyword.INLINE_START
        "inline-end" -> InsetAreaKeyword.INLINE_END
        "self-start" -> InsetAreaKeyword.SELF_START
        "self-end" -> InsetAreaKeyword.SELF_END
        "self-block-start" -> InsetAreaKeyword.SELF_BLOCK_START
        "self-block-end" -> InsetAreaKeyword.SELF_BLOCK_END
        "self-inline-start" -> InsetAreaKeyword.SELF_INLINE_START
        "self-inline-end" -> InsetAreaKeyword.SELF_INLINE_END
        "x-start" -> InsetAreaKeyword.X_START
        "x-end" -> InsetAreaKeyword.X_END
        "y-start" -> InsetAreaKeyword.Y_START
        "y-end" -> InsetAreaKeyword.Y_END
        else -> null
    }
}
