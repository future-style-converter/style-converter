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
     * Byte-parallel with `GreedyLineBreaker.lines(text:maxWidth:measure:)`
     * — same fold, same "first word always opens the line" rule, same
     * candidate-measured-as-one-string rule (so the separator's own
     * advance and any kerning across it are included exactly as rendered).
     */
    @JvmStatic
    fun lines(
        text: String,
        maxWidth: Float,
        measure: (String) -> Float
    ): List<String> {
        val out = ArrayList<String>()
        // Hard breaks split paragraphs; each wraps independently.
        for (para in text.split("\n")) {
            // Collapse-split into words. Swift's `split(separator:)` omits
            // empty subsequences by default, so the filter is what keeps
            // the two implementations identical on runs of spaces.
            val words = para.split(" ").filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                // An all-space paragraph yields an empty visual line,
                // preserved so the line COUNT stays stable across the
                // rewrite (downstream line-box math counts lines).
                out.add("")
                continue
            }
            // Greedy fold: the first word always opens the line — a line
            // is never empty, and an overlong first word overflows.
            var line = words[0]
            for (i in 1 until words.size) {
                val word = words[i]
                // Candidate = current line + separator + next word,
                // measured as ONE string (see the banner).
                val candidate = "$line $word"
                if (measure(candidate) <= maxWidth) {
                    line = candidate            // fits → keep accumulating
                } else {
                    out.add(line)               // commit the full line…
                    line = word                 // …and open the next one
                }
            }
            out.add(line)                       // trailing partial line
        }
        return out
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
     *   with a language tag is live for this run, so §6.1 ADDS the
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
