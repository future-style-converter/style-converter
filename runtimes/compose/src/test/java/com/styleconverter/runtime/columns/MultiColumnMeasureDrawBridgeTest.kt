package com.styleconverter.runtime.columns

// Wave 21 (B-RC6) — the nested-multicol WEDGE pin. The one fixture with a
// multicol nested inside a multicol (css-multicol
// abspos-multicol-in-second-outer-clipped: outer 100×100 2-col → relative
// clip h:200 → ABSOLUTE 2-col h:200 w:100% → child h:300) crash-looped the
// Android app with zero PNGs. Confirmed on-device diagnosis (logcat, wave-21
// emulator repro):
//
//   java.lang.IllegalArgumentException:
//     Can't represent a width of 1073741823 and height of 0 in Constraints
//   at NodeMeasuringIntrinsics.minHeight  ← the gate's intrinsic probe
//   at MultiColumnApplier.kt (naturalHeights probe)
//   at ComponentRenderer.absposOverflowMeasure (unbounded Constraints())
//
// Two independent hazards, both fixed and both pinned here:
//  1. UNBOUNDED INLINE SIZE: absposOverflowMeasure measures the abspos inner
//     multicol with Constraints() (maxWidth == Infinity); Width:100% is
//     runtime-dependent (null) so nothing caps it. resolveUsedColumns then
//     yields columnWidth == Infinity/2 == 1_073_741_823 — NOT a representable
//     Constraints dimension — and the intrinsic probe throws. Fix: the
//     fragmentation gate requires a bounded inline size, and the legacy
//     path's child-measure cap / reported width stay finite.
//  2. MEASURE-PHASE SNAPSHOT TRAFFIC: the measure body compared-and-wrote
//     fragmentsState. Intrinsic measurement executes the WHOLE measure
//     lambda (a nested multicol reaches it through the outer's probe), so
//     the state was written during intrinsics, and the compare-read
//     registered the measure pass as a snapshot reader — remeasure
//     ping-pong. Fix: the state is published ONLY from placement blocks
//     (never executed by intrinsics, no measure-pass read observation).
//
// Pure pins + source scans (no Robolectric — the suite's standing
// constraint, same style as MultiColumnFragmentationGateTest).

