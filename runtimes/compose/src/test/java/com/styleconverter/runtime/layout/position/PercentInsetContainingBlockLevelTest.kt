package com.styleconverter.runtime.layout.position

// Wave 50 (lane B2) — JVM pins for the LEVEL of the containing-block channel
// a percentage inset resolves against (CSS 2.1 §10.1 + §9.4.3).
//
// Every payload below is copied VERBATIM out of the frozen wave49-final
// per-test IR the Android capture actually consumed:
//   tools/titan/runs/wave49-final/sections/css-position/per-test-ir/
//     wpt__css-position__position-relative-00{1,2,6,8}.json
// so what these tests assert is the wire the corpus produces.
//
// What they pin, in one sentence: `PercentInsetResolve` was fed the block this
// element PUBLISHES FOR ITS CHILDREN instead of the block the element is laid
// out in, and on exactly one corpus carrier those two differ.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.variables.ContainingBlock
import com.styleconverter.runtime.core.variables.DynamicValueResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentInsetContainingBlockLevelTest {

    /** IR property, as the decoder hands it to the renderer. */
    private fun ir(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    /** IR property pair, as `extractPositionConfig` consumes it. */
    private fun p(type: String, json: String): Pair<String, JsonElement?> =
        type to Json.parseToJsonElement(json)

    // ── The two corpus components of position-relative-006, verbatim ──────

    /** `wpt__css-position__position-relative-006__1-213` — the RED parent. */
    private val redParent = listOf(
        ir("Width", """{"type":"length","px":100}"""),
        ir("MinHeight", """{"type":"length","px":100}"""),
        ir("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
    )

    /** `position-relative-006__1__0-214` — the GREEN child. */
    private val greenChild = listOf(
        ir("Width", """{"type":"length","px":100}"""),
        ir("Height", """{"type":"length","px":100}"""),
        ir("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0}}"""),
        ir("Top", "-10000"),
        ir("Position", "\"RELATIVE\""),
    )

    // ── L1/L2: the channel selector ───────────────────────────────────────

    @Test fun `L1 - an unpublished element channel falls back to the ambient read`() {
        // The state of the tree until the ComponentRenderer seam lands: the
        // local is null, so the frozen (wrong-level) behaviour is kept rather
        // than silently changing every committed capture.
        val ambient = ContainingBlock(widthPx = 12f, heightPx = 34f)
        assertSame(ambient, ElementContainingBlock.containingBlockFor(null, ambient))
    }

    @Test fun `L1 - the fallback leaves a PropertyTracker breadcrumb`() {
        // CLAUDE.md's no-silent-fallthrough rule: a capture that kept the
        // legacy geometry must be visible in the coverage report. Same
        // reset/isUnhandled/reset shape as the FontSizeAdjustTest precedent,
        // so the shared global tracker is left as this test found it.
        com.styleconverter.runtime.PropertyTracker.reset()
        ElementContainingBlock.containingBlockFor(null, ContainingBlock())
        assertTrue(
            com.styleconverter.runtime.PropertyTracker
                .isUnhandled(ElementContainingBlock.UNPUBLISHED_BREADCRUMB)
        )
        com.styleconverter.runtime.PropertyTracker.reset()
    }

    @Test fun `L2 - the published path leaves NO breadcrumb`() {
        // Negative control for the pin above: the breadcrumb must mark the
        // fallback only, or it would read as "always degraded".
        com.styleconverter.runtime.PropertyTracker.reset()
        ElementContainingBlock.containingBlockFor(ContainingBlock(1f, 2f), ContainingBlock())
        assertFalse(
            com.styleconverter.runtime.PropertyTracker
                .isUnhandled(ElementContainingBlock.UNPUBLISHED_BREADCRUMB)
        )
        com.styleconverter.runtime.PropertyTracker.reset()
    }

    @Test fun `L2 - a published element channel wins over the ambient read`() {
        // The post-seam state: the element's OWN containing block is used and
        // the child-level ambient value is ignored.
        val element = ContainingBlock(widthPx = 100f, heightPx = null)
        val ambient = ContainingBlock(widthPx = 100f, heightPx = 100f)
        assertSame(element, ElementContainingBlock.containingBlockFor(element, ambient))
    }

    // ── L3: the one corpus carrier where the two levels differ ────────────

    @Test fun `L3 - the two levels differ on position-relative-006 and only there`() {
        // The block the RED parent publishes for the green child: Width 100,
        // and MIN-height is not height, so the block axis stays indefinite.
        val elementCb = DynamicValueResolver.childContainingBlock(redParent, ContainingBlock())
        assertEquals(100f, elementCb.widthPx)
        assertNull(elementCb.heightPx)
        // The block the GREEN CHILD ITSELF publishes for ITS children — the
        // value a `Modifier.composed` factory reads, because it materialises
        // inside the child's own CompositionLocalProvider.
        val ambientCb = DynamicValueResolver.childContainingBlock(greenChild, elementCb)
        assertEquals(100f, ambientCb.widthPx)
        assertEquals(100f, ambientCb.heightPx)
        // The two levels give DIFFERENT answers on the block axis — the whole
        // reason this carrier is the corpus discriminator.
        assertTrue(elementCb.heightPx != ambientCb.heightPx)
    }

    @Test fun `L3 - the child-level read reproduces the frozen Android capture`() {
        // -10000 % of the child's OWN 100px block axis is -10 000 px, which is
        // bit-for-bit the legacy number-as-pixels value — so the wave-49
        // repair could not move a pixel, and wave49-final
        // css-position/position-relative-006 android stayed f 0.9966 with the
        // green square 10 000 px off the canvas and the RED parent bare.
        val cfg = PositionExtractor.extractPositionConfig(greenChild.map { it.type to it.data })
        val elementCb = DynamicValueResolver.childContainingBlock(redParent, ContainingBlock())
        val ambientCb = DynamicValueResolver.childContainingBlock(greenChild, elementCb)
        val wrongLevel = PercentInsetResolve.resolve(cfg, ambientCb)
        assertEquals(-10000f, wrongLevel.top?.value)
        assertEquals(cfg.top?.value, wrongLevel.top?.value)
    }

    @Test fun `L3 - the element-level read resolves the inset to zero`() {
        // css-position-3 §relpos-insets, which position-relative-006's own
        // <meta name=assert> states verbatim: a percentage against an
        // INDEFINITE dimension resolves to zero. The green square then sits
        // over the red parent and the reference's "filled green square and no
        // red" is what the capture paints.
        val cfg = PositionExtractor.extractPositionConfig(greenChild.map { it.type to it.data })
        val elementCb = DynamicValueResolver.childContainingBlock(redParent, ContainingBlock())
        val right = PercentInsetResolve.resolve(
            cfg,
            ElementContainingBlock.containingBlockFor(elementCb, ambient = ContainingBlock()),
        )
        assertEquals(0f, right.top?.value)
        assertEquals(0f, PositionApplier.resolvedOffset(right).y.value, 0f)
    }

    // ── L4/L5: the other six carriers are level-INSENSITIVE ───────────────

    @Test fun `L4 - position-relative-002 resolves identically at both levels`() {
        // The green div's parent is an inline <span> with no declared size
        // (`position-relative-002__1__0-195`, meta.sourceTag "span"), so the
        // element-level block is (null, null) and the guard keeps the legacy
        // value; the child's OWN block is (100, 100) and -100 % of 100 is
        // -100 px — the same number. Neither level moves this cell, which is
        // why the CSS 2.1 §10.1 republish this lane did NOT implement cannot
        // change it either.
        val childProps = listOf(
            ir("Width", """{"type":"length","px":100}"""),
            ir("Height", """{"type":"length","px":100}"""),
            ir("Position", "\"RELATIVE\""),
            ir("Top", "-100"), ir("Left", "-100"),
        )
        val spanProps = listOf(
            ir("Position", "\"RELATIVE\""),
            ir("Top", """{"px":100}"""), ir("Left", """{"px":100}"""),
        )
        val cfg = PositionExtractor.extractPositionConfig(childProps.map { it.type to it.data })
        val elementCb = DynamicValueResolver.childContainingBlock(spanProps, ContainingBlock(100f, 100f))
        val ambientCb = DynamicValueResolver.childContainingBlock(childProps, elementCb)
        assertEquals(-100f, PercentInsetResolve.resolve(cfg, elementCb).top?.value)
        assertEquals(-100f, PercentInsetResolve.resolve(cfg, ambientCb).top?.value)
        assertEquals(-100f, PercentInsetResolve.resolve(cfg, elementCb).start?.value)
        assertEquals(-100f, PercentInsetResolve.resolve(cfg, ambientCb).start?.value)
    }

    @Test fun `L5 - position-relative-008 resolves identically at both levels`() {
        // The `<tr>` carries `top: 100%`; its `<tbody>` parent has NO
        // properties at all and the `<tr>` declares no size either, so BOTH
        // levels are (null, null) → the channel-gap guard → legacy 100 px.
        val trProps = listOf(ir("Position", "\"RELATIVE\""), ir("Top", "100"))
        val tbodyProps = emptyList<IRProperty>()
        val cfg = PositionExtractor.extractPositionConfig(trProps.map { it.type to it.data })
        val elementCb = DynamicValueResolver.childContainingBlock(tbodyProps, ContainingBlock())
        val ambientCb = DynamicValueResolver.childContainingBlock(trProps, elementCb)
        assertEquals(cfg, PercentInsetResolve.resolve(cfg, elementCb))
        assertEquals(cfg, PercentInsetResolve.resolve(cfg, ambientCb))
    }

    @Test fun `L6 - the 001-005 span family resolves identically at both levels`() {
        // position-relative-001/003/004/005: a relatively-positioned <span>
        // with `top/left: ±100%` inside a 100×100 red div. The span declares
        // no size, so its OWN block is (null, null) → guard → legacy ±100 px,
        // while its element-level block is (100, 100) → ±100 px. Same number
        // on both levels, which is the whole reason this lane predicts ONE
        // moved cell and not five.
        val spanProps = listOf(
            ir("Position", "\"RELATIVE\""), ir("Top", "100"), ir("Left", "100"),
        )
        val redDiv = listOf(
            ir("Width", """{"type":"length","px":100}"""),
            ir("Height", """{"type":"length","px":100}"""),
        )
        val cfg = PositionExtractor.extractPositionConfig(spanProps.map { it.type to it.data })
        val elementCb = DynamicValueResolver.childContainingBlock(redDiv, ContainingBlock())
        val ambientCb = DynamicValueResolver.childContainingBlock(spanProps, elementCb)
        assertEquals(100f, PercentInsetResolve.resolve(cfg, elementCb).top?.value)
        assertEquals(100f, PercentInsetResolve.resolve(cfg, ambientCb).top?.value)
    }
}
