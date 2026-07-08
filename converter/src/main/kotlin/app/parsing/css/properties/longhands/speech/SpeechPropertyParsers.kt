package app.parsing.css.properties.longhands.speech

import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.speech.*
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.TimeParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object VolumePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return VolumeProperty(VolumeValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return VolumeProperty(VolumeValue.Raw(trimmed))
        }

        val v = when (lower) {
            "silent" -> VolumeValue.Silent
            "x-soft" -> VolumeValue.XSoft
            "soft" -> VolumeValue.Soft
            "medium" -> VolumeValue.Medium
            "loud" -> VolumeValue.Loud
            "x-loud" -> VolumeValue.XLoud
            else -> {
                if (lower.endsWith("%")) {
                    val p = lower.removeSuffix("%").toDoubleOrNull()
                    if (p != null) VolumeValue.Percentage(IRPercentage(p))
                    else VolumeValue.Raw(trimmed)
                } else {
                    val n = lower.toDoubleOrNull()
                    if (n != null) VolumeValue.Number(IRNumber(n))
                    else VolumeValue.Raw(trimmed)
                }
            }
        }
        return VolumeProperty(v)
    }
}

object SpeakPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "normal" -> SpeakValue.NORMAL
            "none" -> SpeakValue.NONE
            "spell-out" -> SpeakValue.SPELL_OUT
            else -> return null
        }
        return SpeakProperty(v)
    }
}

object SpeakAsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val values = value.trim().split(Regex("\\s+"))
        return SpeakAsProperty(values)
    }
}

object PausePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))
        val before = TimeParser.parse(parts.getOrNull(0) ?: return null)
        val after = if (parts.size > 1) TimeParser.parse(parts[1]) else before
        return PauseProperty(before, after)
    }
}

object PauseBeforePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val time = TimeParser.parse(value.trim()) ?: return null
        return PauseBeforeProperty(time)
    }
}

object PauseAfterPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val time = TimeParser.parse(value.trim()) ?: return null
        return PauseAfterProperty(time)
    }
}

object RestPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))
        val before = TimeParser.parse(parts.getOrNull(0) ?: return null)
        val after = if (parts.size > 1) TimeParser.parse(parts[1]) else before
        return RestProperty(before, after)
    }
}

object RestBeforePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val time = TimeParser.parse(value.trim()) ?: return null
        return RestBeforeProperty(time)
    }
}

object RestAfterPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val time = TimeParser.parse(value.trim()) ?: return null
        return RestAfterProperty(time)
    }
}

object CuePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))
        val before = parseCueValue(parts.getOrNull(0) ?: return null)
        val after = if (parts.size > 1) parseCueValue(parts[1]) else before
        return CueProperty(before, after)
    }
}

object CueBeforePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseCueValue(value.trim()) ?: return null
        return CueBeforeProperty(v)
    }
}

object CueAfterPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseCueValue(value.trim()) ?: return null
        return CueAfterProperty(v)
    }
}

private fun parseCueValue(s: String): CueValue? {
    val trimmed = s.lowercase()
    return when {
        trimmed == "none" -> CueValue.None
        trimmed.startsWith("url(") && trimmed.endsWith(")") -> {
            val url = trimmed.removePrefix("url(").removeSuffix(")").trim()
                .removeSurrounding("\"").removeSurrounding("'")
            CueValue.Url(url)
        }
        else -> null
    }
}

object VoiceFamilyPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val values = value.split(",").map { it.trim() }
        return VoiceFamilyProperty(values)
    }
}

object VoiceRatePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        return VoiceRateProperty(value.trim())
    }
}

object VoicePitchPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        return VoicePitchProperty(value.trim())
    }
}

object VoiceRangePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        return VoiceRangeProperty(value.trim())
    }
}

object VoiceStressPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        return VoiceStressProperty(value.trim())
    }
}

object VoiceVolumePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return VoiceVolumeProperty(VolumeValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return VoiceVolumeProperty(VolumeValue.Raw(trimmed))
        }

        val v = when (lower) {
            "silent" -> VolumeValue.Silent
            "x-soft" -> VolumeValue.XSoft
            "soft" -> VolumeValue.Soft
            "medium" -> VolumeValue.Medium
            "loud" -> VolumeValue.Loud
            "x-loud" -> VolumeValue.XLoud
            else -> {
                if (lower.endsWith("%")) {
                    val p = lower.removeSuffix("%").toDoubleOrNull()
                    if (p != null) VolumeValue.Percentage(IRPercentage(p))
                    else VolumeValue.Raw(trimmed)
                } else {
                    val n = lower.toDoubleOrNull()
                    if (n != null) VolumeValue.Number(IRNumber(n))
                    else VolumeValue.Raw(trimmed)
                }
            }
        }
        return VoiceVolumeProperty(v)
    }
}

object VoiceDurationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val time = TimeParser.parse(value.trim()) ?: return null
        return VoiceDurationProperty(time)
    }
}

object VoiceBalancePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val n = value.trim().toDoubleOrNull() ?: return null
        return VoiceBalanceProperty(IRNumber(n))
    }
}

object PitchPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        return PitchProperty(value.trim())
    }
}

object PitchRangePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val n = value.trim().toDoubleOrNull() ?: return null
        return PitchRangeProperty(IRNumber(n))
    }
}

object RichnessPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val n = value.trim().toDoubleOrNull() ?: return null
        return RichnessProperty(IRNumber(n))
    }
}

object StressPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val n = value.trim().toDoubleOrNull() ?: return null
        return StressProperty(IRNumber(n))
    }
}

object SpeechRatePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        return SpeechRateProperty(value.trim())
    }
}
