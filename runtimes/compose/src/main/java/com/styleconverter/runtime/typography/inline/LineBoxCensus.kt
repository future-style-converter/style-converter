// typography/inline — wave 50 (lane B9): the CROSS-BLOCK LINE-BOX CENSUS
// behind the Compose block-level line-clamp cap. Twin of the wave-46 iOS
// `StyleEngine/scrolling/LineClampCensus.swift` (the Swift tree splits its
// census across scrolling/LineClampCensus*.swift; on Compose the census
// lives beside the other inline-content readers because `scrolling/` here
// owns only the cap itself — same model, different file home).
//
// ## The defect it closes (measured, wave49-final)
// The wave-41 Compose cap is UNIFORM: `line-clamp: <n>` becomes a height
// budget of N × the CLAMP ROOT's own line box. That is exact only while
// every line box in the container is the root's. css-overflow-4 §5.2
// counts LINE BOXES, and a line box is as tall as the run that produced it
// (CSS 2.1 §10.8) — so a `font: 24px/48px` block child inside a
// `16px/32px` clamp root makes the count and the height disagree:
//   • line-clamp-006 (clamp 5): the ref closes at 32+32+48+48+32 = 192px,
//     the uniform cap says 5 × 32 = 160 — the wave49-final Android capture
//     stops after "Line 4" where the ref paints "Line 5…" (android-ref
//     0.9446 f; iOS, which has this census, 0.9822 P);
//   • line-clamp-007 (clamp 3): the `overflow: auto` child is an
//     INDEPENDENT formatting context whose lines are not this container's
//     (css-overflow-3 §3), so the ref keeps the whole child box and closes
//     after "Line 5" at 192px — Android's 96px cap cuts inside the child
//     (android-ref 0.9409 f; iOS 0.9822 P);
//   • line-clamp-005 (clamp 3): ref 32+32+48 = 112px vs the uniform 96 —
//     the Android yellow box is visibly 16px short (android-ref 0.9615 P,
//     a passing cell that is nonetheless wrong).
//
// ## The contract, and why it can only ever help
// The census walks the container's in-flow content in DOCUMENT ORDER,
// accumulating each run's own line boxes until the Nth, and returns that
// bottom edge. It bails to [Verdict.Unprovable] the moment a run's line
// count cannot be PROVEN — an under-count would clip a visible line, which
// is worse than the drift the census removes — and the cap resolver maps
// BOTH non-capped verdicts back onto the wave-41 uniform number, so every
// container the census cannot prove keeps its calibrated byte-identical
// cap.
//
// ## TWO deliberate divergences from the iOS twin (this list is exhaustive)
// This header claimed ONE until wave-50 lane F1 (skeptic S6) found the
// second. Both are stated here AND at the line of code that carries them.
//
// (a) THE UNPROVABLE VERDICT. Swift's `.unbounded` removes the cap
//     entirely; Compose maps it back onto the wave-41 uniform number
//     instead (`LineClampCapResolve.resolveCapPx`). 24 of the 38
//     fixed-count clamp ROOTS in the 34 wave49-final per-test IR documents
//     that carry one land on an unprovable soft-wrapping text run, and all
//     but block-ellipsis-032 PASS on Android today — removing their cap
//     would also drop the block-axis ink clip that makes the discarded
//     lines unpaintable (css-overflow-4 §5.3).
//
// (b) `<br>`. `LineBoxCensusRuns.childRun` returns a zero-line,
//     zero-height run for `tag == "br" || role == "line-break"`, because a
//     forced break generates no box (HTML §4.5.27) and the converter
//     nonetheless stamps a measured `height` (0 or 20px in the corpus) on
//     it. Swift's `LineClampCensusRuns.childRun` has NO such arm, so that
//     stamped height falls through to its explicit-height branch and a
//     `<br>` is budgeted there as a MONOLITHIC box. The two platforms
//     therefore budget a br-bearing clamp root differently.
//     CORPUS IMPACT UNMEASURED: wave 50 ran no device gate, and the S3
//     mutation log records that deleting this arm moves ZERO corpus caps
//     today (tools/titan/results/wave50-S3/_note.md — the branch is latent
//     on wave49-final), which is why neither side was changed to match the
//     other this wave. The carriers to measure first are
//     block-ellipsis-002 / -004 / -005, whose `<br>` members carry the
//     20px stamp.
//
// Pure Kotlin (no Compose, no Android) so the JVM suite pins every rule on
// the verbatim wave49-final per-test IR without a device.
package com.styleconverter.runtime.typography.inline

/**
 * One run of a clamp container's in-flow content: how tall ITS line boxes
 * are and how many it produces.
 */
data class LineBoxRun(
    /** The height of one line box this run produces, in px. */
    val lineBoxPx: Float,
    /**
     * The number of line boxes the run produces, or null when the count is
     * not provable without a layout pass (a soft-wrapping run whose wrap
     * width this reader does not know).
     */
    val exactLines: Int?,
    /**
     * Vertical padding + border + margin ABOVE a block child's FIRST line
     * box (0 for the container's own anonymous text runs), or null when the
     * child declares a band in a shape this reader cannot resolve. The
     * box-model bands sit above a block child's line boxes (CSS 2.1 §8.1),
     * so the census must budget them.
     */
    val leadingBandPx: Float? = 0f,
    /**
     * Non-null: the run is an UNFRAGMENTABLE box of this full height that
     * produces NO line boxes OF THIS CONTAINER. Two shapes qualify — a
     * child establishing an independent formatting context (a non-`visible`
     * overflow, css-overflow-3 §3: css-overflow-4 §5.3 clamps the
     * container's OWN line boxes, and the clamp cannot enter another
     * formatting context — line-clamp-007's `overflow: auto` child stays
     * whole and "Line 5" after it is the 3rd line box), and a box with a
     * definite `height` (the converter's measured `<br>`/`<wbr>` boxes and
     * block-ellipsis-009's `height: 50px` child).
     */
    val monolithicPx: Float? = null,
)

