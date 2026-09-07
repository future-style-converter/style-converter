package app.parsing.css.properties.longhands.images

// wave-37 lane W2 — `object-view-box`'s other two <basic-shape-rect>
// spellings (css-shapes-1 §3.2, admitted by css-images-5 §3.1).
//
// The parser recognised `none` and `inset()` only, so `rect()` / `xywh()`
// returned null, the declaration was dropped, and the image rendered
// un-cropped — measured on object-view-box-{rect,xywh}{,-percentage},
// which paint the full four-quadrant source where the ref shows one
// quadrant (ssim 0.9740, coverage veto, all four).
//
// Pinned invariants:
//   1. The exact corpus spellings parse to their variants.
//   2. Percentage arguments survive (they cannot ride an IRLength at all —
//      the new arms use IRLengthPercentage for exactly this reason).
//   3. rect()/xywh() take EXACTLY four arguments: unlike inset(), neither
//      inherits the margin grammar's 1/2/3-value shorthand.
//   4. `inset()` and `none` are byte-unchanged.

import app.irmodels.IRLengthPercentage
import app.irmodels.properties.images.ObjectViewBoxProperty
import app.irmodels.properties.images.ObjectViewBoxValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ObjectViewBoxPropertyParserTest {

    private fun value(css: String) =
        (ObjectViewBoxPropertyParser.parse(css) as? ObjectViewBoxProperty)?.value

    private fun px(v: IRLengthPercentage) = (v as IRLengthPercentage.Length).length.pixels
    private fun pct(v: IRLengthPercentage) = (v as IRLengthPercentage.Percentage).percentage.value

    @Test
    fun `xywh parses to origin plus size`() {
        // EXACT declaration from WPT css-images/object-view-box-xywh.html.
        val v = value("xywh(25px 50px 25px 50px)")
        assertIs<ObjectViewBoxValue.Xywh>(v)
        assertEquals(25.0, px(v.x))
        assertEquals(50.0, px(v.y))
        assertEquals(25.0, px(v.width))
        assertEquals(50.0, px(v.height))
    }

    @Test
    fun `rect parses to four edges`() {
        // EXACT declaration from WPT css-images/object-view-box-rect.html.
        val v = value("rect(50px 50px 100px 25px)")
        assertIs<ObjectViewBoxValue.Rect>(v)
        assertEquals(50.0, px(v.top))
        assertEquals(50.0, px(v.right))
        assertEquals(100.0, px(v.bottom))
        assertEquals(25.0, px(v.left))
    }

    @Test
    fun `percentage arguments survive on both spellings`() {
        val x = value("xywh(50% 50% 50% 50%)")
        assertIs<ObjectViewBoxValue.Xywh>(x)
        assertEquals(50.0, pct(x.x))
        val r = value("rect(50% 100% 100% 50%)")
        assertIs<ObjectViewBoxValue.Rect>(r)
        assertEquals(100.0, pct(r.right))
    }

    @Test
    fun `rect and xywh reject anything but four arguments`() {
        // inset()'s 1/2/3-value shorthand is NOT part of these two
        // productions; accepting it would invent a rectangle.
        assertNull(ObjectViewBoxPropertyParser.parse("rect(10px)"))
        assertNull(ObjectViewBoxPropertyParser.parse("xywh(10px 20px)"))
        assertNull(ObjectViewBoxPropertyParser.parse("rect(10px 20px 30px 40px 50px)"))
        // A non-<length-percentage> argument invalidates the declaration.
        assertNull(ObjectViewBoxPropertyParser.parse("xywh(10px auto 30px 40px)"))
        // The shape grammar's `round <radius>` tail has no view-box meaning.
        assertNull(ObjectViewBoxPropertyParser.parse("rect(0px 0px 1px 1px round 2px)"))
    }

    @Test
    fun `none and inset are unchanged`() {
        assertIs<ObjectViewBoxValue.None>(value("none"))
        val i = value("inset(50px 0px 0px 0px)")
        assertIs<ObjectViewBoxValue.Inset>(i)
        assertEquals(50.0, i.top.pixels)
        // inset()'s margin-style shorthand still expands.
        val two = value("inset(10px 20px)")
        assertIs<ObjectViewBoxValue.Inset>(two)
        assertEquals(10.0, two.bottom.pixels)
        assertEquals(20.0, two.left.pixels)
    }
}
