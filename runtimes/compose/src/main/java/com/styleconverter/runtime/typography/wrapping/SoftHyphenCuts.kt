package com.styleconverter.runtime.typography.wrapping

/**
 * SoftHyphenCuts.kt
 * typography/wrapping — wave 54 (lane L3, unit U2-android).
 *
 * "Did the fold TAKE a soft-hyphen break?" — the wave-52 (lane L9, F5)
 * trigger of [PreBreakPipeline]: css-text-3 §5.3 `hyphens: manual` makes
 * U+00AD a break opportunity, and Minikin under Compose's `Hyphens.None`
 * ignores it and emergency-breaks the word instead (hyphens-manual-inline-012
 * android `Deoxyri`/`bonucle`), so a run whose fold took one must fire.
 *
 * WHY A WALK, NOT A COUNT. Wave 52 answered by COUNTING hyphen characters:
 * a taken U+00AD materialises one hyphenChar at a line end, so the lines carry
 * more of them than the source. That is blind to `hyphenate-character: ""`
 * (css-text-4 §6.3, WPT css-text/hyphens/hyphenate-character-001): the empty
 * string adds nothing to count, the trigger never fired, and Minikin painted
 * wave53-open's `impl/emen/tati/on`. [took] instead aligns the display lines
 * with the source WORDS and asks whether any line ends at a hyphenation op
 * (`paintsHyphen`, [WordBreakOpportunities.Op]) — the same decomposition the
 * fold itself used, so it holds for every string, `""` included.
 *
 * The count survives as [legacyCount]: the fallback when an alignment is
 * impossible (lines not produced by this fold), and the equivalence oracle the
 * JVM suite checks the walk against on every U+00AD run of the corpus.
 */
object SoftHyphenCuts {

    /**
     * True when some line of [lines] (the fold's DISPLAY lines for [text],
     * before any clamp) ends at a taken soft hyphen.
     */
    @JvmStatic
    fun took(text: String, lines: List<String>, hyphenChar: String): Boolean {
        // No U+00AD in the source → nothing to take (the wave-52 short-circuit).
        if (text.indexOf('\u00AD') < 0) return false
        // The walk answers whenever it can align; otherwise the wave-52 count.
        return walk(text, lines, hyphenChar) ?: legacyCount(text, lines, hyphenChar)
    }

    /**
     * Align [lines] with the source words: true at the first line ending in a
     * taken hyphenation op, false when every word is consumed without one,
     * null when the lines cannot be explained by the fold.
     */
    private fun walk(text: String, lines: List<String>, hyphenChar: String): Boolean? {
        // The fold's own words: paragraphs on "\n", words on U+0020 (empties
        // dropped), each decomposed into display text + explicit ops.
        val words = text.split('\n').flatMap { p -> p.split(' ').filter { it.isNotEmpty() } }
            .map { WordBreakOpportunities.analyze(it) }
        // Cursor: the current word, and how much of its text earlier lines used.
        var w = 0
        var off = 0
        for (line in lines) {
            // Cursor inside the display line.
            var pos = 0
            while (pos < line.length) {
                // Between words the fold writes exactly one U+0020 separator.
                if (off == 0 && line[pos] == ' ') { pos++; continue }
                // Ink with no source word left: not this fold's output.
                val word = words.getOrNull(w) ?: return null
                // The unconsumed rest of the current word.
                val rest = word.text.substring(off)
                // Where that rest would end if the whole of it sits here.
                val end = pos + rest.length
                if (line.startsWith(rest, pos) && (end == line.length || line[end] == ' ')) {
                    // The rest of the word is on this line: move to the next word.
                    pos = end; w++; off = 0; continue
                }
                // Otherwise the line ENDS with a head of the word, cut at an op:
                // the greediest op whose head (+ painted hyphen) is the line's tail,
                // which is the op WordBreakOpportunities.split prefers.
                val tail = line.substring(pos)
                val op = word.ops.lastOrNull {
                    it.offset > off &&
                        word.text.substring(off, it.offset) + (if (it.paintsHyphen) hyphenChar else "") == tail
                } ?: return null
                // A hyphenation op (a removed U+00AD) is a TAKEN soft hyphen.
                if (op.paintsHyphen) return true
                // A literal-hyphen break-after: keep walking from the cut.
                off = op.offset; pos = line.length
            }
        }
        // Every word consumed and no soft hyphen taken; leftovers = misaligned.
        return if (w == words.size && off == 0) false else null
    }

    /**
     * True when [walk] can explain [lines] as this fold's output for [text]
     * (no fallback to [legacyCount] needed) — exposed for the JVM pin that
     * the walk, not the fallback, answers on every corpus run.
     */
    internal fun walkAligns(text: String, lines: List<String>, hyphenChar: String): Boolean =
        // Null is the one "cannot align" answer.
        walk(text, lines, hyphenChar) != null

    /**
     * The wave-52 answer, verbatim (PreBreakPipeline.tookSoftHyphenBreak until
     * wave 54): more hyphenChars in the lines than in the source. Exact for any
     * non-empty hyphenChar that never occurs inside the source; always false for
     * `""` — the defect [took] exists to close.
     */
    @JvmStatic
    fun legacyCount(text: String, lines: List<String>, hyphenChar: String): Boolean {
        // Sliding-window (step 1) occurrence count of hyphenChar in s, as wave 52 wrote it.
        fun count(s: String): Int = if (hyphenChar.isEmpty()) 0 else s.windowed(hyphenChar.length, 1).count { it == hyphenChar }
        // A taken break adds one hyphenChar the source did not have.
        return lines.sumOf { count(it) } > count(text)
    }
}
