package com.styleconverter.runtime.typography

// DecorationSkipSpaces.kt — css-text-decor-4 §2.6 `text-decoration-
// skip-spaces` (applier campaign wave 47, lane Z7).
//
// BYTE-PARALLEL TWIN of the iOS runtime's
// runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/
// decoration/DecorationSkipSpaces.swift — same spacer set, same
// edge-trim semantics, same pin table (DecorationSkipSpacesTest ↔
// DecorationSkipSpacesTests). Change one, change both.
//
// WHAT THIS IMPLEMENTS — `text-decoration-skip-spaces`' INITIAL value is
// `start end`: underline / overline / line-through must not paint over
// spacers sitting at the START or END of a line box, while MID-line
// spacers keep their decoration (WPT text-decoration-skip-spaces-002/
// 003/004 — mid-line spacers — already passed on both natives; -001,
// whose spacer runs sit at line edges, painted full-width bands across
// them: wave-46 gate Android 0.902 / iOS 0.934 vs web PASS 0.952,
// visible as three long blue bars where the Chromium ref underlines
// only "ABCDEF").
//
// THE SPACER SET — pinned to -001's own character inventory (U+2000…
// U+200A, U+205F, U+3000, U+1680 OGHAM SPACE MARK, U+00A0 NBSP, plain
// U+0020), all of which Chromium skips: Unicode general category Zs,
// plus TAB U+0009 (White_Space, decoration-transparent in every engine)
// and ZWSP U+200B (zero-width Cf — including it keeps an edge run
// contiguous when it sits between two real spacers; its advance is zero
// either way). All members are BMP code points, so per-Char
// classification is exact — no surrogate handling needed.
//
// Pure Kotlin/JVM string code — no Compose imports — so the JVM unit
// suite pins every branch without an Android canvas (the DecorationOps
// test pattern).
object DecorationSkipSpaces {

    /** True for a "spacer" in the css-text-decor-4 §2.6 sense — see the
     *  file header for the exact set and why. */
    fun isSpacer(c: Char): Boolean = when (c) {
        // TAB — White_Space, never decorated by any engine.
        '\t' -> true
        // ZWSP — zero-width (Cf); kept so an edge spacer run stays
        // contiguous across it (its own advance is zero regardless).
        '\u200B' -> true
        // Unicode Zs — the space separators: U+0020, U+00A0 NBSP,
        // U+1680 OGHAM, U+2000…U+200A, U+202F NNBSP, U+205F MMSP,
        // U+3000 IDEOGRAPHIC SPACE.
        else -> Character.getType(c) == Character.SPACE_SEPARATOR.toInt()
    }

    /**
     * The index range of one rendered line's decorated CORE — the line
     * with its maximal spacer prefix and suffix trimmed — or null when
     * the line is spacers-only (or empty): such a line paints NO
     * decoration at all, exactly like Chromium on -001's spacers-only
     * wrapped lines.
     */
    fun coreRange(line: String): IntRange? {
        // First ink-bearing character — everything before it is lead.
        val first = line.indexOfFirst { !isSpacer(it) }
        if (first < 0) return null
        // Last ink-bearing character — everything after it is trail.
        val last = line.indexOfLast { !isSpacer(it) }
        return first..last
    }

    /**
     * The trimmed paint extent [left, right] for one visual line, or
     * null when the line paints no decoration (spacers-only).
     *
     * @param lineText   the line's own characters (layout offsets
     *                   [lineStart, lineStart + lineText.length)).
     * @param lineStart  the line's first character offset in the whole
     *                   laid-out string — what [caretX] is keyed by.
     * @param left       the untrimmed line left (getLineLeft), returned
     *                   UNTOUCHED when the line has no leading spacers
     *                   (float-exact byte-stability for every committed
     *                   baseline text).
     * @param right      the untrimmed line right (getLineRight), same
     *                   identity guarantee for a spacer-free tail.
     * @param caretX     caret x for a character offset — on Android,
     *                   TextLayoutResult.getHorizontalPosition(offset,
     *                   usePrimaryDirection = true).
     *
     * An RTL (or mixed-direction) line can hand back caret positions
     * that do not bracket [left, right] left-to-right; rather than smear
     * the band we fall back to the untrimmed extent — honest no-op,
     * logged by the caller. (The -001 corpus line is pure LTR.)
     */
    fun trimmedExtent(
        lineText: String,
        lineStart: Int,
        left: Float,
        right: Float,
        caretX: (Int) -> Float
    ): Pair<Float, Float>? {
        // Spacers-only line → no decoration at all.
        val core = coreRange(lineText) ?: return null
        // No edge spacers → the untrimmed extent, bit-for-bit.
        if (core.first == 0 && core.last == lineText.length - 1) {
            return left to right
        }
        // Leading edge of the first core character / trailing edge of
        // the last (caret AFTER it) — LTR visual order.
        val inkLeft = if (core.first == 0) left else caretX(lineStart + core.first)
        val inkRight = if (core.last == lineText.length - 1) right
            else caretX(lineStart + core.last + 1)
        // Direction sanity: a reversed bracket means bidi reordering —
        // fall back to the untrimmed extent instead of painting a
        // negative-width or misplaced band.
        return if (inkLeft < inkRight) inkLeft to inkRight else left to right
    }
}
