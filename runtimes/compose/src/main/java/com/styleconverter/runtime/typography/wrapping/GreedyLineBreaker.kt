package com.styleconverter.runtime.typography.wrapping

/**
 * GreedyLineBreaker.kt
 * typography/wrapping — wave 38 (lane N8), the Kotlin twin of
 * `StyleEngine/typography/GreedyLineBreaker.swift`.
 *
 * WHY A KOTLIN COPY AT ALL. iOS needed this class as a RENDER path:
 * TextKit's default `lineBreakStrategy` includes push-out (orphan
 * avoidance), so SwiftUI wrapped a run where neither Chromium nor
 * Compose does, and the iOS lane pre-breaks EVERY wrappable run with
 * hard newlines to force TextKit onto the greedy positions.
 *
 * Compose does NOT need that: Minikin's default break strategy IS
 * greedy, and the committed captures agree with the browser-ref on
 * ordinary wrap points. What Compose needs is the OPPOSITE half — a way
 * to find the ONE line the platform is about to break where CSS says it
 * must not (css-text-3 §5.5 / CSS 2.1 §9.5: under `overflow-wrap:
 * normal` a word with no soft-wrap opportunity OVERFLOWS the line box;
 * the emergency character break is reserved for `overflow-wrap:
 * break-word|anywhere` and `word-break: break-all`). Minikin does the
 * emergency break unconditionally, and its line breaker lives INSIDE
 * `Text` with no per-run "never break a word" switch — so the only
 * lever left is the string. Deciding whether that lever is needed means
 * reproducing the CSS line breaking here first, which is exactly what
 * this breaker does. See [PreBreakPipeline] for the decision + the
 * rewrite it drives.
 *
 * The algorithm core takes an INJECTED measurer, exactly like the Swift
 * twin, so the JVM suite pins break positions with no device, no font
 * resolver and no Compose runtime. The production measurer on this
 * platform is a `TextMeasurer` closure built at the render call site
 * (ComponentRenderer.PlaceholderContent) — it cannot live here without
 * dragging androidx.compose.ui into a pure-Kotlin file.
 */
object GreedyLineBreaker {

    /** css-overflow-4 §4.2 — the UA `block-ellipsis: auto` string. U+2026
     *  HORIZONTAL ELLIPSIS is what Chromium paints, hence what every
     *  frozen line-clamp ref carries (block-ellipsis-025's fourth line is
     *  this one glyph alone). */
    const val BLOCK_ELLIPSIS_MARKER: String = "…"

    /**
     * Wave 52 (lane L9) — a fixed-count `line-clamp` whose marker is DRAWN
     * (css-overflow-4 §5.1: `line-clamp: <n>` expands to `max-lines: <n>` +
     * `block-ellipsis: auto`; §4.2: `auto` = the UA ellipsis). The caller
     * passes null for a marker-suppressed clamp (`no-ellipsis` / `""`,
     * LineClampWire.markerSuppressed) and for the `max-lines` longhand
     * alone (block-ellipsis initial `none` — no marker).
     *
     * @property lines the cap N — lines past it are hidden.
     * @property marker the string placed at the end of line N; a REAL
     *   character in the returned string, measured and rendered like any
     *   other glyph — the fit test is honest only because of that.
     */
    data class Clamp(val lines: Int, val marker: String = BLOCK_ELLIPSIS_MARKER)

