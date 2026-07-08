package app.parsing.css.properties.longhands.regions

import app.irmodels.IRProperty
import app.irmodels.properties.regions.*
import app.parsing.css.properties.longhands.PropertyParser

object FlowIntoPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val v = if (trimmed.lowercase() == "none") FlowValue.None else FlowValue.Named(trimmed)
        return FlowIntoProperty(v)
    }
}

object FlowFromPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val v = if (trimmed.lowercase() == "none") FlowValue.None else FlowValue.Named(trimmed)
        return FlowFromProperty(v)
    }
}

object RegionFragmentPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "auto" -> RegionFragmentValue.AUTO
            "break" -> RegionFragmentValue.BREAK
            else -> return null
        }
        return RegionFragmentProperty(v)
    }
}

object ContinuePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "auto" -> ContinueValue.AUTO
            "discard" -> ContinueValue.DISCARD
            "overflow" -> ContinueValue.OVERFLOW
            else -> return null
        }
        return ContinueProperty(v)
    }
}

object CopyIntoPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        return CopyIntoProperty(value.trim())
    }
}

object WrapFlowPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "auto" -> WrapFlowValue.AUTO
            "both" -> WrapFlowValue.BOTH
            "start" -> WrapFlowValue.START
            "end" -> WrapFlowValue.END
            "maximum" -> WrapFlowValue.MAXIMUM
            "clear" -> WrapFlowValue.CLEAR
            else -> return null
        }
        return WrapFlowProperty(v)
    }
}

object WrapThroughPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "wrap" -> WrapThroughValue.WRAP
            "none" -> WrapThroughValue.NONE
            else -> return null
        }
        return WrapThroughProperty(v)
    }
}

object WrapBeforePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseWrapBreakValue(value) ?: return null
        return WrapBeforeProperty(v)
    }
}

object WrapAfterPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseWrapBreakValue(value) ?: return null
        return WrapAfterProperty(v)
    }
}

object WrapInsidePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseWrapBreakValue(value) ?: return null
        return WrapInsideProperty(v)
    }
}

private fun parseWrapBreakValue(value: String): WrapBreakValue? {
    return when (value.trim().lowercase()) {
        "auto" -> WrapBreakValue.AUTO
        "avoid" -> WrapBreakValue.AVOID
        "avoid-page" -> WrapBreakValue.AVOID_PAGE
        "avoid-column" -> WrapBreakValue.AVOID_COLUMN
        "avoid-region" -> WrapBreakValue.AVOID_REGION
        else -> null
    }
}
