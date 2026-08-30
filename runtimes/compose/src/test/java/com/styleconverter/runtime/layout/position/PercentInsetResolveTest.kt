package com.styleconverter.runtime.layout.position

// Wave 49 (lane A7) — JVM pins for PERCENTAGE INSETS
// (CSS 2.1 §9.4.3 + css-position-3 §relpos-insets).
//
// Every payload below is copied VERBATIM out of the frozen wave-48
// per-test IR the runtimes actually consumed:
//   tools/titan/runs/wave48-final/sections/css-position/per-test-ir/
//     wpt__css-position__position-relative-00{1,2,6,8}.json
// so the wire shape these tests assert against is the wire shape the
// corpus produces, not an invented one. The 7 css-position tests named
// here are the ONLY components in all 30 frozen sections whose inset
// data is a bare JSON number (the converter's percentage wire) — the
// scan is written up in PercentInsetResolve's header.

import com.styleconverter.runtime.core.variables.ContainingBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PercentInsetResolveTest {

    /** IR property pair as `extractPositionConfig` consumes them. */
    private fun p(type: String, json: String): Pair<String, JsonElement?> =
        type to Json.parseToJsonElement(json)

    // ── P1: the wire discriminator ────────────────────────────────────────

    @Test fun `P1 - a bare number is the percentage wire`() {
        // InsetValueSerializer emits IRPercentage as a bare scalar; this is
        // literally position-relative-006's Top payload.
        assertEquals(-10000f, PercentInsetResolve.percentOf(Json.parseToJsonElement("-10000")))
    }

    @Test fun `P1 - every length shape stays off the percentage channel`() {
        // The two object shapes the corpus uses for lengths — 2932 of the
        // 2953 inset payloads in the frozen sections are one of these.
        assertNull(PercentInsetResolve.percentOf(Json.parseToJsonElement("""{"px":-100}""")))
        assertNull(
            PercentInsetResolve.percentOf(
                Json.parseToJsonElement("""{"type":"length","px":100}""")
            )
        )
        // `auto` is a quoted primitive, not a number.
        assertNull(PercentInsetResolve.percentOf(Json.parseToJsonElement("\"auto\"")))
        // calc()/anchor() carriers ride an object with `expr`.
        assertNull(
            PercentInsetResolve.percentOf(
                Json.parseToJsonElement("""{"expr":"calc(anchor(bottom) + 5px)"}""")
            )
        )
        assertNull(PercentInsetResolve.percentOf(null))
    }

    // ── P2: extraction records the percentage WITHOUT moving the Dp slot ──

    @Test fun `P2 - position-relative-006 child extracts percent and legacy px`() {
        // Verbatim: per-test-ir/wpt__css-position__position-relative-006.json,
        // component `position-relative-006__1__0` (the GREEN square).
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(
                p("Width", """{"type":"length","px":100}"""),
                p("Height", """{"type":"length","px":100}"""),
                p("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0}}"""),
                p("Top", "-10000"),
                p("Position", "\"RELATIVE\""),
            )
        )
        assertEquals(PositionType.RELATIVE, cfg.type)
        // The percentage is recorded…
        assertEquals(-10000f, cfg.percent?.top)
        // …and the Dp slot keeps the pre-wave-49 number-as-pixels reading,
        // so every consumer that cannot see a containing block (the
        // backdrop lane, the anchor slots, all non-WPT hosts) is untouched.
        assertEquals(-10000f, cfg.top?.value)
    }

    @Test fun `P2 - a px inset never populates the percentage channel`() {
        // Verbatim: position-relative-001's inner GREEN div.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(
                p("Position", "\"RELATIVE\""),
                p("Top", """{"px":-100}"""),
                p("Left", """{"px":-100}"""),
            )
        )
        assertNull(cfg.percent)
        assertEquals(-100f, cfg.top?.value)
    }

    @Test fun `P2 - a bare-number ZIndex is not an inset`() {
        // ZIndex shares the bare-number wire shape. If it leaked into the
        // percentage channel the composed lane would fire on every
        // z-indexed element in the corpus.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(p("Position", "\"ABSOLUTE\""), p("ZIndex", "5"))
        )
        assertNull(cfg.percent)
        assertEquals(5f, cfg.zIndex)
    }

    // ── P3: the repair — an indefinite block axis resolves to zero ────────

    @Test fun `P3 - position-relative-006 resolves top to zero`() {
        // The parent (`wpt__…__position-relative-006__1`, the RED box)
        // declares Width:100px and MIN-height:100px — no `height` — so
        // DynamicValueResolver.childContainingBlock publishes
        // (widthPx = 100, heightPx = null). css-position-3 §relpos-insets:
        // a percentage against an indefinite dimension resolves to ZERO,
        // which is exactly what the test's own <meta name=assert> demands.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(p("Top", "-10000"), p("Position", "\"RELATIVE\""))
        )
        val out = PercentInsetResolve.resolve(cfg, ContainingBlock(widthPx = 100f))
        assertEquals(0f, out.top?.value)
        // …and therefore no offset at all: the green square stays over the
        // red parent instead of translating 10 000 px off the canvas.
        assertEquals(0f, PositionApplier.resolvedOffset(out).y.value, 0f)
    }

    @Test fun `P3 - a definite block axis scales the percentage`() {
        // position-relative-001's relatively-positioned <span>: `top:100%;
        // left:100%` inside a 100x100 red div → cb (100, 100). Both axes
        // are definite so the used insets are +100/+100 — the SAME numbers
        // the legacy number-as-pixels path produced, which is why 001/003/
        // 004/005 are byte-neutral under this change.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(p("Position", "\"RELATIVE\""), p("Top", "100"), p("Left", "100"))
        )
        val out = PercentInsetResolve.resolve(cfg, ContainingBlock(100f, 100f))
        assertEquals(100f, out.top?.value)
        assertEquals(100f, out.start?.value)
    }

    @Test fun `P3 - a fractional percentage scales, it does not round to the raw number`() {
        // Guards against the change degenerating back into "number as px":
        // 25% of a 200px containing block is 50px, not 25px.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(p("Position", "\"ABSOLUTE\""), p("Top", "25"), p("Left", "25"))
        )
        val out = PercentInsetResolve.resolve(cfg, ContainingBlock(200f, 400f))
        assertEquals(100f, out.top?.value)   // 25% of the 400px block axis
        assertEquals(50f, out.start?.value)  // 25% of the 200px inline axis
    }

    // ── P4: the channel-gap guard ─────────────────────────────────────────

    @Test fun `P4 - a containing block with no definite axis is left alone`() {
        // position-relative-002's GREEN div: its parent is an inline
        // <span> with no declared size, so the channel publishes
        // (null, null). That null means "we never modelled the real
        // containing block" (CSS 2.1 §10.1 puts it at the nearest BLOCK
        // CONTAINER ancestor, which the channel skipped) — not
        // "CSS-indefinite". Keeping the legacy value is the honest
        // degradation; it can never move a box that renders correctly.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(p("Position", "\"RELATIVE\""), p("Top", "-100"), p("Left", "-100"))
        )
        val out = PercentInsetResolve.resolve(cfg, ContainingBlock())
        assertEquals(-100f, out.top?.value)
        assertEquals(-100f, out.start?.value)
        assertEquals(cfg, out)
    }

    @Test fun `P4 - position-relative-008 tbody parent hits the same guard`() {
        // The `<tr>` carries `top: 100%` and its parent `<tbody>`
        // (`position-relative-008__1__0`) has NO properties at all, so the
        // channel is (null, null). Untouched — this test passes on all
        // three platforms today and must keep doing so.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(p("Position", "\"RELATIVE\""), p("Top", "100"))
        )
        assertEquals(cfg, PercentInsetResolve.resolve(cfg, ContainingBlock()))
    }

    @Test fun `P4 - a config with no percentages is returned identically`() {
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(p("Position", "\"RELATIVE\""), p("Top", """{"px":40}"""))
        )
        assertEquals(cfg, PercentInsetResolve.resolve(cfg, ContainingBlock(100f, 100f)))
    }

    // ── P5: axis assignment across all eight longhands ────────────────────

    @Test fun `P5 - inline longhands read width and block longhands read height`() {
        // CSS 2.1 §9.4.3 axis table. right/left (+ inset-inline-*) scale the
        // containing block's WIDTH; top/bottom (+ inset-block-*) its HEIGHT.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(
                p("Position", "\"ABSOLUTE\""),
                p("Top", "10"), p("Right", "10"), p("Bottom", "10"), p("Left", "10"),
            )
        )
        val out = PercentInsetResolve.resolve(cfg, ContainingBlock(200f, 400f))
        assertEquals(40f, out.top?.value)     // 10% of 400
        assertEquals(40f, out.bottom?.value)  // 10% of 400
        assertEquals(20f, out.start?.value)   // 10% of 200
        assertEquals(20f, out.end?.value)     // 10% of 200
    }

    @Test fun `P5 - the logical spellings take the same axes`() {
        // fixtures/properties/layout/inset-logical.json is the fixture that
        // exercises this family (`inset-block-start: 10%`, …). Horizontal-tb
        // is the only writing mode the runtime models, so block = vertical.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(
                p("Position", "\"ABSOLUTE\""),
                p("InsetBlockStart", "10"), p("InsetBlockEnd", "10"),
                p("InsetInlineStart", "10"), p("InsetInlineEnd", "10"),
            )
        )
        assertNotNull(cfg.percent)
        val out = PercentInsetResolve.resolve(cfg, ContainingBlock(200f, 400f))
        assertEquals(40f, out.insetBlockStart?.value)
        assertEquals(40f, out.insetBlockEnd?.value)
        assertEquals(20f, out.insetInlineStart?.value)
        assertEquals(20f, out.insetInlineEnd?.value)
    }

    // ── P6: the channel really answers what P3/P4 assume ──────────────────

    @Test fun `P6 - the 006 parent publishes a definite width and a null height`() {
        // The load-bearing assumption of P3: `min-height` is NOT `height`,
        // so DynamicValueResolver.childContainingBlock leaves the block
        // axis null while the inline axis stays definite — the (a)-case
        // shape the guard keys on. Payload verbatim from per-test-ir
        // component `wpt__css-position__position-relative-006__1`.
        val cb = com.styleconverter.runtime.core.variables.DynamicValueResolver
            .childContainingBlock(
                listOf(
                    com.styleconverter.runtime.core.ir.IRProperty(
                        "Width", Json.parseToJsonElement("""{"type":"length","px":100}""")
                    ),
                    com.styleconverter.runtime.core.ir.IRProperty(
                        "MinHeight", Json.parseToJsonElement("""{"type":"length","px":100}""")
                    ),
                ),
                ContainingBlock(),
            )
        assertEquals(100f, cb.widthPx)
        assertNull(cb.heightPx)
    }

    @Test fun `P6 - the 002 span parent publishes nothing on either axis`() {
        // The load-bearing assumption of P4: an inline <span> that declares
        // no size nulls BOTH axes, which is why the guard has to keep the
        // legacy value there. Payload verbatim from per-test-ir component
        // `position-relative-002__1__0`.
        val cb = com.styleconverter.runtime.core.variables.DynamicValueResolver
            .childContainingBlock(
                listOf(
                    com.styleconverter.runtime.core.ir.IRProperty(
                        "Position", Json.parseToJsonElement("\"RELATIVE\"")
                    ),
                    com.styleconverter.runtime.core.ir.IRProperty(
                        "Top", Json.parseToJsonElement("""{"px":100}""")
                    ),
                ),
                ContainingBlock(100f, 100f),
            )
        assertNull(cb.widthPx)
        assertNull(cb.heightPx)
    }

    @Test fun `P5 - a mixed declaration keeps its px side verbatim`() {
        // `top: 50%; left: 10px` — only the percentage side is rewritten.
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(p("Position", "\"ABSOLUTE\""), p("Top", "50"), p("Left", """{"px":10}"""))
        )
        val out = PercentInsetResolve.resolve(cfg, ContainingBlock(200f, 400f))
        assertEquals(200f, out.top?.value)
        assertEquals(10f, out.start?.value)
    }
}
