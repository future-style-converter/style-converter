package com.styleconverter.runtime.layout.flexbox

// Lane FLEX-SAFE — JVM pins for the abspos static-position safe-alignment
// fallback (css-flexbox-1 §4.1 sole-item hypothetical + css-align-3 §4.4
// overflow keywords), shared-semantics contract with the SwiftUI runtime:
// AbsposStaticAlignmentTests.swift pins the SAME (childPx, containerPx,
// spec) → offset table, so the two natives cannot drift.
//
// Fixture numbers: the flex-abspos-staticpos-align-self-safe-001/002 WPT
// containers are 50px border-box with a 3px border on the natives →
// 44px content/padding box; the abspos child is 65px (border painted
// inside the frame on both natives). The non-overflow rows use a 25px
// child (the safe-003 fixture's size).

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsposStaticAlignmentTest {

    // ── wire builders (shapes pinned against the LIVE converter) ──

    /** Typed wire: `align-self: center` → {"type":"AlignSelf","data":"CENTER"}. */
    private fun typed(keyword: String) = listOf(
        IRProperty("AlignSelf", Json.parseToJsonElement("\"$keyword\""))
    )

    /** Generic wire: `align-self: safe center` → the unmapped escape hatch. */
    private fun generic(rawValue: String) = listOf(
        IRProperty(
            "Generic",
            Json.parseToJsonElement(
                "{\"propertyName\":\"align-self\",\"rawValue\":\"$rawValue\",\"_unmapped\":true}"
            )
        )
    )

    /** Inset property with the typed-length wire (only the TYPE matters). */
    private fun inset(type: String) = IRProperty(
        type, Json.parseToJsonElement("{\"type\":\"length\",\"px\":10.0}")
    )

    private val center = AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.CENTER, safe = false)
    private val safeCenter = AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.CENTER, safe = true)
    private val end = AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.END, safe = false)
    private val safeEnd = AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.END, safe = true)

    // ── 1. wire resolution (both channels) ──

    @Test
    fun `typed CENTER resolves to unsafe center`() {
        // Plain keyword: default overflow behaviour keeps center even
        // when overflowing (the WPT refs paint symmetric spill).
        assertEquals(center, AbsposStaticAlignment.resolveCross(typed("CENTER")))
    }

    @Test
    fun `generic safe center resolves with the safe flag`() {
        // The exact live-converter wire of the safe-001/002 fixtures.
        assertEquals(safeCenter, AbsposStaticAlignment.resolveCross(generic("safe center")))
    }

    @Test
    fun `generic unsafe center resolves without the safe flag`() {
        // css-align-3 §4.4: unsafe == honour the alignment regardless.
        assertEquals(center, AbsposStaticAlignment.resolveCross(generic("unsafe center")))
    }

    @Test
    fun `generic safe end resolves - the safe-003 fixture value`() {
        assertEquals(safeEnd, AbsposStaticAlignment.resolveCross(generic("safe end")))
        // flex-end spelling folds to the same END base.
        assertEquals(safeEnd, AbsposStaticAlignment.resolveCross(generic("safe flex-end")))
    }

    @Test
    fun `no claim for absent auto stretch or unrelated generics`() {
        // Absent → the caller keeps the legacy start-anchored behaviour.
        assertNull(AbsposStaticAlignment.resolveCross(emptyList()))
        // auto/stretch/baseline have no static-position claim here
        // (css-flexbox-1 §4.1 treats them as flex-start for abspos).
        assertNull(AbsposStaticAlignment.resolveCross(typed("STRETCH")))
        assertNull(AbsposStaticAlignment.resolveCross(typed("AUTO")))
        // A Generic for a DIFFERENT property never resolves.
        assertNull(
            AbsposStaticAlignment.resolveCross(
                listOf(
                    IRProperty(
                        "Generic",
                        Json.parseToJsonElement(
                            "{\"propertyName\":\"justify-self\",\"rawValue\":\"safe center\",\"_unmapped\":true}"
                        )
                    )
                )
            )
        )
    }

    // ── 2. the fallback math — SHARED pins with the SwiftUI twin ──

    @Test
    fun `overflow - plain center keeps center - negative offset`() {
        // 65px child in a 44px container: free = −21 → center = −10.5
        // (the child spills 10.5px past BOTH edges — unsafe semantics).
        assertEquals(-10.5, AbsposStaticAlignment.crossOffset(65.0, 44.0, center), 1e-9)
    }

    @Test
    fun `overflow - safe center falls back to start`() {
        // css-align-3 §4.4: safe + overflow → start (offset 0) — the
        // exact geometry the safe-001/002 web captures paint.
        assertEquals(0.0, AbsposStaticAlignment.crossOffset(65.0, 44.0, safeCenter), 1e-9)
    }

    @Test
    fun `overflow - unsafe center keeps center`() {
        // unsafe center is byte-identical to plain center here.
        assertEquals(
            -10.5,
            AbsposStaticAlignment.crossOffset(
                65.0, 44.0,
                AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.CENTER, safe = false)
            ),
            1e-9
        )
    }

    @Test
    fun `overflow - end and safe end`() {
        // end → full free space (−21: bottom/right edge alignment)…
        assertEquals(-21.0, AbsposStaticAlignment.crossOffset(65.0, 44.0, end), 1e-9)
        // …while safe end falls back to start on overflow.
        assertEquals(0.0, AbsposStaticAlignment.crossOffset(65.0, 44.0, safeEnd), 1e-9)
    }

    @Test
    fun `fits - safe and unsafe agree - the safe-003 numbers`() {
        // 25px child in a 44px container: no overflow, so safe changes
        // nothing — center → 9.5, end/safe end → 19.
        assertEquals(9.5, AbsposStaticAlignment.crossOffset(25.0, 44.0, center), 1e-9)
        assertEquals(9.5, AbsposStaticAlignment.crossOffset(25.0, 44.0, safeCenter), 1e-9)
        assertEquals(19.0, AbsposStaticAlignment.crossOffset(25.0, 44.0, end), 1e-9)
        assertEquals(19.0, AbsposStaticAlignment.crossOffset(25.0, 44.0, safeEnd), 1e-9)
    }

    // ── 3. the inset gate (css-position-3 §3.5) ──

    @Test
    fun `explicit cross inset disables the static position`() {
        // Vertical axis: top/bottom + the logical block insets bind.
        assertTrue(AbsposStaticAlignment.hasCrossInset(listOf(inset("Top")), vertical = true))
        assertTrue(AbsposStaticAlignment.hasCrossInset(listOf(inset("InsetBlockEnd")), vertical = true))
        // A HORIZONTAL inset does not gate the vertical axis…
        assertFalse(AbsposStaticAlignment.hasCrossInset(listOf(inset("Left")), vertical = true))
        // …and vice versa (horizontal axis: left/right + inline insets).
        assertTrue(AbsposStaticAlignment.hasCrossInset(listOf(inset("Right")), vertical = false))
        assertFalse(AbsposStaticAlignment.hasCrossInset(listOf(inset("Bottom")), vertical = false))
    }
}
