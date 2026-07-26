package com.styleconverter.runtime.layout.flexbox

// Wave 19 (lane FLEX) — JVM pins for the FULL abspos static-position
// resolver (AbsposStaticPosition): the css-flexbox-1 §5 axis-mapping
// table + the physical per-axis offset math, shared-semantics contract
// with the SwiftUI runtime: AbsposStaticPositionTests.swift pins the
// SAME wires and the SAME (childPx, containerPx, spec) → offset table,
// so the two natives cannot drift.
//
// PIN TABLE — the WPT flex-abspos-staticpos-align-self-safe fixtures,
// per sub-container. Containers are 50×50 padding-box (WPT content-box
// native path); child margin-box extents: 69 (safe-001: 65 + 2·2px
// border), 79 (safe-002: 69 + 2·5px margin), 29 (safe-003: 25 + 2·2px
// border). Offsets are the child MARGIN-box origin relative to the
// container's padding-box origin (+x right, +y down), matching the
// browser refs pixel-for-pixel:
//
//   test     | sub | flex-direction | writing-mode | claim        |   x |   y
//   safe-001 |  0  | row            | horizontal   | safe center  |   0 |   0
//   safe-001 |  1  | column         | horizontal   | safe center  |   0 |   0
//   safe-001 |  2  | row            | vertical-rl  | safe center  | −19 |   0
//   safe-001 |  3  | column         | vertical-rl  | safe center  | −19 |   0
//   safe-002 |  0  | row-reverse    | horizontal   | safe center  | −29 |   0
//   safe-002 |  1  | column-reverse | horizontal   | safe center  |   0 | −29
//   safe-002 |  2  | row-reverse    | vertical-rl  | safe center  | −29 | −29
//   safe-002 |  3  | column-reverse | vertical-rl  | safe center  |   0 |   0
//   safe-003 |  0  | row            | horizontal   | safe end     |   0 |  21
//   safe-003 |  1  | column         | horizontal   | safe end     |  21 |   0
//   safe-003 |  2  | row            | vertical-rl  | safe end     |   0 |   0
//   safe-003 |  3  | column         | vertical-rl  | safe end     |  21 |  21

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsposStaticPositionTest {

    // ── wire builders (shapes pinned against the LIVE converter) ──

    /** Typed keyword wire, e.g. {"type":"FlexDirection","data":"ROW_REVERSE"}. */
    private fun kw(type: String, keyword: String) =
        IRProperty(type, Json.parseToJsonElement("\"$keyword\""))

    /** Container list: raw flex-direction / writing-mode (+ optional justify). */
    private fun container(fd: String? = null, wm: String? = null, justify: String? = null) =
        buildList {
            fd?.let { add(kw("FlexDirection", it)) }
            wm?.let { add(kw("WritingMode", it)) }
            justify?.let { add(kw("JustifyContent", it)) }
        }

    /** Child list: the Generic align-self escape hatch (live wire shape). */
    private fun child(rawAlignSelf: String) = listOf(
        IRProperty(
            "Generic",
            Json.parseToJsonElement(
                "{\"propertyName\":\"align-self\",\"rawValue\":\"$rawAlignSelf\",\"_unmapped\":true}"
            )
        )
    )

    /** Inset property with the typed-length wire (only the TYPE matters). */
    private fun inset(type: String) = IRProperty(
        type, Json.parseToJsonElement("{\"type\":\"length\",\"px\":10.0}")
    )

    /** The full static offset for one axis-claim pair — the shared math. */
    private fun off(childPx: Double, containerPx: Double, spec: AbsposStaticPosition.AxisSpec?) =
        spec?.let { AbsposStaticPosition.axisOffset(childPx, containerPx, it) } ?: 0.0

    // ── 1. the axis-mapping table (css-flexbox-1 §5) ──

    @Test
    fun `axis map - horizontal-tb row family`() {
        // row: main = inline = +x; cross = block = +y.
        assertEquals(
            AbsposStaticPosition.AxisMap(mainIsHorizontal = true, mainReversed = false, crossReversed = false),
            AbsposStaticPosition.axisMap("ROW", null, null)
        )
        // Absent wire → the CSS initial row (css-flexbox-1 §5.1).
        assertEquals(
            AbsposStaticPosition.AxisMap(true, false, false),
            AbsposStaticPosition.axisMap(null, null, null)
        )
        // row-reverse flips ONLY the main axis.
        assertEquals(
            AbsposStaticPosition.AxisMap(true, true, false),
            AbsposStaticPosition.axisMap("ROW_REVERSE", null, null)
        )
    }

    @Test
    fun `axis map - horizontal-tb column family`() {
        // column: main = block = +y; cross = inline = +x.
        assertEquals(
            AbsposStaticPosition.AxisMap(false, false, false),
            AbsposStaticPosition.axisMap("COLUMN", null, null)
        )
        assertEquals(
            AbsposStaticPosition.AxisMap(false, true, false),
            AbsposStaticPosition.axisMap("COLUMN_REVERSE", null, null)
        )
    }

    @Test
    fun `axis map - vertical-rl swaps axes and reverses the block axis`() {
        // vertical-rl row: main = inline = +y (top→bottom); cross =
        // block = −x (right→left) — css-writing-modes-4 §2.4.
        assertEquals(
            AbsposStaticPosition.AxisMap(mainIsHorizontal = false, mainReversed = false, crossReversed = true),
            AbsposStaticPosition.axisMap("ROW", "VERTICAL_RL", null)
        )
        // vertical-rl column: main = block = −x; cross = inline = +y.
        assertEquals(
            AbsposStaticPosition.AxisMap(true, true, false),
            AbsposStaticPosition.axisMap("COLUMN", "VERTICAL_RL", null)
        )
        // vertical-rl row-reverse: main = inline reversed = −y.
        assertEquals(
            AbsposStaticPosition.AxisMap(false, true, true),
            AbsposStaticPosition.axisMap("ROW_REVERSE", "VERTICAL_RL", null)
        )
        // vertical-rl column-reverse: block-rl × reverse cancel → +x.
        assertEquals(
            AbsposStaticPosition.AxisMap(true, false, false),
            AbsposStaticPosition.axisMap("COLUMN_REVERSE", "VERTICAL_RL", null)
        )
        // vertical-lr: block runs +x — no cross reversal for row.
        assertEquals(
            AbsposStaticPosition.AxisMap(false, false, false),
            AbsposStaticPosition.axisMap("ROW", "VERTICAL_LR", null)
        )
        // RTL flips the INLINE axis (row main).
        assertEquals(
            AbsposStaticPosition.AxisMap(true, true, false),
            AbsposStaticPosition.axisMap("ROW", "HORIZONTAL_TB", "RTL")
        )
    }

    // ── 2. the physical offset math (shared table with iOS) ──

    @Test
    fun `axisOffset - reversed axes mirror inside the free space`() {
        val safeCenterRev = AbsposStaticPosition.AxisSpec(
            AbsposStaticAlignment.Base.CENTER, safe = true, reversed = true)
        // safe-001 C2 cross: 69px child in 50px, safe center overflows →
        // logical start → PHYSICAL END (right-anchored): x = −19.
        assertEquals(-19.0, AbsposStaticPosition.axisOffset(69.0, 50.0, safeCenterRev), 1e-9)
        val endRev = AbsposStaticPosition.AxisSpec(
            AbsposStaticAlignment.Base.END, safe = false, reversed = true)
        // Fits + end on a reversed axis = physical start: 0.
        assertEquals(0.0, AbsposStaticPosition.axisOffset(29.0, 50.0, endRev), 1e-9)
        val startRev = AbsposStaticPosition.AxisSpec(
            AbsposStaticAlignment.Base.START, safe = false, reversed = true)
        // Fits + start on a reversed axis = physical end: free = 21.
        assertEquals(21.0, AbsposStaticPosition.axisOffset(29.0, 50.0, startRev), 1e-9)
        // Center is symmetric — reversal changes nothing: −9.5 both ways.
        val centerRev = AbsposStaticPosition.AxisSpec(
            AbsposStaticAlignment.Base.CENTER, safe = false, reversed = true)
        val centerFwd = AbsposStaticPosition.AxisSpec(
            AbsposStaticAlignment.Base.CENTER, safe = false, reversed = false)
        assertEquals(
            AbsposStaticPosition.axisOffset(69.0, 50.0, centerFwd),
            AbsposStaticPosition.axisOffset(69.0, 50.0, centerRev), 1e-9)
    }

    // ── 3. the full fixture pin table (resolveStatic → axisOffset) ──

    /** Resolve + offset both axes for one sub-container configuration. */
    private fun place(
        fd: String?, wm: String?, rawAlignSelf: String,
        childW: Double, childH: Double, containerPx: Double = 50.0
    ): Pair<Double, Double> {
        val pos = AbsposStaticPosition.resolveStatic(container(fd, wm), child(rawAlignSelf))
        return off(childW, containerPx, pos.x) to off(childH, containerPx, pos.y)
    }

    @Test
    fun `safe-001 - four sub-containers`() {
        // C0 row htb: overflow safe center → start fallback on both axes.
        assertEquals(0.0 to 0.0, place("ROW", null, "safe center", 69.0, 69.0))
        // C1 column htb: same top-left anchor.
        assertEquals(0.0 to 0.0, place("COLUMN", null, "safe center", 69.0, 69.0))
        // C2 row vertical-rl: cross = block −x → RIGHT-anchored spill.
        assertEquals(-19.0 to 0.0, place("ROW", "VERTICAL_RL", "safe center", 69.0, 69.0))
        // C3 column vertical-rl: main = block −x → right-anchored too.
        assertEquals(-19.0 to 0.0, place("COLUMN", "VERTICAL_RL", "safe center", 69.0, 69.0))
    }

    @Test
    fun `safe-002 - reverse directions with margins`() {
        // Margin-box extent 79 (69 + 2·5px margin) — the static position
        // places the MARGIN box (css-flexbox-1 §4.1 hypothetical item).
        // C0 row-reverse htb: main −x → margin box right-anchored.
        assertEquals(-29.0 to 0.0, place("ROW_REVERSE", null, "safe center", 79.0, 79.0))
        // C1 column-reverse htb: main −y → bottom-anchored.
        assertEquals(0.0 to -29.0, place("COLUMN_REVERSE", null, "safe center", 79.0, 79.0))
        // C2 row-reverse vertical-rl: main −y AND cross −x.
        assertEquals(-29.0 to -29.0, place("ROW_REVERSE", "VERTICAL_RL", "safe center", 79.0, 79.0))
        // C3 column-reverse vertical-rl: the two x-flips cancel → (0,0).
        assertEquals(0.0 to 0.0, place("COLUMN_REVERSE", "VERTICAL_RL", "safe center", 79.0, 79.0))
    }

    @Test
    fun `safe-003 - safe end fits so END is honored`() {
        // Frame extent 29 (25 + 2·2px border) fits the 50px container.
        assertEquals(0.0 to 21.0, place("ROW", null, "safe end", 29.0, 29.0))
        assertEquals(21.0 to 0.0, place("COLUMN", null, "safe end", 29.0, 29.0))
        // C2 row vertical-rl: cross −x, end → physical LEFT: (0,0).
        assertEquals(0.0 to 0.0, place("ROW", "VERTICAL_RL", "safe end", 29.0, 29.0))
        // C3 column vertical-rl: main −x start → right; cross end → bottom.
        assertEquals(21.0 to 21.0, place("COLUMN", "VERTICAL_RL", "safe end", 29.0, 29.0))
    }

    // ── 4. justify-content as the sole-item main claim ──

    @Test
    fun `justify-content sole-item folds`() {
        // Declared center claims the main axis (css-position-3 §3.1.4.1).
        val pos = AbsposStaticPosition.resolveStatic(
            container(fd = "ROW", justify = "CENTER"), child("safe end"))
        assertEquals(
            AbsposStaticPosition.AxisSpec(AbsposStaticAlignment.Base.CENTER, false, false),
            pos.x)
        // …and marks the TYPED-declared flag (Compose arrangement gate).
        assertTrue(pos.justifyTyped)
        // Distribution keywords fold per css-align-3 §5.1 (sole item):
        // space-between → start, space-around → center.
        assertEquals(
            AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.START, false),
            AbsposStaticPosition.justifySpec(container(justify = "SPACE_BETWEEN")))
        assertEquals(
            AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.CENTER, false),
            AbsposStaticPosition.justifySpec(container(justify = "SPACE_AROUND")))
        // Undeclared → null (the resolver synthesizes default start).
        assertNull(AbsposStaticPosition.justifySpec(container(fd = "ROW")))
        assertFalse(AbsposStaticPosition.resolveStatic(container(fd = "ROW"), child("safe end")).justifyTyped)
        // Undeclared justify still yields the default START main claim —
        // reversal carried on the spec (row-reverse right anchor).
        val rev = AbsposStaticPosition.resolveStatic(container(fd = "ROW_REVERSE"), emptyList())
        assertEquals(
            AbsposStaticPosition.AxisSpec(AbsposStaticAlignment.Base.START, false, true),
            rev.x)
    }

    // ── 5. the §3.5 inset gate is PHYSICAL per axis ──

    @Test
    fun `explicit insets null the claim on their physical axis only`() {
        // dynamic-align-self-001's exact shape: align-self typed ships
        // through resolveCross; Top+Left insets kill BOTH axes.
        val insetChild = listOf(
            IRProperty("AlignSelf", Json.parseToJsonElement("\"END\"")),
            inset("Top"), inset("Left")
        )
        val pos = AbsposStaticPosition.resolveStatic(container(fd = "ROW"), insetChild)
        assertNull(pos.x)
        assertNull(pos.y)
        // A vertical-only inset keeps the horizontal claim: row main (x)
        // survives, the cross (y) claim stands down (css-position-3 §3.5).
        val vOnly = listOf(
            IRProperty("AlignSelf", Json.parseToJsonElement("\"END\"")),
            inset("Top")
        )
        val pos2 = AbsposStaticPosition.resolveStatic(container(fd = "ROW"), vOnly)
        assertEquals(
            AbsposStaticPosition.AxisSpec(AbsposStaticAlignment.Base.START, false, false),
            pos2.x)
        assertNull(pos2.y)
    }

    @Test
    fun `auto insets keep the static position - non-auto insets replace it`() {
        // Live wire (pinned 2026-07-26): `top: auto` ships as
        // {"type":"Top","data":"auto"} — §3.5 only replaces the static
        // position for NON-auto values, so both claims must stand.
        val autoChild = listOf(
            IRProperty("AlignSelf", Json.parseToJsonElement("\"END\"")),
            IRProperty("Top", Json.parseToJsonElement("\"auto\"")),
            IRProperty("Left", Json.parseToJsonElement("\"auto\""))
        )
        val pos = AbsposStaticPosition.resolveStatic(container(fd = "ROW"), autoChild)
        assertEquals(
            AbsposStaticPosition.AxisSpec(AbsposStaticAlignment.Base.START, false, false),
            pos.x)
        assertEquals(
            AbsposStaticPosition.AxisSpec(AbsposStaticAlignment.Base.END, false, false),
            pos.y)
        // Mixed: auto Top + px Left → x owned by the real inset, the
        // vertical (cross) claim stands.
        val mixed = listOf(
            IRProperty("AlignSelf", Json.parseToJsonElement("\"END\"")),
            IRProperty("Top", Json.parseToJsonElement("\"auto\"")),
            inset("Left")
        )
        val pos2 = AbsposStaticPosition.resolveStatic(container(fd = "ROW"), mixed)
        assertNull(pos2.x)
        assertEquals(
            AbsposStaticPosition.AxisSpec(AbsposStaticAlignment.Base.END, false, false),
            pos2.y)
    }

    @Test
    fun `definiteExtentPx - logical bare-px wire with writing-mode mapping`() {
        // Live wire (pinned 2026-07-26): `inline-size: 40px` ships as
        // {"type":"InlineSize","data":{"px":40.0}} — NO "length"
        // discriminator, unlike the physical Width/Height wire.
        val logical = listOf(
            IRProperty("InlineSize", Json.parseToJsonElement("{\"px\":40.0}")),
            IRProperty("BlockSize", Json.parseToJsonElement("{\"px\":30.0}"))
        )
        // horizontal-tb: inline = width (x), block = height (y).
        assertEquals(40.0, AbsposStaticPosition.definiteExtentPx(logical, vertical = false)!!, 1e-9)
        assertEquals(30.0, AbsposStaticPosition.definiteExtentPx(logical, vertical = true)!!, 1e-9)
        // vertical-rl swaps the pair (css-logical-1 §4.1): inline =
        // height, block = width.
        val verticalRl = logical + listOf(kw("WritingMode", "VERTICAL_RL"))
        assertEquals(30.0, AbsposStaticPosition.definiteExtentPx(verticalRl, vertical = false)!!, 1e-9)
        assertEquals(40.0, AbsposStaticPosition.definiteExtentPx(verticalRl, vertical = true)!!, 1e-9)
        // Relative-unit shape ({"original":{"v":4,"u":"EM"}}) stays
        // indefinite — no px channel to read.
        val em = listOf(IRProperty(
            "BlockSize", Json.parseToJsonElement("{\"original\":{\"v\":4.0,\"u\":\"EM\"}}")))
        assertNull(AbsposStaticPosition.definiteExtentPx(em, vertical = true))
    }

    // ── 6. definite extent reader (internal-placement basis) ──

    @Test
    fun `definiteExtentPx reads only the typed exact-length wire`() {
        val props = listOf(
            IRProperty("Width", Json.parseToJsonElement("{\"type\":\"length\",\"px\":50.0}")),
            IRProperty("Height", Json.parseToJsonElement("{\"type\":\"percentage\",\"value\":40.0}"))
        )
        // Width: definite 50; Height: percent → null (honest fallback).
        assertEquals(50.0, AbsposStaticPosition.definiteExtentPx(props, vertical = false)!!, 1e-9)
        assertNull(AbsposStaticPosition.definiteExtentPx(props, vertical = true))
    }
}
