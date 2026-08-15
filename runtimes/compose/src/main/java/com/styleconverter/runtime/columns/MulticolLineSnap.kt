// Pure line-box-snapped fragment geometry for css-break-3 §4 multicol
// fragmentation (wave-42 lane W4) — no Compose imports on purpose: the JUnit
// suite pins this math on the plain JVM and the IDENTICAL LS-table is pinned
// by the iOS twin (runtimes/swiftui .../columns/MulticolLineSnap.swift), so
// both natives snap fragments to the same line boundaries byte-identically.
package com.styleconverter.runtime.columns

/**
 * Class-B breakpoint snapping for the wave-10 clip+translate replay.
 *
 * ## The defect this fixes (wave-41 evidence, discard-multicol-001/002/004)
 * The raw [FragmentGeometry] slice translates fragment i by −i·H — correct
 * for content that may break anywhere, but a TEXT child is a stack of line
 * boxes and css-break-3 §4 only allows class-B breakpoints BETWEEN line
 * boxes ("in the middle of a line box" is never a break opportunity). When
 * the column block-size H is not an exact multiple of the platform line
 * height L (Android: H = 2lh resolved ≈ 29px vs a ~17px rendered pitch),
 * the raw slice cuts glyphs in half and drifts further down every column —
 * the exact torn-ink rendering in the wave-41 Android captures.
 *
 * ## The snapped model
 * Chromium packs k = ⌊H / L⌋ WHOLE lines per column and starts every column
 * flush at a line-box top. The snapped fragment list reproduces that:
 * fragment i clips column i to the k-line band (min(H, k·L) tall) and
 * translates the continuous child paint by −i·k·L — always a line-box
 * boundary, never mid-glyph.
 *
 * ## When it declines (returns null → callers keep the raw slice)
 * The child must actually LOOK like a uniform line stack; anything else is
 * not text and must keep the general geometry:
 *  - no line probe available (L == null: dark stage, or the intrinsic
 *    channel refused) or degenerate (L ≤ 0, H ≤ 0);
 *  - L > H: a "line" taller than the column is a block child, not a line
 *    stack (the floats-clear .container probes its 250px float there);
 *  - fewer than 2 lines (a single line can never tear mid-line);
 *  - C is not an integral line count within ±1px per line (mixed-content
 *    children whose height is not a line grid keep the raw slice).
 */
object MulticolLineSnap {

    /**
     * The largest width `androidx.compose.ui.unit.Constraints` can carry at
     * all, when the other axis needs no more than 13 bits (the default
     * `Constraints(maxWidth = w)` shape every inherited `minIntrinsicHeight`
     * builds: minHeight = 0, maxHeight = Infinity ⇒ height packs into 13).
     *
     * Read off the shipped bytecode of androidx.compose.ui:ui-unit 1.11.4,
     * `ConstraintsKt.bitsNeedForSizeUnchecked(int)`: <8191 → 13 bits,
     * <32767 → 15, <65535 → 16, <262143 → 18, else 255 (the poison value);
     * `createConstraints` throws as soon as widthBits + heightBits > 31. So
     * 262142 (18 + 13 = 31) packs and 262143 does not.
     *
     * Named here — not inlined at the clamp — because it is the boundary the
     * whole probe path must respect, and [MulticolLineProbeBoundsTest] pins
     * it against the real `Constraints` factory.
     */
    const val MAX_CONSTRAINT_WIDTH_PX = 262_142

    /**
     * The inline size the line-box probe measures at: wide enough that any
     * fixture-scale text lays out as ONE line (its minIntrinsicHeight then
     * IS the single line-box height L), and REPRESENTABLE in Compose
     * `Constraints` with room to spare (65534 needs 16 width bits, leaving
     * 15 for the other axis — see [MAX_CONSTRAINT_WIDTH_PX]).
     *
     * Wave-42 skeptic S2 (executed repro, pinned by
     * [MulticolLineProbeBoundsTest]): this constant used to be 1_000_000 —
     * ~3.8x the LARGEST width Constraints can represent. The probe query
     * therefore raised IllegalArgumentException out of Constraints packing,
     * which `IntrinsicChannel.probe` deliberately does NOT catch (it narrows
     * to IllegalStateException so that real packing bugs keep propagating),
     * and an unguarded throw inside a measure pass kills the entire capture
     * composition — the wave-39 crash-loop class, twice paid for.
     */
    const val LINE_PROBE_WIDTH_PX = 65_534

