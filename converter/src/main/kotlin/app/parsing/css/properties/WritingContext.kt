package app.parsing.css.properties

/**
 * The writing context a component's declarations are resolved under: its own
 * computed `writing-mode` (css-writing-modes-4 §3.2) and `direction` (§2.1).
 *
 * Both properties are "Inherited: yes", so a component's context is its own
 * declaration when it has one and its parent's otherwise — [derive] walks
 * exactly that, and CssParsing threads the result down the children map so a
 * `writing-mode: vertical-rl` on an ancestor reaches a margin-only child.
 *
 * A value the converter cannot resolve without the document tree or the UA
 * sheet (`revert`, `revert-layer`, `var()`, garbage) marks the axis
 * [UNDECIDABLE]; every descendant inherits that marker, and
 * [LogicalAliasResolution] abstains for it rather than guess.
 */
data class WritingContext(val writingMode: String, val direction: String) {
    /** False when either axis carries the [UNDECIDABLE] marker. */
    val isDecidable: Boolean get() = writingMode != UNDECIDABLE && direction != UNDECIDABLE

    /** The only mode this converter collapses aliases under (see the resolver). */
    val isHorizontalTb: Boolean get() = writingMode == "horizontal-tb"

    override fun toString(): String = "$writingMode/$direction"

    companion object {
        /** Sentinel for an axis whose value needs data the converter lacks. */
        const val UNDECIDABLE = "undecidable"

        /** css-writing-modes-4 §3.2 / §2.1 initial values — the harness root. */
        val ROOT = WritingContext("horizontal-tb", "ltr")

        /**
         * The child context: the parent's, overridden per axis by an own
         * `writing-mode` / `direction` declaration (names normalised exactly
         * as PropertiesParser does, so camelCase input resolves too).
         * Returns the SAME instance when nothing changes — the common case.
         */
        fun derive(parent: WritingContext, ownDeclarations: Map<String, String>?): WritingContext {
            if (ownDeclarations.isNullOrEmpty()) return parent
            var wm = parent.writingMode
            var dir = parent.direction
            for ((rawName, rawValue) in ownDeclarations) {
                when (PropertiesParser.normalizePropertyName(rawName)) {
                    "writing-mode" -> wm = resolveWritingMode(rawValue, parent.writingMode)
                    "direction" -> dir = resolveDirection(rawValue, parent.direction)
                }
            }
            return if (wm == parent.writingMode && dir == parent.direction) parent else WritingContext(wm, dir)
        }

        /**
         * css-writing-modes-4 §3.2 values; §3.2.1.1's table folds the obsolete
         * SVG1.1 spellings. WritingModePropertyParser accepts only the five
         * §3.2 spellings, so an SVG1.1 value never reaches the IR as a
         * WritingModeProperty — folding it here only ever decides THIS
         * component's alias pairing, which is the reading every runtime would
         * layout under anyway (they see no writing-mode property at all).
         */
        private fun resolveWritingMode(raw: String, inherited: String): String = when (val v = raw.trim().lowercase()) {
            "horizontal-tb", "lr", "lr-tb", "rl", "rl-tb" -> "horizontal-tb" // §3.2.1.1 table: lr/lr-tb/rl/rl-tb → horizontal-tb
            "vertical-rl", "tb", "tb-rl" -> "vertical-rl"                   // §3.2.1.1 table: tb/tb-rl → vertical-rl
            "vertical-lr", "sideways-rl", "sideways-lr" -> v                // §3.2 modern values, as written
            "inherit", "unset" -> inherited                                 // css-cascade-4 §7.3.2 / §7.3.3 on an inherited property
            "initial" -> "horizontal-tb"                                    // css-cascade-4 §7.3.1 + §3.2 "Initial: horizontal-tb"
            else -> UNDECIDABLE                                             // revert(-layer) needs the UA origin; var()/invalid unknown
        }

        /** css-writing-modes-4 §2.1 values. */
        private fun resolveDirection(raw: String, inherited: String): String = when (val v = raw.trim().lowercase()) {
            "ltr", "rtl" -> v                                               // §2.1 the two values
            "inherit", "unset" -> inherited                                 // css-cascade-4 §7.3.2 / §7.3.3 (direction is inherited)
            "initial" -> "ltr"                                              // css-cascade-4 §7.3.1 + §2.1 "Initial: ltr"
            else -> UNDECIDABLE                                             // revert(-layer) depends on the `dir` attribute the converter never sees
        }
    }
}
