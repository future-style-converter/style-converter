package app.irmodels.properties.columns

// IRLength (dual px/original storage) comes from the shared value types.
import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CSS Gap Decorations Level 1 §4.3 `row-rule-width`.
 *
 * Row-axis twin of `column-rule-width`. Value domain is `<line-width>`:
 * either a `<length>` or one of the three legacy keywords. Mirrors
 * [ColumnRuleWidthProperty] exactly so the emitted `data` is
 * `{"type":"length","px":10}` / `{"type":"keyword","value":"THIN"}` — the
 * same bytes the wave23 css-gaps IR shows for ColumnRuleWidth.
 */
@Serializable
data class RowRuleWidthProperty(
    // Single field → flattened away by IRPropertySerializer, leaving the
    // sealed-variant object as the property `data`.
    val width: RuleWidth
) : IRProperty {
    // Canonical kebab-case CSS name.
    override val propertyName = "row-rule-width"

    /** `<line-width>` = `<length>` | thin | medium | thick. */
    @Serializable
    sealed interface RuleWidth {
        // kotlinx writes the class discriminator as "type"; SerialName pins it
        // to the lower-case tag the runtimes already switch on.
        @SerialName("length")
        @Serializable
        data class LengthValue(val length: IRLength) : RuleWidth

        // Keyword variant — deep-flatten turns {"type":"keyword","value":…}
        // into the same shape ColumnRuleWidth emits.
        @SerialName("keyword")
        @Serializable
        data class Keyword(val value: RuleWidthKeyword) : RuleWidth
    }

    /** The three `<line-width>` keywords (CSS Backgrounds & Borders 3 §4.3). */
    enum class RuleWidthKeyword {
        THIN, MEDIUM, THICK
    }
}
