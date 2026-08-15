package com.styleconverter.runtime.columns

import androidx.compose.ui.unit.Constraints
import com.styleconverter.runtime.layout.IntrinsicChannel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WAVE-42 SKEPTIC S2 — the line-box probe width must be REPRESENTABLE.
 *
 * (Was `ZZSkepticS2LineProbeTest`, the skeptic's executed refutation of
 * lane W4's probe width; kept as the standing regression pin now that the
 * defect is fixed.)
 *
 * ## The defect, as the skeptic proved it
 * [MulticolLineSnap.LINE_PROBE_WIDTH_PX] was 1_000_000 — ~3.8x the LARGEST
 * width `androidx.compose.ui.unit.Constraints` can represent at all.
 *
 * Shipped bytecode of androidx.compose.ui:ui-unit 1.11.4 (the BOM this
 * module builds against), `ConstraintsKt.bitsNeedForSizeUnchecked(int)`:
 *
 *     size <   8191 -> 13 bits
 *     size <  32767 -> 15
 *     size <  65535 -> 16
 *     size < 262143 -> 18
 *     else          -> 255      <-- the poison value
 *
 * and `createConstraints` throws `throwInvalidConstraintException` as soon
 * as widthBits + heightBits > 31. `NodeMeasuringIntrinsics.minHeight$ui`
 * — the default `minIntrinsicHeight(width)` every LayoutModifierNode and
 * MeasurePolicy inherits — builds exactly `Constraints(maxWidth = width)`
 * from the queried width (bytecode: iconst_0 / iload width / iconst_0 /
 * iconst_0 / bipush 13 -> ConstraintsKt.Constraints$default).
 *
 * So `measurables[0].minIntrinsicHeight(LINE_PROBE_WIDTH_PX)` — the
 * wave-42 line-snap probe in MultiColumnApplier's sole-child fragmentation
 * branch — raised IllegalArgumentException, which [IntrinsicChannel.probe]
 * catches ONLY IllegalStateException and therefore deliberately re-throws
 * (its own comment names "an IllegalArgumentException out of Constraints
 * packing" as a real bug that must keep propagating). An unguarded throw
 * inside a measure pass kills the whole capture composition — the wave-39
 * failure mode IntrinsicChannel exists to prevent.
 *
 * ## What the fix pins (below)
 *  1. the constant is representable, with room on the other axis;
 *  2. the 262142/262143 boundary itself (so a future edit cannot "fix" the
 *     constant by moving it onto the cliff edge);
 *  3. [MulticolLineSnap.clampedProbeWidthPx] clamps ANY width into the
 *     legal range — defense in depth for future call sites;
 *  4. [IntrinsicChannel.probe] still does NOT swallow the packing throw
 *     (the reason 1–3 are load-bearing rather than belt-and-braces);
 *  5. the applier's call site actually goes through the clamp.
 */
class MulticolLineProbeBoundsTest {

    @Test
    fun `the line probe width is representable in Compose Constraints`() {
        // The exact Constraints the platform's default intrinsic path
        // builds from the probe width: minWidth 0 / maxWidth = w /
        // minHeight 0 / maxHeight Infinity (height packs into 13 bits).
        val c = Constraints(maxWidth = MulticolLineSnap.LINE_PROBE_WIDTH_PX)
        assertEquals(MulticolLineSnap.LINE_PROBE_WIDTH_PX, c.maxWidth)
        // 65534 needs 16 width bits, so 15 remain for the other axis — the
        // "room to spare" the constant's doc claims, pinned rather than
        // asserted in prose: a 32766-tall band still packs alongside it.
        assertEquals(
            32766,
            Constraints(
                maxWidth = MulticolLineSnap.LINE_PROBE_WIDTH_PX,
                maxHeight = 32766
            ).maxHeight
        )
    }

    @Test
    fun `the representable boundary is 262142 and the constant names it`() {
        // 262142 packs (18 bits width + 13 bits height = 31); 262143 does
        // not — the cliff MulticolLineSnap.MAX_CONSTRAINT_WIDTH_PX names.
        assertEquals(262142, MulticolLineSnap.MAX_CONSTRAINT_WIDTH_PX)
        assertEquals(
            MulticolLineSnap.MAX_CONSTRAINT_WIDTH_PX,
            Constraints(maxWidth = MulticolLineSnap.MAX_CONSTRAINT_WIDTH_PX).maxWidth
        )
        try {
            Constraints(maxWidth = MulticolLineSnap.MAX_CONSTRAINT_WIDTH_PX + 1)
            org.junit.Assert.fail("262143 was expected to be unrepresentable")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Can't represent a width of"))
        }
    }

    @Test
    fun `the clamp maps any width into the legal range`() {
        // The old poison value comes back as the boundary, not as a throw.
        assertEquals(
            MulticolLineSnap.MAX_CONSTRAINT_WIDTH_PX,
            MulticolLineSnap.clampedProbeWidthPx(1_000_000)
        )
        // Infinity-derived garbage (the B-RC6 half-Infinity width that
        // crash-looped the nested-multicol fixture) also lands legal.
        assertEquals(
            MulticolLineSnap.MAX_CONSTRAINT_WIDTH_PX,
            MulticolLineSnap.clampedProbeWidthPx(1_073_741_823)
        )
        // A legal width passes through untouched (no silent narrowing).
        assertEquals(
            MulticolLineSnap.LINE_PROBE_WIDTH_PX,
            MulticolLineSnap.clampedProbeWidthPx(MulticolLineSnap.LINE_PROBE_WIDTH_PX)
        )
        // Negative widths floor at 0 — a negative maxWidth is not a legal
        // Constraints dimension either (the z-ordering-003 wedge class).
        assertEquals(0, MulticolLineSnap.clampedProbeWidthPx(-9))
        // And every clamped answer really does build Constraints: the
        // property the clamp exists to guarantee, checked end-to-end.
        for (w in listOf(-9, 0, 17, MulticolLineSnap.LINE_PROBE_WIDTH_PX, 1_000_000, Int.MAX_VALUE)) {
            val clamped = MulticolLineSnap.clampedProbeWidthPx(w)
            assertEquals(clamped, Constraints(maxWidth = clamped).maxWidth)
        }
    }

    @Test
    fun `IntrinsicChannel still does not guard the Constraints packing throw`() {
        // The skeptic's structural claim, kept: probe() narrows its catch
        // to IllegalStateException on purpose, so an unrepresentable width
        // would STILL escape into the measure pass. That is exactly why the
        // constant and the clamp above are the fix (not a wider catch).
        try {
            IntrinsicChannel.probe(
                logTag = "MulticolLineSnap",
                refusalContext = "S2 regression pin"
            ) { Constraints(maxWidth = 1_000_000); 0 }
            org.junit.Assert.fail("probe swallowed the Constraints packing throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Can't represent a width of"))
        }
    }

    @Test
    fun `the applier probes through the clamp, not the bare constant`() {
        // Source-scan wiring pin (MultiColumnFragmentationGateTest's
        // precedent — no Robolectric in this suite): the sole-child
        // line-box probe must hand minIntrinsicHeight a CLAMPED width.
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MultiColumnApplier.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        val source = File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
        assertTrue(
            "the line-box probe must clamp its width",
            source.contains("MulticolLineSnap.clampedProbeWidthPx(")
        )
        // …and no call site may reach minIntrinsicHeight with the raw
        // constant again (the exact shape the skeptic refuted).
        assertTrue(
            "raw LINE_PROBE_WIDTH_PX must not reach minIntrinsicHeight",
            !source.contains("minIntrinsicHeight(MulticolLineSnap.LINE_PROBE_WIDTH_PX)")
        )
    }
}
