package com.styleconverter.runtime.images

import com.styleconverter.runtime.core.ir.IRAttrs
import com.styleconverter.runtime.core.ir.IRComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit pins for the wave-39 lane A2 REPLACED-ELEMENT image channel's pure and
 * JVM-reachable halves:
 *
 *   * [ReplacedBoxSizing] — the CSS 2.1 §10.3.2 / §10.6.2 used-size table, the
 *     one decision both natives must agree on to the pixel (its Swift twin
 *     pins the identical rows);
 *   * [ReplacedImageContent.isCandidate] — the identity gate that decides
 *     whether the image path claims a leaf at all;
 *   * [DocumentImageRegistry]'s DECLINE behaviour — the half that matters most,
 *     because a silent decline would paint a wrong picture with nothing in any
 *     log.
 *
 * The DECODE half is deliberately not pinned here: `BitmapFactory` is an
 * Android framework class the module's JVM suite runs without (no
 * `returnDefaultValues`), so every decode attempt lands in the registry's
 * decline branch. That is a real limit, and it is exactly why the device gate
 * — a css-writing-modes / css-grid section run against the frozen refs — is the
 * measurement that proves the raster path, not this file.
 */
class ReplacedImageChannelTest {

    // ── ReplacedBoxSizing: the four §10.3.2 rows ────────────────────────────

    @Test
    fun `both axes definite fill the box the style chain produced`() {
        assertEquals(
            ReplacedBoxSizing.Mode.FILL_BOTH,
            ReplacedBoxSizing.mode(widthDefinite = true, heightDefinite = true, aspectRatio = 2f),
        )
        // Ratio is irrelevant once both axes are declared — §10.3.2 rule 1
        // gives the used size straight from the declarations, and honouring a
        // ratio here would silently letterbox a box the author sized.
        assertEquals(
            ReplacedBoxSizing.Mode.FILL_BOTH,
            ReplacedBoxSizing.mode(widthDefinite = true, heightDefinite = true, aspectRatio = null),
        )
    }

    @Test
    fun `one declared axis derives the other from the intrinsic ratio`() {
        assertEquals(
            ReplacedBoxSizing.Mode.WIDTH_FILLS_RATIO_HEIGHT,
            ReplacedBoxSizing.mode(widthDefinite = true, heightDefinite = false, aspectRatio = 2f),
        )
        assertEquals(
            ReplacedBoxSizing.Mode.HEIGHT_FILLS_RATIO_WIDTH,
            ReplacedBoxSizing.mode(widthDefinite = false, heightDefinite = true, aspectRatio = 2f),
        )
    }

    @Test
    fun `neither axis declared uses the intrinsic size`() {
        // The css-writing-modes img-intrinsic-size-contribution family's row:
        // the `<img>` carries no Width/Height at all, so the box must hug the
        // raster's own 200x100 — which is the whole assertion of those tests.
        assertEquals(
            ReplacedBoxSizing.Mode.INTRINSIC,
            ReplacedBoxSizing.mode(widthDefinite = false, heightDefinite = false, aspectRatio = 2f),
        )
    }

    @Test
    fun `an unusable ratio DOWNGRADES a single-axis box to intrinsic, never to fill`() {
        // §10.3.2 rule 2 is conditioned on "has an intrinsic ratio"; without
        // one the used size falls to the intrinsic dimension. Falling through
        // to FILL_BOTH instead would stretch the raster across an axis nothing
        // declared — a silent distortion, which is the failure mode this whole
        // channel exists to avoid.
        for (bad in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(
                "ratio=$bad",
                ReplacedBoxSizing.Mode.INTRINSIC,
                ReplacedBoxSizing.mode(widthDefinite = true, heightDefinite = false, aspectRatio = bad),
            )
            assertEquals(
                "ratio=$bad",
                ReplacedBoxSizing.Mode.INTRINSIC,
                ReplacedBoxSizing.mode(widthDefinite = false, heightDefinite = true, aspectRatio = bad),
            )
        }
    }

    // ── the identity gate ──────────────────────────────────────────────────

    private fun component(tag: String?, src: String?) = IRComponent(
        id = "c", name = "c", properties = emptyList(),
        _tag = tag, attrs = if (src == null) null else IRAttrs(src = src),
    )

