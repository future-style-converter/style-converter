package com.styleconverter.runtime.typography.wrapping

/**
 * WordBreakOpportunities.kt
 * typography/wrapping — wave 41 (lane T3), the Kotlin twin of
 * `StyleEngine/typography/wrapping/WordBreakOpportunities.swift`.
 *
 * The IN-WORD break opportunities [GreedyLineBreaker]'s space-split fold
 * was blind to, per css-text-3:
 *
 *  * U+00AD SOFT HYPHEN (§5.3) — a CONDITIONAL hyphenation opportunity:
 *    invisible unless the line breaks there, in which case the UA paints
 *    the hyphenate-character. `hyphens: none` never lets one reach this
 *    code ([SoftHyphenPolicy] deletes them at the call site), so any shy
 *    seen here IS an opportunity by construction (`manual`/`auto`).
 *  * U+002D HYPHEN-MINUS and U+2010 HYPHEN (UAX #14 class HY/BA) — plain
 *    break-AFTER opportunities `hyphens` does not govern (the assertion
 *    of WPT css-text/hyphens-none-012: "'hyphens: none' does not
 *    suppress line wrapping after … an actual hyphen-minus"). The glyph
 *    is already in the text, so nothing is painted at the break.
 *
 * WHY COMPOSE NEEDS THIS AT ALL, when Minikin owns ordinary breaking:
 * the fold only renders through [PreBreakPipeline] when rule B fires
 * (softWrap = false), and there the wave-38 space-only fold committed
 * shy/hyphen-carrying words WHOLE. Measured at wave40-final android-ref:
 * hyphens-manual-013 0.9458 (Minikin, hyphenation off, character-breaks
 * `Deoxy­ribonucleic` at the box edge with no hyphen glyph where
 * the ref breaks `Deoxy-` at the authored soft hyphen) and
 * hyphens-none-012 0.8834 (`lation` character-broken `latio`/`n`). With
 * the ops modeled, the fold spends them BEFORE the rule-B probe runs, so
 * a fired run carries the CSS break positions and an un-fired run is
 * returned by identity exactly as before (manual-011/012's committed
 * behaviour is decision-identical — every split line fits, the probe
 * stays false, and the pipeline declines).
 *
 * En/em dashes, ZWSP and ideographs stay UNmodeled on purpose: the fold
 * leaves such words whole and the `hasSoftWrapOpportunity` veto inside
 * [GreedyLineBreaker.hasUnbreakableOverflowingLine] keeps rule B from
 * firing on them — Minikin then owns those breaks exactly as before.
 *
 * Unlike the Swift twin there is NO dictionary seam here: Android ships
 * no public per-word hyphenation query (Minikin's `Hyphenator` is
 * internal to the platform), so `hyphens: auto` rides `TextStyle.hyphens
 * = Auto` instead (see [AutoHyphenation]) and this model carries the
 * EXPLICIT ops only. Pure Kotlin so the JVM suite pins it deviceless.
 */
object WordBreakOpportunities {

    /** The css-text-3 §5.3 UA hyphenate-character: U+2010 HYPHEN is what
     *  Chromium paints at a taken soft hyphen, so it is what the frozen
     *  refs carry (byte-parallel with the Swift twin's
     *  `AutoHyphenation.defaultHyphenCharacter`). */
    const val DEFAULT_HYPHEN_CHARACTER: String = "\u2010"

    /**
     * One in-word break opportunity, addressed in the CLEANED word.
     *
     * @property offset UTF-16 offset into [Word.text] where the break
     *   lands: head = `text.substring(0, offset)`, tail =
     *   `text.substring(offset)`. Always > 0 and < the word's length
     *   (a break needs ink on both sides).
     * @property paintsHyphen true — a HYPHENATION point (a removed soft
     *   hyphen): taking it paints the hyphenate-character at the end of
     *   the head (§5.3). false — a literal-hyphen break-after point: the
     *   glyph is already there.
     */
    data class Op(val offset: Int, val paintsHyphen: Boolean)

    /**
     * A space-split word ready for the greedy fold: the DISPLAY text
     * (soft hyphens removed — they have no advance unless taken, so this
     * is also the correct MEASUREMENT string) plus its explicit ops.
     */
    data class Word(val text: String, val ops: List<Op>)

