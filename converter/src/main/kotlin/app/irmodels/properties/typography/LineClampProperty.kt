package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `line-clamp` property.
 *
 * ## CSS Property
 * **Syntax**: `line-clamp: none | [ <integer [1,∞]> || <'block-ellipsis'> ] -webkit-legacy?`
 * (css-overflow-4 §5.1, as pinned by WPT css/css-overflow/parsing/line-clamp-valid.html)
 *
 * ## Description
 * Limits the contents of a block to the specified number of lines.
 * Often used with `-webkit-line-clamp` for text truncation.
 * `auto` clamps at the element's own block-size constraint (max-height /
 * height) instead of at a fixed count — the count is a USED value, so it is
 * left for the runtimes to resolve (`null`-means-runtime-dependent, the same
 * contract lengths use).
 *
 * @property clamp The line-clamp value
 * @see [MDN line-clamp](https://developer.mozilla.org/en-US/docs/Web/CSS/line-clamp)
 */
@Serializable
data class LineClampProperty(
    val clamp: LineClamp
) : IRProperty {
    override val propertyName = "line-clamp"

    @Serializable
    sealed interface LineClamp {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : LineClamp

        /**
         * A fixed-count clamp: `line-clamp: <integer [1,∞]>`, optionally with
         * the two-value grammar's marker component riding along (wave-42
         * lane W7 — css-overflow-4 §5.1 `<integer> || <'block-ellipsis'>`).
         *
         * `ellipsis == null` means the marker component was absent OR was the
         * explicit `ellipsis` keyword — the marker longhand's INITIAL value
         * (WPT line-clamp-valid.html pins `line-clamp: 8 ellipsis` ≡ `8`), so
         * every pre-wave-42 emission stays BYTE-IDENTICAL: the converter's
         * Json leaves `encodeDefaults` off and omits the field, and both
         * native decoders read only `{type,count}` from this object (Compose
         * LineClampCap.linesCount / TextStyleApplier.extractLineClampValue,
         * Swift LineClampExtractor) — the field is strictly additive.
         */
        @Serializable
        @SerialName("lines")
        data class Lines(
            val count: IRNumber,
            val ellipsis: BlockEllipsis? = null
        ) : LineClamp

        /**
         * `line-clamp: auto` (css-overflow-4 §5.1). Carries no count: the
         * clamp point is wherever the element's block-size constraint falls,
         * which only a runtime that knows the used line-height can compute.
         * Serializes as `{"type":"auto"}` — an additive variant on a leaf the
         * v2 schema already treats permissively, and one that every existing
         * decoder already falls through to "no clamp" on (Compose
         * `extractLineClamp`, Swift `LineClampExtractor`).
         */
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : LineClamp
    }

    /**
     * The `<'block-ellipsis'>` component of the two-value `line-clamp`
     * grammar (wave-42 lane W7): which marker, if any, the runtime may draw
     * on the last shown line. Serialized with the usual `type` discriminator
     * as a nested object, e.g. `{"ellipsis":{"type":"no-ellipsis"}}` — an
     * additive leaf under the v2 schema's permissive property data.
     */
    @Serializable
    sealed interface BlockEllipsis {
        /**
         * `no-ellipsis` — clamp, but never render a marker; content must NOT
         * be displaced to make room for one (css-overflow-4 §block-ellipsis;
         * WPT block-ellipsis-023's assert is exactly this).
         */
        @Serializable
        @SerialName("no-ellipsis")
        data class NoEllipsis(val unit: Unit = Unit) : BlockEllipsis

        /**
         * `auto` — the UA chooses the marker string (an ellipsis on every
         * shipping engine). Distinct from the absent/`ellipsis` default only
         * in serialization (`8 auto` round-trips as `8 auto`, WPT valid set).
         */
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : BlockEllipsis

        /**
         * `<string>` — an author-supplied marker, quotes stripped. The EMPTY
         * string has the same effect as `no-ellipsis` (WPT block-ellipsis-024's
         * assert) but is kept verbatim: the IR carries computed values, and
         * the computed value of `line-clamp: 4 ""` retains its string.
         */
        @Serializable
        @SerialName("string")
        data class Text(val value: String) : BlockEllipsis
    }
}
