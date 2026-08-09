package app.parsing.css.properties.longhands.images

import app.irmodels.*
import app.irmodels.properties.images.ObjectPositionProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.PositionParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords
import app.parsing.css.properties.primitiveParsers.ExpressionDetector

/**
 * `object-position` — css-images-3 §5.2, whose value is the css-values-4
 * `<position>` grammar shared with `background-position` (css-backgrounds-3
 * §3.6), `mask-position`, and every `<basic-shape>` `at` clause.
 *
 * WAVE-36 LANE M1 — THE AXIS BUG. The pre-wave-36 implementation read the
 * token stream POSITIONALLY: `parts[0] → x, parts[1] → y` for the two-value
 * form, and `keyword offset keyword offset` as x-first for the four-value
 * form. The grammar does not work that way. Its third arm is a `&&`
 * (unordered), so `top right` means **x = right, y = top**, and
 * `top 25% left 25%` means **y = (top, 25%), x = (left, 25%)** — both
 * spellings the css-images WPT `object-fit-*` family uses on six of the
 * seven boxes in every row. Six of every seven elements in 132 corpus tests
 * therefore reached the runtimes with the two axes SWAPPED. The one-value
 * form was wrong for the same reason: `top` alone is `center top`, not
 * `top center`.
 *
 * WAVE-38 LANE N4 — WHERE THE GRAMMAR LIVES NOW. Wave 36 fixed the axis
 * logic HERE; wave 37 hit the same class of bug in `clip-path` and lifted
 * the grammar into
 * [app.parsing.css.properties.primitiveParsers.PositionParser], leaving
 * this file as a second copy kept in agreement by a "if one changes,
 * change both" comment. A comment is not a contract — the duplication is
 * exactly what shipped the axis swap in the first place — so this parser
 * now delegates the whole grammar to that primitive and does nothing but
 * translate its result into this property's IR variants. The three
 * whole-value arms below (global keyword, expression, ungrammatical →
 * `Raw`) are the only behaviour left here, because they are about
 * `ObjectPositionProperty`'s wire shape rather than about `<position>`.
 *
 * WIRE SHAPE IS UNCHANGED, and that is why the delegation goes through
 * [PositionParser.parseAuthored] rather than the edge-normalising
 * [PositionParser.parse]: `ObjectPositionValue` has a distinct variant per
 * AUTHORED form (`keyword`, `keyword-offset`, `length`, `percentage`), so
 * normalising `left` to "0% from the left edge" would rewrite a
 * `{"keyword":"LEFT"}` leaf as `{"percentage":0}` — a byte-shape change
 * (schema/spec/05-versioning.md) with no benefit to any runtime.
 */
object ObjectPositionPropertyParser : PropertyParser {

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle global keywords first (inherit, initial, unset, revert,
        // revert-layer) — they replace the whole value, both axes at once.
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            val globalValue = ObjectPositionProperty.ObjectPositionValue.GlobalKeyword(trimmed)
            return ObjectPositionProperty(ObjectPositionProperty.Position(globalValue, globalValue))
        }

        // Handle CSS functions (var, env, calc, …). These cannot be resolved
        // at convert time (CLAUDE.md: unresolved means runtime-dependent), so
        // both axes carry the author's bytes verbatim.
        if (ExpressionDetector.containsExpression(trimmed)) {
            val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
            return ObjectPositionProperty(ObjectPositionProperty.Position(rawValue, rawValue))
        }

        // The grammar, in one call. Null means the value is not a
        // `<position>` (`top 10px`, `left right`, five components, an
        // unparseable unit) — see the Raw fallback below.
        val authored = PositionParser.parseAuthored(trimmed)
        val position = authored?.let {
            ObjectPositionProperty.Position(component(it.x), component(it.y))
        }
        return ObjectPositionProperty(position ?: raw(value))
    }

    /** The Raw fallback both axes share when the value is unparseable — a
     *  verbatim echo, never a guess (CLAUDE.md: no silent fallthroughs). A
     *  browser drops an invalid declaration, and a `Raw` value re-emitted
     *  verbatim on the web is dropped by the browser for the same reason,
     *  so the pipeline and the reference agree. */
    private fun raw(value: String): ObjectPositionProperty.Position {
        val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
        return ObjectPositionProperty.Position(rawValue, rawValue)
    }

    /**
     * One authored `<position>` component → the matching IR variant.
     *
     * [PositionParser.AuthoredComponent] has exactly three inhabitants and
     * they map one-to-one onto three of `ObjectPositionValue`'s variants;
     * the fourth branch is unreachable by that type's own invariant, so it
     * is a hard error rather than a quiet default.
     */
    private fun component(
        part: PositionParser.AuthoredComponent,
    ): ObjectPositionProperty.ObjectPositionValue {
        val keyword = part.keyword
        val offset = part.offset
        return when {
            // `left` / `center` / … — a bare keyword.
            keyword != null && offset == null ->
                ObjectPositionProperty.ObjectPositionValue.Keyword(irKeyword(keyword))
            // `right 2px` — an offset measured in from that keyword's edge.
            keyword != null ->
                ObjectPositionProperty.ObjectPositionValue.KeywordOffset(irKeyword(keyword), offset!!)
            // A bare `<length-percentage>`. Percentages keep their own
            // variant because §5.2 resolves them against the
            // (box − content) size, which only the runtime knows.
            offset != null && offset.unit == IRLength.LengthUnit.PERCENT ->
                ObjectPositionProperty.ObjectPositionValue.PercentageValue(IRPercentage(offset.value))
            offset != null ->
                ObjectPositionProperty.ObjectPositionValue.LengthValue(offset)
            // Unreachable: AuthoredComponent never has both fields null.
            else -> error("PositionParser returned an empty <position> component")
        }
    }

    /** `PositionParser`'s keyword enum → this property's own. Two enums
     *  because the IR one is `@Serializable` and its names are wire bytes,
     *  while the parser's is an internal detail of the grammar. */
    private fun irKeyword(
        keyword: PositionParser.Keyword,
    ): ObjectPositionProperty.PositionKeyword = when (keyword) {
        PositionParser.Keyword.LEFT -> ObjectPositionProperty.PositionKeyword.LEFT
        PositionParser.Keyword.RIGHT -> ObjectPositionProperty.PositionKeyword.RIGHT
        PositionParser.Keyword.TOP -> ObjectPositionProperty.PositionKeyword.TOP
        PositionParser.Keyword.BOTTOM -> ObjectPositionProperty.PositionKeyword.BOTTOM
        PositionParser.Keyword.CENTER -> ObjectPositionProperty.PositionKeyword.CENTER
    }
}
