package com.styleconverter.runtime.transforms

// Wave 52 (lane L4, native-near-misses T5) — JVM pins for the
// `backface-visibility: hidden` decision split by `transform-style`
// (css-transforms-2 §10 + §4.1.2), on VERBATIM wave51-fix per-test IR:
//   tools/titan/runs/wave51-fix/sections/css-transforms/per-test-ir/
//     wpt__css-transforms__composited-under-rotateY-180deg-preserve-3d.json
//     wpt__css-transforms__backface-visibility-hidden-001.json
//     wpt__css-transforms__backface-visibility-hidden-animated-001.json
//
// MUTATION EXECUTED (2026-09-25, this lane): with BackfaceCull.decide's last
// line changed to `return Decision.HIDE_SUBTREE` (i.e. the pre-wave-52
// "alpha the subtree" behaviour restored for preserve-3d too),
// `T5 - the preserve-3d culled parent culls its OWN face only` FAILS
// (expected CULL_OWN_FACE, was HIDE_SUBTREE) while every flat-carrier pin
// below stays green; bytes restored, sha-verified, and the suite is green
// again. The strip pin dies when stripOwnFace stops nulling the background.
// Fix pass 2026-10-05: M1 re-run (also kills BackfaceCullChainTest W1-W3);
// MS (the kept-shadow breadcrumb guarded by `if (false)`) → the shadow pin red.

