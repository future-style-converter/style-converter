package com.styleconverter.runtime.columns

// JUnit4 plumbing — the suite style of FragmentGeometryTest.
import com.styleconverter.runtime.columns.FragmentGeometry.Fragment
import com.styleconverter.runtime.columns.MulticolCloneGeometry.Bands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the css-break-3 §5.2 `box-decoration-break: clone` fragment
 * geometry ([MulticolCloneGeometry.cloneFragments]) against the wave-46
 * SHARED K-table — the SAME rows are pinned on the iOS runtime
 * (MulticolCloneGeometryTests.swift), so any drift here is a cross-
 * platform divergence, not a refactor.
 *
 * Inputs (px): unfragmented border-box block-size C, column block-size H,
 * bands (start, end, endBand), column width W, gap G, used count N.
 * Expected fragments are {columnIndex, clipRect(l,t,w,h), translate(x,y)}
 * with x_i = i*(W+G); a full clone fragment translates (x_i, 0) — the
 * same H-tall box again — and a short last fragment is two entries.
 */
class MulticolCloneGeometryTest {

    /** K1 — WPT css-break borders-008: three 100px circles. */
    @Test
    fun `K1 - borders-008 - 240px content with 10px borders clones into three full 100px fragments`() {
        // C = 240 + 10 + 10 = 260, H = 100, capacity = 80 → F = ceil(240/80) = 3;
        // every fragment is a full 100px box replayed with NO block shift.
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 100, 100, 0, 0),
                Fragment(1, 110, 0, 100, 100, 110, 0),
                Fragment(2, 220, 0, 100, 100, 220, 0)
            ),
            MulticolCloneGeometry.cloneFragments(
                childBlockSizePx = 260, columnBlockSizePx = 100,
                bands = Bands(10, 10, 50),
                columnWidthPx = 100, columnGapPx = 10, columnCount = 3
            )
        )
    }

    /** K2 — WPT css-break background-image-007: one no-repeat cat per column. */
    @Test
    fun `K2 - background-image-007 - 600px bandless child clones into four 150px fragments`() {
        // No bands: capacity = H = 150 → F = 4, all full, gap 0 → pitch 150.
        assertEquals(
            List(4) { i -> Fragment(i, i * 150, 0, 150, 150, i * 150, 0) },
            MulticolCloneGeometry.cloneFragments(
                childBlockSizePx = 600, columnBlockSizePx = 150,
                bands = Bands(0, 0, 0),
                columnWidthPx = 150, columnGapPx = 0, columnCount = 4
            )
        )
    }

    /** K3 — WPT css-break background-image-004 (green today, must stay green). */
    @Test
    fun `K3 - background-image-004 - 120px content inside 20px bands clones into two full fragments`() {
        // C = 120 + 20 + 20 = 160, H = 100, capacity = 60 → F = 2, both full.
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 50, 100, 0, 0),
                Fragment(1, 50, 0, 50, 100, 50, 0)
            ),
            MulticolCloneGeometry.cloneFragments(
                childBlockSizePx = 160, columnBlockSizePx = 100,
                bands = Bands(20, 20, 20),
                columnWidthPx = 50, columnGapPx = 0, columnCount = 2
            )
        )
    }

    /** K4 — a SHORT last fragment: top rows + the block-end band from the paint's bottom. */
    @Test
    fun `K4 - short last fragment is its top rows plus the block-end band shifted up`() {
        // C = 250 + 20 = 270, H = 100, capacity = 80 → F = 4; the last holds
        // 250 − 240 = 10px of content → a 30px box: 20 top rows (translate 0)
        // and the 10px end band taken from the bottom of the 100px paint
        // (translate 30 − 100 = −70).
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 100, 100, 0, 0),
                Fragment(1, 110, 0, 100, 100, 110, 0),
                Fragment(2, 220, 0, 100, 100, 220, 0),
                Fragment(3, 330, 0, 100, 20, 330, 0),
                Fragment(3, 330, 20, 100, 10, 330, -70)
            ),
            MulticolCloneGeometry.cloneFragments(
                childBlockSizePx = 270, columnBlockSizePx = 100,
                bands = Bands(10, 10, 10),
                columnWidthPx = 100, columnGapPx = 10, columnCount = 4
            )
        )
    }

    /** K5 — the end band widens to a bottom corner radius so the arcs rejoin. */
    @Test
    fun `K5 - bottom corner radius widens the short fragment's end band`() {
        // Same as K4 but the bottom corners have a 25px vertical radius: the
        // 30px last box takes 25 rows from the paint's bottom, 5 from its top.
        val frags = MulticolCloneGeometry.cloneFragments(
            childBlockSizePx = 270, columnBlockSizePx = 100,
            bands = Bands(10, 10, 25),
            columnWidthPx = 100, columnGapPx = 10, columnCount = 4
        )!!
        assertEquals(Fragment(3, 330, 0, 100, 5, 330, 0), frags[3])
        assertEquals(Fragment(3, 330, 5, 100, 25, 330, -70), frags[4])
        // The band never exceeds the short box itself.
        val tall = MulticolCloneGeometry.cloneFragments(
            childBlockSizePx = 270, columnBlockSizePx = 100,
            bands = Bands(10, 10, 90),
            columnWidthPx = 100, columnGapPx = 10, columnCount = 4
        )!!
        assertEquals(listOf(Fragment(3, 330, 0, 100, 30, 330, -70)), tall.drop(3))
    }

    /** K6 — no content capacity (bands ≥ H) or no fragmentainer: null, caller slices. */
    @Test
    fun `K6 - bands filling the fragmentainer or a non-positive H yield null`() {
        assertNull(MulticolCloneGeometry.cloneFragments(
            childBlockSizePx = 260, columnBlockSizePx = 20, bands = Bands(10, 10, 10),
            columnWidthPx = 100, columnGapPx = 10, columnCount = 3))
        assertNull(MulticolCloneGeometry.cloneFragments(
            childBlockSizePx = 260, columnBlockSizePx = 0, bands = Bands(0, 0, 0),
            columnWidthPx = 100, columnGapPx = 10, columnCount = 3))
    }

    /** K7 — the N cap: overflow fragments beyond the last column are never listed (slice parity). */
    @Test
    fun `K7 - the used column count caps the list exactly like the slice table`() {
        // 600/150 = 4 fragments wanted, N = 2 → two FULL fragments (the
        // capped remainder exceeds the capacity, so no short box appears).
        assertEquals(
            listOf(Fragment(0, 0, 0, 150, 150, 0, 0), Fragment(1, 150, 0, 150, 150, 150, 0)),
            MulticolCloneGeometry.cloneFragments(
                childBlockSizePx = 600, columnBlockSizePx = 150, bands = Bands(0, 0, 0),
                columnWidthPx = 150, columnGapPx = 0, columnCount = 2)
        )
    }

    /** K8 — an empty clone box (content 0) still owns column 0 as a bands-only short box. */
    @Test
    fun `K8 - zero content is one short fragment of just the bands`() {
        // C = bands = 20, H = 100 → F = 1, box 20px: 10 top rows + 10 end rows.
        assertEquals(
            listOf(Fragment(0, 0, 0, 100, 10, 0, 0), Fragment(0, 0, 10, 100, 10, 0, -80)),
            MulticolCloneGeometry.cloneFragments(
                childBlockSizePx = 20, columnBlockSizePx = 100, bands = Bands(10, 10, 10),
                columnWidthPx = 100, columnGapPx = 10, columnCount = 3)
        )
    }

    /** K9 — defensive floors: negative bands/gap/width and a zero count never break the math. */
    @Test
    fun `K9 - negative inputs floor to zero and the count to one`() {
        assertEquals(
            listOf(Fragment(0, 0, 0, 0, 100, 0, 0)),
            MulticolCloneGeometry.cloneFragments(
                childBlockSizePx = 260, columnBlockSizePx = 100, bands = Bands(-5, -5, -5),
                columnWidthPx = -1, columnGapPx = -3, columnCount = 0)
        )
    }
}