    @Test
    fun `isCandidate needs BOTH a replaced tag and a non-blank source`() {
        for (tag in listOf("img", "embed", "object", "video", "IMG")) {
            assertTrue(tag, ReplacedImageContent.isCandidate(component(tag, "css/s/a.png")))
        }
        // A replaced tag with no source renders as its ALT TEXT in a browser —
        // which is the existing text/placeholder path. Claiming it here would
        // paint nothing where something was painted before.
        assertFalse(ReplacedImageContent.isCandidate(component("img", null)))
        assertFalse(ReplacedImageContent.isCandidate(component("img", "   ")))
        // Non-replaced tags keep their normal content path. `input` is the
        // load-bearing one: it belongs to the UA-widget lane, whose mount hook
        // runs beside this one, and the two tag sets must stay disjoint.
        for (tag in listOf("input", "div", "p", "select", null)) {
            assertFalse("tag=$tag", ReplacedImageContent.isCandidate(component(tag, "css/s/a.png")))
        }
    }

    // ── the registry's decline contract ────────────────────────────────────

    @Test
    fun `an unconfigured registry declines every source and says so`() {
        DocumentImageRegistry.clear()
        assertNull(DocumentImageRegistry.resolve("css/s/a.png"))
        val r = DocumentImageRegistry.lastReport
        assertEquals(1, r.requested)
        assertEquals(0, r.decoded)
        assertEquals(listOf("css/s/a.png"), r.declined)
    }

    @Test
    fun `a blank source is not a request at all`() {
        DocumentImageRegistry.clear()
        assertNull(DocumentImageRegistry.resolve(null))
        assertNull(DocumentImageRegistry.resolve(""))
        assertNull(DocumentImageRegistry.resolve("  "))
        // Never a candidate ⇒ never counted. A decline count inflated by
        // non-requests would make the harness's per-document line lie about
        // how much this run actually failed to deliver.
        assertEquals(0, DocumentImageRegistry.lastReport.requested)
    }

    @Test
    fun `a decline is cached, so one bad source cannot spam the log per box`() {
        DocumentImageRegistry.clear()
        repeat(5) { assertNull(DocumentImageRegistry.resolve("css/s/missing.png")) }
        // css-grid's abspos family paints ONE support image from 41 boxes; a
        // per-box re-attempt would log 41 identical declines and re-hit the
        // filesystem 41 times for a file already known to be absent.
        assertEquals(1, DocumentImageRegistry.lastReport.requested)
        assertEquals(listOf("css/s/missing.png"), DocumentImageRegistry.lastReport.declined)
    }

    @Test
    fun `an SVG source is declined by NAME, not by a failed decode`() {
        // 19 of the 28 depth-48 replaced-source tests are SVG (the css-ui
        // box-sizing cluster) and Android ships no SVG rasteriser at all. The
        // format gate exists so the log says that, instead of the
        // indistinguishable "BitmapFactory returned no raster" a corrupt PNG
        // would produce.
        DocumentImageRegistry.clear()
        val dir = kotlin.io.path.createTempDirectory("w39a2-img").toFile()
        try {
            val svg = java.io.File(dir, "r1-1.svg")
            svg.writeText("<svg viewBox=\"0 0 100 100\"/>")
            DocumentImageRegistry.configure(dir)
            assertNull(DocumentImageRegistry.resolve("r1-1.svg"))
            assertEquals(listOf("r1-1.svg"), DocumentImageRegistry.lastReport.declined)
        } finally {
            dir.deleteRecursively()
            DocumentImageRegistry.clear()
        }
    }

    @Test
    fun `configure REPLACES the previous document's cache`() {
        DocumentImageRegistry.clear()
        DocumentImageRegistry.resolve("css/s/a.png")
        assertEquals(1, DocumentImageRegistry.lastReport.requested)
        // The inbox renders many documents in one process. A raster cached
        // under a path the NEXT document also uses would paint the previous
        // document's picture even though this one's delivery failed.
        DocumentImageRegistry.configure(null)
        assertEquals(0, DocumentImageRegistry.lastReport.requested)
        assertEquals(emptyList<String>(), DocumentImageRegistry.lastReport.declined)
    }
}
