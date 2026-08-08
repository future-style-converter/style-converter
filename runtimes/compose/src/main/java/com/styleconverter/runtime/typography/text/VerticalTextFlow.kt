package com.styleconverter.runtime.typography.text

// Compose runtime — wave 35 lane B5, the VERTICAL-FLOW decision module.
//
// ## What this file is
// The pure, platform-free half of vertical text layout: given a
// `writing-mode`, a `text-orientation` and the run's string, it answers the
// three questions a renderer has to ask before it can typeset a vertical
// line, and nothing else. No Compose types appear here on purpose — the
// SwiftUI twin
// (runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/
//  writing/VerticalTextFlow.swift) is the same file in Swift, function for
// function, so the two natives can never disagree about WHICH runs are
// upright or WHERE a vertical line breaks.
//
//   1. [runOrientation] — css-writing-modes-4 §5.1 `text-orientation`: do
//      this run's glyphs stand UPRIGHT in the vertical line, or are they
//      typeset ROTATED 90° (the "sideways" family)? MIXED = the run needs
//      both and this slice declines it.
//   2. [lineStack] — css-writing-modes-4 §3: which way do successive LINES
//      stack? `vertical-rl` / `sideways-rl` stack right-to-left, the two
//      `-lr` modes left-to-right.
//   3. [uprightColumns] — the line-breaking plan for an UPRIGHT run: the
//      inline axis is VERTICAL, so the available "width" a line wraps at is
//      the box's HEIGHT budget, and each glyph advances by its own vertical
//      advance.
//
// ## Why the upright case needs its own path at all
// Measured on the frozen wave34-final css-writing-modes capture
// (available-size-011, the WPT "ＰＡＳＳ" test): Android renders that run
// through ComponentRenderer's wave-5 rotated-run branch, which lays the text
// out against the swapped constraint and then spins the WHOLE laid-out run
// +90°. That produces the right LINE ORDER (the top line becomes the
// rightmost column) but leaves every glyph lying on its side — Android
// 0.9247 against a web that renders the same test upright at 0.9898. The
// characters are FULLWIDTH Latin (U+FF30 Ｐ, U+FF21 Ａ, U+FF33 Ｓ), whose
// Unicode Vertical_Orientation is U, so `text-orientation: mixed` (the
// initial value) sets them UPRIGHT. A whole-run rotation can never express
// that: the glyphs and the line stacking need opposite transforms.
//
// ## Blast radius, enumerated before the change
// Replaying [runOrientation] over every component in the 29 frozen
// wave34-final sections that carries a vertical `writing-mode` AND its own
// text yields exactly ONE upright run — `available-size-011__1__0`. The
// other 17 are ASCII (`"0"`, `"0 0 0 0 0 0 0"`, css-text-decor's
// `"ABC ABC"`), all Vertical_Orientation R ⇒ ROTATED ⇒ the caller keeps its
// frozen branch verbatim. Everything outside a vertical writing mode never
// reaches this file.

/**
 * How the glyphs of ONE run stand inside a vertical line
 * (css-writing-modes-4 §5.1).
 */
enum class GlyphOrientation {
    /** Every glyph stands upright; the line advances downward. */
    UPRIGHT,

    /** Every glyph is typeset sideways (rotated 90° clockwise). */
    ROTATED,

    /**
     * The run mixes both classes. Real browsers typeset such a run
     * per-character; this slice declines it and the caller keeps whatever it
     * did before, rather than guessing (repo no-silent-fallthrough rule).
     */
    MIXED,
}

/** Which way successive LINES stack (css-writing-modes-4 §3). */
enum class LineStack {
    /** `vertical-rl`, `sideways-rl`: line 1 is the RIGHTMOST column. */
    RIGHT_TO_LEFT,

    /** `vertical-lr`, `sideways-lr`: line 1 is the LEFTMOST column. */
    LEFT_TO_RIGHT,
}

object VerticalTextFlow {

