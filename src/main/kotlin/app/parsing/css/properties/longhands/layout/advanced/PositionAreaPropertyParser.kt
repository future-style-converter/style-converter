package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.PositionAreaProperty
import app.irmodels.properties.layout.advanced.PositionAreaValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object PositionAreaPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            val kw = PositionAreaValue.Keyword(lower)
            return PositionAreaProperty(kw, kw)
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            val raw = PositionAreaValue.Raw(trimmed)
            return PositionAreaProperty(raw, raw)
        }

        val parts = lower.split(Regex("\\s+"))
        if (parts.isEmpty()) {
            val raw = PositionAreaValue.Raw(trimmed)
            return PositionAreaProperty(raw, raw)
        }

        val row = parseAreaValue(parts[0])
        val column = if (parts.size > 1) parseAreaValue(parts[1]) else row

        return PositionAreaProperty(row, column)
    }

    private fun parseAreaValue(s: String): PositionAreaValue {
        return when (s) {
            "none" -> PositionAreaValue.None
            "top" -> PositionAreaValue.Top
            "bottom" -> PositionAreaValue.Bottom
            "left" -> PositionAreaValue.Left
            "right" -> PositionAreaValue.Right
            "center" -> PositionAreaValue.Center
            "block-start" -> PositionAreaValue.BlockStart
            "block-end" -> PositionAreaValue.BlockEnd
            "inline-start" -> PositionAreaValue.InlineStart
            "inline-end" -> PositionAreaValue.InlineEnd
            "span-all" -> PositionAreaValue.SpanAll
            "span-left" -> PositionAreaValue.SpanLeft
            "span-right" -> PositionAreaValue.SpanRight
            "span-top" -> PositionAreaValue.SpanTop
            "span-bottom" -> PositionAreaValue.SpanBottom
            "span-block-start" -> PositionAreaValue.SpanBlockStart
            "span-block-end" -> PositionAreaValue.SpanBlockEnd
            "span-inline-start" -> PositionAreaValue.SpanInlineStart
            "span-inline-end" -> PositionAreaValue.SpanInlineEnd
            "self-block-start" -> PositionAreaValue.SelfBlockStart
            "self-block-end" -> PositionAreaValue.SelfBlockEnd
            "self-inline-start" -> PositionAreaValue.SelfInlineStart
            "self-inline-end" -> PositionAreaValue.SelfInlineEnd
            else -> PositionAreaValue.Raw(s)
        }
    }
}