    /**
     * Wave 52 (lane L9) — apply a drawn-marker [clamp] to greedy [lines]:
     * keep lines 1…N and, on line N, hide content back to the LATEST soft
     * wrap opportunity at which `kept + marker` fits [maxWidth]
     * ([clampHead]); when none fits the line is the marker alone. This is css-overflow-4
     * §4.2's placement rule as Chromium performs it — content at the end of
     * the last line is hidden at soft wrap opportunities until the ellipsis
     * FITS, never a character-level truncation: block-ellipsis-025's ref
     * shows the whole 34ch `supercalifragilisticexpialidocious` displaced
     * off line 4 (it overflows the 32.5ch box, so even "word + …" cannot
     * fit) leaving `…` alone, where SwiftUI's tail truncation appended `…`
     * AFTER the full word (ios f 0.9495) and Compose's Clip painted no
     * marker at all (android P 0.9607, the wrong picture).
     *
     * Identity — the SAME list instance — when the paragraph fits the cap
     * (a clamp only ever REMOVES lines) or the cap is non-positive, so a
     * caller can detect "the clamp trimmed" by reference (PreBreakPipeline
     * pairs a trimmed result with `softWrap = false` + Visible, the marker
     * being baked into the string; TextOverflow.Ellipsis there would trip
     * the finalMaxLines landmine the wave-39 banner documents). ALSO the
     * same instance when line N's hidden tail would cross an opportunity
     * the walk does not model ([clampHead] → null): the fired run then
     * keeps its wave-51 shape (no marker baked), logged once.
     *
     * @param source wave 52 fix pass (skeptic M1) — the run's text as the
     *   fold received it, so line N's whole words get back the U+00AD soft
     *   hyphens the display string dropped ([markedLine]):
     *   block-ellipsis-028's ref hides `cally` at `uncharacteristi<U+00AD>cally`'s
     *   soft hyphen and paints `uncharacteristi‐…`. null = no soft hyphens.
     * @param hyphenChar the glyph a soft-hyphen cut paints (css-text-3
     *   §5.3) — the same one the fold paints at a taken soft hyphen.
     */
    @JvmStatic
    fun clampLines(
        lines: List<String>,
        clamp: Clamp,
        maxWidth: Float,
        measure: (String) -> Float,
        source: String? = null,
        hyphenChar: String = WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER
    ): List<String> {
        // Nothing hidden → nothing to mark (a 4-line clamp on a 3-line
        // paragraph paints no ellipsis, css-overflow-4 §4.2 "if content
        // overflows").
        if (clamp.lines <= 0 || lines.size <= clamp.lines) return lines
        val kept = lines.subList(0, clamp.lines).toMutableList()
        val last = kept[clamp.lines - 1]
        // The whole of line N plus the marker fits: nothing to hide.
        if (measure(last + clamp.marker) <= maxWidth) {
            kept[clamp.lines - 1] = last + clamp.marker
            return kept
        }
        // Hide back to the latest opportunity that leaves room, or decline.
        val head = clampHead(markedLine(last, source), clamp.marker, maxWidth, measure, hyphenChar)
        if (head == null) {
            // No silent fallthrough: the wave-51 shape stays, named once.
            warnUnmodelledOnce()
            return lines
        }
        kept[clamp.lines - 1] = head + clamp.marker
        return kept
    }

    /**
     * Wave 52 fix pass (skeptic M1) — the longest prefix of [marked] (line
     * N, untaken soft hyphens restored) that ends at a soft wrap
     * opportunity and fits beside [marker]; "" when none does; null =
     * DECLINE. css-overflow-4 §4.2 hides content "at soft wrap
     * opportunities", and the fold's U+0020-only word split is coarser than
     * UAX #14: block-ellipsis-030 is `123<U+1680>5 789` at 5ch, whose OGHAM
     * SPACE MARK (class BA) the ref breaks at (`123…`), where a U+0020-only
     * cut found nothing and baked `…` alone. The walk goes from the END,
     * boundary by boundary, so the first fit is the latest. It declines the
     * moment the hidden tail holds an ideograph or an SA-script letter
     * (UAX #14 ID / SA: opportunities between letters, which only a class
     * table or a dictionary can place) — a later opportunity might exist
     * there. Twin: GreedyLineBreaker.swift `clampHead`.
     */
    @JvmStatic
    fun clampHead(
        marked: String,
        marker: String,
        maxWidth: Float,
        measure: (String) -> Float,
        hyphenChar: String
    ): String? {
        // Code points, not UTF-16 units, so both twins index identically.
        val s = marked.codePoints().toArray()
        var p = s.size - 1
        // Boundary p sits between s[p-1] and s[p]; s[p..] is hidden.
        while (p >= 1) {
            // The hidden tail just grew by s[p] — an unmodelled class ends it.
            if (unmodelledOpportunity(s[p])) return null
            val head = cutHead(s, p, hyphenChar)
            if (head != null && measure(head + marker) <= maxWidth) return head
            p--
        }
        // Nothing fits: the whole line is hidden (025's 34ch word), unless
        // its first code point is itself an unmodelled opportunity.
        if (s.isNotEmpty() && unmodelledOpportunity(s[0])) return null
        return ""
    }

