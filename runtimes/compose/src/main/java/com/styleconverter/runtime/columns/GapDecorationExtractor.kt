package com.styleconverter.runtime.columns

// css-gap-decorations-1 — IR → [GapDecorationConfig] (wave 24, lane GAPS-A).
//
// Consumes the wave-24 WIRE CONTRACT verbatim. The row-* twins are
// byte-identical in shape to the column-* originals the converter has
// always emitted, which was verified against a live conversion of
// fixtures/wpt/css-gaps/flex__flex-gap-decorations-001.json:
//   ColumnRuleColor → {"srgb":{"r":0,"g":0.501…,"b":0},"original":"green"}
//   ColumnRuleStyle → "SOLID"
//   ColumnRuleWidth → {"type":"length","px":10}
// so RowRuleColor/Style/Width decode through the SAME ValueExtractors.
//
// This extractor deliberately does NOT decide whether a component paints
// gap decorations — that gate is the draw hook's (flex containers only),
// which is what keeps the multicol `column-rule` path untouched.

import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Decodes the eight gap-decoration IR properties (plus the three shared
 * `column-rule-*` longhands) into a [GapDecorationConfig].
 */
object GapDecorationExtractor {

    // Claim the wave-24 property names for the `columns` category. The
    // three column-rule-* longhands are already claimed by
    // ColumnsRegistration; PropertyRegistry.migrated is first-write-wins,
    // so re-listing them here would be a harmless no-op — they are omitted
    // to keep ownership unambiguous.
    init {
        PropertyRegistry.migrated(
            "RowRuleWidth", "RowRuleStyle", "RowRuleColor",
            "ColumnRuleBreak", "RowRuleBreak",
            "ColumnRuleInset", "RowRuleInset",
            "RuleOverlap",
            owner = "columns"
        )
    }

    /** The IR names this extractor reads that no other extractor owns. */
    val GAP_DECORATION_PROPERTIES: Set<String> = setOf(
        "RowRuleWidth", "RowRuleStyle", "RowRuleColor",
        "ColumnRuleBreak", "RowRuleBreak",
        "ColumnRuleInset", "RowRuleInset",
        "RuleOverlap"
    )

    /**
     * Build the config for one component.
     *
     * @param properties the component's (type, data) pairs — the same list
     *        shape every other extractor in this runtime consumes.
     */
    fun extract(properties: List<Pair<String, JsonElement?>>): GapDecorationConfig {
        var column = GapRuleSpec()
        var row = GapRuleSpec()
        var overlap = GapRuleOverlap.ROW_OVER_COLUMN
        for ((type, data) in properties) {
            when (type) {
                // ── column family ────────────────────────────────────────
                "ColumnRuleStyle" -> column = column.copy(style = style(data))
                "ColumnRuleWidth" -> column = column.copy(widthPx = width(data))
                "ColumnRuleColor" -> column = column.copy(color = ValueExtractors.extractColor(data))
                "ColumnRuleBreak" -> column = column.copy(breakMode = breakMode(data, type))
                "ColumnRuleInset" -> column = column.copy(insetPx = inset(data, type))
                // ── row family (identical shapes) ────────────────────────
                "RowRuleStyle" -> row = row.copy(style = style(data))
                "RowRuleWidth" -> row = row.copy(widthPx = width(data))
                "RowRuleColor" -> row = row.copy(color = ValueExtractors.extractColor(data))
                "RowRuleBreak" -> row = row.copy(breakMode = breakMode(data, type))
                "RowRuleInset" -> row = row.copy(insetPx = inset(data, type))
                // ── shared ───────────────────────────────────────────────
                "RuleOverlap" -> overlap = overlapMode(data)
            }
        }
        return GapDecorationConfig(column = column, row = row, overlap = overlap)
    }

    /**
     * `<line-style>` — same grammar and same enum as `column-rule-style`,
     * so the decode is the twin of MultiColumnExtractor.extractRuleStyle.
     * An unrecognised keyword degrades to NONE (paints nothing) AND is
     * reported, so it shows up in the coverage report instead of vanishing.
     */
    private fun style(data: JsonElement?): ColumnRuleStyle {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase() ?: return ColumnRuleStyle.NONE
        return runCatching { ColumnRuleStyle.valueOf(keyword) }.getOrElse {
            PropertyTracker.markUnhandled("GapRuleStyle:$keyword"); ColumnRuleStyle.NONE
        }
    }

    /**
     * `<line-width>` — the CSS keyword ladder plus a length. Mirrors
     * MultiColumnExtractor.extractRuleWidth so the two rule families agree
     * on thin/medium/thick (1/3/5 px, Chromium's used values).
     */
    private fun width(data: JsonElement?): Float? {
        return when (ValueExtractors.extractKeyword(data)?.lowercase()) {
            "thin" -> 1f
            "medium" -> GapRuleSpec.MEDIUM_WIDTH_PX
            "thick" -> 5f
            else -> ValueExtractors.extractDp(data)?.value
        }
    }

    /**
     * `<gap-rule-break>`. The wire carries UPPERCASE tokens
     * (NORMAL / SPANNING_ITEM / INTERSECTION); the hyphenated CSS spelling
     * is normalised too so a fixture fed straight from CSS still decodes.
     */
    private fun breakMode(data: JsonElement?, type: String): GapRuleBreak {
        val keyword = ValueExtractors.extractKeyword(data)
            ?.uppercase()?.replace('-', '_')
            ?: return GapRuleBreak.NORMAL
        return runCatching { GapRuleBreak.valueOf(keyword) }.getOrElse {
            // Not a silent fallthrough: report, then use the CSS initial.
            PropertyTracker.markUnhandled("$type:$keyword"); GapRuleBreak.NORMAL
        }
    }

    /** `rule-overlap`; same normalisation and same honest-failure rule. */
    private fun overlapMode(data: JsonElement?): GapRuleOverlap {
        val keyword = ValueExtractors.extractKeyword(data)
            ?.uppercase()?.replace('-', '_')
            ?: return GapRuleOverlap.ROW_OVER_COLUMN
        return runCatching { GapRuleOverlap.valueOf(keyword) }.getOrElse {
            PropertyTracker.markUnhandled("RuleOverlap:$keyword"); GapRuleOverlap.ROW_OVER_COLUMN
        }
    }

    /**
     * `<gap-rule-inset>` as a LENGTH only, negatives allowed — that is the
     * whole of the wave-24 wire contract for this property.
     *
     * Percentage and `overlap-join` insets exist in the spec (WPT
     * 014/053-055/064) but are outside the contract: the reader leaves
     * them unresolved, so extractDp returns null. Those cases are reported
     * and treated as 0 rather than guessed at — the percentage basis
     * differs per end (cap vs junction, css-gap-decorations-1 §4.3) and
     * this lane has no fresh ref pinning it.
     */
    private fun inset(data: JsonElement?, type: String): Float {
        val dp = ValueExtractors.extractDp(data)
        if (dp != null) return dp.value
        PropertyTracker.markUnhandled("$type:non-length")
        return 0f
    }
}
