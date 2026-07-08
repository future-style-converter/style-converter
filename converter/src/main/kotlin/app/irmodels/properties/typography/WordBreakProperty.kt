package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
sealed interface WordBreakProperty : IRProperty {
    override val propertyName: String get() = "word-break"

    @Serializable
    data class WordBreak(val value: WordBreakValue) : WordBreakProperty

    @Serializable
    data class Keyword(val value: String) : WordBreakProperty

    @Serializable
    data class Raw(val raw: String) : WordBreakProperty

    enum class WordBreakValue {
        NORMAL, BREAK_ALL, KEEP_ALL, BREAK_WORD, AUTO_PHRASE
    }
}