    /** Break-after hyphens and dashes (UAX #14 HY / BA / B2). */
    private val CLAMP_DASHES = intArrayOf(0x2D, 0x2010, 0x2013, 0x2014)

    /** The kept DISPLAY text when line N breaks at boundary [p], or null when
     *  UAX #14 (as approximated here) gives no opportunity there. */
    private fun cutHead(s: IntArray, p: Int, hyphenChar: String): String? {
        val before = s[p - 1]
        val after = s[p]
        // §5.3: a soft-hyphen break paints the hyphenate character.
        var suffix = ""
        when {
            // Before a space run (SP / BA spaces): it hangs and is hidden
            // with the tail (css-text-3 §4.1.3) — 030's U+1680, 029's U+0020.
            isClampSeparator(after) && !isClampSeparator(before) -> Unit
            // ZERO WIDTH SPACE (ZW): breaks after it; inkless, so cutting
            // before it paints the same picture.
            after == 0x200B -> Unit
            // An untaken soft hyphen (manual): taken here, so it paints —
            // unless a literal hyphen already ends the head (`analyze`'s rule).
            after == 0xAD && !isClampSeparator(before) ->
                suffix = if (before == 0x2D || before == 0x2010) "" else hyphenChar
            // Break AFTER a hyphen or dash — not word-initial (UAX #14
            // LB20.1) and not HY × NU (`1-2`), as `analyze` rules.
            before in CLAMP_DASHES && p >= 2 && !isClampSeparator(after) &&
                !isClampSeparator(s[p - 2]) &&
                !((before == 0x2D || before == 0x2010) && after in 0x30..0x39) -> Unit
            // Anything else: no opportunity at this boundary.
            else -> return null
        }
        // Remaining soft hyphens have no advance; trailing spaces/ZWSP hang.
        val head = s.copyOfRange(0, p).filter { it != 0xAD }.toMutableList()
        while (head.isNotEmpty() && (isClampSeparator(head.last()) || head.last() == 0x200B)) {
            head.removeAt(head.size - 1)
        }
        // Rebuild the display string code point by code point.
        val out = StringBuilder()
        head.forEach { out.appendCodePoint(it) }
        return out.toString() + suffix
    }

    /** UAX #14 space separators that are break opportunities (SP and the
     *  BA-class Zs), i.e. Zs minus the no-break U+00A0 / U+2007 / U+202F. */
    private fun isClampSeparator(c: Int): Boolean =
        c == 0x20 || c == 0x09 || c == 0x1680 || c in 0x2000..0x2006 ||
            c in 0x2008..0x200A || c == 0x205F || c == 0x3000

    /** Code points whose opportunities sit BETWEEN letters (ID ideographs,
     *  Hangul, fullwidth forms; SA Thai/Lao/Myanmar/Khmer/Tai) — the classes
     *  the walk cannot place, so a hidden tail holding one declines. */
    private fun unmodelledOpportunity(c: Int): Boolean =
        (c in 0x2E80..0x9FFF && c != 0x3000) || c in 0xAC00..0xD7AF ||
            c in 0xF900..0xFAFF || c in 0xFF00..0xFFEF || c in 0x20000..0x3FFFF ||
            c in 0x0E00..0x0EFF || c in 0x1000..0x109F || c in 0x1780..0x17FF ||
            c in 0x1950..0x19DF || c in 0x1A20..0x1AAF || c in 0xAA60..0xAADF

