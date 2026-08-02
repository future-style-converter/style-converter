package com.styleconverter.runtime.lists

import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

object ListStyleExtractor {

    /**
     * Fold a property list onto [base].
     *
     * @param base the configuration the declarations override. Defaults to
     *   [ListStyleConfig] (disc / outside) so every pre-wave-24 caller
     *   keeps its exact behaviour; [resolveMarkerConfig] passes the list
     *   container's UA default instead.
     */
    fun extractListStyleConfig(
        properties: List<Pair<String, JsonElement?>>,
        base: ListStyleConfig = ListStyleConfig()
    ): ListStyleConfig {
        var config = base

        for ((type, data) in properties) {
            config = when (type) {
                // Unrecognised keywords leave the running value alone (the
                // extractors return null) — see extractListStyleType.
                "ListStyleType" ->
                    extractListStyleType(data)?.let { config.copy(listStyleType = it) } ?: config
                "ListStylePosition" ->
                    extractListStylePosition(data)?.let { config.copy(listStylePosition = it) } ?: config
                "ListStyleImage" -> config.copy(listStyleImage = extractListStyleImage(data))
                // Wave 25: the UNEXPANDED shorthand. isListStyleProperty
                // has always claimed this type, so a wire doc carrying it
                // reached here and then applied NOTHING. It is unreachable
                // from this repo's converter (pinned by a live run — see
                // ListStyleShorthand's header) but tolerated by
                // schema/spec/05-versioning.md, so a foreign producer can
                // emit it; the branch makes the claim honest.
                "ListStyle" ->
                    ListStyleShorthand.apply(config, ValueExtractors.extractKeyword(data))
                else -> config
            }
        }

        return config
    }

    /**
     * The UA-stylesheet marker family a list container hands its items —
     * HTML §15.3.9 (`ol { list-style-type: decimal }`, `ul, menu, dir {
     * list-style-type: disc }`). Null for anything that is not a list
     * container, which is how [resolveMarkerConfig] declines to synthesise
     * a marker at all.
     */
    fun uaMarkerDefault(sourceTag: String?): ListStyleType? =
        when (sourceTag?.lowercase()) {
            "ol" -> ListStyleType.DECIMAL
            "ul", "menu", "dir" -> ListStyleType.DISC
            else -> null
        }

    /**
     * Resolve the marker configuration for ONE `<li>` (wave 24, lane LF,
     * B-RC3 parts 1+2).
     *
     * Before this, both native renderers derived the marker purely from
     * the PARENT's `meta.sourceTag` — `<ol>` ⇒ "1.", `<ul>` ⇒ "•" — and
     * threw the item's own declarations away. The live wire puts them on
     * the child: `tools/titan/runs/wave23-final/sections/css-lists/
     * per-test-ir/wpt__css-lists__change-list-style-type-001.json` carries
     * `{"type":"ListStyleType","data":"square"}` (also `"none"`,
     * `"upper-roman"`, `"decimal"`) on each `li` component while the `ul`
     * parent carries only `{"type":"ListStylePosition","data":"INSIDE"}`.
     * Every one of those items painted a plain disc.
     *
     * Resolution order is the CSS cascade for an inherited property
     * (css-lists-3 §3.1 — all three list-style-* longhands are
     * "Inherited: yes"; css-cascade-4 §4.3):
     *
     *  1. the container's UA default for [parentTag],
     *  2. the container's declarations, which the renderer hands in
     *     already inheritance-merged,
     *  3. the item's OWN declarations, which win.
     *
     * ## Wave 25 — the cascade inversion this order used to have
     * Slot 2 is a MERGED list, so an ancestor's `list-style-type` used to
     * arrive there and beat the container's UA default. That is backwards:
     * css-cascade-4 §4.3 consults inheritance only when the cascade
     * produced NO value for the element, and the UA sheet's
     * `ul { list-style-type: disc }` (HTML §15.3.9) IS a declaration on
     * the container element — so an inherited value can never reach it.
     * The repair is NOT a fold reorder here (an author declaration on the
     * container must still beat the UA rule — the live
     * `marker-text-matches-armenian` `<ol>` declares `armenian` and must
     * keep it). It happens one step earlier, at the inheritance merge,
     * where the container's OWN list is still separable from what it
     * inherited: [ListStyleUaRule.apply] substitutes the UA value for an
     * ancestor-inherited one. By the time [parentProperties] reaches this
     * function it is therefore already cascade-correct, and the plain
     * parent-then-child fold below is right.
     *
     * @param parentProperties the container's INHERITANCE-MERGED
     *   declarations, after [ListStyleUaRule.apply]. `list-style-position`
     *   and `-image` have no UA declaration on `ul`/`ol`, so an ancestor's
     *   value for those legitimately still reaches the item through here.
     * @return null when [parentTag] is not a list container — the caller
     *   must then render the child with no marker at all.
     */
    fun resolveMarkerConfig(
        parentTag: String?,
        parentProperties: List<Pair<String, JsonElement?>>,
        childProperties: List<Pair<String, JsonElement?>>
    ): ListStyleConfig? {
        val uaDefault = uaMarkerDefault(parentTag) ?: return null
        // Step 1 — the UA default. `outside` is the initial value of
        // list-style-position (css-lists-3 §3.1), already ListStyleConfig's.
        val base = ListStyleConfig(listStyleType = uaDefault)
        // Steps 2+3 in one fold: parent entries first, child entries last,
        // and the fold is last-wins — so the item's own declaration beats
        // the container's, which beats the UA default. Correct now that
        // the container's own list-style-type can no longer be an
        // ancestor's (ListStyleUaRule, applied at the merge).
        return extractListStyleConfig(
            parentProperties.filter { isListStyleProperty(it.first) } +
                childProperties.filter { isListStyleProperty(it.first) },
            base
        )
    }

