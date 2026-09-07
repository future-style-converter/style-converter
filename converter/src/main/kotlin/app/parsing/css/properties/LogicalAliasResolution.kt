package app.parsing.css.properties

/**
 * Collapses logical/physical alias pairs by cascade order — the converter-side
 * fix for retrospective finding A11#4.
 *
 * css-logical-1 §4 ("Flow-Relative Box Model Properties"): within a logical
 * property group "corresponding flow-relative and physical properties are
 * paired using the element's own computed writing mode. Although the specified
 * value of each property remains distinct, paired properties share a computed
 * value. This shared value is determined by cascading the declarations of both
 * properties together as one; in other words, the computed value of both
 * properties in the pair is derived from the specified value of the property
 * declared with higher priority in the CSS cascade." Inside one declaration
 * block that is the LATER declaration (the spec's own example:
 * `margin-inline-start: 1px; margin-left: 2px` computes margin-left 2px under
 * horizontal-tb/ltr). The converter used to emit BOTH properties and each
 * runtime picked its own winner — pairs-06 PW_Sizing_Spacing_03
 * (`block-size: auto … height: 150px`) rendered a 182px canvas on the natives
 * and 112px on web from the same IR. Dropping the loser here gives all three
 * runtimes one answer, and the answer the spec gives.
 *
 * SCOPE — deliberately narrow:
 *  1. Base-declaration bucket only (the caller passes the component's
 *     [WritingContext]; selector/media buckets and keyframe stops pass none).
 *     A bucket's `margin-left` versus the base's `margin-inline-start` is a
 *     cross-bucket cascade the runtimes own; this file never sees it.
 *  2. `horizontal-tb` only, ltr and rtl (css-writing-modes-4 §6.4 table). The
 *     vertical and sideways modes are decidable from the same table, but the
 *     natives resolve logical sides under those modes themselves today
 *     (Compose/Swift LogicalSides); folding them here is a separate change.
 *     Undecidable contexts (see [WritingContext]) abstain too. Every
 *     abstention with a real pair present is reported in [Result.kept] and
 *     logged by PropertiesParser — never silent.
 *  3. Name→value maps only; the winner's VALUE and wire shape are untouched,
 *     so no IR byte shape moves (schema/spec/05-versioning.md): a component
 *     declaring no pair is returned as the very same map instance.
 */
object LogicalAliasResolution {

    /** One collapsed pair: the earlier declaration lost to the later one. */
    data class Drop(val loser: String, val loserValue: String, val winner: String, val winnerValue: String)

    /** A pair the resolver saw but deliberately left intact, with the reason. */
    data class Kept(val logical: String, val physical: String, val reason: String)

    /** The surviving map (same instance as the input when nothing dropped) plus the audit trail. */
    class Result(val properties: Map<String, String>, val dropped: List<Drop>, val kept: List<Kept>)

    /**
     * The physical longhand that shares `logical`'s computed value under `ctx`,
     * or null when `logical` is not a flow-relative alias or `ctx` is outside
     * the collapsible scope (non-horizontal or undecidable).
     */
    fun physicalFor(logical: String, ctx: WritingContext): String? = tableFor(ctx)?.get(logical)

    /** Every flow-relative longhand the table knows (both directions agree on the key set). */
    fun logicalNames(): Set<String> = LTR_TABLE.keys

    /** Every physical longhand any table entry maps to (union over ltr and rtl). */
    fun physicalNames(): Set<String> = LTR_TABLE.values.toSet() + RTL_TABLE.values.toSet()

    /**
     * Drop the earlier-declared member of every present alias pair.
     *
     * @param expanded post-shorthand-expansion longhand map (name → value), in
     *   emission order — the order the IR properties will be written in.
     * @param declarationIndex for each longhand, the index of the SOURCE
     *   declaration that last wrote it (a shorthand's longhands share the
     *   shorthand's index). This, not map position, is the cascade proxy:
     *   LinkedHashMap keeps a re-put key at its FIRST position.
     * @param ctx the component's own computed writing context.
     */
    fun collapse(expanded: Map<String, String>, declarationIndex: Map<String, Int>, ctx: WritingContext): Result {
        val present = expanded.keys.filter { it in LTR_TABLE }
        if (present.isEmpty()) return Result(expanded, emptyList(), emptyList()) // fast path: no logical longhand at all
        val table = tableFor(ctx)
        val dropped = ArrayList<Drop>()
        val kept = ArrayList<Kept>()
        if (table == null) {
            // Out-of-scope context (vertical/sideways, or undecidable — see
            // [WritingContext]): abstain, and report only the REAL pairs the
            // horizontal-tb collapse would have cascaded together. A logical
            // longhand's twin is its LTR_TABLE / RTL_TABLE entry — the two
            // horizontal candidates (css-writing-modes-4 §6.4: they differ
            // only on the inline axis) — so `block-size` pairs with `height`
            // and with nothing else. Retro S5 caught the previous breadcrumb
            // naming EVERY physical longhand in the block ("Kept both
            // 'block-size' and 'border-top-width,border-top-style,…'" on the
            // css-break sections) and firing for a lone logical longhand whose
            // twin was absent (css-contain's `contain-intrinsic-inline-size`
            // beside height/width). A logical longhand without its twin in the
            // block is not a pair: nothing could have been dropped, so nothing
            // is reported — the log stays a truthful record of abstentions.
            for (logical in present) {
                for (twin in listOfNotNull(LTR_TABLE[logical], RTL_TABLE[logical]).distinct()) { // ltr and rtl agree except on the inline axis
                    if (twin !in expanded) continue                        // twin absent → no pair → silent by design
                    kept += Kept(logical, twin, "writing context $ctx is outside the horizontal-tb collapse scope — both declarations forwarded")
                }
            }
            return Result(expanded, emptyList(), kept)
        }
        val losers = HashSet<String>()
        for (logical in present) {
            val physical = table[logical] ?: continue
            if (physical !in expanded) continue                        // no pair → nothing to cascade together
            val li = declarationIndex[logical]
            val pi = declarationIndex[physical]
            if (li == null || pi == null || li == pi) {
                // Without a strict order there is no "declared later"; a tie
                // cannot arise from expansion (no shorthand emits both
                // spellings) so this is a caller bug worth surfacing, not hiding.
                kept += Kept(logical, physical, "no cascade order for the pair (indices $li/$pi) — both declarations forwarded")
                continue
            }
            // css-logical-1 §4: the later declaration supplies the shared computed value.
            val (loser, winner) = if (li < pi) logical to physical else physical to logical
            losers += loser
            dropped += Drop(loser, expanded.getValue(loser), winner, expanded.getValue(winner))
        }
        if (dropped.isEmpty()) return Result(expanded, emptyList(), kept)  // identity → byte-stable emission
        return Result(expanded.filterKeys { it !in losers }, dropped, kept) // filterKeys keeps LinkedHashMap order
    }

