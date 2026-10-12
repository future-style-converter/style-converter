package com.styleconverter.runtime.borders.sides

// Wave 55 (lane L6, unit U2 PO-sides-android) — pin P3: on StyleApplier's
// legacy route the border sides chain INNER of the background and paint
// behind the content (CSS 2.1 Appendix E §E.2: background, border, then
// descendants). Before U2, BordersFacade.apply chained the band OUTER of
// ColorApplier and painted it AFTER drawContent() (androidx Modifier.border's
// fast path, or the slow path's drawWithContent { drawContent(); paintSide… }),
// so a parent's band covered every child that crossed it: WPT
// flex-abspos-staticpos-align-self-safe-001's 3px black `.flex` band across
// its yellow abspos children (1152 px on Android, 0 on the ref).
//
// Verbatim wires (tools/titan/runs/wave54-final/sections/…/per-test-ir/), each
// the component's `properties` array exactly as the wire lists it (fix-r1: the
// first cut interleaved the sides; skeptic D3):
//   css-flexbox  wpt__css-flexbox__abspos__flex-abspos-staticpos-align-self-safe-001
//                `…__0-023` — the SOLID fast path;
//   css-backgrounds  wpt__css-backgrounds__background-clip-002 `…__2-095` — the
//                DOTTED slow path (its order is drawInOrder's, pinned by P1);
//   css-cascade  wpt__css-cascade__revert-layer-015 `…__0-077` — an INSET <input>
//                that clips (`overflow: clip`): P3h, fix-r1 (skeptic D1);
//   css-anchor-position  wpt__css-anchor-position__anchor-scope-scroll `…__0-781` —
//                a SOLID box with `overflow-y: hidden`: P3i, fix-r1.
// The JVM chain technique of BackfaceCullChainTest (extractConfig →
// applyConfig, folded with foldIn; no drawing — android.graphics is stubbed).

