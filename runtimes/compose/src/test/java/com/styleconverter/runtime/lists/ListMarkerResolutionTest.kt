package com.styleconverter.runtime.lists

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // HTML §15.3.9 UA sheet: ul ⇒ disc, ol ⇒ decimal.
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
}