    /**
     * Hard ceiling on the number of vertical lines a plan may produce.
     *
     * A bounded plan is a plan we can reason about; an unbounded one is a
     * per-glyph Text explosion in the renderer. 64 columns is ~3× the widest
     * upright run any WPT reference page in the corpus draws, so no real
     * document hits it — it only stops a degenerate budget (a 1px height on
     * a 500-character run) from composing 500 leaf views.
     */
    const val MAX_COLUMNS: Int = 64

    /**
     * Is this code point's Unicode Vertical_Orientation `U` or `Tu` — i.e.
     * does it stand UPRIGHT under `text-orientation: mixed`?
     *
     * This is a deliberately BOUNDED range table, not a full UCD import: the
     * runtime ships no Unicode data files and the property has ~300 ranges.
     * What is listed below is the U-class core — the CJK/Kana/Hangul blocks
     * plus the fullwidth compatibility forms — which is exactly the set the
     * WPT corpus exercises. Anything not listed answers `false` (the R
     * class, "rotate me"), which is both the majority answer and the safe
     * one: it keeps the caller on its frozen rotated branch.
     *
     * Ranges (UAX #50 Vertical_Orientation=U, abridged):
     *  - U+1100–U+11FF  Hangul Jamo
     *  - U+2E80–U+303E  CJK Radicals, Kangxi, CJK Symbols & Punctuation
     *  - U+3041–U+33FF  Kana, Bopomofo, Hangul Compat Jamo, Kanbun,
     *                   CJK Strokes, Enclosed CJK, CJK Compatibility
     *  - U+3400–U+4DBF  CJK Unified Ideographs Extension A
     *  - U+4E00–U+9FFF  CJK Unified Ideographs
     *  - U+A000–U+A4CF  Yi Syllables + Radicals
     *  - U+A960–U+A97F  Hangul Jamo Extended-A
     *  - U+AC00–U+D7FF  Hangul Syllables + Jamo Extended-B
     *  - U+F900–U+FAFF  CJK Compatibility Ideographs
     *  - U+FE10–U+FE19  Vertical Forms
     *  - U+FE30–U+FE4F  CJK Compatibility Forms
     *  - U+FF01–U+FF60  FULLWIDTH ASCII variants  ← the available-size-011 run
     *  - U+FFE0–U+FFE6  Fullwidth signs
     *  - U+20000–U+2FFFD / U+30000–U+3FFFD  CJK Extensions B…
     *
     * Deliberately NOT listed: U+FF61–U+FF9F (HALFWIDTH katakana), whose
     * Vertical_Orientation is R — halfwidth forms rotate, which is the whole
     * point of the halfwidth/fullwidth distinction in vertical typesetting.
     */
    fun isUprightOrientation(codePoint: Int): Boolean = when (codePoint) {
        in 0x1100..0x11FF -> true
        in 0x2E80..0x303E -> true
        in 0x3041..0x33FF -> true
        in 0x3400..0x4DBF -> true
        in 0x4E00..0x9FFF -> true
        in 0xA000..0xA4CF -> true
        in 0xA960..0xA97F -> true
        in 0xAC00..0xD7FF -> true
        in 0xF900..0xFAFF -> true
        in 0xFE10..0xFE19 -> true
        in 0xFE30..0xFE4F -> true
        in 0xFF01..0xFF60 -> true
        in 0xFFE0..0xFFE6 -> true
        in 0x20000..0x2FFFD -> true
        in 0x30000..0x3FFFD -> true
        else -> false
    }