    /**
     * @return null when the keyword names no counter style this runtime
     *   models — the caller keeps whatever value it already had.
     *
     * KNOWN GAP (not a silent fallthrough): `@counter-style` at-rules are
     * not carried on the IR wire, so a custom name — the live
     * `marker-text-matches-disc` / `-circle` fixtures declare
     * `list-style-type: my-disc` / `my-circle` — cannot be resolved to its
     * symbols. Returning null keeps the container's UA default (`•` under
     * a `<ul>`), which is what those two @counter-style rules happen to
     * define, instead of css-counter-styles-3 §7.1's "treat an UNDEFINED
     * name as decimal" (they ARE defined — the wire just lost the rule).
     */
    private fun extractListStyleType(json: JsonElement?): ListStyleType? =
        ValueExtractors.extractKeyword(json)?.let { typeFromKeyword(it) }

    /**
     * The counter-style keyword table, split out of [extractListStyleType]
     * so [ListStyleShorthand] resolves a shorthand's type component from
     * the SAME table (a second copy would be free to drift). Accepts both
     * wire spellings — hyphenated (`upper-roman`, what the live css-lists
     * IR carries) and underscored — and any casing.
     */
    internal fun typeFromKeyword(rawKeyword: String): ListStyleType? {
        val keyword = rawKeyword.lowercase().replace("-", "_")

        return when (keyword) {
            "disc" -> ListStyleType.DISC
            "circle" -> ListStyleType.CIRCLE
            "square" -> ListStyleType.SQUARE
            "none" -> ListStyleType.NONE
            "decimal" -> ListStyleType.DECIMAL
            "decimal_leading_zero" -> ListStyleType.DECIMAL_LEADING_ZERO
            "lower_alpha", "lower_latin" -> ListStyleType.LOWER_ALPHA
            "upper_alpha", "upper_latin" -> ListStyleType.UPPER_ALPHA
            "lower_roman" -> ListStyleType.LOWER_ROMAN
            "upper_roman" -> ListStyleType.UPPER_ROMAN
            "lower_greek" -> ListStyleType.LOWER_GREEK
            "upper_greek" -> ListStyleType.UPPER_GREEK
            "armenian" -> ListStyleType.ARMENIAN
            "georgian" -> ListStyleType.GEORGIAN
            "hebrew" -> ListStyleType.HEBREW
            "cjk_decimal" -> ListStyleType.CJK_DECIMAL
            "hiragana" -> ListStyleType.HIRAGANA
            "katakana" -> ListStyleType.KATAKANA
            "hiragana_iroha" -> ListStyleType.HIRAGANA_IROHA
            "katakana_iroha" -> ListStyleType.KATAKANA_IROHA
            else -> null
        }
    }

    /**
     * @return null for an unrecognised keyword — same keep-the-base rule
     *   as [extractListStyleType]. Note the live wire emits the position
     *   UPPERCASED (`{"type":"ListStylePosition","data":"INSIDE"}` in the
     *   css-lists per-test IR) while the type stays lowercase-hyphenated
     *   (`"upper-roman"`), so the `.lowercase()` here is load-bearing.
     */
    private fun extractListStylePosition(json: JsonElement?): ListStylePosition? {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (keyword) {
            "inside" -> ListStylePosition.INSIDE
            "outside" -> ListStylePosition.OUTSIDE
            else -> null
        }
    }

    private fun extractListStyleImage(json: JsonElement?): String? {
        // URL for list marker image
        val keyword = ValueExtractors.extractKeyword(json) ?: return null
        if (keyword.startsWith("url(") && keyword.endsWith(")")) {
            return keyword.removeSurrounding("url(", ")").trim().removeSurrounding("\"").removeSurrounding("'")
        }
        return null
    }

    fun isListStyleProperty(type: String): Boolean {
        return type in setOf("ListStyleType", "ListStylePosition", "ListStyleImage", "ListStyle")
    }
}