    /**
     * Line N with each WHOLE word's untaken soft hyphens restored from
     * [source] (the display word → its raw spelling). A word split across
     * lines N-1/N matches nothing and keeps its display text — its soft
     * hyphens are a stated loss (the clamp then hides it whole).
     */
    @JvmStatic
    fun markedLine(line: String, source: String?): String {
        // No soft hyphen anywhere → the display line IS the marked line.
        if (source == null || source.indexOf('\u00AD') < 0) return line
        val raw = HashMap<String, String>()
        // The fold's own word split (U+0020, paragraphs at "\n").
        for (word in source.split(' ', '\n')) {
            // Only soft-hyphen carriers differ from their display text.
            if (word.indexOf('\u00AD') < 0) continue
            // First spelling wins; analyze() is the fold's display mapping.
            raw.getOrPut(WordBreakOpportunities.analyze(word).text) { word }
        }
        // Token-wise: the fold joins every line's words with ONE U+0020.
        return line.split(' ').joinToString(" ") { raw[it] ?: it }
    }

    /** Set once the decline below has been logged (warn once, not per frame). */
    private val unmodelledWarned = java.util.concurrent.atomic.AtomicBoolean(false)

    /** The decline's breadcrumb — guarded because the JVM suite has no
     *  android.util.Log (the runCatching idiom LineClampCapResolve uses). */
    private fun warnUnmodelledOnce() {
        // First sighting only.
        if (!unmodelledWarned.compareAndSet(false, true)) return
        runCatching {
            android.util.Log.w(
                "LineClamp",
                "line-clamp: the hidden tail crosses an ideographic / SA-script break " +
                    "opportunity the clamp walk does not model; no marker is baked",
            )
        }
    }

    /**
     * Break [text] into greedy lines at [maxWidth]: words accumulate
     * left-to-right and a word moves to the next line the moment the
     * candidate line no longer fits — Chromium's default css-text-3 §5
     * behaviour and Minikin's `BreakStrategy.Simple` (no look-ahead, no
     * push-out, no balancing). A single word wider than [maxWidth] stays
     * alone on its line and OVERFLOWS, which is CSS without
     * `overflow-wrap` (CSS 2.1 §9.5) — the case [hasUnbreakableOverflowingLine]
     * reports on. Pre-existing hard newlines are preserved as paragraph
     * boundaries and each paragraph wraps independently. Words are
     * space-separated: the converter's text channel is whitespace-collapsed
     * upstream (css-text-3 §4.1), matching the collapse the browser applied
     * to the reference page.
     *
     * Wave 41 (lane T3) — the fold now models IN-WORD opportunities via
     * [WordBreakOpportunities]: U+00AD soft hyphens (a `none` run never
     * reaches here with any — [SoftHyphenPolicy] deletes them first) and
     * literal `-`/U+2010 break-afters. Words carrying neither decompose
     * to themselves with no ops, so every split probe declines at once
     * and the fold's decisions are IDENTICAL to wave 38's. Returned
     * lines are DISPLAY strings: soft hyphens are gone, taken ones
     * replaced by [hyphenChar], so a fired [PreBreakPipeline] run
     * renders exactly what was measured (wave40-final android-ref
     * evidence in the twin file's banner: hyphens-manual-013 0.9458,
     * hyphens-none-012 0.8834).
     *
     * Byte-parallel with the Swift twin's fold — same "first word always
     * opens the line" rule, same candidate-measured-as-one-string rule
     * (so the separator's own advance and any kerning across it are
     * included exactly as rendered), same §5.3 handling — minus the
     * dictionary seam, which on this platform belongs to Minikin
     * ([AutoHyphenation]'s banner owns that story).
     *
     * @param hyphenChar the glyph painted at a taken hyphenation point
     *   (css-text-3 §5.3 UA-defined; css-text-4 `hyphenate-character`
     *   would override). A REAL character in the returned string, so it
     *   is measured and rendered like any other glyph — a line that ends
     *   in a hyphen must fit WITH the hyphen.
     * @param clamp wave 52 (lane L9) — a drawn-marker `line-clamp`, or
     *   null (every pre-wave-52 caller): the greedy lines are trimmed to
     *   the cap with the marker placed by [clampLines]. Both twins take it
     *   in the same position with the same default.
     */
    @JvmStatic
    @JvmOverloads
    fun lines(
        text: String,
        maxWidth: Float,
        measure: (String) -> Float,
        hyphenChar: String = WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER,
        clamp: Clamp? = null
    ): List<String> {
        // The fold proper (below), then the wave-52 clamp trim over it —
        // identity when no clamp rides, byte-for-byte the wave-41 result.
        val folded = fold(text, maxWidth, measure, hyphenChar)
        // `text` rides along so the clamp sees line N's soft hyphens.
        return clamp?.let { clampLines(folded, it, maxWidth, measure, text, hyphenChar) } ?: folded
    }