/**
 * The pure census: line boxes in, the clamp's closing edge out.
 */
object LineBoxCensus {

    /** What the census could prove about a clamp of N over a run list. */
    sealed interface Verdict {
        /**
         * The Nth line box was reached: [contentPx] is the container's
         * CONTENT-box height at its bottom edge (the caller adds the
         * container's own padding + border band, because the Compose cap
         * node sits at the step-7 overflow position with those bands
         * nested inside it — see LineClampCap.capPx).
         */
        data class Capped(val contentPx: Float) : Verdict

        /**
         * Every run is proven and together they hold FEWER than N line
         * boxes: nothing is discarded. Distinct from [Unprovable] because
         * it is a different FACT (and a different breadcrumb), even though
         * the cap resolver treats both the same way — Compose's
         * `cappedHeight` is already a no-op on content that fits.
         */
        object Short : Verdict

        /**
         * A run's line count (or its band) is not provable, so the Nth line
         * box's edge cannot be known. The resolver keeps the wave-41
         * uniform cap: a guessed edge would clip a visible line.
         */
        object Unprovable : Verdict
    }

    /**
     * Walk [runs] in document order, taking line boxes until [lines] are
     * taken (css-overflow-4 §5.3: the clamp keeps the container's first N
     * line boxes and discards the rest).
     */
    fun verdict(lines: Int, runs: List<LineBoxRun>): Verdict {
        // Defensive guard — the grammar is <integer [1,∞]> (§5.1); a sub-1
        // count never reaches here (LineClampCap.linesCount gates it).
        if (lines < 1) return Verdict.Unprovable
        // How many line boxes the clamp still has to give, and the height
        // consumed so far.
        var remaining = lines
        var height = 0f
        for (run in runs) {
            // A monolithic box is kept whole and yields no line box of this
            // container: add its full height and move on.
            val monolithic = run.monolithicPx
            if (monolithic != null) {
                height += monolithic
                continue
            }
            // An unprovable count (or an unresolvable band) before the Nth
            // box: the whole verdict is unprovable — never guess an edge.
            val n = run.exactLines ?: return Verdict.Unprovable
            val band = run.leadingBandPx ?: return Verdict.Unprovable
            // A glyph-less run produces no line box, so it costs nothing —
            // and, unlike a monolithic box, no height either.
            if (n == 0) continue
            // The child's top band sits above its first line box.
            height += band
            // Take what this run contributes, up to what the clamp has left.
            val taken = minOf(n, remaining)
            height += taken * run.lineBoxPx
            remaining -= taken
            // The Nth line box closes the container's content box.
            if (remaining == 0) return Verdict.Capped(height)
        }
        // Ran out of content before the clamp: nothing is discarded.
        return Verdict.Short
    }

    /**
     * The number of line boxes a text run produces under a `white-space`
     * keyword, or null when soft wrapping could add lines this reader
     * cannot count without a layout pass. Byte-parallel to the Swift twin
     * (`LineClampCensus.exactLineCount`).
     *
     * Keyword spellings: the live wire ships the serializer's enum names
     * (`PRE_WRAP`), which the callers lowercase; the hyphen forms cover
     * CSS-keyword-shaped documents. Both are normalised here.
     *
     * css-text-3 §3 (the `white-space` keywords), §4.1.3 (segment-break
     * transformation) and §5 (line breaking / soft wrap opportunities):
     *   • `pre` / `nowrap` never soft-wrap → `pre` keeps each segment as
     *     one line box, `nowrap` collapses breaks to ONE line;
     *   • `pre-wrap` / `pre-line` / `break-spaces` preserve breaks but wrap
     *     at spaces → provable only when no segment holds a space or tab;
     *   • `normal` collapses breaks INTO spaces (wrap opportunities) →
     *     provable only for a single whitespace-free word.
     * An empty run produces no line box (0).
     */
    fun exactLineCount(text: String?, whiteSpace: String?): Int? {
        // No glyphs → no line box, under every keyword.
        if (text.isNullOrEmpty()) return 0
        // One normalisation for both wire spellings (PRE_WRAP / pre-wrap).
        val ws = (whiteSpace ?: "normal").lowercase().replace('_', '-')
        // Hard segment breaks: one line box per segment (an empty trailing
        // segment is still a line box, so this counts separators + 1).
        val hardLines = text.count { it == '\n' } + 1
        // Any soft-wrap opportunity inside a segment → not provable.
        val hasSpace = text.contains(' ') || text.contains('\t')
        return when (ws) {
            // No soft wrapping at all: the segments ARE the line boxes.
            "pre" -> hardLines
            // No soft wrapping, breaks collapsed away: exactly one line.
            "nowrap" -> 1
            // Breaks preserved, spaces wrap → provable without spaces.
            "pre-wrap", "pre-line", "break-spaces" -> if (hasSpace) null else hardLines
            // `normal` (and any keyword this reader does not model): breaks
            // become spaces, so anything with whitespace can wrap.
            else -> if (hasSpace || text.contains('\n')) null else 1
        }
    }
}