import androidx.compose.ui.unit.Constraints
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiColumnMeasureDrawBridgeTest {

    // ── 1. The crash arithmetic, pinned (why the guard exists) ──────────────

    @Test
    fun `unbounded inline size yields the unrepresentable half-Infinity column width`() {
        // Constraints() (the abspos unbounded measure) really is Infinity-max
        // on the inline axis — the shape absposOverflowMeasure hands down.
        assertEquals(Constraints.Infinity, Constraints().maxWidth)
        // The §3.4 formula over that Infinity: (2147483647 - 0) / 2 — the
        // exact 1_073_741_823 from the on-device crash message. This is the
        // value that must never reach an intrinsic probe or a Constraints
        // dimension, hence the inlineSizeBounded gate.
        assertEquals(
            1_073_741_823,
            MultiColumnApplier.resolveUsedColumns(Constraints.Infinity, 2, 0).widthPx)
    }

    // ── 2. The nested fixture's TWO fragmentation levels stay sane once
    //       each level's inline size is bounded (the post-fix geometry) ─────

    @Test
    fun `outer level of the wedge fixture fragments 200px of content into two 100px columns`() {
        // Outer multicol: 100px wide, 2 columns, gap 0 → used width 50…
        assertEquals(
            MultiColumnApplier.UsedColumns(count = 2, widthPx = 50),
            MultiColumnApplier.resolveUsedColumns(100, 2, 0))
        // …and its sole 200px-tall child (the relative clip wrapper) slices
        // into exactly ceil(200/100) = 2 fragments — finite, per-column.
        assertEquals(
            2,
            FragmentGeometry.fragmentGeometry(
                childBlockSizePx = 200, columnBlockSizePx = 100,
                columnWidthPx = 50, columnGapPx = 0, columnCount = 2).size)
    }

    @Test
    fun `inner level would fragment its 300px child into two 200px-capped bands under a bounded width`() {
        // The inner abspos multicol (h:200, 2 cols) with its 300px child:
        // ceil(300/200) = 2 fragments once a REAL (bounded) column width
        // exists. Pre-fix it never got here — the probe crashed first; the
        // MULTICOL lane owns making its width genuinely bounded (shrink-to-
        // fit), this pin just fixes the target geometry for that follow-up.
        assertEquals(
            2,
            FragmentGeometry.fragmentGeometry(
                childBlockSizePx = 300, columnBlockSizePx = 200,
                columnWidthPx = 50, columnGapPx = 0, columnCount = 2).size)
    }

    // ── 3. Source scans: the two fixes are wired in the REAL measure body ──
    //       (MultiColumnFragmentationGateTest precedent — feasible-without-
    //       Robolectric wiring pins over the applier's actual source).

    /** The applier source, located by walking up from the test working dir. */
    private val applierSource: String by lazy {
        // Repo-root-relative path of the file under scan.
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MultiColumnApplier.kt"
        // Walk up until the relative path resolves (gradle test cwd varies).
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root ($rel) not found above ${System.getProperty("user.dir")}" }, rel)
            .readText()
    }

    @Test
    fun `fragmentation gate requires a bounded inline size and logs the bail`() {
        // The guard itself: derived from the incoming inline max, not a probe.
        assertTrue(
            "bounded-inline-size guard missing",
            applierSource.contains("val inlineSizeBounded = constraints.maxWidth != Constraints.Infinity"))
        // The gate must AND it in — the probe can then never run half-Infinity.
        assertTrue(
            "gate must require inlineSizeBounded",
            applierSource.contains("if (definiteBlockSize && inlineSizeBounded)"))
        // No silent fallthrough: the unbounded bail is a logged reason.
        assertTrue(
            "unbounded bail must be logged once",
            applierSource.contains("unbounded inline size"))
    }

    @Test
    fun `legacy path never feeds the half-Infinity width into Constraints or layout`() {
        // Children are capped by childMaxWidth (== Infinity passthrough when
        // unbounded — Infinity IS representable; half-Infinity is not).
        assertTrue(
            "child measure must use the guarded cap",
            applierSource.contains("maxWidth = childMaxWidth"))
        // The reported size uses the guarded layoutWidth (widest child when
        // unbounded — layout() must never report Infinity).
        assertTrue(
            "layout must report the guarded width",
            applierSource.contains("layout(layoutWidth, maxHeight)"))
    }

    @Test
    fun `measure body has NO fragmentsState reads - the remeasure-loop hazard stays dead`() {
        // The two pre-fix measure-phase patterns, pinned ABSENT: the
        // compare-before-write read…
        assertFalse(
            "measure-phase compare-read must not return",
            applierSource.contains("if (fragmentsState.value != fragments)"))
        // …and the isNotEmpty reset read. Either one re-registers the measure
        // pass as a snapshot reader of the draw bridge.
        assertFalse(
            "measure-phase reset-read must not return",
            applierSource.contains("fragmentsState.value.isNotEmpty()"))
        // Exactly three touches of the state remain: ONE draw-pass read and
        // TWO placement-phase writes — nothing else may creep in.
        assertEquals(
            "fragmentsState.value must be touched exactly 3 times (1 draw read + 2 placement writes)",
            3,
            Regex(Regex.escape("fragmentsState.value")).findAll(applierSource).count())
    }

    @Test
    fun `both fragmentsState writes live INSIDE placement blocks - intrinsics never execute them`() {
        // Fragmentation branch: the publish sits after the branch's layout()
        // call and before its place() — i.e. inside the placement lambda,
        // which NodeMeasuringIntrinsics never invokes.
        val fragLayout = applierSource.indexOf("return@Layout layout(constraints.maxWidth, columnBlockSize)")
        val fragWrite = applierSource.indexOf("fragmentsState.value = fragments")
        val fragPlace = applierSource.indexOf("placeable.place(0, 0)")
        assertTrue("fragmentation publish must be inside its placement block",
            fragLayout in 0 until fragWrite && fragWrite < fragPlace)
        // Legacy path: the reset sits after the legacy layout() call — same
        // placement-block containment argument.
        val legacyLayout = applierSource.indexOf("layout(layoutWidth, maxHeight)")
        val legacyReset = applierSource.indexOf("fragmentsState.value = emptyList()")
        assertTrue("legacy reset must be inside its placement block",
            legacyLayout in 0 until legacyReset)
    }
}
