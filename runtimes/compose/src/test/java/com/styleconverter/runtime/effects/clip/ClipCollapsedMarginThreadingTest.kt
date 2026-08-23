package com.styleconverter.runtime.effects.clip

// Wave 46 (lane Y4 FIX, skeptic S2) — the pin for the collapse override's
// route into the clip node.
//
// The defect this locks out: the reference box's first cut read
// `BlockMarginCollapse.LocalCollapsedMargin.current` from inside a
// `Modifier.composed {}` lane. A composed modifier materialises at the
// LAYOUT NODE's composition site, and ComponentRenderer re-provides that
// local as null for everything under a component ("§8.3.1 collapse channels
// are strictly one-level"), so the clip always saw null while the element's
// MARGIN step used the real override (which it takes as a parameter for
// exactly this reason). On WPT clip-path-ellipse-006 (`width:150;
// height:100; margin:50px; clip-path: ellipse()`) the harness strips the
// root's block bands via CollapsedMargin(0,0): the node is 250×100, so
// reading null subtracted a 50px top AND bottom band from a 100px node and
// left a ZERO-HEIGHT border box — the ellipse degenerated and 006/007/008
// rendered EMPTY (a regression from a 0.9484 pair).
//
// The pin therefore drives the PUBLIC applier with a non-null override and
// asserts the geometry the installed Shape actually resolves, rather than
// re-testing ClipReferenceBox (pinned in ClipReferenceBoxTest) in isolation.
//
// Two mechanical constraints shape how it reads the result. (1) There is no
// composition in a JVM suite, so the Shape is pulled back out of the
// modifier chain by reflection — `Modifier.clip(shape)` is
// `graphicsLayer(shape =, clip = true)` and that element exposes a public
// `getShape()`. (2) `Outline.Generic` needs an android.graphics.Path, which
// this suite has no Android runtime for (no Robolectric — the standing
// constraint ClipReferenceBoxTest states), so the radial shapes' outlines
// cannot be built here; their reference box is read from the shape instead,
// and the legacy `clip: rect(auto,auto,auto,auto)` lane — whose outline is a
// Path-free [Outline.Rectangle] equal to the reference box itself — carries
// the end-to-end assertion.

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.styleconverter.runtime.spacing.CollapsedMargin
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClipCollapsedMarginThreadingTest {

    private val density = Density(1f)
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun px(v: Int) = parse("""{"px":$v}""")

    // clip-path-ellipse-006's verbatim per-test IR properties (wave45-final),
    // minus colour: an absolutely positioned 150×100 box with a 50px margin
    // on all four sides and a bare `ellipse()` (centre 50% 50%, both radii
    // closest-side).
    private val ellipse006 = listOf(
        "Width" to parse("""{"type":"length","px":150}"""),
        "Height" to parse("""{"type":"length","px":100}"""),
        "Position" to parse("\"ABSOLUTE\""),
        "MarginTop" to px(50), "MarginRight" to px(50), "MarginBottom" to px(50), "MarginLeft" to px(50),
        "ClipPath" to parse("""{"type":"ellipse"}"""),
    )

    // The node the harness measures for that box: the two INLINE bands are
    // padded into it (150 + 50 + 50 = 250) while the root strip moves the
    // block ones into the stack's gap spacers, leaving the declared 100.
    private val ellipse006Node = Size(250f, 100f)

    /** The Shape the applier installed, pulled back out of the chain (see header). */
    private fun installedShape(modifier: Modifier): Shape {
        val found = mutableListOf<Shape>()
        modifier.foldIn(Unit) { _, element ->
            val getter = runCatching { element.javaClass.getMethod("getShape") }.getOrNull()
            if (getter != null) {
                getter.isAccessible = true
                (runCatching { getter.invoke(element) }.getOrNull() as? Shape)?.let(found::add)
            }
        }
        assertEquals("exactly one clip shape must be installed", 1, found.size)
        return found.single()
    }

    /**
     * The reference box a radial shape captured — the ONE piece of its
     * outline that does not need a Path. The factories close over the
     * [ClipPathApplier.ClipRefBox] the applier built from the threaded
     * override, so the capture is found by TYPE (not by the synthetic
     * field's name) and asked for its frame.
     */
    private fun referenceBox(shape: Shape, size: Size) =
        shape.javaClass.declaredFields
            .asSequence()
            .mapNotNull { f -> f.isAccessible = true; f.get(shape) as? ClipPathApplier.ClipRefBox }
            .firstOrNull()
            ?.frame(size, density)
            ?: throw AssertionError("no ClipRefBox captured by ${shape.javaClass.name}")

    @Test
    fun `ellipse-006 keeps its full-height border box when the override is threaded in`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(ellipse006)
        val shape = installedShape(
            ClipPathApplier.applyClipPath(Modifier, cfg, CollapsedMargin(0f, 0f)),
        )
        val box = referenceBox(shape, ellipse006Node).rect
        // The regression signature: reading null subtracted BOTH block bands
        // from the 100px node and gave a zero-height border box (ry = 0, an
        // empty clip). With the override threaded in, the declared height
        // survives and only the inline bands are subtracted.
        assertEquals("border-box height must be the declared 100", 100f, box.height, 0.001f)
        assertEquals("border-box width must be the declared 150", 150f, box.width, 0.001f)
        assertEquals(50f, box.left, 0.001f)
        assertEquals(0f, box.top, 0.001f)

        // rx / ry: `ellipse()` defaults to closest-side on both axes from a
        // 50% 50% centre, i.e. half the BORDER box on each axis. These are
        // the two lines createEllipseShape runs after `ref.frame(...)`;
        // re-composed here because the Path its Outline.Generic needs cannot
        // be constructed without an Android runtime (see the header).
        val ellipse = cfg.shape as ClipShape.Ellipse
        val centerX = ClipRadialShapes.axisCenter(
            ellipse.centerXDp, ellipse.centerX, ellipse.centerFromRight, box.width, density,
        )
        val centerY = ClipRadialShapes.axisCenter(
            ellipse.centerYDp, ellipse.centerY, ellipse.centerFromBottom, box.height, density,
        )
        val rx = ClipRadialShapes.resolveEllipseRadius(
            ellipse.radiusX, box.size, centerX, centerY, density, isHorizontal = true,
        )
        val ry = ClipRadialShapes.resolveEllipseRadius(
            ellipse.radiusY, box.size, centerX, centerY, density, isHorizontal = false,
        )
        assertEquals("rx must be 50% of the BORDER box width", 75f, rx, 0.001f)
        assertEquals("ry must be 50% of the BORDER box height", 50f, ry, 0.001f)
    }

    @Test
    fun `the same margin geometry clips the border box end to end`() {
        // The Path-free lane: legacy `clip: rect(auto,auto,auto,auto)` on
        // ellipse-006's box metrics (its `position:absolute` is what makes
        // the legacy property apply at all, CSS 2.1 §11.1.2) resolves to an
        // Outline.Rectangle that IS the reference box — so this one
        // assertion runs the whole chain, applier → reference box →
        // outline, with nothing re-composed by the test.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            ellipse006.dropLast(1) + ("Clip" to parse("""{"type":"rect"}"""))
        )
        val outline = installedShape(
            ClipPathApplier.applyClipPath(Modifier, cfg, CollapsedMargin(0f, 0f)),
        ).createOutline(ellipse006Node, LayoutDirection.Ltr, density)
        val rect = (outline as Outline.Rectangle).rect
        assertEquals(50f, rect.left, 0.001f)
        assertEquals(0f, rect.top, 0.001f)
        assertEquals(200f, rect.right, 0.001f)
        assertEquals(100f, rect.bottom, 0.001f)
    }

    @Test
    fun `without the override the same IR collapses to the empty box S2 measured`() {
        // The bug's own arithmetic, kept as the contrast case: with no
        // override both 50px block bands count as applied, so a 100px node
        // has nothing left for the border box. It is also the RIGHT answer
        // for an element genuinely outside a collapsing flow (whose node
        // would then be 200px tall) — which is why the default stays null.
        val cfg = ClipPathExtractor.extractClipPathConfig(ellipse006)
        val box = referenceBox(
            installedShape(ClipPathApplier.applyClipPath(Modifier, cfg)), ellipse006Node,
        ).rect
        assertEquals(0f, box.height, 0.001f)
    }

    @Test
    fun `the clip lane reads no CompositionLocal and the override is threaded end to end`() {
        // Structural guard: the composed lane is what made the value
        // unreachable, so its absence is part of the contract. A JVM test
        // cannot run a composition, so pin it at the source — the same
        // technique the backdrop parity suite uses for chain order.
        val applier = repoFile(
            "runtimes/compose/src/main/java/com/styleconverter/runtime/effects/clip/ClipPathApplier.kt",
        ).readText()
        val body = applier.substringAfter("fun applyClipPath(")
        assertTrue(
            "applyClipPath must not resurrect the composed/CompositionLocal lane",
            !body.contains("composed {") && !body.contains("LocalCollapsedMargin"),
        )
        // …and the value has to actually arrive: StyleApplier's step 3 hands
        // its collapsedMargin to EffectsFacade, which hands it to the clip.
        val facade = repoFile(
            "runtimes/compose/src/main/java/com/styleconverter/runtime/effects/EffectsFacade.kt",
        ).readText()
        assertTrue(
            "EffectsFacade must forward the override to the clip applier",
            facade.contains("ClipPathApplier.applyClipPath(result, config.clipPath, collapsed)"),
        )
        val styleApplier = repoFile(
            "runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt",
        ).readText()
        val step3 = styleApplier.substringAfter("result = EffectsFacade.apply(")
            .substringBefore("// 3.5. Mask")
        assertTrue(
            "StyleApplier step 3 must pass its collapsedMargin to the effects step",
            step3.contains("collapsed = collapsedMargin,"),
        )
    }

    /** Repo-root walk-up, as in the backdrop suites (module rootDir is apps/android-harness). */
    private fun repoFile(relative: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, relative)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relative from ${File(".").absolutePath}")
    }
}
