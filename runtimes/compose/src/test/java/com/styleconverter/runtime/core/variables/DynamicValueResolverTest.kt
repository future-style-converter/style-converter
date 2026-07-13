package com.styleconverter.runtime.core.variables

// Wave-6 resolver-wiring pins. The pre-resolution pass is the single point
// where the (previously dead) CssVariableResolver + CalcExpressionEvaluator
// enter the live render path, so this suite pins the whole contract:
//
//   1. var() chains through 2 slot levels — CssVariableScope.merge order
//      (element shadows slot-parent, spec 02 resolution step 1-2).
//   2. Fallbacks, nested per css-variables-1 §2.3 (variables-basic golden).
//   3. Guaranteed-invalid ⇒ UNSET — the declaration is DROPPED so the
//      renderer paints the absent-property default (token-fallbacks
//      "missing" tile: transparent, exactly what web paints).
//   4. calc() with mixed units against the real context: % against the
//      containing-block channel, em against the inheritance channel's
//      font size, var() inside calc (calc-units fixture shapes verbatim).
//   5. Plain em/rem/vw originals ({v,u} carriers) that the appliers used
//      to default against constants.
//   6. The static fast path: a list with no dynamic values comes back as
//      the SAME instance (committed-baseline safety).

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicValueResolverTest {

    private fun prop(type: String, dataJson: String) =
        IRProperty(type, Json.parseToJsonElement(dataJson))

    private fun ctx(
        cbW: Float? = null,
        cbH: Float? = null,
        parentFs: Float = 16f
    ) = DynamicValueResolver.Context(
        containingBlockWidthPx = cbW,
        containingBlockHeightPx = cbH,
        viewportWidthPx = 390f,
        viewportHeightPx = 844f,
        parentFontSizePx = parentFs,
        rootFontSizePx = 16f
    )

    private fun pxOf(props: List<IRProperty>, type: String): Double? =
        ((props.firstOrNull { it.type == type }?.data as? JsonObject)
            ?.get("px") as? JsonPrimitive)?.doubleOrNull

    private fun srgbOf(props: List<IRProperty>, type: String): JsonObject? =
        (props.firstOrNull { it.type == type }?.data as? JsonObject)
            ?.get("srgb") as? JsonObject

    // ── 1. scope chain through two slot levels ────────────────────────

    @Test
    fun `var resolves through two-level scope chain with shadowing`() {
        // TK_TwoLevelShadow: root defines --accent=#c0392b, mid overrides
        // with #1a5276; the midLeaf sees the CLOSER definition (spec 02
        // step 1: element scope → nearest slot parent wins).
        val rootScope = CssVariableScope(mapOf("--accent" to "#c0392b"))
        val midScope = rootScope.merge(CssVariableScope(mapOf("--accent" to "#1a5276")))
        val leaf = listOf(prop("BackgroundColor", """{"original":"var(--accent)"}"""))

        val atRoot = DynamicValueResolver.resolve(leaf, rootScope.variables, ctx())
        val atMid = DynamicValueResolver.resolve(leaf, midScope.variables, ctx())

        // #c0392b → r=0xc0/255; #1a5276 → r=0x1a/255.
        assertEquals(0xC0 / 255.0, srgbOf(atRoot.properties, "BackgroundColor")!!["r"]!!.jsonPrimitive.doubleOrNull!!, 1e-6)
        assertEquals(0x1A / 255.0, srgbOf(atMid.properties, "BackgroundColor")!!["r"]!!.jsonPrimitive.doubleOrNull!!, 1e-6)
        // The substituted literal is surfaced as the new original.
        assertEquals(
            "#1a5276",
            ((atMid.properties[0].data as JsonObject)["original"] as JsonPrimitive).content
        )
    }

    @Test
    fun `variable names are case-sensitive`() {
        // css-variables-1 §2: custom property names never case-fold.
        // variables-basic golden defines BOTH --brand-bg and --Brand-Fg.
        val vars = mapOf("--Brand-Fg" to "#ffffff", "--brand-fg" to "#000000")
        val props = listOf(prop("Color", """{"original":"var(--Brand-Fg)"}"""))
        val r = DynamicValueResolver.resolve(props, vars, ctx())
        assertEquals(1.0, srgbOf(r.properties, "Color")!!["r"]!!.jsonPrimitive.doubleOrNull!!, 1e-6)
    }

    // ── 2. fallbacks (css-variables-1 §2.3) ───────────────────────────

    @Test
    fun `missing variable uses fallback, nested fallback recurses`() {
        val props = listOf(
            // var(--nope, #e67e22) → fallback color (token-fallbacks "saved").
            prop("BackgroundColor", """{"original":"var(--nope, #e67e22)"}"""),
            // var(--a, var(--b, 4px)) → falls through BOTH to 4px
            // (variables-basic golden's PaddingLeft shape).
            prop("PaddingLeft", """{"expr":"var(--a, var(--b, 4px))"}""")
        )
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        assertEquals(0xE6 / 255.0, srgbOf(r.properties, "BackgroundColor")!!["r"]!!.jsonPrimitive.doubleOrNull!!, 1e-6)
        assertEquals(4.0, pxOf(r.properties, "PaddingLeft"))
    }

    @Test
    fun `nested fallback stops at first defined variable`() {
        // TK_NestedFallback "stopsAtMid": var(--top, var(--mid, #ffffff))
        // with --mid defined → resolves to --mid's value, not white.
        val vars = mapOf("--mid" to "#9b59b6")
        val props = listOf(prop("BackgroundColor", """{"original":"var(--top, var(--mid, #ffffff))"}"""))
        val r = DynamicValueResolver.resolve(props, vars, ctx())
        assertEquals(0x9B / 255.0, srgbOf(r.properties, "BackgroundColor")!!["r"]!!.jsonPrimitive.doubleOrNull!!, 1e-6)
    }

    // ── 3. guaranteed-invalid ⇒ unset (dropped) ───────────────────────

    @Test
    fun `missing variable without fallback drops the declaration`() {
        // token-fallbacks "missing" tile: background must UNSET (transparent
        // box on web) — the property disappears from the resolved list.
        val props = listOf(
            prop("BackgroundColor", """{"original":"var(--nope)"}"""),
            prop("Width", """{"type":"length","px":70.0}""")
        )
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        assertNull(r.properties.firstOrNull { it.type == "BackgroundColor" })
        assertEquals(70.0, pxOf(r.properties, "Width"))
    }

    @Test
    fun `invalid width in block context fills the containing block like web`() {
        // CSS 2.1 §10.3.3: a block-level box whose width computes to auto
        // (here: guaranteed-invalid var ⇒ unset ⇒ auto) fills its
        // containing block — web paints tokenMath's standalone capture as
        // a full-width bar. In block context the resolver emits the 100%
        // wire shape; in flex context (blockLevelWidthAuto=false) the
        // declaration truly drops (flex auto = content-based).
        val props = listOf(prop("Width", """{"type":"expression","expr":"calc(var(--u) * 10)"}"""))
        val block = DynamicValueResolver.resolve(
            props, emptyMap(),
            ctx().copy(blockLevelWidthAuto = true)
        )
        val data = block.properties.single { it.type == "Width" }.data as JsonObject
        assertEquals("percentage", data["type"]!!.jsonPrimitive.contentOrNull)
        assertEquals(100.0, data["value"]!!.jsonPrimitive.doubleOrNull)

        val flex = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        assertNull(flex.properties.firstOrNull { it.type == "Width" })
    }

    @Test
    fun `cyclic variable reference is guaranteed-invalid, not a hang`() {
        // css-variables-1 §3: cycles make the value invalid at
        // computed-value time. `--a: var(--a)` must terminate AND unset.
        val vars = mapOf("--a" to "var(--a)")
        val props = listOf(prop("Width", """{"type":"expression","expr":"var(--a)"}"""))
        val r = DynamicValueResolver.resolve(props, vars, ctx())
        assertTrue(r.properties.isEmpty())
    }

    // ── 4. calc() with the real EvalContext ───────────────────────────

    @Test
    fun `calc percent resolves against the containing-block width channel`() {
        // calc-units CU_PercentMinus: width calc(100% - 40px) inside a
        // 280px content box → 240 (web-verified geometry).
        val props = listOf(prop("Width", """{"type":"expression","expr":"calc(100% - 40px)"}"""))
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx(cbW = 280f))
        assertEquals(240.0, pxOf(r.properties, "Width"))
    }

    @Test
    fun `nested calc resolves innermost-first`() {
        // CU_NestedAndVar "half": calc(calc(100% - 20px) / 2) @ 280 → 130.
        val props = listOf(prop("Width", """{"type":"expression","expr":"calc(calc(100% - 20px) / 2)"}"""))
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx(cbW = 280f))
        assertEquals(130.0, pxOf(r.properties, "Width"))
    }

    @Test
    fun `calc em uses the element's own font size from the inheritance channel`() {
        // CU_EmChain child: height calc(1em + 4px) with inherited
        // font-size 20px → 24. The FontSize arrives px-resolved via the
        // merge channel; em elsewhere must use IT, not the 16px constant.
        val props = listOf(
            prop("FontSize", """{"original":{"px":20.0,"type":"length"},"px":20.0}"""),
            prop("Height", """{"type":"expression","expr":"calc(1em + 4px)"}""")
        )
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        assertEquals(24.0, pxOf(r.properties, "Height"))
        assertEquals(20f, r.fontSizePx)
    }

    @Test
    fun `var inside calc substitutes before evaluation`() {
        // CU_NestedAndVar "vartimes": width calc(var(--u) * 10), --u=12px → 120.
        val vars = mapOf("--u" to "12px")
        val props = listOf(prop("Width", """{"type":"expression","expr":"calc(var(--u) * 10)"}"""))
        val r = DynamicValueResolver.resolve(props, vars, ctx())
        assertEquals(120.0, pxOf(r.properties, "Width"))
    }

    @Test
    fun `bare expr carrier on spacing keeps the bare px wire shape`() {
        // Padding calc carrier is {"expr":…} WITHOUT a type tag; the
        // rewrite must stay bare ({"px":N}) so PaddingExtractor's
        // extractLength sees the Exact shape it is frozen on.
        val props = listOf(prop("PaddingTop", """{"expr":"calc(1em + 2px)"}"""))
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        val data = r.properties[0].data as JsonObject
        assertEquals(18.0, data["px"]!!.jsonPrimitive.doubleOrNull)
        assertNull(data["type"]) // bare carrier stays bare
    }

    @Test
    fun `calc percent without a containing block stays unresolved`() {
        // No % base → honest Keep: the property survives untouched (the
        // legacy skip path), never a guessed base.
        val original = prop("Width", """{"type":"expression","expr":"calc(100% - 40px)"}""")
        val r = DynamicValueResolver.resolve(listOf(original), emptyMap(), ctx(cbW = null))
        assertEquals(original, r.properties[0])
    }

    // ── 5. plain relative originals (em/rem/vw + %) ───────────────────

    @Test
    fun `em and rem originals resolve against live font sizes`() {
        // {v,u} carriers previously resolved against the 16px constant in
        // SpacingResolve/SizingApplier; the pre-pass pins them to the real
        // channels: em → own font size (20), rem → root (16).
        val props = listOf(
            prop("FontSize", """{"original":{"px":20.0,"type":"length"},"px":20.0}"""),
            prop("Width", """{"type":"length","original":{"v":2.0,"u":"EM"}}"""),
            prop("Height", """{"type":"length","original":{"v":3.0,"u":"REM"}}"""),
            prop("PaddingTop", """{"original":{"v":1.0,"u":"EM"}}""")
        )
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        assertEquals(40.0, pxOf(r.properties, "Width"))
        assertEquals(48.0, pxOf(r.properties, "Height"))
        assertEquals(20.0, pxOf(r.properties, "PaddingTop"))
    }

    @Test
    fun `font-size em resolves against the PARENT size, then feeds siblings`() {
        // css-values-4 §5.1.1: em on font-size is relative to the INHERITED
        // size (parent channel = 20) → own 30; em on OTHER properties then
        // uses the element's own 30.
        val props = listOf(
            prop("FontSize", """{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}"""),
            prop("Width", """{"type":"length","original":{"v":2.0,"u":"EM"}}""")
        )
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx(parentFs = 20f))
        assertEquals(30f, r.fontSizePx)
        assertEquals(30.0, pxOf(r.properties, "FontSize"))
        assertEquals(60.0, pxOf(r.properties, "Width"))
    }

    @Test
    fun `a98rgb-shaped em width and height resolve to 192 at the default 16px root font`() {
        // TITAN WPT Round 5 (em/rem length resolution). The four css-color/
        // a98rgb tests size their box with `width: 12em; height: 12em` (or
        // 6em) on an element that declares NO font-size, so em resolves
        // against the CSS-initial `medium` = 16px root font. This pins that
        // the exact IR shape the converter emits for those tests —
        // {"type":"length","original":{"v":12,"u":"EM"}} with NO top-level px
        // (em is runtime-dependent, so the converter leaves px null) — is
        // rewritten to px 192 (12 × 16) by the render path's pre-resolution
        // pass. Regression guard: if this drops back to null the box would
        // collapse (no content, no floor in composed WPT mode) and vanish.
        val props = listOf(
            // Byte-identical to per-test-ir/wpt__css-color__a98rgb-002.json.
            prop("Width", """{"type":"length","original":{"v":12,"u":"EM"}}"""),
            prop("Height", """{"type":"length","original":{"v":12,"u":"EM"}}""")
        )
        // ctx() defaults parentFontSizePx = 16 and declares no FontSize on the
        // element, so ownFontSizePx falls back to 16 — the em base.
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        assertEquals(192.0, pxOf(r.properties, "Width"))   // 12em × 16px
        assertEquals(192.0, pxOf(r.properties, "Height"))  // 12em × 16px (12em box)

        // End-to-end: the rewritten wire shape ({…,"px":192}) must decode
        // through SizingExtractor to an EXACT dp — this is the value
        // SizingApplier hands to Modifier.width(192.dp)/height(192.dp). The
        // extractor prefers the top-level px over the preserved em original
        // (LengthValue.extractLength: px-present ⇒ Exact), so the box is a
        // definite 192×192, matching the browser-ref's rendered square.
        val cfg = com.styleconverter.runtime.sizing.SizingExtractor
            .extractSizingConfig(r.properties.map { it.type to it.data })
        assertEquals(com.styleconverter.runtime.core.types.LengthValue.Exact(192.0), cfg.width)
        assertEquals(com.styleconverter.runtime.core.types.LengthValue.Exact(192.0), cfg.height)
    }

    @Test
    fun `rem width resolves against the root font size, ignoring the element font size`() {
        // css-values-4 §5.1.1: rem is ALWAYS relative to the ROOT font size,
        // never the element's own. Even with a local `font-size: 32px`, a
        // `width: 12rem` must resolve to 12 × 16 (root) = 192, NOT 12 × 32.
        // ctx().rootFontSizePx is fixed at 16 (the harness never styles the
        // root, so the browser default the web reference inherits is honest).
        val props = listOf(
            // A local font-size that em WOULD use but rem must ignore.
            prop("FontSize", """{"original":{"px":32.0,"type":"length"},"px":32.0}"""),
            prop("Width", """{"type":"length","original":{"v":12,"u":"REM"}}""")
        )
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        assertEquals(192.0, pxOf(r.properties, "Width"))   // 12rem × 16 root
    }

    @Test
    fun `em width honours an explicit element font-size override`() {
        // When the element (or an ancestor threaded through the inheritance
        // channel) sets an explicit font-size, em resolves against THAT, not
        // the 16px default: `font-size: 10px; width: 12em` → 120px. This is
        // the "font-size override" leg of the em contract — the render path
        // resolves the element's own FontSize FIRST (resolveOwnFontSize) and
        // uses it as the em base for every other property on the element.
        val props = listOf(
            prop("FontSize", """{"original":{"px":10.0,"type":"length"},"px":10.0}"""),
            prop("Width", """{"type":"length","original":{"v":12,"u":"EM"}}""")
        )
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx())
        assertEquals(120.0, pxOf(r.properties, "Width"))   // 12em × 10px own font
        assertEquals(10f, r.fontSizePx)                    // element font base
    }

    @Test
    fun `font-size var carrier resolves through the expression shape`() {
        // token-theme "styled": FontSize {"original":{"type":"expression",
        // "expr":"var(--type)"}} with --type=18px → px 18 lands top-level
        // where TextStyleApplier.extractFontSize reads it.
        val vars = mapOf("--type" to "18px")
        val props = listOf(prop("FontSize", """{"original":{"type":"expression","expr":"var(--type)"}}"""))
        val r = DynamicValueResolver.resolve(props, vars, ctx())
        assertEquals(18.0, pxOf(r.properties, "FontSize"))
        assertEquals(18f, r.fontSizePx)
    }

    @Test
    fun `bare percent padding resolves against the width channel only when known`() {
        // CSS 2.1 §8.4: padding-% (all four sides) resolves against the
        // containing block's WIDTH. Known base → px; unknown → untouched.
        val p = prop("PaddingTop", """{"original":{"v":10.0,"u":"PERCENT"}}""")
        val with = DynamicValueResolver.resolve(listOf(p), emptyMap(), ctx(cbW = 358f))
        val without = DynamicValueResolver.resolve(listOf(p), emptyMap(), ctx(cbW = null))
        assertEquals(35.8, pxOf(with.properties, "PaddingTop")!!, 1e-4)
        assertEquals(p, without.properties[0])
    }

    @Test
    fun `box-relative percent carriers stay untouched (border-radius)`() {
        // css-backgrounds-3 §4.2: border-radius % resolves against the
        // BOX's own axes at draw time — never the containing block. The
        // Edge_PercentRadius baseline pin: leaking the 358px cb here
        // turned a circle into a 179px corner blob.
        val p = prop("BorderTopLeftRadius", """{"original":{"v":50.0,"u":"PERCENT"}}""")
        val r = DynamicValueResolver.resolve(listOf(p), emptyMap(), ctx(cbW = 358f))
        assertEquals(p, r.properties[0])
    }

    @Test
    fun `whole-value var of a percentage keeps the sizing percentage shape`() {
        // width: var(--w) with --w=50% → the typed carrier re-emits the
        // wire's percentage shape so SizingApplier's fillMaxWidth(0.5)
        // path (exact at layout time) keeps owning it.
        val vars = mapOf("--w" to "50%")
        val props = listOf(prop("Width", """{"type":"expression","expr":"var(--w)"}"""))
        val r = DynamicValueResolver.resolve(props, vars, ctx())
        val data = r.properties[0].data as JsonObject
        assertEquals("percentage", data["type"]!!.jsonPrimitive.contentOrNull)
        assertEquals(50.0, data["value"]!!.jsonPrimitive.doubleOrNull)
    }

    // ── Generic fall-through remap ────────────────────────────────────

    @Test
    fun `generic border-radius var remaps onto the typed property`() {
        // token-theme tile "c": border-radius: var(--pad) falls through the
        // parser as Generic; after substitution it must land on the REAL
        // longhand with the bare px shape those appliers are frozen on.
        val vars = mapOf("--pad" to "8px")
        val props = listOf(prop(
            "Generic",
            """{"propertyName":"border-top-left-radius","rawValue":"var(--pad)","_unmapped":true}"""
        ))
        val r = DynamicValueResolver.resolve(props, vars, ctx())
        assertEquals("BorderTopLeftRadius", r.properties[0].type)
        assertEquals(8.0, pxOf(r.properties, "BorderTopLeftRadius"))
    }

    // ── 6. static fast path (baseline safety) ─────────────────────────

    @Test
    fun `static property lists return the same instance`() {
        // The visual-test corpus has zero dynamic values; the pass must be
        // a true no-op there (same List instance ⇒ identical render).
        val props = listOf(
            prop("Width", """{"type":"length","px":250.0}"""),
            prop("BackgroundColor", """{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"red"}"""),
            prop("PaddingTop", """{"px":10.0}"""),
            prop("Display", "\"FLEX\""),
            // percentage width stays on the layout-time fillMaxWidth path
            prop("Height", """{"type":"percentage","value":50.0}""")
        )
        val r = DynamicValueResolver.resolve(props, emptyMap(), ctx(cbW = 358f))
        assertSame(props, r.properties)
    }

    // ── childContainingBlock (width channel derivation) ───────────────

    @Test
    fun `child containing block is the resolved content box`() {
        // CSS 2.1 §10.1: children's % base = this box's CONTENT box —
        // border-box width minus padding and border bands.
        val props = listOf(
            prop("Width", """{"type":"length","px":300.0}"""),
            prop("PaddingLeft", """{"px":10.0}"""),
            prop("PaddingRight", """{"px":10.0}"""),
            prop("BorderLeftWidth", """{"px":2.0,"original":"2px"}"""),
            prop("BorderRightWidth", """{"px":2.0,"original":"2px"}""")
        )
        val cb = DynamicValueResolver.childContainingBlock(props, ContainingBlock(widthPx = 358f))
        assertEquals(276f, cb.widthPx)
        assertNull(cb.heightPx) // height auto → unknown, never guessed
    }

    @Test
    fun `percent width chains the parent base into the child containing block`() {
        // width: 50% of a known 358 base → 179 content box for children.
        val props = listOf(prop("Width", """{"type":"percentage","value":50.0}"""))
        val cb = DynamicValueResolver.childContainingBlock(props, ContainingBlock(widthPx = 358f))
        assertEquals(179f, cb.widthPx)
    }

    @Test
    fun `indefinite parent yields an unknown containing block`() {
        // fit-content parents aren't statically sizable — the channel must
        // say "unknown" (null), keeping child % values honestly unresolved.
        val cb = DynamicValueResolver.childContainingBlock(emptyList(), ContainingBlock(widthPx = 358f))
        assertNull(cb.widthPx)
        assertNull(cb.heightPx)
    }
}
