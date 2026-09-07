package com.styleconverter.runtime.columns

// Plain-JVM JUnit4 — same suite style as FragmentGeometryTest.
import com.styleconverter.runtime.columns.FragmentGeometry.Fragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The SHARED line-snap pin table (wave-42 lane W4) — pins [MulticolLineSnap]
 * exactly; the iOS twin (runtimes/swiftui .../MulticolLineSnapTests.swift)
 * carries the SAME LS rows byte-for-byte, so any change must land on both
 * platforms in the same commit (native-pair parity gate).
 *
 * LS1 is pinned against the LIVE wave41-final Android capture geometry of
 * css-overflow discard-multicol-001 (tools/titan/runs/wave41-final): the
 * container resolved `height: 2lh` to ~29px while the text renders ~17px
 * lines — the raw −i·H slice put column 2's band mid-glyph 5px down and
 * column 3's 10px down (the measured drift). The snap emits whole-line
 * bands instead.
 */
class MulticolLineSnapTest {

    /** LS1 — discard-multicol-001 shape: 7×17px lines, H=29 → 1 line per column, 3 columns. */
    @Test
    fun `LS1 seven 17px lines in 29px columns snap to one whole line per column`() {
        // C=119 (7·17), H=29, L=17, W=66, G=7, N=3 — the live capture's
        // geometry. k=⌊29/17⌋=1 line per column; F=min(3,⌈7/1⌉)=3; each
        // band is exactly one line (17px), translated by whole lines.
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 66, 17, 0, 0),
                Fragment(1, 73, 0, 66, 17, 73, -17),
                Fragment(2, 146, 0, 66, 17, 146, -34)
            ),
            MulticolLineSnap.snappedFragments(119, 29, 17, 66, 7, 3)
        )
    }

    /** LS2 — H an exact line multiple: the snap coincides with the raw slice. */
    @Test
    fun `LS2 line-multiple column height makes snap and raw slice agree`() {
        // C=96 (6·16), H=32 (2 lines exactly) → k=2, F=3, band=32,
        // translate −i·32 — byte-identical to the raw −i·H geometry, so
        // fixing the lh resolution upstream (H → a true line multiple)
        // makes the snap a no-op relative to raw. Pinned as the identity.
        val snapped = MulticolLineSnap.snappedFragments(96, 32, 16, 60, 4, 3)
        assertEquals(FragmentGeometry.fragmentGeometry(96, 32, 60, 4, 3), snapped)
    }

    /** LS3 — a "line" taller than the column is a block child, not a line stack. */
    @Test
    fun `LS3 line probe taller than the column declines`() {
        // The floats-clear .container shape: the single-line probe answers
        // its 250px float stack, H=100 → not a line stack → raw slice.
        assertNull(MulticolLineSnap.snappedFragments(500, 100, 250, 100, 0, 3))
    }

    /** LS4 — no probe answer (dark stage / refused channel) declines. */
    @Test
    fun `LS4 null line probe declines`() {
        assertNull(MulticolLineSnap.snappedFragments(119, 29, null, 66, 7, 3))
    }

    /** LS5 — a single line can never tear mid-line: decline (raw identity owns it). */
    @Test
    fun `LS5 single-line child declines`() {
        // C=17 = one 17px line → n=1 < 2 → null.
        assertNull(MulticolLineSnap.snappedFragments(17, 29, 17, 66, 7, 3))
    }

    /** LS6 — C off the line grid beyond ±1px/line is mixed content: decline. */
    @Test
    fun `LS6 non-line-grid child height declines`() {
        // C=110 vs n=6 lines of 17 = 102: |110−102| = 8 > n=6 → null.
        assertNull(MulticolLineSnap.snappedFragments(110, 29, 17, 66, 7, 3))
    }

    /** LS7 — the overflow cap: more line chunks than columns clip at N. */
    @Test
    fun `LS7 fragment count caps at the used column count`() {
        // 10 lines of 10px, H=15 → k=1 → ⌈10/1⌉=10 chunks, capped at N=3
        // (the column-fill:auto overflow clip — for `continue: discard`
        // containers this cap IS the discard, css-overflow-4 §5.3).
        assertEquals(3, MulticolLineSnap.snappedFragments(100, 15, 10, 50, 5, 3)!!.size)
    }

    /** LS8 — the k-line band leaves the H − k·L remainder empty. */
    @Test
    fun `LS8 band height is the whole-line extent not the column height`() {
        // H=40, L=16 → k=2 lines, band=32: the trailing 8px stay empty
        // because the third line moved WHOLE to the next column (class-B
        // breakpoints — css-break-3 §4 never cuts a line box).
        val frags = MulticolLineSnap.snappedFragments(96, 40, 16, 60, 0, 3)!!
        assertEquals(32, frags[0].clipHeight)
        assertEquals(-32, frags[1].translateY)
        // 6 lines at 2 per column fill exactly 3 columns.
        assertEquals(3, frags.size)
    }

    /** LS9 — ±1px/line rounding tolerance accepts real platform line grids. */
    @Test
    fun `LS9 one-pixel-per-line rounding still snaps`() {
        // 5 lines whose true pitch rounds unevenly: C=87 vs 5·17=85 →
        // |87−85| = 2 ≤ n=5 → snaps, k=1, F=min(3,5)=3.
        assertEquals(3, MulticolLineSnap.snappedFragments(87, 29, 17, 66, 7, 3)!!.size)
    }
}
