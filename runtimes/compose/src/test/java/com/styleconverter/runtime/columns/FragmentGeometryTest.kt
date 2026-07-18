package com.styleconverter.runtime.columns

// JUnit4 plumbing — matches the suite style used by ColumnsRegistryTest.
import com.styleconverter.runtime.columns.FragmentGeometry.Fragment
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the css-break-3 §4 multicol fragmentation geometry
 * ([FragmentGeometry.fragmentGeometry]) against the wave-10 SHARED S-table —
 * the SAME expected values are pinned on the iOS runtime, so any drift here is
 * a cross-platform divergence, not a refactor.
 *
 * Table inputs (all px): child block-size C, column block-size H, column
 * width W, gap G, used column count N. Expected fragments are
 * {columnIndex, clipRect(l,t,w,h), translate(x,y)} with x_i = i*(W+G) and
 * translate = (+x_i, −i*H) per box-decoration-break: slice (css-break-3 §6).
 */
class FragmentGeometryTest {

    // Shorthand: the table's fixed column geometry (W=150, G=10 → pitch 160).
    private fun run(c: Int, h: Int = 120, w: Int = 150, g: Int = 10, n: Int = 3) =
        FragmentGeometry.fragmentGeometry(
            childBlockSizePx = c, columnBlockSizePx = h,
            columnWidthPx = w, columnGapPx = g, columnCount = n
        )