    /** The greedy fold itself — see [lines] for the contract. */
    private fun fold(
        text: String,
        maxWidth: Float,
        measure: (String) -> Float,
        hyphenChar: String
    ): List<String> {
        val out = ArrayList<String>()
        // Hard breaks split paragraphs; each wraps independently.
        for (para in text.split("\n")) {
            // Collapse-split into words (Swift's `split(separator:)` omits
            // empty subsequences by default, so the filter is what keeps
            // the two implementations identical on runs of spaces), then
            // decompose each into its display text + explicit ops.
            val words = para.split(" ").filter { it.isNotEmpty() }
                .map { WordBreakOpportunities.analyze(it) }
            if (words.isEmpty()) {
                // An all-space paragraph yields an empty visual line,
                // preserved so the line COUNT stays stable across the
                // rewrite (downstream line-box math counts lines).
                out.add("")
                continue
            }
            // Greedy fold: the first word always opens the line — a line
            // is never empty; an opening word that overflows breaks at
            // its own ops first (CSS 2.1 §9.5 overflow only remains for
            // words with none).
            var line = open(words[0], maxWidth, out, measure, hyphenChar)
            for (i in 1 until words.size) {
                val word = words[i]
                // Candidate = current line + separator + next word,
                // measured as ONE string (see the banner).
                val candidate = "$line ${word.text}"
                if (measure(candidate) <= maxWidth) {
                    line = candidate            // fits → keep accumulating
                    continue
                }
                // Before moving the whole word down, offer its ops the
                // chance to split it ACROSS the break — an op inside the
                // word sits AFTER the space, so it is the greedier break.
                // No overflow fallback here: if no head fits, the plain
                // space break below is the correct earlier opportunity.
                val cut = WordBreakOpportunities.split(
                    word, prefix = "$line ", maxWidth = maxWidth,
                    measure = measure, hyphenChar = hyphenChar)
                if (cut != null) {
                    out.add("$line ${cut.first}")
                    // The tail opens the next line and may need splitting
                    // again (a long word can span three lines in a
                    // narrow box).
                    line = open(cut.second, maxWidth, out, measure, hyphenChar)
                    continue
                }
                out.add(line)                   // commit the full line…
                line = open(word, maxWidth, out, measure, hyphenChar)
            }
            out.add(line)                       // trailing partial line
        }
        return out
    }