    /**
     * Decompose one raw space-split word: strip U+00AD (recording each
     * position as a hyphenation op) and record a break-after op behind
     * every U+002D / U+2010 whose successor may start a line.
     */
    @JvmStatic
    fun analyze(raw: String): Word {
        val text = StringBuilder(raw.length)
        val ops = ArrayList<Op>()
        var pendingHyphen = false // saw -/‐, op confirmed by successor.
        for (ch in raw) {
            if (ch == '\u00AD') {
                // Soft hyphen: do NOT emit the char. Its position in the
                // cleaned word is a hyphenation opportunity — unless it
                // is word-initial (offset 0: no head to break off) or a
                // duplicate of the previous op's offset (an authored
                // run of soft hyphens breaks in one place only).
                if (text.isNotEmpty() && ops.lastOrNull()?.offset != text.length) {
                    ops.add(Op(text.length, paintsHyphen = true))
                }
                continue
            }
            // A pending literal-hyphen op is confirmed by its successor —
            // unless that successor is a digit (UAX #14 forbids HY × NU:
            // `1-2` ranges keep their halves together). A soft hyphen
            // recorded at the same offset is REPLACED: the break lands in
            // one place and the literal glyph is already there, so
            // nothing extra may be painted.
            if (pendingHyphen) {
                if (ch !in '0'..'9') {
                    if (ops.lastOrNull()?.offset == text.length) ops.removeAt(ops.size - 1)
                    ops.add(Op(text.length, paintsHyphen = false))
                }
                pendingHyphen = false
            }
            text.append(ch)
            // Break-after candidate; confirmed above once we know there
            // IS a successor (a word-final hyphen has no tail to move).
            if (ch == '-' || ch == '\u2010') pendingHyphen = true
        }
        return Word(text.toString(), ops)
    }

    /**
     * The GREEDIEST split of [word] whose head — placed after [prefix],
     * plus the painted hyphen when the op asks for one — fits [maxWidth]:
     * css-text-3 §5 breaks at the LAST opportunity that fits, which is
     * what Chromium's and Minikin's line breakers both do.
     *
     * [overflowFallback] — when no op fits AND the word opens its line
     * ([prefix] empty), take the FIRST op anyway: CSS still breaks at the
     * earliest opportunity to MINIMIZE the overflow (the Chromium ref for
     * hyphens-none-012 wraps `imple-menta-tion` as `imple-`/`menta-`/
     * `tion` even where `imple-` alone overflows the 6ch box).
     *
     * Null when no op is usable — the caller then falls back to the
     * whole-word behaviour (CSS 2.1 §9.5 overflow), unchanged.
     */
    @JvmStatic
    fun split(
        word: Word,
        prefix: String,
        maxWidth: Float,
        measure: (String) -> Float,
        hyphenChar: String = DEFAULT_HYPHEN_CHARACTER,
        overflowFallback: Boolean = false
    ): Pair<String, Word>? {
        // Walk DOWN from the last opportunity so the first fit found is
        // the greediest one (identical to the Swift twin's walk).
        for (i in word.ops.indices.reversed()) {
            val cut = pieces(word, word.ops[i], hyphenChar) ?: continue
            if (measure(prefix + cut.first) <= maxWidth) return cut
        }
        // Nothing fits: an opening word may still break at its FIRST op.
        if (overflowFallback && prefix.isEmpty()) {
            for (op in word.ops) {           // ascending — earliest usable.
                pieces(word, op, hyphenChar)?.let { return it }
            }
        }
        return null
    }

    /**
     * Materialize the (head, tail) pair for one op, or null when the
     * offset is unusable — out of range, tail empty, or splitting a
     * surrogate pair (nonsense to break inside; SKIPPED, not forced —
     * the same rule as the Swift twin's Character-boundary guard).
     * Surviving ops are REBASED into the tail: soft-hyphen positions
     * cannot be recomputed from the suffix (the marks were removed by
     * [analyze]), so rebase is the only correct tail constructor.
     */
    private fun pieces(word: Word, op: Op, hyphenChar: String): Pair<String, Word>? {
        val t = word.text
        if (op.offset <= 0 || op.offset >= t.length) return null
        // Never split between a high and low surrogate.
        if (t[op.offset - 1].isHighSurrogate() && t[op.offset].isLowSurrogate()) return null
        // §5.3: a taken hyphenation point paints the hyphenate-character;
        // a literal-hyphen break-after point already ends in its glyph.
        val head = t.substring(0, op.offset) + if (op.paintsHyphen) hyphenChar else ""
        val tail = Word(
            t.substring(op.offset),
            word.ops.filter { it.offset > op.offset }
                .map { Op(it.offset - op.offset, it.paintsHyphen) }
        )
        return head to tail
    }
}
