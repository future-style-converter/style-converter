package com.styleconverter.runtime.columns

// css-gap-decorations-1 — typed CONFIG for the flex gap-decoration family
// (wave 24, lane GAPS-A). One config per flex container.
//
// Wire contract consumed here (frozen for this wave; the converter lane
// emits exactly these IR property type names and value shapes):
//   ColumnRuleColor / ColumnRuleStyle / ColumnRuleWidth   — existing shapes
//   RowRuleColor    / RowRuleStyle    / RowRuleWidth      — byte-identical twins
//   ColumnRuleBreak / RowRuleBreak    — "NORMAL" | "SPANNING_ITEM" | "INTERSECTION"
//   ColumnRuleInset / RowRuleInset    — length px object, negatives allowed
//   RuleOverlap                       — "ROW_OVER_COLUMN" | "COLUMN_OVER_ROW"
//
// [ColumnRuleStyle] is REUSED from MultiColumnConfig.kt rather than
// re-declared: the CSS <line-style> grammar is one grammar, and sharing
// the enum keeps `column-rule-style` decoding identical between the
// multicol path (untouched by this lane) and this one.

import androidx.compose.ui.graphics.Color

/**
 * `<gap-rule-break>` — how a rule reacts to things crossing its gap
 * (css-gap-decorations-1 §5.1).
 *
 * - [NORMAL]        CSS initial value: the rule is continuous along its
 *                   gap. Matches the converter lane's
 *                   RowRuleBreakProperty.RuleBreak documentation.
 * - [SPANNING_ITEM] breaks only at items that SPAN the gap. Flex layout
 *                   has no spanning items (only grid does), so in this
 *                   runtime it behaves exactly like [NORMAL] —
 *                   deliberate, and asserted by a unit test so it can
 *                   never rot into a silent fallthrough.
 * - [INTERSECTION]  the rule additionally stops at every crossing gap.
 */
enum class GapRuleBreak { NORMAL, SPANNING_ITEM, INTERSECTION }

/**
 * `rule-overlap` — paint order where a column rule and a row rule cross
 * (css-gap-decorations-1 §5). Initial value is [ROW_OVER_COLUMN]; WPT
 * flex-gap-decorations-012 is the only pinned test that flips it.
 */
enum class GapRuleOverlap { ROW_OVER_COLUMN, COLUMN_OVER_ROW }

/**
 * One rule family's resolved stroke + geometry knobs.
 *
 * @param style   `*-rule-style`; NONE/HIDDEN suppress the family entirely.
 * @param widthPx `*-rule-width` in IR px (== dp). Null means the property
 *                was absent — [effectiveWidthPx] then supplies the CSS
 *                initial `medium`.
 * @param color   `*-rule-color`, already normalized to sRGB by the reader.
 *                Null means absent → `currentColor`, which this runtime
 *                cannot resolve here (the flex container's colour lives on
 *                a different extractor); the painter substitutes black and
 *                the extractor logs it, rather than dropping the rule.
 * @param breakMode `*-rule-break`.
 * @param insetPx `*-rule-inset` in IR px; positive shortens both ends,
 *                negative lengthens them. Percentages are OUT of the wire
 *                contract for this wave and arrive unresolved (null px);
 *                the extractor logs and falls back to 0.
 */
data class GapRuleSpec(
    val style: ColumnRuleStyle = ColumnRuleStyle.NONE,
    val widthPx: Float? = null,
    val color: Color? = null,
    val breakMode: GapRuleBreak = GapRuleBreak.NORMAL,
    val insetPx: Float = 0f
) {
    /**
     * CSS initial `border-width` keyword ladder: `medium` == 3px, the same
     * value MultiColumnExtractor.extractRuleWidth maps `medium` to. Used
     * when a style is declared without a width.
     */
    val effectiveWidthPx: Float get() = widthPx ?: MEDIUM_WIDTH_PX

    /**
     * True when this family contributes paint at all: css-backgrounds-3
     * §5.3 zeroes the used width for `none`/`hidden`, and a non-positive
     * width paints nothing either.
     */
    val paints: Boolean
        get() = style != ColumnRuleStyle.NONE &&
            style != ColumnRuleStyle.HIDDEN &&
            effectiveWidthPx > 0f

    companion object {
        /** CSS `medium` line width as Chromium resolves it. */
        const val MEDIUM_WIDTH_PX: Float = 3f
    }
}

/**
 * Everything the painter needs for one flex container.
 *
 * [active] is the GATE: the whole family is inert unless at least one of
 * the two rule specs actually paints. Combined with the draw hook's
 * flex-container check this is what keeps the frozen 327-pair dark stage
 * byte-stable — see GapDecorationHook for the full argument.
 */
data class GapDecorationConfig(
    val column: GapRuleSpec = GapRuleSpec(),
    val row: GapRuleSpec = GapRuleSpec(),
    val overlap: GapRuleOverlap = GapRuleOverlap.ROW_OVER_COLUMN
) {
    /** True when this container paints any gap decoration at all. */
    val active: Boolean get() = column.paints || row.paints

    /** The spec for a given family — used by the painter's per-segment dispatch. */
    fun specFor(axis: GapAxis): GapRuleSpec = when (axis) {
        GapAxis.COLUMN -> column
        GapAxis.ROW -> row
    }

    companion object {
        /** All-inert config; the extractor returns this when nothing applies. */
        val Inert: GapDecorationConfig = GapDecorationConfig()
    }
}