import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.interactions.InteractionConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackfaceCullTest {

    /** IR property pair, as StyleApplier.extractConfig consumes it. */
    private fun p(type: String, json: String): Pair<String, JsonElement?> =
        type to Json.parseToJsonElement(json)

    /** The whole wire → StyleConfig → decision path the applier runs. */
    private fun decide(vararg props: Pair<String, JsonElement?>) =
        BackfaceCull.decide(StyleApplier.extractConfig(props.toList()))

    // ── The target: composited-under-rotateY-180deg-preserve-3d, verbatim ──

    /** `wpt__css-transforms__composited-under-rotatey-180deg-preserve-3d__0-091`. */
    private val preserve3dParent = listOf(
        p("Transform", """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":180}}]}"""),
        p("Width", """{"type":"length","px":100}"""),
        p("BackfaceVisibility", "\"HIDDEN\""),
        p("TransformStyle", "\"PRESERVE_3D\""),
    )

    @Test fun `T5 - the preserve-3d culled parent culls its OWN face only`() {
        // §4.1.2: the green child is its own plane and keeps `visible`; the
        // parent's back face is its (absent) box decoration. Before wave 52
        // this decided HIDE_SUBTREE and the canvas was blank.
        assertEquals(BackfaceCull.Decision.CULL_OWN_FACE, decide(*preserve3dParent.toTypedArray()))
    }

    @Test fun `T5 - the same element under FLAT hides the whole subtree`() {
        // Negative control: drop only `transform-style` and the flattened
        // subtree IS the back face — today's behaviour, unchanged.
        val flat = preserve3dParent.filterNot { it.first == "TransformStyle" }
        assertEquals(BackfaceCull.Decision.HIDE_SUBTREE, decide(*flat.toTypedArray()))
    }

    // ── The 11 flat carriers keep today's answer (hidden-001, verbatim) ──

    @Test fun `T5 - hidden-001 red face (flat, rotateY 180) is still hidden with its subtree`() {
        // `backface-visibility-hidden-001__1__0__0-043`: no TransformStyle on
        // the face itself (the PRESERVE_3D sits on its PARENT), so it is a
        // flat plane → the WPT "no red" the cell passes on today.
        assertEquals(
            BackfaceCull.Decision.HIDE_SUBTREE,
            decide(
                p("Position", "\"ABSOLUTE\""),
                p("Top", """{"px":50}"""), p("Left", """{"px":50}"""),
                p("Width", """{"type":"length","px":100}"""),
                p("Height", """{"type":"length","px":100}"""),
                p("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
                p("ZIndex", """{"value":1,"original":{"type":"integer","value":1}}"""),
                p("Transform", """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":180}}]}"""),
                p("BackfaceVisibility", "\"HIDDEN\""),
            ),
        )
    }

    @Test fun `T5 - hidden-001 green face (no rotation) is not culled`() {
        // `backface-visibility-hidden-001__1__0__1-044`: hidden but front-facing.
        assertEquals(
            BackfaceCull.Decision.NONE,
            decide(
                p("Position", "\"ABSOLUTE\""),
                p("Width", """{"type":"length","px":100}"""),
                p("Height", """{"type":"length","px":100}"""),
                p("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""),
                p("BackfaceVisibility", "\"HIDDEN\""),
            ),
        )
    }

    // ── At-risk cells: the animated-001/002 degree rule must stay ──

    @Test fun `T5 - animated-001 matrix3d back face is NOT culled (degree rule kept)`() {
        // `backface-visibility-hidden-animated-001__0__0-066`: `matrix3d(−1,…,−1,…)`
        // is rotateY(180deg) in matrix form on BOTH `#flip` and `#back`, so the
        // accumulated matrix is identity and Chromium paints the light-blue
        // box (frozen ref 19360 px of (173,216,230); ios P 0.9646). The
        // element-local degree rule sees no RotateY and leaves it painted.
        assertEquals(
            BackfaceCull.Decision.NONE,
            decide(
                p("Transform", """{"type":"functions","list":[{"fn":"matrix3d","a1":-1,"b1":0,"c1":0,"d1":0,"a2":0,"b2":1,"c2":0,"d2":0,"a3":0,"b3":0,"c3":-1,"d3":0,"a4":0,"b4":0,"c4":0,"d4":1}]}"""),
                p("Position", "\"ABSOLUTE\""),
                p("Width", """{"type":"length","px":200}"""),
                p("Height", """{"type":"length","px":200}"""),
                p("BackfaceVisibility", "\"HIDDEN\""),
                p("BackgroundColor", """{"srgb":{"r":0.6784313725490196,"g":0.8470588235294118,"b":0.9019607843137255},"original":{"r":173,"g":216,"b":230}}"""),
            ),
        )
    }

    @Test fun `T5 - a preserve-3d element WITHOUT backface hidden is never culled`() {
        // animated-001's `#flip` (`-065`): PRESERVE_3D + matrix3d, visibility
        // left at its initial `visible` → NONE regardless of the rotation.
        assertEquals(
            BackfaceCull.Decision.NONE,
            decide(
                p("Width", """{"type":"length","px":100}"""),
                p("Transform", """{"type":"functions","list":[{"fn":"matrix3d","a1":-1,"b1":0,"c1":0,"d1":0,"a2":0,"b2":1,"c2":0,"d2":0,"a3":0,"b3":0,"c3":-1,"d3":0,"a4":0,"b4":0,"c4":0,"d4":1}]}"""),
                p("TransformStyle", "\"PRESERVE_3D\""),
            ),
        )
    }

    // ── The degree rule itself (carried over verbatim) ──

    @Test fun `T5 - degree rule normalises and treats edge-on as visible`() {
        fun cfg(vararg fns: TransformFunction) = TransformConfig(functions = fns.toList())
        assertTrue(BackfaceCull.isBackFacing(cfg(TransformFunction.RotateY(180f))))
        assertTrue(BackfaceCull.isBackFacing(cfg(TransformFunction.RotateX(-135f))))
        // 270° ≡ −90° → edge-on → strictly-greater keeps it visible.
        assertFalse(BackfaceCull.isBackFacing(cfg(TransformFunction.RotateY(270f))))
        assertFalse(BackfaceCull.isBackFacing(cfg(TransformFunction.RotateY(90f))))
        assertFalse(BackfaceCull.isBackFacing(cfg(TransformFunction.RotateY(45f))))
        // Longhand axes add to the list total: 100 + 100 = 200 → back-facing.
        assertTrue(BackfaceCull.isBackFacing(TransformConfig(rotateY = 100f, functions = listOf(TransformFunction.RotateY(100f)))))
        // A `visible` flag short-circuits before the degree rule runs.
        assertEquals(
            BackfaceCull.Decision.NONE,
            BackfaceCull.decide(InteractionConfig(), cfg(TransformFunction.RotateY(180f)), Transform3DConfig()),
        )
    }

    // ── The own-face strip the decoration steps paint ──

    @Test fun `T5 - stripOwnFace removes background and borders and keeps everything else`() {
        val config = StyleApplier.extractConfig(
            listOf(
                p("Width", """{"type":"length","px":100}"""),
                p("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
                p("BorderTopWidth", """{"px":2}"""), p("BorderTopStyle", "\"SOLID\""),
                p("BorderTopColor", """{"srgb":{"r":0,"g":0,"b":0}}"""),
                p("Opacity", "0.5"),
                p("PaddingLeft", """{"px":8}"""),
            ),
        )
        assertTrue("fixture must carry a border", config.borders.sides.hasBorders)
        val stripped = BackfaceCull.stripOwnFace(config)
        // The back face — background + border sides + outline — is gone…
        assertNull(stripped.colors.backgroundColor)
        assertTrue(stripped.colors.backgroundImages.isEmpty())
        assertFalse(stripped.borders.sides.hasBorders)
        assertFalse(stripped.borders.outline.hasOutline)
        // …and the group opacity that wraps the (still visible) children,
        // the layout lane and the radius are exactly the extracted ones.
        assertEquals(0.5f, stripped.colors.opacity)
        assertSame(config.layout, stripped.layout)
        assertSame(config.borders.radius, stripped.borders.radius)
        assertSame(config.transforms, stripped.transforms)
    }

    @Test fun `T5 - a kept box-shadow on the culled face is breadcrumbed, not silent`() {
        // The shadow rides the effects step and stays painted (header): the
        // strip must record it. Wire: css-color currentcolor-003's BoxShadow.
        val shadow = p("BoxShadow", """[{"x":{"px":111},"y":{"px":0},"blur":{"px":5},"c":{"original":"currentColor"}}]""")
        com.styleconverter.runtime.PropertyTracker.reset()
        BackfaceCull.stripOwnFace(StyleApplier.extractConfig(listOf(shadow)))
        assertTrue(com.styleconverter.runtime.PropertyTracker.isUnhandled(BackfaceCull.SHADOW_KEPT_BREADCRUMB))
        // Negative control: no shadow, no breadcrumb.
        com.styleconverter.runtime.PropertyTracker.reset()
        BackfaceCull.stripOwnFace(StyleApplier.extractConfig(listOf(p("Width", """{"type":"length","px":100}"""))))
        assertFalse(com.styleconverter.runtime.PropertyTracker.isUnhandled(BackfaceCull.SHADOW_KEPT_BREADCRUMB))
        com.styleconverter.runtime.PropertyTracker.reset()
    }
}