    /**
     * Open a line with [word], splitting at its opportunities while the
     * remainder still overflows; each head becomes a committed line and
     * the final remainder is returned as the (open) current line.
     *
     * Identity — returns `word.text` and appends nothing — whenever the
     * word fits or has no usable op: that is the CSS 2.1 §9.5 overflow
     * the wave-38 fold already produced. With ops, the overflow fallback
     * inside [WordBreakOpportunities.split] breaks an unfittable opening
     * word at its FIRST op to minimize the overflow, as Chromium does.
     */
    private fun open(
        word: WordBreakOpportunities.Word,
        maxWidth: Float,
        out: ArrayList<String>,
        measure: (String) -> Float,
        hyphenChar: String
    ): String {
        var rest = word
        // Bounded: every accepted split strictly shortens `rest`, and the
        // loop stops the moment no point is usable.
        while (measure(rest.text) > maxWidth) {
            val cut = WordBreakOpportunities.split(
                rest, prefix = "", maxWidth = maxWidth, measure = measure,
                hyphenChar = hyphenChar, overflowFallback = true) ?: break
            out.add(cut.first)
            rest = cut.second
        }
        return rest.text
    }

    /**
     * Does any committed line overflow [maxWidth] WITH NOWHERE LEFT TO
     * BREAK? This is the rule-B trigger (wave 37 lane W7 named it; the
     * Swift twin carries the same method under the same name).
     *
     * [lines] guarantees a fit for every line EXCEPT one holding a single
     * word wider than [maxWidth], which css-text-3 §5.5 requires to
     * overflow the line box rather than break. Minikin does not know that
     * and character-breaks it at the constraint edge, so the caller must
     * take the run out of the width-constrained layout — see
     * [PreBreakPipeline].
     *
     * BOTH halves matter. This breaker splits on SPACES only, so its
     * "word" is coarser than UAX #14's: `regu-lation` (a real U+002D
     * hyphen-minus, class HY) and `foo<U+200B>bar` are one word here but two
     * break opportunities to the platform breaker — and there the platform
     * is RIGHT, `hyphens` does not govern them. Firing on such a line would
     * stop a wrap the reference performs (measured on iOS
     * css-text/hyphens-none-012, the `regu-lation imple-menta-tion` box:
     * 0.8548 → 0.8431 with the width-only test, restored by this per-line
     * veto). So an overflowing line only counts when it is GENUINELY
     * unbreakable — wave 21's whole-run predicate
     * (`DecorationOps.hasSoftWrapOpportunity`, the shared UAX #14
     * approximation both natives already use) applied per line.
     *
     * [tolerance] absorbs the sub-pixel rounding between the fit test
     * (a float text advance) and the integer width the layout actually
     * proposes; without it a line that measured exactly [maxWidth] could
     * report as overflowing on a half-pixel difference and pull a
     * perfectly fitting run out of the constrained layout.
     */
    /**
     * @param dictionaryHyphenation wave 40 (lane T2) — `hyphens: auto`
     *   with a language tag is live for this run, so §5.3 ADDS the
     *   dictionary's opportunities to the UAX #14 set the veto below
     *   approximates. A line holding a hyphenatable word is therefore no
     *   longer unbreakable and must not trigger rule B — pre-breaking it
     *   would hand the run to `softWrap = false` and suppress the
     *   hyphenation. Lines with no letters to hyphenate (`00000`) still
     *   count, which is what keeps css-text/hyphens-punctuation-001's
     *   digit runs from being desperate-broken. See
     *   [AutoHyphenation.hasDictionaryOpportunity].
     */
    @JvmStatic
    fun hasUnbreakableOverflowingLine(
        lines: List<String>,
        maxWidth: Float,
        tolerance: Float = 0.5f,
        dictionaryHyphenation: Boolean = false,
        measure: (String) -> Float
    ): Boolean = lines.any { line ->
        measure(line) > maxWidth + tolerance &&
            !com.styleconverter.runtime.typography.DecorationOps
                .hasSoftWrapOpportunity(line) &&
            !(dictionaryHyphenation && AutoHyphenation.hasDictionaryOpportunity(line))
    }
}