    // ── mapping table ─────────────────────────────────────────────────────

    /** The table for a collapsible context, or null (vertical/sideways/undecidable). */
    private fun tableFor(ctx: WritingContext): Map<String, String>? = when {
        !ctx.isDecidable || !ctx.isHorizontalTb -> null
        ctx.direction == "rtl" -> RTL_TABLE
        else -> LTR_TABLE
    }

    private val LOGICAL_SIDES = listOf("block-start", "block-end", "inline-start", "inline-end")
    private val LOGICAL_CORNERS = listOf("start-start", "start-end", "end-start", "end-end")

    /** css-writing-modes-4 §6.4 table, horizontal-tb column: block axis is vertical, inline axis follows direction. */
    private fun sideFor(logicalSide: String, direction: String): String = when (logicalSide) {
        "block-start" -> "top"
        "block-end" -> "bottom"
        "inline-start" -> if (direction == "rtl") "right" else "left"
        "inline-end" -> if (direction == "rtl") "left" else "right"
        else -> error("not a logical side: $logicalSide")
    }

    /** css-logical-1 §4.6: `border-<block>-<inline>-radius` names the corner where those two sides meet. */
    private fun cornerFor(logicalCorner: String, direction: String): String {
        val (block, inline) = logicalCorner.split("-")                  // "start-start" → block-start + inline-start
        return sideFor("block-$block", direction) + "-" + sideFor("inline-$inline", direction)
    }

    private fun buildTable(direction: String): Map<String, String> {
        val t = LinkedHashMap<String, String>()
        // css-logical-1 §4.1 — logical height and width, plus their min/max.
        t["block-size"] = "height"; t["inline-size"] = "width"
        t["min-block-size"] = "min-height"; t["max-block-size"] = "max-height"
        t["min-inline-size"] = "min-width"; t["max-inline-size"] = "max-width"
        for (side in LOGICAL_SIDES) {
            val p = sideFor(side, direction)
            t["margin-$side"] = "margin-$p"                                // css-logical-1 §4.2 flow-relative margins
            t["inset-$side"] = p                                           // css-logical-1 §4.3 flow-relative offsets → top/right/bottom/left
            t["padding-$side"] = "padding-$p"                              // css-logical-1 §4.4 flow-relative padding
            t["border-$side-width"] = "border-$p-width"                    // css-logical-1 §4.5.1 border widths
            t["border-$side-style"] = "border-$p-style"                    // css-logical-1 §4.5.2 border styles
            t["border-$side-color"] = "border-$p-color"                    // css-logical-1 §4.5.3 border colors
            t["scroll-margin-$side"] = "scroll-margin-$p"                  // css-scroll-snap-1 Appendix A, flow-relative longhands for scroll-margin
            t["scroll-padding-$side"] = "scroll-padding-$p"                // css-scroll-snap-1 Appendix A, flow-relative longhands for scroll-padding
        }
        for (corner in LOGICAL_CORNERS) {
            t["border-$corner-radius"] = "border-${cornerFor(corner, direction)}-radius" // css-logical-1 §4.6 flow-relative corner rounding
        }
        // Axis-only families: under horizontal-tb the block axis is y and the
        // inline axis is x regardless of direction (css-writing-modes-4 §6.4).
        t["overflow-block"] = "overflow-y"; t["overflow-inline"] = "overflow-x"                                  // css-overflow-3 §3.1
        t["overscroll-behavior-block"] = "overscroll-behavior-y"; t["overscroll-behavior-inline"] = "overscroll-behavior-x" // css-overscroll-1 §4.3 / §4.4
        t["contain-intrinsic-block-size"] = "contain-intrinsic-height"; t["contain-intrinsic-inline-size"] = "contain-intrinsic-width" // css-sizing-4 §5.2
        t["background-position-block"] = "background-position-y"; t["background-position-inline"] = "background-position-x" // css-backgrounds-4 §2.6
        // scroll-start / scroll-start-target: defined with these flow-relative
        // longhands in the 2023 css-scroll-snap-2 ED (since replaced there by
        // scroll-initial-target, §3.1); the converter still models them, and
        // the css-logical-1 §4 pairing rule is generic to any logical group.
        t["scroll-start-block"] = "scroll-start-y"; t["scroll-start-inline"] = "scroll-start-x"
        t["scroll-start-target-block"] = "scroll-start-target-y"; t["scroll-start-target-inline"] = "scroll-start-target-x"
        return t
    }

    private val LTR_TABLE: Map<String, String> by lazy { buildTable("ltr") }
    private val RTL_TABLE: Map<String, String> by lazy { buildTable("rtl") }
}
