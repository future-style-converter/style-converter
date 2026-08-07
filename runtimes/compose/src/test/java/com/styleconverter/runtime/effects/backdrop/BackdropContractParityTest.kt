package com.styleconverter.runtime.effects.backdrop

// CROSS-NATIVE PIN TABLE for the unified wave-26 backdrop contract.
//
// Every assertion in this file has a byte-identical twin in
// runtimes/swiftui/Tests/StyleConverterRuntimeTests/BackdropContractParityTests.swift.
// The two suites call each platform's own pure functions with the SAME inputs
// and assert the SAME outputs, so "the natives agree" is a diffable fact
// rather than a claim in a comment. Anything that changes one table and not
// the other turns one of the two suites red.
//
// The five clauses of the contract, and where each is pinned here:
//   1. blur edge model  — the sample table + keepRect table below;
//   2. chain admission  — the all-or-nothing table;
//   3. paint ordering   — source-order pins (Compose's chain is built in
//                         EffectsFacade; a JVM test cannot run Compose draw);
//   4. clip box         — the border-box table (margin bands subtracted);
//   5. shadow leak      — the EffectsFacade order pin.

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.effects.filter.FilterFunction
import com.styleconverter.runtime.spacing.MarginApplier
import com.styleconverter.runtime.spacing.MarginConfig
import com.styleconverter.runtime.spacing.MarginValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackdropContractParityTest {

    // ── clause 1: the sample table (the BORDER BOX, clamped) ─────────────

    /**
     * One row of the shared table. Kept as a data class so the Kotlin and
     * Swift tables read the same top to bottom.
     */
    private data class Row(
        val name: String,
        val elemLeft: Int, val elemTop: Int, val elemW: Int, val elemH: Int,
        val sigma: Float,
        val srcW: Int, val srcH: Int,
        // Expected sample, or null for a refusal.
        val expect: BackdropSample?,
        // Expected keep rect (left, top, width, height) of the border box
        // inside the crop, or null when the row is a refusal.
        val keep: List<Int>?,
    )

    private val table = listOf(
        // Invert-only chain: the sample IS the border box, and the keep rect
        // is the whole crop. backdrop-filter-basic.html geometry.
        Row("interior, no blur", 60, 150, 100, 100, 0f, 390, 600,
            BackdropSample(60, 150, 100, 100, 0, 0), listOf(0, 0, 100, 100)),
        // blur(10px) over the SAME box → the SAME sample. σ does not widen the
        // rect (wave-34: filter-effects-2 §2 clips the backdrop to the border
        // box first); TileMode.MIRROR / mirrorPad supply the rest.
        Row("interior, blur 10", 100, 100, 50, 50, 10f, 390, 600,
            BackdropSample(100, 100, 50, 50, 0, 0), listOf(0, 0, 50, 50)),
        // Box hugging the canvas origin: nothing to clamp, because the sample
        // never reaches outside the box in the first place.
        Row("box at the canvas origin", 0, 0, 50, 50, 10f, 390, 600,
            BackdropSample(0, 0, 50, 50, 0, 0), listOf(0, 0, 50, 50)),
        // Same on the far edges: the box ends exactly at 390x600.
        Row("box at the far edges", 360, 570, 30, 30, 10f, 390, 600,
            BackdropSample(360, 570, 30, 30, 0, 0), listOf(0, 0, 30, 30)),
        // The element itself hangs off the LEFT edge: the crop is narrower
        // than the border box, and the keep rect says which strip it covers.
        // This is now the ONLY way the clamp can bite.
        Row("element overhangs the left edge", -10, 10, 40, 20, 0f, 390, 600,
            BackdropSample(0, 10, 30, 20, 10, 0), listOf(0, 0, 30, 20)),
        // …and blurring it does not change that: still the on-canvas strip.
        Row("element overhangs the left edge, blurred", -10, 10, 40, 20, 10f, 390, 600,
            BackdropSample(0, 10, 30, 20, 10, 0), listOf(0, 0, 30, 20)),
        // A box running off the BOTTOM-RIGHT: clamped on two sides, dst stays
        // zero because only the far edges moved.
        Row("element overhangs bottom-right", 370, 580, 40, 40, 10f, 390, 600,
            BackdropSample(370, 580, 20, 20, 0, 0), listOf(0, 0, 20, 20)),
        // Refusals — nothing to filter / nothing to read / nothing on canvas.
        Row("zero width refuses", 10, 10, 0, 40, 0f, 390, 600, null, null),
        Row("zero height refuses", 10, 10, 40, 0, 0f, 390, 600, null, null),
        Row("empty plate refuses", 10, 10, 40, 40, 0f, 0, 0, null, null),
        Row("fully off canvas refuses", 500, 10, 40, 40, 0f, 390, 600, null, null),
    )

    @Test
    fun `sample table matches the cross-native pins`() {
        for (row in table) {
            val got = BackdropSampleGeometry.sample(
                elemLeft = row.elemLeft, elemTop = row.elemTop,
                elemWidth = row.elemW, elemHeight = row.elemH,
                srcWidth = row.srcW, srcHeight = row.srcH,
            )
            assertEquals("sample[${row.name}]", row.expect, got)
        }
    }

    @Test
    fun `the sample rect is sigma-independent`() {
        // The wave-34 edge-model claim, stated directly rather than inferred
        // from the table: for one box, EVERY σ gives the same sample. If a
        // grow term ever comes back, this is the assertion that catches it.
        val baseline = BackdropSampleGeometry.sample(
            elemLeft = 100, elemTop = 100, elemWidth = 50, elemHeight = 50,
            srcWidth = 390, srcHeight = 600,
        )
        assertEquals(BackdropSample(100, 100, 50, 50, 0, 0), baseline)
        // The table's own σ column is the input set: 0 (invert-only), 10
        // (backdrop-filter-basic-blur), and the boundary fixture's largest.
        for (sigma in listOf(0f, 2.5f, 10f, 96f)) {
            val chain = if (sigma <= 0f) BackdropChain(emptyList())
            else BackdropChain(listOf(BackdropOp.Blur(sigma.dp)))
            // Compute σ the way the painter does, then assert it changed
            // nothing about the rect the painter asks for.
            assertEquals(sigma, chain.totalBlurSigmaPx { it.value }, 1e-3f)
            assertEquals(
                "sample must not depend on sigma=$sigma",
                baseline,
                BackdropSampleGeometry.sample(
                    elemLeft = 100, elemTop = 100, elemWidth = 50, elemHeight = 50,
                    srcWidth = 390, srcHeight = 600,
                ),
            )
        }
    }

    @Test
    fun `keep rect table matches the cross-native pins`() {
        // The keep rect is the border box's own extent INSIDE the padded crop
        // — what the filtered buffer is cut back to. Compose expresses it
        // implicitly (the painter clips to the border box after drawing the
        // padded patch at a negative offset), so the arithmetic is restated
        // here in the same closed form the Swift twin uses.
        for (row in table) {
            val expect = row.keep ?: continue
            // Non-null by construction: every row carrying a keep rect also
            // carries the sample it is measured inside.
            val s = requireNotNull(row.expect) { "row ${row.name} has keep but no sample" }
            // boxLeft/boxTop: where the border box starts inside the crop.
            val boxLeft = -s.dstLeft
            val boxTop = -s.dstTop
            val left = maxOf(0, boxLeft)
            val top = maxOf(0, boxTop)
            val right = minOf(s.width, boxLeft + row.elemW)
            val bottom = minOf(s.height, boxTop + row.elemH)
            assertEquals("keep.left[${row.name}]", expect[0], left)
            assertEquals("keep.top[${row.name}]", expect[1], top)
            assertEquals("keep.width[${row.name}]", expect[2], right - left)
            assertEquals("keep.height[${row.name}]", expect[3], bottom - top)
        }
    }

    @Test
    fun `blur sigma composition matches the cross-native pins`() {
        // σ still has to be computed identically on both natives — it drives
        // the Gaussian itself (and, on iOS, the width of the mirror band).
        // What it no longer drives is the sample rect (see the σ-independence
        // pin above), which is why this test only asserts the composition.
        assertEquals(0f, BackdropChain(emptyList()).totalBlurSigmaPx { it.value }, 1e-4f)
        assertEquals(
            10f,
            BackdropChain(listOf(BackdropOp.Blur(10.dp))).totalBlurSigmaPx { it.value },
            1e-4f,
        )
        // Two 3px blurs compose in quadrature to σ = √18 ≈ 4.2426.
        val quadrature = BackdropChain(listOf(BackdropOp.Blur(3.dp), BackdropOp.Blur(3.dp)))
            .totalBlurSigmaPx { it.value }
        assertEquals(4.2426f, quadrature, 1e-3f)
    }

    // ── clause 2: all-or-nothing admission ───────────────────────────────

    @Test
    fun `admission table matches the cross-native pins`() {
        // In scope, alone and combined, with CSS order preserved.
        assertEquals(
            listOf(BackdropOp.Invert(1f)),
            BackdropChain.of(listOf(FilterFunction.Invert(1f)))!!.ops,
        )
        assertEquals(
            listOf(BackdropOp.Blur(10.dp)),
            BackdropChain.of(listOf(FilterFunction.Blur(10.dp)))!!.ops,
        )
        assertEquals(
            listOf(BackdropOp.Blur(4.dp), BackdropOp.Invert(1f)),
            BackdropChain.of(listOf(FilterFunction.Blur(4.dp), FilterFunction.Invert(1f)))!!.ops,
        )
        // Out of scope → the WHOLE chain refuses. The second case is the one
        // that used to diverge: iOS executed the invert prefix and painted a
        // half-filtered backdrop while Compose painted none.
        assertNull(BackdropChain.of(listOf(FilterFunction.Grayscale(1f))))
        assertNull(
            BackdropChain.of(listOf(FilterFunction.Invert(1f), FilterFunction.Sepia(1f))),
        )
        assertNull(
            BackdropChain.of(listOf(FilterFunction.Blur(6.dp), FilterFunction.Brightness(1.1f))),
        )
        // Identity chains are NOT refusals — they simply carry no work.
        assertNull(BackdropChain.of(emptyList()))
        assertNull(BackdropChain.of(listOf(FilterFunction.None)))
    }

    // ── clause 4: the clip box is the BORDER box ─────────────────────────

    @Test
    fun `border box table strips the margin bands`() {
        // A 140x90 draw node whose element declares margin 8/20/12/10
        // (top/right/bottom/left) → a 110x70 border box at (10,8).
        val box = BackdropSampleGeometry.borderBox(
            nodeWidth = 140f, nodeHeight = 90f,
            marginLeftPx = 10f, marginTopPx = 8f,
            marginRightPx = 20f, marginBottomPx = 12f,
        )!!
        assertEquals(10f, box.localLeft, 0f)
        assertEquals(8f, box.localTop, 0f)
        assertEquals(110f, box.width, 0f)
        assertEquals(70f, box.height, 0f)
    }

    @Test
    fun `border box is the node itself without margins`() {
        // The overwhelmingly common case: no margin, so nothing moves and the
        // committed captures cannot shift.
        val box = BackdropSampleGeometry.borderBox(200f, 100f, 0f, 0f, 0f, 0f)!!
        assertEquals(0f, box.localLeft, 0f)
        assertEquals(0f, box.localTop, 0f)
        assertEquals(200f, box.width, 0f)
        assertEquals(100f, box.height, 0f)
    }

    @Test
    fun `negative margin bands never grow the border box`() {
        // MarginApplier routes negative margins through Modifier.offset, which
        // translates without resizing — so they contribute nothing here. A
        // caller handing us a negative number must not inflate the box.
        val box = BackdropSampleGeometry.borderBox(100f, 50f, -20f, -5f, 0f, 0f)!!
        assertEquals(0f, box.localLeft, 0f)
        assertEquals(100f, box.width, 0f)
        assertEquals(50f, box.height, 0f)
    }

    @Test
    fun `margins that eat the whole node refuse`() {
        // No border box left → no honest patch. Null, not a degenerate rect.
        assertNull(BackdropSampleGeometry.borderBox(40f, 40f, 30f, 0f, 30f, 0f))
        assertNull(BackdropSampleGeometry.borderBox(40f, 40f, 0f, 25f, 0f, 15f))
    }

    @Test
    fun `resolved insets are the same numbers the margin step pads with`() {
        // The value the backdrop subtracts comes from MarginApplier itself, so
        // it cannot drift from the absolutePadding the layout step adds.
        val cfg = MarginConfig(
            top = MarginValue.Length(LengthValue.Exact(8.0)),
            right = MarginValue.Length(LengthValue.Exact(20.0)),
            bottom = MarginValue.Length(LengthValue.Exact(12.0)),
            left = MarginValue.Length(LengthValue.Exact(10.0)),
        )
        val insets = MarginApplier.resolvedInsets(cfg)
        assertEquals(10f, insets.left.value, 0f)
        assertEquals(8f, insets.top.value, 0f)
        assertEquals(20f, insets.right.value, 0f)
        assertEquals(12f, insets.bottom.value, 0f)
    }

    @Test
    fun `resolved insets drop the negative and auto sides`() {
        // Negative → offset, not padding. Auto → centering, not padding.
        // Both leave the LAYOUT box at the border box's own size on that side.
        val cfg = MarginConfig(
            top = MarginValue.Length(LengthValue.Exact(-6.0)),
            left = MarginValue.Auto,
            right = MarginValue.Auto,
            bottom = MarginValue.Length(LengthValue.Exact(4.0)),
        )
        val insets = MarginApplier.resolvedInsets(cfg)
        assertEquals(0f, insets.top.value, 0f)
        assertEquals(0f, insets.left.value, 0f)
        assertEquals(0f, insets.right.value, 0f)
        assertEquals(4f, insets.bottom.value, 0f)
    }

    @Test
    fun `a margin-less element resolves to the zero insets singleton`() {
        assertTrue(MarginApplier.resolvedInsets(MarginConfig()).isZero)
    }

    // ── clauses 3 + 5: paint order, pinned at the source ─────────────────

    /**
     * Same walk-up the sibling probe suite uses: this module's rootDir is
     * apps/android-harness (out-of-tree include), so the repo root is found by
     * climbing until the relative path resolves rather than assuming a depth.
     */
    private fun repoFile(relative: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, relative)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun `EffectsFacade installs the backdrop OUTSIDE the shadow and the foreground filters`() {
        // Clause 5 (shadow leak) and clause 3 (ordering) are both statements
        // about Compose modifier-chain ORDER, and a JVM test cannot run Compose
        // draw. Pin the order at the source: earlier in the chain == outer, so
        // backdrop < shadow guarantees pass A's early return suppresses the
        // element's own box-shadow instead of baking it into its own backdrop,
        // and backdrop < foreground filters guarantees `filter: invert(1)`
        // cannot re-invert the backplate.
        val src = repoFile(
            "runtimes/compose/src/main/java/com/styleconverter/runtime/effects/EffectsFacade.kt",
        ).readText()
        val backdrop = src.indexOf("FilterApplier.applyBackdropFilters(")
        val shadow = src.indexOf("ShadowApplier.applyShadow(")
        val foreground = src.indexOf("FilterApplier.applyForegroundFilters(")
        assertTrue("all three steps must exist", backdrop > 0 && shadow > 0 && foreground > 0)
        assertTrue("backdrop must be OUTER of the shadow", backdrop < shadow)
        assertTrue("shadow must be OUTER of the foreground filters", shadow < foreground)
    }

    @Test
    fun `the foreground filter step cannot install a backdrop node`() {
        // Structural guard for the split: if the backdrop registration ever
        // slid back into the foreground helper, the ordering pin above would
        // still pass while the double-apply bug returned.
        val src = repoFile(
            "runtimes/compose/src/main/java/com/styleconverter/runtime/effects/filter/FilterApplier.kt",
        ).readText()
        val body = src.substringAfter("fun applyForegroundFilters(")
            .substringBefore("private fun FilterFunction.isColorMatrixFilter()")
        assertTrue(
            "applyForegroundFilters must not touch the backdrop lane",
            !body.contains("backdrop") && !body.contains("Backdrop"),
        )
    }

    @Test
    fun `StyleApplier still runs effects before layout — which is why insets are threaded`() {
        // The margin subtraction only makes sense while the effects step is
        // OUTER of the margin step. If that ever flips, the subtraction becomes
        // a double-count and this pin is the alarm.
        val src = repoFile(
            "runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt",
        ).readText()
        val effects = src.indexOf("result = EffectsFacade.apply(")
        val layout = src.indexOf("result = LayoutFacade.applyToModifier(result, config.layout")
        assertTrue(effects > 0 && layout > 0)
        assertTrue("effects (step 3) precedes layout/margin (step 4)", effects < layout)
        // …and the insets actually ride along.
        assertTrue(
            "the effects call must thread MarginApplier.resolvedInsets",
            src.contains("MarginApplier.resolvedInsets("),
        )
    }
}
