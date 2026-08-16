package com.styleconverter.runtime.lists

// Wave 43, lane V4 — pin table for ListOrdinal: the HTML §4.4.5/§4.4.8
// ordinal algorithm (`<ol start>` base, `<li value>` reset, reversed
// countdown), built from the VERBATIM `meta.attrs` payloads the wave42
// gate recorded for wpt/css-lists/counter-list-item.html
// (tools/titan/runs/wave42-final/sections/css-lists/per-test-ir/
// wpt__css-lists__counter-list-item.json: `{"start":"30"}` on the second
// ol, `{"value":"30"}`/`{"value":"35"}` on the value'd items) and the
// ordinals visible in the frozen Chromium ref PNG.
//
// TWIN of the iOS suite ListOrdinalTests.swift — same cases, same
// expectations. Change one, change both.

import com.styleconverter.runtime.core.ir.IRAttrs
import com.styleconverter.runtime.core.ir.IRComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListOrdinalTest {

    /** One `<li>` child, optionally carrying the wire's verbatim value. */
    private fun li(value: String? = null) = IRComponent(
        id = "li", name = "li", _tag = "li",
        attrs = value?.let { IRAttrs(value = it) }
    )

    /** A non-item sibling (an anonymous text run) — consumes no ordinal. */
    private fun textRun() = IRComponent(id = "t", name = "t", _text = "x")

    // ── parseHtmlInteger: HTML §2.3.4.1 ────────────────────────────────

    @Test
    fun `plain digits parse`() =
        assertEquals(30, ListOrdinal.parseHtmlInteger("30"))

    @Test
    fun `leading whitespace and signs follow the spec`() {
        // Step 4 skips ASCII whitespace before the sign.
        assertEquals(5, ListOrdinal.parseHtmlInteger("  +5"))
        // '-' flips the sign (steps 5-6).
        assertEquals(-2, ListOrdinal.parseHtmlInteger("-2"))
    }

    @Test
    fun `trailing junk is ignored not fatal`() =
        // §2.3.4.1 returns once the digit run ends — browser behaviour.
        assertEquals(30, ListOrdinal.parseHtmlInteger("30abc"))

    @Test
    fun `non-integers are null`() {
        assertNull(ListOrdinal.parseHtmlInteger(null))
        assertNull(ListOrdinal.parseHtmlInteger(""))
        assertNull(ListOrdinal.parseHtmlInteger("abc"))
        // A sign with no digits is an error, not zero (step 8).
        assertNull(ListOrdinal.parseHtmlInteger("-"))
    }

    // ── ordinals: the counter-list-item payloads, forward lists ────────

    @Test
    fun `a plain ol numbers from one`() =
        // First ordered list in the fixture: no attrs at all → 1,2,3.
        assertEquals(
            listOf(1, 2, 3),
            ListOrdinal.ordinals(null, false, List(3) { li() }).toList()
        )

    @Test
    fun `ol start 30 numbers from thirty`() =
        // The fixture's second ol: meta.attrs {"start":"30"} — the ref
        // paints 30. 31. 32. where the raw index painted 1. 2. 3.
        assertEquals(
            listOf(30, 31, 32),
            ListOrdinal.ordinals("30", false, List(3) { li() }).toList()
        )

    @Test
    fun `li value resets the running counter`() =
        // The fixture's third ol: value=30 on item 3, value=35 on item 5
        // → ref paints 1,2,30,31,35,36.
        assertEquals(
            listOf(1, 2, 30, 31, 35, 36),
            ListOrdinal.ordinals(
                null, false,
                listOf(li(), li(), li("30"), li(), li("35"), li())
            ).toList()
        )

    @Test
    fun `a negative start counts up through zero`() =
        // HTML integers are signed: <ol start="-2"> → -2,-1,0,1.
        assertEquals(
            listOf(-2, -1, 0, 1),
            ListOrdinal.ordinals("-2", false, List(4) { li() }).toList()
        )

    @Test
    fun `non-item siblings consume no ordinal`() =
        // A whitespace run between items must not shift the numbering
        // (the raw-loop-index defect this lane closes).
        assertEquals(
            listOf(1, 2),
            ListOrdinal.ordinals(
                null, false, listOf(li(), textRun(), li())
            ).let { listOf(it[0], it[2]) }
        )

    // ── ordinals: the reversed columns ─────────────────────────────────
    // css-lists-3 §4.4.2 "Instantiating Counters" — the SPEC rule for a
    // reversed counter created without an explicit value, not a fit to the
    // ref PNG (the ref agrees because Chromium runs the same algorithm).
    // Wave 44 (lane U5) closed the wire gap these columns used to wait on:
    // the producer forwards `<ol reversed>` as a presence-`true` boolean and
    // the ComponentRenderer plan passes `attrs?.reversed == true` — so these
    // pins now guard the LIVE path, not a dormant one.

    @Test
    fun `reversed without start counts down to one`() =
        // Fixture's first reversed ol → ref paints 3. 2. 1. §4.4.2 with no
        // `counter-set` in the walk: num = 1+1+1 (+1 trailing) = 4, and the
        // first item's own −1 step lands its marker on 3.
        assertEquals(
            listOf(3, 2, 1),
            ListOrdinal.ordinals(null, true, List(3) { li() }).toList()
        )

    @Test
    fun `reversed with start 30 counts down from it`() =
        // Fixture's second reversed ol (start="30") → ref: 30. 29. 28.
        assertEquals(
            listOf(30, 29, 28),
            ListOrdinal.ordinals("30", true, List(3) { li() }).toList()
        )

    @Test
    fun `reversed anchors its countdown on the first li value`() =
        // Fixture's third reversed ol → ref paints 32,31,30,29,35,34, and
        // css-lists-3 §4.4.2 computes exactly that: the instantiation walk
        // adds +1 for each of the two plain items, hits `counter-set: 30`
        // (`<li value="30">`) and stops ⇒ num = 32, +1 trailing ⇒ initial
        // 33; the first item's −1 step makes its marker 32, so the run
        // flows INTO the value'd item. value=35 re-sets mid-walk and the
        // countdown continues. NOT a ref-fit: the naive "initial = item
        // count" shortcut ignores `counter-set` and would open this list on
        // 6,5,… — visibly wrong, and not what any CSS UA does.
        assertEquals(
            listOf(32, 31, 30, 29, 35, 34),
            ListOrdinal.ordinals(
                null, true,
                listOf(li(), li(), li("30"), li(), li("35"), li())
            ).toList()
        )

    // ── markerIndex: the getMarker bridge ──────────────────────────────

    @Test
    fun `markerIndex bridges ordinals to getMarker's 0-based input`() {
        // Ordinal 30 → index 29 → DECIMAL renders "30." end to end.
        val plan = ListOrdinal.ordinals("30", false, List(3) { li() })
        assertEquals(
            "30.",
            ListStyleApplier.getMarker(
                ListOrdinal.markerIndex(plan, 0),
                ListStyleConfig(listStyleType = ListStyleType.DECIMAL)
            )
        )
    }

    @Test
    fun `markerIndex without a plan is the raw index`() =
        // Null plan (non-list parents) keeps pre-wave-43 behaviour bit
        // for bit: raw child position passes straight through.
        assertEquals(3, ListOrdinal.markerIndex(null, 3))
}
