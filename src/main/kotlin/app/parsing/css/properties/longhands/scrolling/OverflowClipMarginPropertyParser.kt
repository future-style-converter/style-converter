package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.OverflowClipMargin
import app.irmodels.properties.scrolling.OverflowClipMarginProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object OverflowClipMarginPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val parts = trimmed.split(Regex("\\s+"))

        return when (parts.size) {
            1 -> parseSingleValue(parts[0])?.let { OverflowClipMarginProperty(it) }
            2 -> {
                // Two values: could be "<box> <length>" or "<length> <box>"
                val box = parseBox(parts[0]) ?: parseBox(parts[1])
                val lengthStr = if (parseBox(parts[0]) != null) parts[1] else parts[0]
                val length = LengthParser.parse(lengthStr) ?: return null
                box?.let {
                    OverflowClipMarginProperty(OverflowClipMargin.BoxWithLength(it, length))
                }
            }
            else -> null
        }
    }

    private fun parseSingleValue(v: String): OverflowClipMargin? {
        return when (v) {
            "content-box" -> OverflowClipMargin.ContentBox
            "padding-box" -> OverflowClipMargin.PaddingBox
            "border-box" -> OverflowClipMargin.BorderBox
            else -> LengthParser.parse(v)?.let { OverflowClipMargin.Length(it) }
        }
    }

    private fun parseBox(v: String): OverflowClipMargin.Box? {
        return when (v) {
            "content-box" -> OverflowClipMargin.Box.CONTENT_BOX
            "padding-box" -> OverflowClipMargin.Box.PADDING_BOX
            "border-box" -> OverflowClipMargin.Box.BORDER_BOX
            else -> null
        }
    }
}
