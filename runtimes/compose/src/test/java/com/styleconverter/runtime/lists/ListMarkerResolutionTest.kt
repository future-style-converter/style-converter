package com.styleconverter.runtime.lists

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Wave 24, lane LF — B-RC3 (parts 1+2) pin table for
 * [ListStyleExtractor.resolveMarkerConfig] + [ListStyleApplier.getMarker].
 *
 * ## The bug
 * Both native renderers synthesised the `<li>` marker from the PARENT's
 * `meta.sourceTag` alone — `<ol>` ⇒ "1.", `<ul>` ⇒ "•" — and threw the
 * item's own declarations away. Every `list-style-type` in the corpus
 * painted a plain disc.
 *
 * ## The live artifact these cases are taken from
 * `tools/titan/runs/wave23-final/sections/css-lists/per-test-ir/
 *  wpt__css-lists__change-list-style-type-001.json` — a flat v2 doc whose
 * `ul` components carry `{"type":"ListStylePosition","data":"INSIDE"}`
 * (note: UPPERCASE) and whose `li` children carry
 * `{"type":"ListStyleType","data":"square"|"none"|"upper-roman"|"decimal"}`
 * (note: lowercase-hyphenated). Both spellings are exercised below —
 * the case normalisation is load-bearing on this wire.
 *
 * TWIN of the iOS suite `ListMarkerTests.swift`: same cases, same
 * expected strings. Change one, change both.
 */
