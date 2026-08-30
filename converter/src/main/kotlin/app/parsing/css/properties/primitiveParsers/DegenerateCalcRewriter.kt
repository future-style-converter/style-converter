package app.parsing.css.properties.primitiveParsers

/**
 * Rewrites DEGENERATE-INFINITE `calc()` lengths to their spec clamp value —
 * wave-48 lane W5.
 *
 * WHY. css-values-4 §10.9 ("Infinities and NaN") makes `calc(1px / 0)` and
 * `calc(Infinity * 1px)` VALID: "Division by zero … produces +∞ or −∞", and
 * an infinite value is "clamped to the allowed range" at used-value time —
 * for a <length> that range's ceiling is the implementation's maximum.
 * Chromium clamps to its LayoutUnit maximum and serializes the used value
 * as 33554400px, which is the constant emitted here; any length that far
 * beyond a 390×600 capture canvas paints pixel-identically to a true ∞.
 *
 * MEASURED CASE (the reason for the file): WPT css-images
 * gradient-infinity-001/002 use these exact spellings as gradient STOP
 * POSITIONS (`linear-gradient(to right in srgb, lime 100px, red
 * calc(1px / 0))`). ExpressionDetector routes any calc() to the Raw
 * fallback, so the web runtime re-emitted the author bytes and passed
 * (1.0000) while both natives painted nothing at all — 0.9171 / 0.9147
 * against the ref on wave48-cal, coverage-vetoed. Folding the infinity at
 * parse time lets the whole gradient take the TYPED path all three
 * runtimes render.
 *
 * DELIBERATE SCOPE — like NumericCalcEvaluator, this refuses far more than
 * it accepts, and a refusal always means "caller keeps its author bytes":
 *   * only WHOLE `calc(...)` spans whose body is one of the two degenerate
 *     productions the spec names and the corpus carries:
 *       <number><unit> / 0            (division by literal zero)
 *       [-]Infinity * <number><unit>  (and the commuted <len> * Infinity)
 *   * the unit must be an ABSOLUTE length unit (px/pt/pc/cm/mm/in/q) — a
 *     relative unit's sign is knowable but its magnitude story belongs to
 *     the runtimes, and no corpus value needs it;
 *   * a value containing any OTHER calc() (finite math, var(), nesting)
 *     rewrites NOTHING — partial rewriting would hand the Raw fallback
 *     bytes the author never wrote;
 *   * NaN is refused (its censoring rule is a different §10.9 clause with
 *     zero corpus coverage — no speculative semantics).
 */
object DegenerateCalcRewriter {

    /** Chromium's used-value clamp for an infinite <length> (LayoutUnit max,
     *  serialized by the capture browser itself as `33554400px`). */
    private const val CLAMP_PX = "33554400px"

    /** Absolute length units (css-values-4 §6.2) whose infinity sign is
     *  decidable at parse time. */
    private const val ABS_UNIT = """(?:px|pt|pc|cm|mm|in|q)"""

    /** `<number><abs-unit> / 0` — division by LITERAL zero (§10.9). The
     *  divisor must be exactly zero (`0`, `0.0`, …); a nonzero divisor is
     *  finite math this rewriter refuses. */
    private val divByZero =
        """^\s*(-?(?:\d+\.?\d*|\.\d+))$ABS_UNIT\s*/\s*(?:0+(?:\.0*)?)\s*$""".toRegex(RegexOption.IGNORE_CASE)

    /** `[-]Infinity * <number><abs-unit>` (either operand order) — the
     *  `infinity` keyword is ASCII case-insensitive like all CSS keywords. */
    private val infTimesLen =
        """^\s*(-?)infinity\s*\*\s*(-?(?:\d+\.?\d*|\.\d+))$ABS_UNIT\s*$""".toRegex(RegexOption.IGNORE_CASE)
    private val lenTimesInf =
        """^\s*(-?(?:\d+\.?\d*|\.\d+))$ABS_UNIT\s*\*\s*(-?)infinity\s*$""".toRegex(RegexOption.IGNORE_CASE)

    /** One degenerate body → its clamped replacement, or null (not ours). */
    private fun clampOf(body: String): String? {
        divByZero.find(body)?.let { m ->
            // Sign of ±∞ follows the numerator's sign (§10.9); a zero
            // numerator would be 0/0 = NaN, which is refused.
            val n = m.groupValues[1].toDoubleOrNull() ?: return null
            if (n == 0.0) return null
            return if (n < 0) "-$CLAMP_PX" else CLAMP_PX
        }
        infTimesLen.find(body)?.let { m ->
            // Sign is the XOR of the keyword's sign and the factor's sign.
            val neg = (m.groupValues[1] == "-") != ((m.groupValues[2].toDoubleOrNull() ?: return null) < 0)
            return if (neg) "-$CLAMP_PX" else CLAMP_PX
        }
        lenTimesInf.find(body)?.let { m ->
            val neg = (m.groupValues[2] == "-") != ((m.groupValues[1].toDoubleOrNull() ?: return null) < 0)
            return if (neg) "-$CLAMP_PX" else CLAMP_PX
        }
        return null
    }

    /**
     * Rewrite every degenerate-infinite calc() in [value] to its clamp, or
     * return null when the value should be left ALONE — either it has no
     * calc() at all, or it has one this rewriter does not model (all-or-
     * nothing, so the Raw fallback always carries pure author bytes).
     */
    fun rewrite(value: String): String? {
        val out = StringBuilder()
        var i = 0
        var rewrote = false
        while (i < value.length) {
            // Find the next calc( head, case-insensitively, outside of any
            // rewriting we have already done.
            val head = value.indexOf("calc(", i, ignoreCase = true)
            if (head < 0) { out.append(value, i, value.length); break }
            out.append(value, i, head)
            // Walk the balanced parens to find the matching close.
            var depth = 0
            var j = head + 4 // index of '('
            var close = -1
            while (j < value.length) {
                when (value[j]) {
                    '(' -> depth++
                    ')' -> { depth--; if (depth == 0) { close = j; break } }
                }
                j++
            }
            if (close < 0) return null // unbalanced — not a value we touch
            val body = value.substring(head + 5, close)
            // Nested calc() inside the body ⇒ outside this rewriter's grammar,
            // and any non-degenerate calc ⇒ leave the WHOLE value alone
            // (all-or-nothing, per the file banner).
            if (body.contains("calc(", ignoreCase = true)) return null
            val clamp = clampOf(body) ?: return null
            out.append(clamp)
            rewrote = true
            i = close + 1
        }
        return if (rewrote) out.toString() else null
    }
}