    /**
     * Clamp a probe width into the legal `Constraints` range — the defense
     * in depth the S2 repro bought: no caller may hand the intrinsic channel
     * a width the platform cannot pack, whatever this file's constant says
     * tomorrow.
     *
     * Pure (Int in, Int out) so the JVM suite pins it without Robolectric.
     *
     * @param requestedPx the width a call site wants to probe at.
     * @return the same width when representable, else the boundary
     *   [MAX_CONSTRAINT_WIDTH_PX]; negatives floor to 0 (a negative maxWidth
     *   is not a legal Constraints dimension either).
     */
    fun clampedProbeWidthPx(requestedPx: Int): Int =
        requestedPx.coerceIn(0, MAX_CONSTRAINT_WIDTH_PX)

    /**
     * The snapped fragment list, or null when the line-stack heuristic
     * declines (see class doc) — callers then fall back to
     * [FragmentGeometry.fragmentGeometry] unchanged.
     *
     * @param childBlockSizePx C — the child's laid-out block-size at column
     *   width (the measured placeable height, same input as the raw slice).
     * @param columnBlockSizePx H — the definite column block-size.
     * @param lineBoxPx L — the child's single-line-box height from the
     *   [LINE_PROBE_WIDTH_PX] intrinsic probe; null = no probe answer.
     * @param columnWidthPx W — the §3.4 used column width.
     * @param columnGapPx G — the used column-gap.
     * @param columnCount N — the §3.4 used column count (the overflow cap:
     *   lines past column N-1 are clipped away, same rule as the raw slice
     *   — which for `continue: discard` containers IS the discard).
     */
    fun snappedFragments(
        childBlockSizePx: Int,
        columnBlockSizePx: Int,
        lineBoxPx: Int?,
        columnWidthPx: Int,
        columnGapPx: Int,
        columnCount: Int
    ): List<FragmentGeometry.Fragment>? {
        // No probe answer → not knowably a line stack → raw slice.
        val l = lineBoxPx ?: return null
        // Degenerate geometry never snaps (mirrors FragmentGeometry's floors).
        if (l <= 0 || columnBlockSizePx <= 0) return null
        // A "line" taller than the column is a block box, not a line stack —
        // the raw slice (which fills every column) is the honest geometry.
        if (l > columnBlockSizePx) return null
        // Rounded line count n = round(C / L), in Long space so a huge C
        // cannot wrap the doubling below.
        val c = maxOf(0, childBlockSizePx).toLong()
        val n = ((2 * c + l) / (2L * l)).toInt()
        // A single line (or empty child) can never tear mid-line — decline
        // so the identity-shaped raw geometry keeps owning that case.
        if (n < 2) return null
        // Line-grid check: C must be n whole lines within ±1px per line
        // (platform text rounds each line box independently); a child whose
        // height is NOT a line grid is mixed content → raw slice.
        if (kotlin.math.abs(c - n.toLong() * l) > n) return null
        // Lines per column: ⌊H / L⌋ — ≥ 1 by the L ≤ H gate above.
        val k = columnBlockSizePx / l
        // Defensive floors mirroring FragmentGeometry (count ≥ 1, gap/width ≥ 0).
        val count = maxOf(1, columnCount)
        val w = maxOf(0, columnWidthPx)
        val g = maxOf(0, columnGapPx)
        // Fragment count: ⌈n / k⌉ columns of k lines, capped at N (the
        // column-fill:auto overflow clip, identical to the raw slice's cap).
        val f = minOf(count, (n + k - 1) / k)
        // Each fragment's visible band is exactly its k whole lines — the
        // H − k·L remainder stays EMPTY (the next line moved whole to the
        // next column), which is the visible difference from the raw slice.
        val band = minOf(columnBlockSizePx, k * l)
        // Build fragment i: clip = column i's rect capped to the k-line
        // band; translate = (+x_i, −i·k·L) — the block shift is a line-box
        // boundary by construction (the snap itself).
        return List(f) { i ->
            // Inline origin of column i — gaps sit BETWEEN columns (§3).
            val x = i * (w + g)
            // The LS-table row: snapped clip + snapped translate.
            FragmentGeometry.Fragment(i, x, 0, w, band, x, -i * k * l)
        }
    }
}
