package com.styleconverter.runtime.layout.flexbox

// Wave 25 lane CFLEX — CAL-RC4 pins.
//
// The overflow contract lives in one pure function so the JVM suite can
// state it without an emulator: what a resolved flex item is measured with
// when the container has already run out of main-axis room.

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Test

class FlexMainSizePinTest {

    // --- the defect: the container's leftover space used to win -------------

    @Test fun `an exhausted row still measures the item at its resolved width`() {
        // css-gaps flex-gap-decorations-008: six 50px `flex-shrink: 0` items
        // in a 196px content box. By item five the Row has 0px left, and
        // Modifier.width(50.dp) constrained itself down to that. §9.7.2
        // froze the item at 50 — so 50 is what it must be measured with.
        val incoming = Constraints(minWidth = 0, maxWidth = 0, minHeight = 0, maxHeight = 200)
        val out = mainSizePinConstraints(incoming, mainPx = 50, horizontal = true)
        assertEquals(50, out.minWidth)
        assertEquals(50, out.maxWidth)
    }

    @Test fun `a partially exhausted row is not allowed to shave the item`() {
        val incoming = Constraints(minWidth = 0, maxWidth = 12, minHeight = 0, maxHeight = 200)
        val out = mainSizePinConstraints(incoming, mainPx = 50, horizontal = true)
        assertEquals(50, out.maxWidth)
    }

    @Test fun `the column twin pins the block axis`() {
        val incoming = Constraints(minWidth = 0, maxWidth = 300, minHeight = 0, maxHeight = 5)
        val out = mainSizePinConstraints(incoming, mainPx = 80, horizontal = false)
        assertEquals(80, out.minHeight)
        assertEquals(80, out.maxHeight)
    }

    // --- the cross axis is somebody else's business -------------------------

    @Test fun `row pin passes the incoming block band through untouched`() {
        // align-items / align-self resolve the cross axis; the pin must not
        // silently re-decide it (a stretch child arrives with a fixed band).
        val incoming = Constraints(minWidth = 0, maxWidth = 100, minHeight = 40, maxHeight = 40)
        val out = mainSizePinConstraints(incoming, mainPx = 50, horizontal = true)
        assertEquals(40, out.minHeight)
        assertEquals(40, out.maxHeight)
    }

    @Test fun `column pin passes the incoming inline band through untouched`() {
        val incoming = Constraints(minWidth = 10, maxWidth = 100, minHeight = 0, maxHeight = 500)
        val out = mainSizePinConstraints(incoming, mainPx = 30, horizontal = false)
        assertEquals(10, out.minWidth)
        assertEquals(100, out.maxWidth)
    }

    // --- byte stability for every line that FITS ----------------------------

    @Test fun `a fitting line pins exactly what Modifier width would have`() {
        // The forced constraints are already inside the incoming ones, so
        // the old constrain() step was a no-op — the frozen corpus cannot
        // move because of this change unless its line actually overflows.
        val incoming = Constraints(minWidth = 0, maxWidth = 400, minHeight = 0, maxHeight = 200)
        val out = mainSizePinConstraints(incoming, mainPx = 93, horizontal = true)
        assertEquals(93, out.minWidth)
        assertEquals(93, out.maxWidth)
    }

    @Test fun `a negative rounding artefact floors at zero`() {
        val incoming = Constraints(minWidth = 0, maxWidth = 400, minHeight = 0, maxHeight = 200)
        val out = mainSizePinConstraints(incoming, mainPx = -3, horizontal = true)
        assertEquals(0, out.minWidth)
        assertEquals(0, out.maxWidth)
    }
}
