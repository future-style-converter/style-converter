package com.styleconverter.runtime.lists

// Wave 52, lane L6 (T7's native half) — pins for ListMarkerEmptyItem and the
// hang's `itemIsEmpty` height. TWIN of runtimes/swiftui/Tests/
// StyleConverterRuntimeTests/ListMarkerEmptyItemTests.swift.
//
// Every item below is a VERBATIM wave51-fix per-test IR `<li>` (byte-shapes
// copied from tools/titan/runs/wave51-fix/sections/*/per-test-ir/):
//   cssom-pad-setter-invalid `__0__0` AFTER L5's F-E (its 100×100
//     placeholder pair gone — the empty bag F-E emits) — EMPTY;
//   the same item BEFORE F-E (Width/Height 100) — declared size, NOT empty;
//   css-lists/counter-list-item-2 `__0` — a baked ::before "1" — NOT empty;
//   css3-counter-styles-101's abspos-only item — declared height — NOT empty;
//   name-case-sensitivity `__0__0` — Float + list-style only — EMPTY.
//
// MUTATION PROOF (EXECUTED 2026-10-05 by tools/titan/results/
// wave52-counters-and-lists/mutate.py, entry `empty-pseudos` in
// mutations.log): dropping the `pseudos` clause turned
// `a baked before counter is content` red; the other pins stayed green.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListMarkerEmptyItemTest {

    private fun json(s: String) = Json.parseToJsonElement(s)
    private fun li(props: List<IRProperty> = emptyList(), pseudos: JsonObject? = null) =
        IRComponent(id = "li", name = "li", properties = props, pseudos = pseudos)

    private val placeholder = listOf(
        IRProperty("Width", json("""{"type":"length","px":100}""")),
        IRProperty("Height", json("""{"type":"length","px":100}"""))
    )

    @Test
    fun `the post-F-E cssom item is empty and takes the row, not the overlay`() {
        val item = li()
        assertTrue(ListMarkerEmptyItem.isEmpty(item))
        // The plain overlay rule WOULD overlay it (inside, no text)…
        assertTrue(ListMarkerRow.rendersInsideOverlay(ListStylePosition.INSIDE,
            ListMarkerRow.itemExposesTextBaseline(item)))
        // …the emptiness-aware rule does not: the marker must size the line.
        assertFalse(ListMarkerEmptyItem.rendersInsideOverlay(ListStylePosition.INSIDE, item))
    }

    @Test
    fun `a declared block size keeps the item's own box and the overlay`() {
        // The wave51-fix (pre-F-E) placeholder and the abspos-only shape.
        assertFalse(ListMarkerEmptyItem.isEmpty(li(placeholder)))
        assertTrue(ListMarkerEmptyItem.rendersInsideOverlay(ListStylePosition.INSIDE, li(placeholder)))
        val abspos = li(listOf(IRProperty("Height", json("""{"type":"length","px":31.25}"""))))
        assertFalse(ListMarkerEmptyItem.isEmpty(abspos))
    }

    @Test
    fun `a baked before counter is content`() {
        // counter-list-item-2's `<li>`: `pseudos.before._text = "1"`.
        val before = json("""{"before":{"properties":{"content":"\"1\""},"_text":"1"}}""") as JsonObject
        assertFalse(ListMarkerEmptyItem.isEmpty(li(pseudos = before)))
    }

    @Test
    fun `text or children are content, list-style declarations are not`() {
        assertFalse(ListMarkerEmptyItem.isEmpty(IRComponent(id = "li", name = "li", _text = "foo")))
        assertFalse(ListMarkerEmptyItem.isEmpty(IRComponent(id = "li", name = "li",
            children = listOf(IRComponent(id = "c", name = "c")))))
        // name-case-sensitivity: Float + ListStyleType + ListStylePosition only.
        val floated = li(listOf(IRProperty("Float", json("\"LEFT\"")),
            IRProperty("ListStyleType", json("\"Hiragana\"")), IRProperty("ListStylePosition", json("\"INSIDE\""))))
        assertTrue(ListMarkerEmptyItem.isEmpty(floated))
    }

    @Test
    fun `an outside hang on an empty item is the marker's line tall`() {
        // Marker 30×20 (a "001." at 16px), empty item 0 tall.
        val p = ListMarkerOutsideHang.place(30, 20, null, 158, 0, null, 4,
            alignsByBaseline = false, itemIsEmpty = true)
        assertEquals(20, p.height)
        // Not empty → the item's size only (the wave-52 T5 identity).
        assertEquals(0, ListMarkerOutsideHang.place(30, 20, null, 158, 0, null, 4,
            alignsByBaseline = false).height)
    }
}
