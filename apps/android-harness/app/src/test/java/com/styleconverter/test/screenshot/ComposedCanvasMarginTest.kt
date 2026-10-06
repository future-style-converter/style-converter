package com.styleconverter.test.screenshot

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * wave-52 lane L2 (M1) — unit pins for [resolveComposedCanvasMargin], the
 * composed canvas's per-side BODY MARGIN resolver, and for the
 * [CanvasMargin] split the Column spends it through.
 *
 * WHY it exists: the extractor emits html+body as ONE synthetic `body-root`
 * whose element children are SIBLINGS, and the three composed canvases read
 * that root for background and PADDING only — so a DECLARED
 * `body { margin-left: 200px }` never moved the flow stack on any platform,
 * while the ref keeps it (its `:where(html, body) { margin: 0 }` injection
 * has specificity 0 and loses to the author's `body {}` rule). MEASURED:
 * css-gaps/flex/flex-gap-decorations-027's whole web page sat exactly 200 px
 * left of the ref (wave51-fix web f 0.9012).
 *
 * The contract pinned here is the SAME one the web harness
 * (resolveCanvasMargin) and iOS (ComposedCaptureCanvas.resolvedMargin)
 * implement, so the three composed canvases cannot drift:
 *   - no body-root, or one declaring no margin ⇒ 0 on all four sides —
 *     every such capture is byte-identical (1426 of the 1435 documents);
 *   - a concrete px side is honoured per side, negatives kept (CSS allows
 *     negative margins) and split into inset (positive) / offset (negative);
 *   - runtime-dependent leaves (`auto`, `em`, `%`) keep 0 rather than guess.
 *
 * Payloads are the VERBATIM body-root bags of the wave51-fix per-test IR
 * (css-gaps 027, CSS2 s-11-1-1b-005 / -006, css-contain contain-body-dir-001's
 * `margin-right: auto`, css-pseudo first-letter-exclude's em margin shape).
 *
 * Plus the ONE-OWNER rule ([withCanvasOwnedBodyMargin]): once the Column
 * carries the body margin, the body-root composes WITHOUT the sides the
 * canvas owns, so the root-stack fold no longer ALSO emits them as gaps
 * (collapsed-border-*-rtl-overflow: 60 + 60 fold gaps put the table at 136
 * where the ref has 76), and the CSS 2.1 §8.3 guard (a table-internal body
 * has no used margin — s-11-1-1b-005's `display: table-cell` body).
 *
 * EXECUTED MUTATIONS (applied to ScreenshotCaptureScreen.kt, this class run
 * alone, source restored byte-exact — sha256 checked; recorded in
 * tools/titan/results/wave52-composed-canvas/mutations.log + _note.md):
 *   MA1 `side("MarginLeft")` → `side("MarginInlineStart")`
 *       → gaps027_bodyMarginLeft200_isTheFlowInset red.
 *   MA2 the §8.3 guard line deleted → s005_tableCellBody_hasNoUsedMargin red.
 *   MA3 withCanvasOwnedBodyMargin's body-root rewrite skipped (`return@map root`
 *       for every root) → oneOwner_collapsedBorder_bodyRootLosesItsFoldGaps red.
 *   MA4 CanvasMargin.neg() forced to 0 (negatives no longer offset)
 *       → negativeTop_isAnOffsetNotAnInset red.
 *
 * Plain junit:4.13.2 — pure function over the IR, only the Dp value type.
 */
class ComposedCanvasMarginTest {

    // Build an IRProperty from a raw IR leaf through the real serializer.
    private fun prop(type: String, json: String) =
        IRProperty(type = type, data = Json.parseToJsonElement(json))

    // A body-root component (meta.role == "body-root") carrying `props`.
    private fun bodyRoot(vararg props: IRProperty) =
        IRComponent(id = "b", name = "t__body", properties = props.toList(), role = "body-root")

    // A plain (non-body) root — never consulted by the resolver.
    private val plainRoot = IRComponent(id = "r", name = "t__0")

    @Test
    fun noBodyRoot_isZeroOnAllSides() {
        // Every document without a synthetic body bag: byte-identical.
        assertEquals(CanvasMargin.ZERO, resolveComposedCanvasMargin(listOf(plainRoot)))
    }

    @Test
    fun bodyRootWithoutMargin_isZeroOnAllSides() {
        // a98rgb-003's shape: a body-root that declares only a background.
        val body = bodyRoot(prop("BackgroundColor", """{"srgb":{"r":0.5,"g":0.5,"b":0.5}}"""))
        assertEquals(CanvasMargin.ZERO, resolveComposedCanvasMargin(listOf(body, plainRoot)))
    }

    @Test
    fun gaps027_bodyMarginLeft200_isTheFlowInset() {
        // The VERBATIM gaps-027 body-root: `body { margin: 0 0 0 200px }`.
        // The flow stack's left inset becomes frame 16 + margin 200 = 216 —
        // the ref's container border sits at image x 216–217.
        val body = bodyRoot(
            prop("MarginTop", """{"px":0}"""), prop("MarginRight", """{"px":0}"""),
            prop("MarginBottom", """{"px":0}"""), prop("MarginLeft", """{"px":200}"""),
        )
        val m = resolveComposedCanvasMargin(listOf(body, plainRoot))
        assertEquals(CanvasMargin(0.dp, 0.dp, 0.dp, 200.dp), m)
        assertEquals(200.dp, m.insetLeft)
        assertEquals(216.dp, CanvasPadding.DEFAULT.left + m.insetLeft)
        // No negative part anywhere → no offset.
        assertEquals(0.dp, m.offsetX)
        assertEquals(0.dp, m.offsetY)
        // The body's content box loses the 200 (027's children see 358 − 200).
        assertEquals(200.dp, m.insetHorizontal)
    }

    @Test
    fun s006_marginTop40Sides8_stacksOnTheFrame() {
        // CSS2/css21-errata/s-11-1-1b-006's body-root: `margin: 40px 8px 8px`.
        val body = bodyRoot(
            prop("MarginTop", """{"px":40}"""), prop("MarginRight", """{"px":8}"""),
            prop("MarginBottom", """{"px":8}"""), prop("MarginLeft", """{"px":8}"""),
        )
        val m = resolveComposedCanvasMargin(listOf(body))
        assertEquals(CanvasMargin(40.dp, 8.dp, 8.dp, 8.dp), m)
        assertEquals(56.dp, CanvasPadding.DEFAULT.top + m.insetTop)
        assertEquals(16.dp, m.insetHorizontal)
    }

    @Test
    fun negativeTop_isAnOffsetNotAnInset() {
        // The s-11-1-1b-005 margin SHAPE (`margin-top: -15px`, sides 8) on a
        // block body — its real body is a table cell and takes no margin
        // (next test). A negative margin pulls content UP — padding cannot, so
        // it rides the Column's offset while the positive sides stay insets.
        val body = bodyRoot(
            prop("MarginTop", """{"px":-15}"""), prop("MarginRight", """{"px":8}"""),
            prop("MarginBottom", """{"px":8}"""), prop("MarginLeft", """{"px":8}"""),
        )
        val m = resolveComposedCanvasMargin(listOf(body))
        assertEquals((-15).dp, m.top)
        assertEquals(0.dp, m.insetTop)
        assertEquals((-15).dp, m.offsetY)
        assertEquals(8.dp, m.insetLeft)
        assertEquals(0.dp, m.offsetX)
    }

    @Test
    fun runtimeDependentLeaves_keepZero() {
        // `margin-right: auto` (contain-body-dir-00x) and `margin: 1em`
        // (first-letter-exclude-*) have no absolute value on this canvas —
        // the documented contract is 0, never a guess.
        val body = bodyRoot(
            prop("MarginRight", "\"auto\""),
            prop("MarginTop", """{"original":{"v":1,"u":"EM"}}"""),
        )
        assertEquals(CanvasMargin.ZERO, resolveComposedCanvasMargin(listOf(body)))
    }

    @Test
    fun wrappedPxLeaf_isReadLikeThePaddingResolver() {
        // The typed-wrapper generation `{"original":{"px":N}}` resolves through
        // the same ValueExtractors path the padding resolver uses.
        val body = bodyRoot(prop("MarginLeft", """{"original":{"px":60}}"""))
        assertEquals(60.dp, resolveComposedCanvasMargin(listOf(body)).left)
    }
    @Test
    fun s005_tableCellBody_hasNoUsedMargin() {
        // CSS2/css21-errata/s-11-1-1b-005's body-root, VERBATIM: `display:
        // table-cell` + `margin: -15px 8px 8px`. CSS 2.1 §8.3: margins do not
        // apply to table-internal boxes, so Chrome uses none — ZERO, no strip,
        // the capture stays byte-identical (web P 0.9947).
        val body = bodyRoot(
            prop("OverflowX", "\"HIDDEN\""), prop("OverflowY", "\"HIDDEN\""),
            prop("Display", "\"TABLE_CELL\""),
            prop("BorderSpacing", """{"type":"single","px":0}"""),
            prop("BackgroundColor", """{"srgb":{"r":1,"g":1,"b":1},"original":"white"}"""),
            prop("MarginTop", """{"px":-15}"""), prop("MarginRight", """{"px":8}"""),
            prop("MarginBottom", """{"px":8}"""), prop("MarginLeft", """{"px":8}"""),
            prop("Width", """{"type":"length","px":20}"""), prop("Height", """{"type":"length","px":20}"""),
        )
        assertEquals(CanvasMargin.ZERO, resolveComposedCanvasMargin(listOf(body)))
        // …and a ZERO margin owns nothing: the same list instance back.
        val roots = listOf(body)
        assertSame(roots, withCanvasOwnedBodyMargin(roots, CanvasMargin.ZERO))
    }

    @Test
    fun oneOwner_collapsedBorder_bodyRootLosesItsFoldGaps() {
        // css-tables/collapsed-border-vertical-rtl-overflow's body-root,
        // VERBATIM: `margin: 60px`. Through wave 51 the fold read its 60/60 as
        // declared block margins (stripped from the box, emitted as gaps); with
        // the Column now insetting 60, the body-root must compose WITHOUT them.
        val body = bodyRoot(
            prop("MarginTop", """{"px":60}"""), prop("MarginRight", """{"px":60}"""),
            prop("MarginBottom", """{"px":60}"""), prop("MarginLeft", """{"px":60}"""),
        )
        val m = resolveComposedCanvasMargin(listOf(body, plainRoot))
        assertEquals(CanvasMargin(60.dp, 60.dp, 60.dp, 60.dp), m)
        val owned = withCanvasOwnedBodyMargin(listOf(body, plainRoot), m)
        // The body-root lost all four longhands; the plain root is the same object.
        assertTrue(owned[0].properties.none { it.type.startsWith("Margin") })
        assertSame(plainRoot, owned[1])
        // The fold: wave-51 plan (60, 60, stripped) vs the owned plan (0, 0).
        assertEquals(RootStackMargin(60f, 60f, stripDeclared = true), composedRootStackPlan(body, hostActive = false))
        assertEquals(RootStackMargin(0f, 0f, stripDeclared = false), composedRootStackPlan(owned[0], hostActive = false))
    }

    @Test
    fun oneOwner_keepsUnresolvedSides_onTheBox() {
        // `margin-left: 100px; margin-right: auto`: the canvas owns only the
        // px side; `auto` stays on the body-root (it was never the canvas's).
        val body = bodyRoot(prop("MarginLeft", """{"px":100}"""), prop("MarginRight", "\"auto\""))
        val owned = withCanvasOwnedBodyMargin(listOf(body), resolveComposedCanvasMargin(listOf(body)))
        assertEquals(listOf("MarginRight"), owned[0].properties.map { it.type })
    }
}