    @Test
    fun `S1 - C 350 in three 120-tall columns fragments into all three`() {
        // F = ceil(350/120) = 3: bands [0,120) [120,240) [240,350) land in
        // columns 0..2 at inline origins 0/160/320 with block shifts 0/-120/-240.
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 150, 120, 0, 0),
                Fragment(1, 160, 0, 150, 120, 160, -120),
                Fragment(2, 320, 0, 150, 120, 320, -240)
            ),
            run(c = 350)
        )
    }

    @Test
    fun `S2 - C 100 fits in one column - single identity fragment`() {
        // C <= H: no fragmentation — one fragment, zero translate (the layout
        // never even consults this list for the fitting case; identity is the
        // pinned degenerate shape).
        assertEquals(listOf(Fragment(0, 0, 0, 150, 120, 0, 0)), run(c = 100))
    }

    @Test
    fun `S3 - C 240 uses exactly two of three columns`() {
        // F = ceil(240/120) = 2 — the third column stays empty (no fragment).
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 150, 120, 0, 0),
                Fragment(1, 160, 0, 150, 120, 160, -120)
            ),
            run(c = 240)
        )
    }

    @Test
    fun `S4 - C 500 caps at three fragments with the tail clipped`() {
        // ceil(500/120) = 5 but N = 3 caps F (column-fill:auto overflow —
        // Chromium clips the remainder). Fragment 2 shows band [240,360); the
        // [360,500) tail simply has no fragment, so it never draws.
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 150, 120, 0, 0),
                Fragment(1, 160, 0, 150, 120, 160, -120),
                Fragment(2, 320, 0, 150, 120, 320, -240)
            ),
            run(c = 500)
        )
    }

    @Test
    fun `S5 - single column keeps one fragment and clips the tail`() {
        // N = 1: F caps at 1 — band [0,120) shows, [120,350) is clipped by the
        // fragment's own 120-tall clip rect.
        assertEquals(listOf(Fragment(0, 0, 0, 150, 120, 0, 0)), run(c = 350, n = 1))
    }

    @Test
    fun `degenerate inputs are floored defensively`() {
        // Zero/negative column block-size: no fragmentainer — one empty-clip
        // fragment, never a division by zero.
        assertEquals(listOf(Fragment(0, 0, 0, 150, 0, 0, 0)), run(c = 350, h = 0))
        assertEquals(listOf(Fragment(0, 0, 0, 150, 0, 0, 0)), run(c = 350, h = -5))
        // Zero-height child still owns column 0 (F floors at 1).
        assertEquals(listOf(Fragment(0, 0, 0, 150, 120, 0, 0)), run(c = 0))
        // Invalid count/gap/width floor to 1 column / 0 gap / 0 width, matching
        // resolveUsedColumns' css-multicol §3.2 + css-align §8 floors.
        assertEquals(
            listOf(Fragment(0, 0, 0, 0, 120, 0, 0)),
            run(c = 350, w = -10, g = -10, n = 0)
        )
    }

    @Test
    fun `near-max child height does not overflow the ceiling math`() {
        // ceil is computed in Long space: C near Int.MAX_VALUE must still cap
        // cleanly at N fragments instead of wrapping negative.
        assertEquals(3, run(c = Int.MAX_VALUE).size)
    }

    @Test
    fun `extractor flags vertical writing modes so the layout can bail`() {
        // The fragmentation pass is horizontal-tb only (vertical is
        // blocked-platform): the extractor must surface the WritingMode IR
        // property so MultiColumnLayout bails + logs once.
        val vertical = MultiColumnExtractor.extractMultiColumnConfig(
            listOf("ColumnCount" to JsonPrimitive(3), "WritingMode" to JsonPrimitive("vertical-rl"))
        )
        assertTrue("vertical-rl must set the bail flag", vertical.verticalWritingMode)
        // horizontal-tb (and an absent WritingMode) keep fragmentation live.
        val horizontal = MultiColumnExtractor.extractMultiColumnConfig(
            listOf("ColumnCount" to JsonPrimitive(3), "WritingMode" to JsonPrimitive("horizontal-tb"))
        )
        assertFalse("horizontal-tb must NOT bail", horizontal.verticalWritingMode)
        val absent = MultiColumnExtractor.extractMultiColumnConfig(
            listOf("ColumnCount" to JsonPrimitive(3))
        )
        assertFalse("absent writing-mode defaults to horizontal-tb", absent.verticalWritingMode)
    }

    // ------------------------------------------------------------------
    // Draw-wiring source scan — the paint-level pin feasible without
    // Robolectric (per the wave-10 lane contract): asserts the applier's
    // fragmentation branch is actually wired as the clip+translate replay of
    // ONE laid-out child, and that the bails log instead of falling through.
    // ------------------------------------------------------------------

    /** The applier source, located by walking up from the test working dir. */
    private val applierSource: String by lazy {
        // Gradle runs this suite from apps/android-harness (or a module dir) —
        // walk up to the repo root, identified by the applier file itself
        // (same walk-up pattern as SchemaConformanceTest).
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MultiColumnApplier.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root ($rel) not found above ${System.getProperty("user.dir")}" }, rel)
            .readText()
    }

    @Test
    fun `applier replays one child draw per fragment via clip plus translate`() {
        // The draw pass must be a drawWithContent replay (Compose cannot place
        // one Placeable twice — the replay IS the fragmentation mechanism)…
        assertTrue("drawWithContent replay missing", applierSource.contains(".drawWithContent {"))
        // …clipped to the fragment's column rect…
        assertTrue("fragment clipRect missing", applierSource.contains("fragment.clipLeft.toFloat()"))
        // …translated by (+x_i, −i*H) from the pinned geometry…
        assertTrue("fragment translate missing", applierSource.contains("fragment.translateX.toFloat()"))
        // …with the geometry sourced from the shared pure function.
        assertTrue(
            "layout must consume FragmentGeometry.fragmentGeometry",
            applierSource.contains("FragmentGeometry.fragmentGeometry(")
        )
    }

    @Test
    fun `overflowing child is measured once with unbounded block-size`() {
        // box-decoration-break: slice requires the child to lay out (and
        // paint) as ONE continuous C-tall box — the branch must lift the
        // height cap for that single measure.
        assertTrue(
            "fragmentation measure must unbind maxHeight",
            applierSource.contains("maxHeight = Constraints.Infinity")
        )
        // And the container must place that child exactly once at the origin
        // (all visible copies come from the draw replay).
        assertTrue("single origin placement missing", applierSource.contains("placeable.place(0, 0)"))
    }

    @Test
    fun `bails are logged once instead of silently falling through`() {
        // Vertical writing-mode bail (blocked-platform) must announce itself…
        assertTrue(
            "vertical writing-mode bail must log",
            applierSource.contains("vertical writing-mode (blocked-platform)")
        )
        // …as must the out-of-contract multi-child overflow case…
        assertTrue(
            "multi-child bail must log",
            applierSource.contains("multi-child container")
        )
        // …both through the shared once-per-reason logger.
        assertTrue(
            "once-per-reason logger missing",
            applierSource.contains("fun logFragmentationFallbackOnce(reason: String)")
        )
    }
}