    /**
     * The glyph orientation for one run, or `null` when the mode is not
     * vertical (nothing to decide — the caller's horizontal path owns it).
     *
     * css-writing-modes-4 §3 + §5.1:
     *  - `sideways-rl` / `sideways-lr` typeset EVERYTHING sideways; the
     *    `text-orientation` value does not apply (the mode already fixes it).
     *  - `text-orientation: sideways` ⇒ ROTATED, `upright` ⇒ UPRIGHT — both
     *    unconditional, no per-character question.
     *  - `text-orientation: mixed` (initial) ⇒ per-character
     *    Vertical_Orientation: U/Tu stand upright, R/Tr rotate.
     *
     * WHITESPACE IS IGNORED when classifying: a space paints no glyph, so its
     * R-class membership must not drag an otherwise all-upright CJK run into
     * [GlyphOrientation.MIXED]. (`"Ｓ Ｓ Ａ Ｐ"` is an upright run with three
     * spaces in it, and Chrome typesets it upright.) A run that is ONLY
     * whitespace has no glyphs to orient and answers ROTATED — the frozen
     * branch, i.e. no behaviour change.
     */
    fun runOrientation(
        writingMode: WritingModeValue,
        textOrientation: TextOrientationValue,
        text: String,
    ): GlyphOrientation? {
        when (writingMode) {
            // Horizontal flow — this module has no opinion.
            WritingModeValue.HORIZONTAL_TB -> return null
            // §3: the sideways-* modes ARE "text-orientation: sideways".
            WritingModeValue.SIDEWAYS_RL,
            WritingModeValue.SIDEWAYS_LR,
            -> return GlyphOrientation.ROTATED
            WritingModeValue.VERTICAL_RL,
            WritingModeValue.VERTICAL_LR,
            -> Unit
        }
        return when (textOrientation) {
            TextOrientationValue.UPRIGHT -> GlyphOrientation.UPRIGHT
            TextOrientationValue.SIDEWAYS -> GlyphOrientation.ROTATED
            TextOrientationValue.MIXED -> classifyMixed(text)
        }
    }

