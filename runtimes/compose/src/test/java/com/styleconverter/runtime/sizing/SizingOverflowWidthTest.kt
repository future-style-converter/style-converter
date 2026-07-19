package com.styleconverter.runtime.sizing

// Wave 12 — definite px widths larger than the incoming constraints must
// OVERFLOW like CSS (css-overflow-3 §3, overflow:visible initial), not
// clamp: Modifier.width() coerced the css-break .container's
// `inline-size: 472px` into the 390px capture canvas while web rendered
// the full 472 spilling across it (pixel-measured by the wave-12 lane).
// Compose's requiredWidth centers the violating box; web overflows
// START-aligned — so SizingApplier's Exact branch now routes through the
// custom start-anchored layout in ExactWidthOverflow.kt.
//
// Pins (this suite's standing no-device contract, the
// FragmentGeometryTest pattern): the pure measure guard on BOTH sides of
// the overflow split, plus a source scan locking the wiring — the Exact
// branch through exactWidth, the guard consumed inside the layout lambda,
// and the start-anchored placement.

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizingOverflowWidthTest {

    // ── the pure guard: overflow side ────────────────────────────────────

    @Test
    fun `declared width beyond incoming max measures declared and reports max`() {
        // The fixture numbers: InlineSize 472 on the 390px canvas — the
        // child must measure at 472 (the ink) while the parent flow sees
        // 390 (its geometry, like the browser canvas, must not grow).
        val (child, reported) = ExactWidthOverflow.measureSpec(472, 0, 390)
        assertEquals("child measures at the DECLARED width", 472, child)
        assertEquals("parent is told the envelope max", 390, reported)
    }

    @Test
    fun `unbounded incoming max never takes the overflow path`() {
        // max == Constraints.Infinity ⇒ nothing can exceed it — the guard
        // must stay on the fitting side (scrollable/intrinsic parents).
        val (child, reported) = ExactWidthOverflow.measureSpec(472, 0, Int.MAX_VALUE)
        assertEquals(472, child)
        assertEquals(472, reported)
    }

    // ── the pure guard: fitting side (Modifier.width parity) ─────────────

    @Test
    fun `fitting width is measured tight and reported verbatim`() {
        // Normal path: byte-identical to Modifier.width's coercion — a
        // 200px width in a 390px envelope measures and reports 200.
        val (child, reported) = ExactWidthOverflow.measureSpec(200, 0, 390)
        assertEquals(200, child)
        assertEquals(200, reported)
    }

    @Test
    fun `width exactly at the incoming max still fits`() {
        // Boundary pin: target == max is NOT overflow (strict >), keeping
        // exact-fit fixtures on the unchanged path.
        val (child, reported) = ExactWidthOverflow.measureSpec(390, 0, 390)
        assertEquals(390, child)
        assertEquals(390, reported)
    }

    @Test
    fun `incoming min still coerces a smaller declared width up`() {
        // Modifier.width parity on the min side: propagateMinConstraints-
        // style parents (min 150) lift a declared 100 to 150, tight.
        val (child, reported) = ExactWidthOverflow.measureSpec(100, 150, 390)
        assertEquals(150, child)
        assertEquals(150, reported)
    }

    // ── source scan: the wiring cannot silently regress ──────────────────

    /** Read a runtime source file by walking up to the repo root (the same
     *  walk-up pattern as FragmentGeometryTest / SchemaConformanceTest). */
    private fun source(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }

    private val applier by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt")
    }
    private val overflow by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/ExactWidthOverflow.kt")
    }

    @Test
    fun `applyWidth exact branch routes through the overflow-aware layout`() {
        // The Exact branch must call the custom exactWidth — reverting to
        // Modifier.width would silently reinstate the clamp.
        assertTrue(
            "Exact width must route through exactWidth",
            applier.contains("is LengthValue.Exact -> m.exactWidth(v.px.toFloat().dp)")
        )
    }

    @Test
    fun `layout lambda consumes the pinned guard and anchors at start`() {
        // The measure lambda must take its numbers from the pure guard…
        assertTrue(
            "layout must consume ExactWidthOverflow.measureSpec",
            overflow.contains("ExactWidthOverflow.measureSpec(targetPx, constraints.minWidth, constraints.maxWidth)")
        )
        // …measure the child TIGHT at the guard's width…
        assertTrue(
            "child must measure tight at the guard width",
            overflow.contains("constraints.copy(minWidth = childPx, maxWidth = childPx)")
        )
        // …and place start-anchored (web's start-aligned overflow), never
        // requiredWidth's centering.
        assertTrue(
            "placement must be start-anchored",
            overflow.contains("placeable.placeRelative(0, 0)")
        )
    }
}