class ListMarkerResolutionTest {

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)

    private fun pair(type: String, wire: String): Pair<String, JsonElement?> =
        type to json(wire)

    /** The exact `li` payload from the live css-lists doc. */
    private fun li(type: String) = listOf(pair("ListStyleType", "\"$type\""))

    /** The exact `ul` payload from the live css-lists doc. */
    private val ulInside = listOf(pair("ListStylePosition", "\"INSIDE\""))

    private fun marker(parentTag: String?, parent: List<Pair<String, JsonElement?>>,
                       child: List<Pair<String, JsonElement?>>, index: Int = 0): String? =
        ListStyleExtractor.resolveMarkerConfig(parentTag, parent, child)
            ?.let { ListStyleApplier.getMarker(index, it) }

    // ── B-RC3 part 1: the CHILD's own type wins ─────────────────────────

    @Test
    fun childListStyleTypeOverridesTheContainerDefault() {
        // The four live `change-list-style-type-001` values under a <ul>.
        assertEquals("■", marker("ul", ulInside, li("square")))
        assertEquals("", marker("ul", ulInside, li("none")))
        assertEquals("I.", marker("ul", ulInside, li("upper-roman")))
        assertEquals("1.", marker("ul", ulInside, li("decimal")))
    }

    @Test
    fun withoutAnOwnDeclarationTheUaDefaultStands() {
        // HTML §15.3.7 UA sheet: ul ⇒ disc, ol ⇒ decimal.
        assertEquals("•", marker("ul", ulInside, emptyList()))
        assertEquals("1.", marker("ol", emptyList(), emptyList()))
        assertEquals("3.", marker("ol", emptyList(), emptyList(), index = 2))
    }

    @Test
    fun nonListContainersProduceNoMarkerAtAll() {
        // Null is how the renderer decides not to synthesise anything.
        assertNull(ListStyleExtractor.resolveMarkerConfig("div", emptyList(), li("square")))
        assertNull(ListStyleExtractor.resolveMarkerConfig(null, emptyList(), emptyList()))
        // …but menu/dir ARE list containers in the UA sheet.
        assertEquals("•", marker("menu", emptyList(), emptyList()))
        assertEquals("•", marker("DIR", emptyList(), emptyList()))
    }

    // ── B-RC3 part 2: inheritance ───────────────────────────────────────

    @Test
    fun aTypeDeclaredOnTheContainerReachesTheItem() {
        // css-lists-3 §3.1 list-style-type is Inherited: yes — the value
        // may sit on the <ul> (or, via the renderer's merged list, on an
        // ancestor) while the item declares nothing.
        val ulSquare = listOf(pair("ListStyleType", "\"square\""))
        assertEquals("■", marker("ul", ulSquare, emptyList()))
        // …and the item's own declaration still beats it.
        assertEquals("i.", marker("ul", ulSquare, li("lower-roman")))
    }

    @Test
    fun positionResolvesFromTheContainerAndFromTheItem() {
        // Live wire spells it UPPERCASE; the item may override.
        assertEquals(ListStylePosition.INSIDE,
            ListStyleExtractor.resolveMarkerConfig("ul", ulInside, emptyList())?.listStylePosition)
        assertEquals(ListStylePosition.OUTSIDE,
            ListStyleExtractor.resolveMarkerConfig(
                "ul", ulInside, listOf(pair("ListStylePosition", "\"outside\"")))?.listStylePosition)
        // Initial value when nobody declares it (css-lists-3 §3.1).
        assertEquals(ListStylePosition.OUTSIDE,
            ListStyleExtractor.resolveMarkerConfig("ol", emptyList(), emptyList())?.listStylePosition)
    }

    // ── The counter-style table is now reachable from the renderer ──────

    @Test
    fun theFullCounterStyleTableRoutesThrough() {
        // css-counter-styles-3 §6 predefined styles, index 0 unless noted.
        assertEquals("01.", marker("ol", emptyList(), li("decimal-leading-zero")))
        assertEquals("a.", marker("ol", emptyList(), li("lower-alpha")))
        assertEquals("B.", marker("ol", emptyList(), li("upper-latin"), index = 1))
        assertEquals("iv.", marker("ol", emptyList(), li("lower-roman"), index = 3))
        assertEquals("α.", marker("ol", emptyList(), li("lower-greek")))
        assertEquals("Ա.", marker("ol", emptyList(), li("armenian")))
        assertEquals("ა.", marker("ol", emptyList(), li("georgian")))
        // Georgian at EXACTLY 10000 — the U+10F5 digit alone, no trailing
        // digits. Twin of the iOS regression pin: iOS's first draft handed
        // its shared `additive()` a remainder of 0, which tripped that
        // helper's own `num > 0` guard and appended a literal "0". Only
        // divergence found by the wave-24 exhaustive twin-table diff.
        assertEquals("ჵ.", marker("ol", emptyList(), li("georgian"), index = 9999))
        assertEquals("ჰშჟთ.", marker("ol", emptyList(), li("georgian"), index = 9998))
        assertEquals("ჵა.", marker("ol", emptyList(), li("georgian"), index = 10000))
        assertEquals("א.", marker("ol", emptyList(), li("hebrew")))
        assertEquals("一.", marker("ol", emptyList(), li("cjk-decimal")))
        assertEquals("あ.", marker("ol", emptyList(), li("hiragana")))
        assertEquals("ア.", marker("ol", emptyList(), li("katakana")))
        assertEquals("い.", marker("ol", emptyList(), li("hiragana-iroha")))
        assertEquals("イ.", marker("ol", emptyList(), li("katakana-iroha")))
        assertEquals("○", marker("ul", emptyList(), li("circle")))
    }

    // ── The documented @counter-style gap ───────────────────────────────

    @Test
    fun unresolvableCustomNameKeepsTheContainerDefault() {
        // `marker-text-matches-disc` / `-circle` declare `my-disc` /
        // `my-circle`, defined by an @counter-style rule the IR wire does
        // not carry. Keeping the container default is the documented
        // choice (and is what those two rules happen to define).
        assertEquals("•", marker("ul", ulInside, li("my-disc")))
        assertEquals("•", marker("ul", ulInside, li("my-circle")))
        assertEquals("1.", marker("ol", emptyList(), li("my-circle")))
    }

    // ── The legacy StyleApplier entry point is untouched ────────────────

    @Test
    fun defaultBaseOverloadIsUnchanged() {
        // StyleApplier calls the one-arg form; unknown keywords must still
        // land on the DISC/OUTSIDE default config, exactly as before.
        val cfg = ListStyleExtractor.extractListStyleConfig(li("totally-unknown"))
        assertEquals(ListStyleType.DISC, cfg.listStyleType)
        assertEquals(ListStylePosition.OUTSIDE, cfg.listStylePosition)
        val square = ListStyleExtractor.extractListStyleConfig(li("square"))
        assertEquals(ListStyleType.SQUARE, square.listStyleType)
    }

    // ── Wave 25 item 1: the UA rule beats an ANCESTOR's declaration ─────

    private fun prop(type: String, wire: String) =
        com.styleconverter.runtime.core.ir.IRProperty(type, json(wire))

    /**
     * The list-style half of [ListStyleUaRule.apply]'s output.
     *
     * Wave 30 (lane 3, fix B5) added a SECOND UA declaration to the same
     * rule — `ul, menu, dir, ol { padding-inline-start: 40px }` (HTML
     * §15.3.7) — so every container path now also carries a `PaddingLeft`
     * entry. The wave-25 cases below are about the TYPE cascade and are
     * kept focused on it by dropping that entry here; the padding has its
     * own pins under "Wave 30".
     */
    private fun listStyleOnly(
        properties: List<com.styleconverter.runtime.core.ir.IRProperty>
    ) = properties.filter { ListStyleExtractor.isListStyleProperty(it.type) }

    @Test
    fun anAncestorTypeLosesToTheContainersUaRule() {
        // The inversion: `<div style="list-style-type:square"><ul><li>`.
        // The ul declares nothing, so the UA rule `ul { list-style-type:
        // disc }` (HTML §15.3.7) wins the cascade on the ul ELEMENT and
        // inheritance is never consulted (css-cascade-4 §4.3) — Chromium
        // paints a disc, not a square.
        val ancestor = listOf(prop("ListStyleType", "\"square\""))
        val corrected = ListStyleUaRule.apply("ul", own = emptyList(), merged = ancestor)
        assertEquals(1, listStyleOnly(corrected).size)
        assertEquals("ListStyleType", listStyleOnly(corrected)[0].type)
        assertEquals("•", marker("ul", corrected.map { it.type to it.data }, emptyList()))
        // …and on an <ol> the UA value is decimal, not the ancestor's square.
        assertEquals("1.",
            marker("ol",
                ListStyleUaRule.apply("ol", emptyList(), ancestor).map { it.type to it.data },
                emptyList()))
    }

    @Test
    fun theContainersOwnDeclarationStillBeatsTheUaRule() {
        // Author origin beats UA (css-cascade-4 §6.1). This is the live
        // `content-property/marker-text-matches-armenian` shape: the <ol>
        // itself carries list-style-type, and must keep it. Guards against
        // "fix" attempts that merely reorder the fold.
        val own = listOf(prop("ListStyleType", "\"armenian\""))
        val merged = ListStyleUaRule.apply("ol", own = own, merged = own)
        // The TYPE half did not fire — the own declaration is returned
        // verbatim. (Not `assertSame` any more: wave 30's padding half
        // appends to the same list, so the instance changes while the
        // list-style content does not.)
        assertEquals(own, listStyleOnly(merged))
        assertEquals("Ա.", marker("ol", merged.map { it.type to it.data }, emptyList()))
    }

    @Test
    fun theUaRuleIsInertOutsideListContainers() {
        val ancestor = listOf(prop("ListStyleType", "\"square\""))
        // Not a list container ⇒ the UA sheet declares nothing ⇒ untouched.
        // Still `assertSame`: NEITHER half of the rule applies off a
        // container, so the identity fast-path is intact for every
        // non-list element in the corpus.
        assertSame(ancestor, ListStyleUaRule.apply("div", emptyList(), ancestor))
        assertSame(ancestor, ListStyleUaRule.apply(null, emptyList(), ancestor))
        // A container with nothing inherited ⇒ nothing to displace.
        val onlyPosition = listOf(prop("ListStylePosition", "\"INSIDE\""))
        assertEquals(onlyPosition,
            listStyleOnly(ListStyleUaRule.apply("ul", emptyList(), onlyPosition)))
        // The unexpanded shorthand counts as an own type declaration.
        val ownShorthand = listOf(prop("ListStyle", "\"square\""))
        assertEquals(ancestor,
            listStyleOnly(ListStyleUaRule.apply("ul", ownShorthand, ancestor)))
    }

    // ── Wave 30 (lane 3, fix B5): the UA `padding-inline-start` ─────────

    /**
     * Every px value the rule's output carries for [type], in order.
     *
     * [type] defaults to `PaddingLeft`, the physical side the UA
     * `padding-inline-start` maps to under the default `direction: ltr`;
     * the rtl cases below pass `PaddingRight` (css-logical-1 §2.1).
     */
    private fun paddingPx(
        properties: List<com.styleconverter.runtime.core.ir.IRProperty>,
        type: String = "PaddingLeft"
    ): List<Double> = properties.filter { it.type == type }.map {
        (it.data as kotlinx.serialization.json.JsonObject)["px"]!!
            .let { px -> (px as kotlinx.serialization.json.JsonPrimitive).content.toDouble() }
    }

    @Test
    fun aListContainerWithNoAuthorPaddingTakesTheUa40px() {
        // HTML §15.3.7 `ul, menu, dir, ol { padding-inline-start: 40px }`.
        // The live shape: change-list-style-type-001's ten `<ul>`s declare
        // ListStylePosition and nothing else, and both natives laid their
        // items out 40px left of web (ink columns 17 vs 56) because nothing
        // supplied this.
        for (tag in listOf("ul", "ol", "menu", "dir")) {
            val merged = ListStyleUaRule.apply(
                tag, emptyList(), listOf(prop("ListStylePosition", "\"INSIDE\"")))
            assertEquals("$tag must take the UA padding",
                listOf(ListStyleUaRule.UA_PADDING_INLINE_START_PX), paddingPx(merged))
        }
        // A non-container gets nothing — the identity path above.
        assertEquals(emptyList<Double>(), paddingPx(ListStyleUaRule.apply(
            "div", emptyList(), listOf(prop("ListStylePosition", "\"INSIDE\"")))))
    }

    @Test
    fun anAuthorInlineStartPaddingBeatsTheUaRule() {
        // css-cascade-4 §6.1. The live shape: css3-counter-styles-007's
        // `<ol>` declares `padding-left: 8em` and must keep exactly that —
        // injecting 40px beside it would double-indent every one of its
        // 24 rows. Under the default ltr all three spellings block it: the
        // physical longhand for THIS direction, the logical longhand, and
        // the `Padding` shorthand — the last one defensive only, since the
        // converter's PaddingExpander always expands `padding` and no
        // `PaddingProperty` exists in the 558-property catalogue.
        assertEquals(listOf(128.0),
            paddingPx(ListStyleUaRule.apply("ol",
                listOf(prop("PaddingLeft", """{"px":128.0}""")),
                listOf(prop("PaddingLeft", """{"px":128.0}""")))))
        for (declared in listOf("PaddingInlineStart", "Padding")) {
            val own = listOf(prop(declared, """{"px":128.0}"""))
            assertEquals("$declared must block the UA padding",
                emptyList<Double>(), paddingPx(ListStyleUaRule.apply("ol", own, own)))
        }
    }

    // ── Wave 30 fix round (lane N, fix N1): the direction mapping ───────

    @Test
    fun anRtlContainerTakesTheUaPaddingOnTheRight() {
        // css-logical-1 §2.1: the inline-START side of a `direction: rtl`
        // box is the RIGHT one, so HTML §15.3.7's `padding-inline-start:
        // 40px` must land there. The first cut of B5 injected the physical
        // `PaddingLeft` unconditionally — 40px on the wrong edge AND 40px
        // missing on the right, an 80px relative error on every rtl list.
        // Live exposure: fixtures/wpt/css-lists/list-marker-symbol-bidi.json
        // carries five `direction: rtl` `<ul>`s.
        val ua = ListStyleUaRule.UA_PADDING_INLINE_START_PX
        val rtl = listOf(prop("Direction", "\"RTL\""),
            prop("ListStylePosition", "\"INSIDE\""))
        val rtlOut = ListStyleUaRule.apply("ul", rtl, rtl)
        assertEquals(listOf(ua), paddingPx(rtlOut, "PaddingRight"))
        assertEquals(emptyList<Double>(), paddingPx(rtlOut, "PaddingLeft"))
        // ltr — declared explicitly and (above) by absence — still lands
        // on the left, so no committed ltr capture moves.
        val ltr = listOf(prop("Direction", "\"LTR\""),
            prop("ListStylePosition", "\"INSIDE\""))
        assertEquals(listOf(ua), paddingPx(ListStyleUaRule.apply("ul", ltr, ltr)))
        // `direction` is Inherited: yes (css-writing-modes-4 §2.1) and sits
        // in ComponentRenderer.INHERITED_PROPERTY_TYPES, so an ANCESTOR's
        // rtl reaches the container through the merged list alone.
        assertEquals(listOf(ua), paddingPx(
            ListStyleUaRule.apply("ul", emptyList(), listOf(prop("Direction", "\"RTL\""))),
            "PaddingRight"))
        // A garbage/unresolved keyword is ltr, the CSS initial value.
        val junk = listOf(prop("Direction", "\"sideways\""))
        assertEquals(listOf(ua), paddingPx(ListStyleUaRule.apply("ul", junk, junk)))
    }

    @Test
    fun theAuthorBlockFollowsTheSameDirectionMapping() {
        val ua = ListStyleUaRule.UA_PADDING_INLINE_START_PX
        val dir = prop("Direction", "\"RTL\"")
        // padding-right ON an rtl container IS its inline start ⇒ blocks.
        val ownRight = listOf(dir, prop("PaddingRight", """{"px":128.0}"""))
        assertEquals(listOf(128.0),
            paddingPx(ListStyleUaRule.apply("ol", ownRight, ownRight), "PaddingRight"))
        // padding-LEFT on the same rtl container says nothing about the
        // inline start (css-logical-1 §2.1), so both declarations survive
        // the cascade exactly as they do in a browser: the author's 128px
        // on the left, the UA's 40px on the right.
        val ownLeft = listOf(dir, prop("PaddingLeft", """{"px":128.0}"""))
        val mixed = ListStyleUaRule.apply("ol", ownLeft, ownLeft)
        assertEquals(listOf(ua), paddingPx(mixed, "PaddingRight"))
        assertEquals(listOf(128.0), paddingPx(mixed, "PaddingLeft"))
        // The logical longhand — and the defensive `Padding` guard — block
        // in BOTH directions, since they name the same side the UA does.
        for (declared in listOf("PaddingInlineStart", "Padding")) {
            val own = listOf(dir, prop(declared, """{"px":128.0}"""))
            val out = ListStyleUaRule.apply("ol", own, own)
            assertEquals("$declared must block the rtl UA padding",
                emptyList<Double>(), paddingPx(out, "PaddingRight"))
            assertEquals(emptyList<Double>(), paddingPx(out, "PaddingLeft"))
        }
    }

    @Test
    fun theDirectionHelpersAreTheSingleMapping() {
        // The two natives are diffed against these, so pin them directly.
        assertEquals("PaddingLeft", ListStyleUaRule.startPaddingType(false))
        assertEquals("PaddingRight", ListStyleUaRule.startPaddingType(true))
        // Last entry wins, the fold convention this package uses.
        assertEquals(true, ListStyleUaRule.isRtl(
            listOf(prop("Direction", "\"LTR\""), prop("Direction", "\"rtl\""))))
        assertEquals(false, ListStyleUaRule.isRtl(
            listOf(prop("Direction", "\"RTL\""), prop("Direction", "\"LTR\""))))
        assertEquals(false, ListStyleUaRule.isRtl(emptyList()))
    }

    @Test
    fun positionAndImageStillInheritFromAnAncestor() {
        // The UA sheet declares NO list-style-position/-image on ul/ol, so
        // for those two inheritance is untouched by the rule — an
        // ancestor's `inside` must still reach the item.
        val ancestor = listOf(
            prop("ListStylePosition", "\"INSIDE\""),
            prop("ListStyleType", "\"square\"")
        )
        val corrected = ListStyleUaRule.apply("ul", emptyList(), ancestor)
        val cfg = ListStyleExtractor.resolveMarkerConfig(
            "ul", corrected.map { it.type to it.data }, emptyList())
        assertEquals(ListStylePosition.INSIDE, cfg?.listStylePosition)
        assertEquals(ListStyleType.DISC, cfg?.listStyleType)
    }

    // ── Wave 25 item 2: the `list-style` shorthand branch ───────────────

    private fun shorthand(value: String) =
        ListStyleExtractor.extractListStyleConfig(listOf(pair("ListStyle", "\"$value\"")))

    @Test
    fun theShorthandExpandsAllThreeComponents() {
        // css-lists-3 §3.6, any component order.
        val full = shorthand("square inside url(bullet.png)")
        assertEquals(ListStyleType.SQUARE, full.listStyleType)
        assertEquals(ListStylePosition.INSIDE, full.listStylePosition)
        assertEquals("bullet.png", full.listStyleImage)
        val reordered = shorthand("url('b.png') upper-roman outside")
        assertEquals(ListStyleType.UPPER_ROMAN, reordered.listStyleType)
        assertEquals(ListStylePosition.OUTSIDE, reordered.listStylePosition)
        assertEquals("b.png", reordered.listStyleImage)
    }

    @Test
    fun omittedShorthandComponentsResetToTheirInitialValues() {
        // css-cascade-4 §3: a shorthand sets every longhand it omits to
        // the INITIAL value — it does not leave them inherited.
        val base = ListStyleConfig(
            listStyleType = ListStyleType.UPPER_ROMAN,
            listStylePosition = ListStylePosition.INSIDE,
            listStyleImage = "inherited.png"
        )
        val applied = ListStyleShorthand.apply(base, "square")
        assertEquals(ListStyleType.SQUARE, applied.listStyleType)
        assertEquals(ListStylePosition.OUTSIDE, applied.listStylePosition)
        assertNull(applied.listStyleImage)
    }

    @Test
    fun theShorthandsNoneAndGlobalKeywords() {
        // One `none` fills type AND image (§3.5).
        assertEquals(ListStyleType.NONE, shorthand("none").listStyleType)
        // A `none` alongside an explicit type goes to the image slot only.
        assertEquals(ListStyleType.SQUARE, shorthand("square none").listStyleType)
        // Two `none`s: one per slot.
        assertEquals(ListStyleType.NONE, shorthand("none none").listStyleType)
        // Three is one more than there are slots ⇒ invalid ⇒ base kept.
        assertEquals(ListStyleType.DISC, shorthand("none none none").listStyleType)
        // `inherit`/`unset` on an inherited property keep the running value.
        val inherited = ListStyleConfig(listStyleType = ListStyleType.HEBREW)
        assertEquals(ListStyleType.HEBREW,
            ListStyleShorthand.apply(inherited, "inherit").listStyleType)
        assertEquals(ListStyleType.HEBREW,
            ListStyleShorthand.apply(inherited, "unset").listStyleType)
        // `initial` resets all three.
        assertEquals(ListStyleType.DISC,
            ListStyleShorthand.apply(inherited, "initial").listStyleType)
    }

    @Test
    fun anInvalidShorthandDropsTheWholeDeclaration() {
        // css-syntax-3 §9: an invalid declaration is dropped, not
        // half-applied. An unresolvable @counter-style ident, a repeated
        // component, and two images all invalidate.
        val base = ListStyleConfig(listStyleType = ListStyleType.HEBREW)
        assertEquals(ListStyleType.HEBREW,
            ListStyleShorthand.apply(base, "my-counter inside").listStyleType)
        assertEquals(ListStyleType.HEBREW,
            ListStyleShorthand.apply(base, "square circle").listStyleType)
        assertEquals(ListStyleType.HEBREW,
            ListStyleShorthand.apply(base, "inside outside").listStyleType)
        assertEquals(ListStyleType.HEBREW,
            ListStyleShorthand.apply(base, "url(a.png) url(b.png)").listStyleType)
        // Empty / absent value is a no-op too.
        assertEquals(ListStyleType.HEBREW, ListStyleShorthand.apply(base, "   ").listStyleType)
        assertEquals(ListStyleType.HEBREW, ListStyleShorthand.apply(base, null).listStyleType)
    }

    @Test
    fun theShorthandReachesTheMarkerResolver() {
        // End-to-end: a foreign wire doc that puts the shorthand on the
        // <li> now paints the right glyph instead of the container default.
        assertEquals("I.", marker("ul", ulInside, listOf(pair("ListStyle", "\"upper-roman\""))))
        assertEquals("", marker("ul", ulInside, listOf(pair("ListStyle", "\"none\""))))
    }

    // ── Wave 25 skeptic: the two twin-dump divergences ──────────────────

    @Test
    fun anInheritedShorthandKeepsItsPositionUnderTheUaRule() {
        // Regression: the UA rule used to REPLACE the whole inherited
        // entry with a ListStyleType, so when the ancestor's value arrived
        // as the unexpanded `list-style: circle inside` shorthand its
        // POSITION component died with it — the `<li>` fell back to
        // `outside`. Only the type is displaced (ul/ol carry no UA
        // list-style-position declaration); browser probe:
        // `<div style="list-style-position:inside"><ul><li>` computes
        // `inside` on the li in Chromium.
        val ancestorShorthand = listOf(prop("ListStyle", "\"circle inside\""))
        val corrected = ListStyleUaRule.apply("ul", emptyList(), ancestorShorthand)
        val cfg = ListStyleExtractor.resolveMarkerConfig(
            "ul", corrected.map { it.type to it.data }, emptyList())
        // Type: the UA `disc` beats the ancestor's `circle`…
        assertEquals(ListStyleType.DISC, cfg?.listStyleType)
        // …but the position it carried still inherits.
        assertEquals(ListStylePosition.INSIDE, cfg?.listStylePosition)
        // A longhand ListStyleType alongside it is still displaced.
        val both = listOf(prop("ListStyle", "\"circle inside\""), prop("ListStyleType", "\"square\""))
        val fixed = ListStyleUaRule.apply("ul", emptyList(), both)
        val cfg2 = ListStyleExtractor.resolveMarkerConfig(
            "ul", fixed.map { it.type to it.data }, emptyList())
        assertEquals(ListStyleType.DISC, cfg2?.listStyleType)
        assertEquals(ListStylePosition.INSIDE, cfg2?.listStylePosition)
    }

    @Test
    fun theShorthandsUrlFunctionNameIsCaseInsensitive() {
        // css-syntax-3 §4.3 — function names are case-insensitive. The
        // component test already lowercased the token, but the strip did
        // not: `URL(a.png)` kept its whole wrapper as the image string on
        // Compose while the iOS twin produced `a.png`.
        assertEquals("a.png", ListStyleShorthand.apply(ListStyleConfig(), "URL(a.png)").listStyleImage)
        assertEquals("a.png", ListStyleShorthand.apply(ListStyleConfig(), "url(a.png)").listStyleImage)
        // One quote layer only — the iOS `unquote` twin peels exactly one.
        assertEquals("q.png", ListStyleShorthand.apply(ListStyleConfig(), "url(\"q.png\")").listStyleImage)
        assertEquals("'a'", ListStyleShorthand.apply(ListStyleConfig(), "url(\"'a'\")").listStyleImage)
    }
}
