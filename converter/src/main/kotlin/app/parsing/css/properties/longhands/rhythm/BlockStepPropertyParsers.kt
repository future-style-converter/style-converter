package app.parsing.css.properties.longhands.rhythm

import app.irmodels.IRProperty
import app.irmodels.properties.rhythm.*
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object BlockStepAlignPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "auto" -> BlockStepAlignValue.AUTO
            "center" -> BlockStepAlignValue.CENTER
            "start" -> BlockStepAlignValue.START
            "end" -> BlockStepAlignValue.END
            else -> return null
        }
        return BlockStepAlignProperty(v)
    }
}

object BlockStepInsertPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "margin" -> BlockStepInsertValue.MARGIN
            "padding" -> BlockStepInsertValue.PADDING
            else -> return null
        }
        return BlockStepInsertProperty(v)
    }
}

object BlockStepRoundPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "up" -> BlockStepRoundValue.UP
            "down" -> BlockStepRoundValue.DOWN
            "nearest" -> BlockStepRoundValue.NEAREST
            else -> return null
        }
        return BlockStepRoundProperty(v)
    }
}

object BlockStepSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val length = LengthParser.parse(value.trim()) ?: return null
        return BlockStepSizeProperty(length)
    }
}

object BlockStepPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split("\\s+".toRegex())
        var size: app.irmodels.IRLength? = null
        var insert: BlockStepInsertValue? = null
        var align: BlockStepAlignValue? = null
        var round: BlockStepRoundValue? = null

        for (part in parts) {
            val lower = part.lowercase()
            when {
                lower == "margin" -> insert = BlockStepInsertValue.MARGIN
                lower == "padding" -> insert = BlockStepInsertValue.PADDING
                lower == "auto" -> align = BlockStepAlignValue.AUTO
                lower == "center" -> align = BlockStepAlignValue.CENTER
                lower == "start" -> align = BlockStepAlignValue.START
                lower == "end" -> align = BlockStepAlignValue.END
                lower == "up" -> round = BlockStepRoundValue.UP
                lower == "down" -> round = BlockStepRoundValue.DOWN
                lower == "nearest" -> round = BlockStepRoundValue.NEAREST
                else -> size = LengthParser.parse(part)
            }
        }

        return BlockStepProperty(size, insert, align, round)
    }
}