// Compose's modifier chain type, walked by foldIn.
import androidx.compose.ui.Modifier
// The chain under test and the facade whose default must not change.
import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.borders.BehindContentBorderElement
import com.styleconverter.runtime.borders.BordersFacade
// Parses the verbatim wire payloads below.
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
// JUnit4 (this module's runner).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderSidesBehindContentTest {

    /** IR property pair, as StyleApplier.extractConfig consumes it. */
    private fun p(type: String, json: String): Pair<String, JsonElement?> = type to Json.parseToJsonElement(json)

    /** A component's verbatim `properties` array → the (type, data) pairs extractConfig consumes. */
    private fun wire(json: String): List<Pair<String, JsonElement?>> =
        Json.parseToJsonElement(json).jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content to it.jsonObject["data"] }

    /** The `.flex` container of flex safe-001 `…__0-023`, verbatim. */
    private val flexSafe001 = wire("""[{"type":"Display","data":"FLEX"},{"type":"Height","data":{"type":"length","px":50}},{"type":"Width","data":{"type":"length","px":50}},{"type":"BorderTopWidth","data":{"px":3}},{"type":"BorderRightWidth","data":{"px":3}},{"type":"BorderBottomWidth","data":{"px":3}},{"type":"BorderLeftWidth","data":{"px":3}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"VerticalAlign","data":{"type":"keyword","value":"TOP"}},{"type":"MarginTop","data":{"px":20}},{"type":"MarginRight","data":{"px":20}},{"type":"MarginBottom","data":{"px":20}},{"type":"MarginLeft","data":{"px":20}},{"type":"Position","data":"RELATIVE"},{"type":"FlexDirection","data":"ROW"}]""")

    /** The DOTTED box of background-clip-002 `…__2-095`, verbatim. */
    private val backgroundClip002 = wire("""[{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"BorderTopWidth","data":{"px":5}},{"type":"BorderRightWidth","data":{"px":5}},{"type":"BorderBottomWidth","data":{"px":5}},{"type":"BorderLeftWidth","data":{"px":5}},{"type":"BorderTopStyle","data":"DOTTED"},{"type":"BorderRightStyle","data":"DOTTED"},{"type":"BorderBottomStyle","data":"DOTTED"},{"type":"BorderLeftStyle","data":"DOTTED"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}},{"type":"Bottom","data":{"px":160}},{"type":"Height","data":{"type":"length","px":100}},{"type":"PaddingTop","data":{"px":25}},{"type":"PaddingRight","data":{"px":25}},{"type":"PaddingBottom","data":{"px":25}},{"type":"PaddingLeft","data":{"px":25}},{"type":"Position","data":"RELATIVE"},{"type":"Width","data":{"type":"length","px":100}}]""")

    /** The UA-styled <input> of revert-layer-015 `…__0-077`, verbatim: 2px INSET, white, `overflow: clip`. */
    private val revertLayer015Input = wire("""[{"type":"Position","data":"STATIC"},{"type":"Width","data":{"type":"length","px":145}},{"type":"Height","data":{"type":"length","px":15}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":1}},{"type":"PaddingRight","data":{"px":2}},{"type":"PaddingBottom","data":{"px":1}},{"type":"PaddingLeft","data":{"px":2}},{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"INSET"},{"type":"BorderRightStyle","data":"INSET"},{"type":"BorderBottomStyle","data":"INSET"},{"type":"BorderLeftStyle","data":"INSET"},{"type":"BorderTopColor","data":{"srgb":{"r":0.4627450980392157,"g":0.4627450980392157,"b":0.4627450980392157},"original":{"r":118,"g":118,"b":118}}},{"type":"BorderRightColor","data":{"srgb":{"r":0.4627450980392157,"g":0.4627450980392157,"b":0.4627450980392157},"original":{"r":118,"g":118,"b":118}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0.4627450980392157,"g":0.4627450980392157,"b":0.4627450980392157},"original":{"r":118,"g":118,"b":118}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0.4627450980392157,"g":0.4627450980392157,"b":0.4627450980392157},"original":{"r":118,"g":118,"b":118}}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":{"r":255,"g":255,"b":255}}},{"type":"Display","data":"INLINE_BLOCK"},{"type":"OverflowX","data":"CLIP"},{"type":"OverflowY","data":"CLIP"}]""")

    /** The scoped scroller of anchor-scope-scroll `…__0-781`, verbatim: 1px SOLID black, `overflow-y: hidden`. */
    private val anchorScopeScroll = wire("""[{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":200}},{"type":"OverflowY","data":"HIDDEN"},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":1}},{"type":"BorderBottomWidth","data":{"px":1}},{"type":"BorderLeftWidth","data":{"px":1}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]""")

    /** applyConfig's element class names, outermost → innermost. */
    private fun names(props: List<Pair<String, JsonElement?>>): List<String> =
        StyleApplier.applyConfig(Modifier, StyleApplier.extractConfig(props, wptCaptureMode = true))
            .foldIn(listOf<String>()) { acc, e -> acc + e.javaClass.simpleName }

    @Test fun `P3a - the verbatim flex wire chains the band INNER of the background-less colors step`() {
        val n = names(flexSafe001)
        // The fast path is the behind-content band, never androidx's border node.
        assertEquals("one behind-content band in $n", 1, n.count { it == "BehindContentBorderElement" })
        assertFalse("androidx border must not install: $n", n.contains("BorderModifierNodeElement"))
        // It sits inner of the margin / sizing frame (padding is the innermost step 8).
        assertTrue("band inner of the size frame in $n",
            n.indexOf("BehindContentBorderElement") > n.indexOfLast { it.startsWith("Size") })
    }

    @Test fun `P3b - with a background the fast-path band sits AFTER (inner of) the Background element`() {
        // The flex wire plus a fill: §E.2 step 2 orders background, then border.
        val n = names(flexSafe001 + p("BackgroundColor", """{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}"""))
        assertTrue("Background present in $n", n.contains("BackgroundElement"))
        assertTrue("band after Background in $n", n.indexOf("BehindContentBorderElement") > n.indexOf("BackgroundElement"))
    }

    @Test fun `P3c - the verbatim DOTTED wire's slow-path draw node sits AFTER the Background element`() {
        val n = names(backgroundClip002)
        // No uniform-solid band here: the slow path's drawWithContent is the band.
        assertFalse(n.contains("BehindContentBorderElement"))
        // The background fill comes first …
        val bg = n.indexOf("BackgroundElement")
        assertTrue("Background present in $n", bg >= 0)
        // … and the only DrawWithContent node (no outline, no opacity on this wire) is inner of it.
        assertEquals("one drawWithContent node in $n", 1, n.count { it == "DrawWithContentElement" })
        assertTrue("slow-path band after Background in $n", n.indexOf("DrawWithContentElement") > bg)
    }

    @Test fun `P3d - under opacity the band joins the element's group (css-color-4 §3_3)`() {
        // Synthetic: the flex wire at opacity 0.5 — OpacityApplier's saveLayer group
        // is a DrawWithContent node; the band must be inner of it, not outside.
        val n = names(flexSafe001 + p("Opacity", """{"alpha":0.5,"original":{"type":"number","value":0.5}}"""))
        val group = n.indexOf("DrawWithContentElement")
        assertTrue("opacity group present in $n", group >= 0)
        assertTrue("band inside the group in $n", n.indexOf("BehindContentBorderElement") > group)
    }

    @Test fun `P3e - BordersFacade's default chain is unchanged for every other caller`() {
        // The facade's default (sidesBehindContent = false) still paints androidx's
        // band after the content: StyleApplier's legacy route is the only opt-in.
        val cfg = BordersFacade.extractConfig(flexSafe001)
        val n = BordersFacade.apply(Modifier, cfg).foldIn(listOf<String>()) { acc, e -> acc + e.javaClass.simpleName }
        assertEquals(listOf("BorderModifierNodeElement"), n)
        // The opt-in route leaves the sides out of apply() and installs them on their own.
        assertEquals(emptyList<String>(), BordersFacade.apply(Modifier, cfg, sidesBehindContent = true)
            .foldIn(listOf<String>()) { acc, e -> acc + e.javaClass.simpleName })
        assertEquals(listOf("BehindContentBorderElement"), BordersFacade.applySidesBehindContent(Modifier, cfg)
            .foldIn(listOf<String>()) { acc, e -> acc + e.javaClass.simpleName })
    }

    @Test fun `P3f - the behind-content fast path is the U1 painter on RectangleShape`() {
        // Same width / colour / shape androidx `border(width, color)` defaults to.
        val cfg = BordersFacade.extractConfig(flexSafe001).sides
        val el = BorderSideApplier.applyBorders(Modifier, cfg, behindContent = true)
            .foldIn(listOf<Modifier.Element>()) { acc, e -> acc + e }.single() as BehindContentBorderElement
        assertEquals(androidx.compose.ui.unit.Dp(3f), el.width)
        assertEquals(androidx.compose.ui.graphics.RectangleShape, el.shape)
    }

    @Test fun `P3g - source - the slow path paints the sides first through drawInOrder`() {
        // The JVM cannot draw, so the slow path's ORDER is pinned in its source:
        // behindContent → drawInOrder(sides, content); default → content, then sides.
        val src = source("runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt")
        // Code lines only: comments may mention drawContent() freely.
        val code = src.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
        assertTrue("behind-content slow path: sides, then content",
            code.contains("if (behindContent) drawInOrder({ paintSides() }, { drawContent() }) else { drawContent(); paintSides() }"))
        // Exactly the two drawContent() call sites of that one line.
        assertEquals("drawContent() call sites", 2, Regex("""drawContent\(\)""").findAll(code).count())
    }

    // ── P3h / P3i (fix-r1, skeptic D1): a box that CLIPS keeps the band over its content ──
    // css-overflow-3 §3 clips a non-`visible` box's content at its PADDING edge, so in CSS the
    // content never reaches the band; Compose's step-7 clip sits at the BORDER box, so a
    // behind-content band would let clipped content (revert-layer-015's UA-widget replica,
    // whose 153x21 canvas overflows its 145x15 content box) paint over the right / bottom band.
    // The pre-wave-55 chain — band OUTER of the background, drawn after the content — is kept.

    @Test fun `P3h - a clipping INSET input keeps its slow-path band outer of the background`() {
        val n = names(revertLayer015Input)
        // The band is the slow path's drawWithContent node, never ours …
        assertFalse("no behind-content band on a clipping box: $n", n.contains("BehindContentBorderElement"))
        assertEquals("one drawWithContent node in $n", 1, n.count { it == "DrawWithContentElement" })
        // … sitting OUTER of (before) the white fill, exactly where the pre-wave-55 chain put it.
        assertTrue("band before Background in $n", n.indexOf("DrawWithContentElement") in 0 until n.indexOf("BackgroundElement"))
    }

    @Test fun `P3i - a clipping SOLID box keeps androidx's band (drawn after its clipped content)`() {
        val n = names(anchorScopeScroll)
        // overflow-y: hidden (x used as auto) clips both axes: the androidx node stays …
        assertEquals("one androidx band in $n", 1, n.count { it == "BorderModifierNodeElement" })
        // … and ours is not installed.
        assertFalse("no behind-content band on a clipping box: $n", n.contains("BehindContentBorderElement"))
    }

    /** A repo file, located by walking up from the test working dir (the BackfaceCullChainTest idiom). */
    private fun source(rel: String): String {
        // Gradle runs tests from the module dir; walk up to the repo root.
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
        return java.io.File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }
}