    /** §5.1 `mixed`: per-character Vertical_Orientation over the whole run. */
    private fun classifyMixed(text: String): GlyphOrientation {
        var sawUpright = false
        var sawRotated = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            // Whitespace paints nothing — see the KDoc's whitespace note.
            if (Character.isWhitespace(cp)) continue
            if (isUprightOrientation(cp)) sawUpright = true else sawRotated = true
            // Both classes present: no single-transform answer exists.
            if (sawUpright && sawRotated) return GlyphOrientation.MIXED
        }
        // All-upright wins only when at least one upright glyph was seen; a
        // glyphless (or all-R) run answers ROTATED = the frozen branch.
        return if (sawUpright) GlyphOrientation.UPRIGHT else GlyphOrientation.ROTATED
    }

    /**
     * Which side line 1 sits on, or `null` for a horizontal mode.
     *
     * css-writing-modes-4 §3: the block-progression direction of the two
     * `-rl` modes is right-to-left, of the two `-lr` modes left-to-right.
     * `direction` (the INLINE direction) does not enter — it orders glyphs
     * within a line, not lines within a block.
     */
    fun lineStack(writingMode: WritingModeValue): LineStack? = when (writingMode) {
        WritingModeValue.HORIZONTAL_TB -> null
        WritingModeValue.VERTICAL_RL, WritingModeValue.SIDEWAYS_RL -> LineStack.RIGHT_TO_LEFT
        WritingModeValue.VERTICAL_LR, WritingModeValue.SIDEWAYS_LR -> LineStack.LEFT_TO_RIGHT
    }

    /**
     * The run split into one string per CODE POINT — the renderer's per-glyph
     * slot list, and the array [uprightColumnIndices] indexes into.
     *
     * Code points, not grapheme clusters: it is the unit the Swift twin's
     * `unicodeScalars` walk uses, so the two natives split identically. The
     * shared limitation that buys is that a combining sequence could be split
     * across two lines — no corpus run contains one (upright runs are
     * CJK/kana/fullwidth, all single-scalar), and the alternative (one
     * platform grouping graphemes and the other not) is exactly the silent
     * twin drift the byte-parallel rule exists to prevent.
     */
    fun codePointsOf(text: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            out.add(text.substring(i, i + n))
            i += n
        }
        return out
    }

    /**
     * Break an UPRIGHT run into vertical lines, in LOGICAL order (element 0
     * is the first line = the RIGHTMOST column under [LineStack.RIGHT_TO_LEFT]).
     *
     * The inline axis of a vertical writing mode is the box's BLOCK axis on
     * screen, so the wrap budget is the box's available HEIGHT and each glyph
     * consumes its own vertical advance.
     *
     * @param glyphs          the run as per-code-point strings — exactly what
     *   [codePointsOf] returns, and exactly the order the renderer composed
     *   its glyph slots in, so the returned indices address them directly.
     * @param charAdvancePx   one upright glyph's advance along the vertical
     *   inline axis. Upright vertical typesetting gives every glyph the same
     *   em-square advance (that is what "upright" means — the glyph sits in
     *   its em box), so ONE number describes the run.
     * @param budgetPx        the available inline extent, or `null` when the
     *   caller's constraint is unbounded.
     *
     * @return per line, the indices into [glyphs] that line paints, or `null`
     *   to DECLINE — the caller must then keep its existing branch untouched.
     *   Declines on: an unbounded or non-positive budget (we refuse to invent
     *   a wrap width), a non-positive advance, a glyphless run, or a plan
     *   wider than [MAX_COLUMNS].
     */
    fun uprightColumnIndices(
        glyphs: List<String>,
        charAdvancePx: Double,
        budgetPx: Double?,
    ): List<List<Int>>? {
        // An unbounded inline axis is a real CSS state (`height: auto` with
        // no constraining ancestor) and its answer is "one very long line" —
        // but a renderer that guesses here would move pixels on documents
        // this slice has never measured, so we decline instead.
        if (budgetPx == null || !budgetPx.isFinite() || budgetPx <= 0.0) return null
        if (!charAdvancePx.isFinite() || charAdvancePx <= 0.0) return null
        if (glyphs.none { !isCollapsibleSpace(it) }) return null

        // How many glyphs fit in one line. `+ 1e-3` absorbs the sub-pixel
        // rounding a measured advance carries (a 16.0000001px advance in a
        // 16px budget must still fit ONE glyph, not zero); floor() then gives
        // the browser's "as many as fully fit" rule. Never below 1 — CSS
        // always places at least one glyph per line, overflowing if it must.
        val capacity = maxOf(1, ((budgetPx / charAdvancePx) + 1e-3).toInt())

        val columns = mutableListOf<List<Int>>()
        var i = 0
        while (i < glyphs.size) {
            // css-text-3 §4.1.3: a space at a line break hangs / collapses —
            // it never starts the next line.
            while (i < glyphs.size && isCollapsibleSpace(glyphs[i])) i++
            if (i >= glyphs.size) break
            val line = mutableListOf<Int>()
            while (i < glyphs.size && line.size < capacity) {
                line.add(i)
                i++
            }
            // …and a space that landed at the END of a line collapses too.
            while (line.isNotEmpty() && isCollapsibleSpace(glyphs[line.last()])) {
                line.removeAt(line.size - 1)
            }
            if (line.isNotEmpty()) columns.add(line)
            if (columns.size > MAX_COLUMNS) return null
        }
        return columns.ifEmpty { null }
    }

    /**
     * [uprightColumnIndices] rendered back to strings — the shape the unit
     * tests assert on, and the shape a debug log prints. Same decisions, one
     * call away, so a test can never pin a plan the renderer does not use.
     */
    fun uprightColumns(
        text: String,
        charAdvancePx: Double,
        budgetPx: Double?,
    ): List<String>? {
        val glyphs = codePointsOf(text)
        val plan = uprightColumnIndices(glyphs, charAdvancePx, budgetPx) ?: return null
        return plan.map { col -> col.joinToString("") { glyphs[it] } }
    }

    /** The two separators CSS collapses at a line break in normal wrapping. */
    private fun isCollapsibleSpace(glyph: String): Boolean = glyph == " " || glyph == "\t"
}
