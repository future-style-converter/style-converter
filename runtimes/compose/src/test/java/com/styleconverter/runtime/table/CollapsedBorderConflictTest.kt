package com.styleconverter.runtime.table

// Wave 34 (lane T, T2) — JVM pins for CSS 2.1 §17.6.2.1 border conflict
// resolution. Byte-parallel with `runtimes/swiftui/Tests/
// StyleConverterRuntimeTests/CollapsedBorderConflictTests.swift`: same
// cases, same order, same expectations.
//
// The sixteen "origin precedence" cases below are not invented — they are
// the sixteen floated tables of CSS2/borders/border-conflict-style-107,
// read straight off `tools/wpt/css/CSS2/borders/border-conflict-style-107
// .html`, where every conflict is 25px `solid` on both sides and the two
// declarations "differ only in color" (green wins, red loses). That test
// scores web-ref 0.782 / android-ref 0.2825 / ios-ref 0.2881 in the frozen
// wave33-final gate; this file pins the half of the gap that is a pure
// decision, and CollapsedBorderConflict's header records — honestly — the
// halves that are not.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollapsedBorderConflictTest {

    // A 25px solid declaration from one origin — the test's own shape.
    private fun solid25(origin: CollapsedBorderConflict.Origin, order: Int = 0) =
        CollapsedBorderConflict.Edge(CollapsedBorderConflict.Style.SOLID, 25.0, origin, order)

    // ── Rule 4: the sixteen origin-precedence cases of the WPT test ────

    @Test
    fun `the sixteen border-conflict-style-107 tables all resolve to the winner`() {
        val cell = CollapsedBorderConflict.Origin.CELL
        val row = CollapsedBorderConflict.Origin.ROW
        val group = CollapsedBorderConflict.Origin.ROW_GROUP
        val col = CollapsedBorderConflict.Origin.COLUMN
        val colGroup = CollapsedBorderConflict.Origin.COLUMN_GROUP
        val table = CollapsedBorderConflict.Origin.TABLE
        // (loser, winner) exactly as the HTML enumerates them; index 1 is
        // always the `.winner` element, so every expectation is `1`.
        val cases = listOf(
            row to cell,        // 1  cell wins over row
            group to cell,      // 2  cell wins over row group
            col to cell,        // 3  cell wins over column
            colGroup to cell,   // 4  cell wins over column group
            table to cell,      // 5  cell wins over table
            group to row,       // 6  row wins over row group
            col to row,         // 7  row wins over column
            colGroup to row,    // 8  row wins over column group
            table to row,       // 9  row wins over table
            col to group,       // 10 row group wins over column
            colGroup to group,  // 11 row group wins over column group
            table to group,     // 12 row group wins over table
            colGroup to col,    // 13 column wins over column group
            table to col,       // 14 column wins over table
            table to colGroup,  // 15 column group wins over table
        )
        for ((i, c) in cases.withIndex()) {
            val (loser, winner) = c
            assertEquals(
                "table ${i + 1}: $winner must beat $loser",
                1,
                CollapsedBorderConflict.winner(listOf(solid25(loser), solid25(winner)))
            )
        }
        // 16 "Table wins when alone" — a lone declaration wins by default.
        assertEquals(0, CollapsedBorderConflict.winner(listOf(solid25(table))))
    }

    @Test
    fun `origin precedence is a total order, strongest first`() {
        val ranked = listOf(
            CollapsedBorderConflict.Origin.CELL,
            CollapsedBorderConflict.Origin.ROW,
            CollapsedBorderConflict.Origin.ROW_GROUP,
            CollapsedBorderConflict.Origin.COLUMN,
            CollapsedBorderConflict.Origin.COLUMN_GROUP,
            CollapsedBorderConflict.Origin.TABLE
        )
        // Strictly increasing rank, and the cell is rank 0.
        assertEquals(0, CollapsedBorderConflict.originRank(ranked.first()))
        for (i in 1 until ranked.size) {
            assertEquals(
                "${ranked[i]} must rank just below ${ranked[i - 1]}",
                CollapsedBorderConflict.originRank(ranked[i - 1]) + 1,
                CollapsedBorderConflict.originRank(ranked[i])
            )
        }
        // Handed ALL SIX at once — the resolver still picks the cell.
        assertEquals(0, CollapsedBorderConflict.winner(ranked.map { solid25(it) }))
    }

    // ── Rule 1: hidden ─────────────────────────────────────────────────

    @Test
    fun `one hidden declaration suppresses the whole edge`() {
        // §17.6.2.1 rule 1 — "any border with this value suppresses all
        // borders at this location". Even against a wider, stronger, more
        // senior neighbour.
        assertNull(
            CollapsedBorderConflict.winner(
                listOf(
                    CollapsedBorderConflict.Edge(
                        CollapsedBorderConflict.Style.DOUBLE, 99.0,
                        CollapsedBorderConflict.Origin.CELL
                    ),
                    CollapsedBorderConflict.Edge(
                        CollapsedBorderConflict.Style.HIDDEN, 1.0,
                        CollapsedBorderConflict.Origin.TABLE
                    )
                )
            )
        )
    }

    // ── Rule 2: none ───────────────────────────────────────────────────

    @Test
    fun `an all-none edge paints nothing`() {
        assertNull(
            CollapsedBorderConflict.winner(
                listOf(
                    CollapsedBorderConflict.Edge(
                        CollapsedBorderConflict.Style.NONE, 25.0,
                        CollapsedBorderConflict.Origin.CELL
                    ),
                    CollapsedBorderConflict.Edge(
                        CollapsedBorderConflict.Style.NONE, 25.0,
                        CollapsedBorderConflict.Origin.TABLE
                    )
                )
            )
        )
        // …and an empty edge list likewise.
        assertNull(CollapsedBorderConflict.winner(emptyList()))
    }

    @Test
    fun `none loses to any real border, however junior`() {
        // The cell's `none` must NOT beat the table's solid, even though
        // the cell outranks the table — rule 2 comes first.
        val edges = listOf(
            CollapsedBorderConflict.Edge(
                CollapsedBorderConflict.Style.NONE, 25.0,
                CollapsedBorderConflict.Origin.CELL
            ),
            solid25(CollapsedBorderConflict.Origin.TABLE)
        )
        assertEquals(1, CollapsedBorderConflict.winner(edges))
    }

    @Test
    fun `a zero-width declaration carries no border to win with`() {
        // §8.5.3 gives `none` a computed width of 0; the converse case — a
        // named style at width 0 — has nothing to paint either.
        val edges = listOf(
            CollapsedBorderConflict.Edge(
                CollapsedBorderConflict.Style.SOLID, 0.0,
                CollapsedBorderConflict.Origin.CELL
            ),
            solid25(CollapsedBorderConflict.Origin.TABLE)
        )
        assertEquals(1, CollapsedBorderConflict.winner(edges))
    }

    // ── Rule 3: width first, then style rank ───────────────────────────

    @Test
    fun `wider wins over more senior`() {
        // "narrow borders are discarded in favor of wider ones" — width is
        // compared BEFORE origin, so the table's 30px beats the cell's 25.
        val edges = listOf(
            solid25(CollapsedBorderConflict.Origin.CELL),
            CollapsedBorderConflict.Edge(
                CollapsedBorderConflict.Style.SOLID, 30.0,
                CollapsedBorderConflict.Origin.TABLE
            )
        )
        assertEquals(1, CollapsedBorderConflict.winner(edges))
    }

    @Test
    fun `style rank is double solid dashed dotted ridge outset groove inset`() {
        val order = listOf(
            CollapsedBorderConflict.Style.DOUBLE,
            CollapsedBorderConflict.Style.SOLID,
            CollapsedBorderConflict.Style.DASHED,
            CollapsedBorderConflict.Style.DOTTED,
            CollapsedBorderConflict.Style.RIDGE,
            CollapsedBorderConflict.Style.OUTSET,
            CollapsedBorderConflict.Style.GROOVE,
            CollapsedBorderConflict.Style.INSET
        )
        for (i in 1 until order.size) {
            assertEquals(
                "${order[i]} must rank just below ${order[i - 1]}",
                CollapsedBorderConflict.styleRank(order[i - 1]) + 1,
                CollapsedBorderConflict.styleRank(order[i])
            )
        }
        // Equal width, equal origin: the better style wins regardless of
        // the order they arrive in.
        for (i in 1 until order.size) {
            val edges = listOf(
                CollapsedBorderConflict.Edge(
                    order[i], 25.0, CollapsedBorderConflict.Origin.CELL
                ),
                CollapsedBorderConflict.Edge(
                    order[i - 1], 25.0, CollapsedBorderConflict.Origin.CELL
                )
            )
            assertEquals("${order[i - 1]} beats ${order[i]}", 1, CollapsedBorderConflict.winner(edges))
        }
    }

    @Test
    fun `style rank is only consulted after width`() {
        // A 25px `inset` (the weakest style) beats a 5px `double` (the
        // strongest) — rule 3 puts width first.
        val edges = listOf(
            CollapsedBorderConflict.Edge(
                CollapsedBorderConflict.Style.DOUBLE, 5.0,
                CollapsedBorderConflict.Origin.CELL
            ),
            CollapsedBorderConflict.Edge(
                CollapsedBorderConflict.Style.INSET, 25.0,
                CollapsedBorderConflict.Origin.TABLE
            )
        )
        assertEquals(1, CollapsedBorderConflict.winner(edges))
    }

    // ── Rule 4's tail: document order ──────────────────────────────────

    @Test
    fun `same origin ties break to the earlier declaration`() {
        // "the one further to the left … and further to the top wins" —
        // supplied to the resolver as document order.
        val edges = listOf(
            solid25(CollapsedBorderConflict.Origin.CELL, order = 3),
            solid25(CollapsedBorderConflict.Origin.CELL, order = 1)
        )
        assertEquals(1, CollapsedBorderConflict.winner(edges))
    }

    @Test
    fun `a total tie is resolved deterministically to the first`() {
        // Not a spec rule — a determinism guarantee, so the two natives
        // cannot paint different colours from identical inputs.
        val edges = listOf(
            solid25(CollapsedBorderConflict.Origin.CELL),
            solid25(CollapsedBorderConflict.Origin.CELL)
        )
        assertEquals(0, CollapsedBorderConflict.winner(edges))
    }

    // ── Wire decoding ──────────────────────────────────────────────────

    @Test
    fun `border style keywords decode from the uppercased wire`() {
        assertEquals(CollapsedBorderConflict.Style.SOLID, CollapsedBorderConflict.styleOf("SOLID"))
        assertEquals(CollapsedBorderConflict.Style.HIDDEN, CollapsedBorderConflict.styleOf("hidden"))
        assertEquals(CollapsedBorderConflict.Style.DOUBLE, CollapsedBorderConflict.styleOf("Double"))
        // An unnameable keyword cannot win, so it decodes to NONE rather
        // than throwing — the no-silent-fallthrough rule points the other
        // way here: a throw would take down an unrelated capture.
        assertEquals(CollapsedBorderConflict.Style.NONE, CollapsedBorderConflict.styleOf("WAVY"))
        assertEquals(CollapsedBorderConflict.Style.NONE, CollapsedBorderConflict.styleOf(null))
    }

    @Test
    fun `source tags map to rule 4 origins`() {
        assertEquals(CollapsedBorderConflict.Origin.CELL, CollapsedBorderConflict.originOf("td"))
        assertEquals(CollapsedBorderConflict.Origin.CELL, CollapsedBorderConflict.originOf("TH"))
        assertEquals(CollapsedBorderConflict.Origin.ROW, CollapsedBorderConflict.originOf("tr"))
        assertEquals(CollapsedBorderConflict.Origin.ROW_GROUP, CollapsedBorderConflict.originOf("tbody"))
        assertEquals(CollapsedBorderConflict.Origin.ROW_GROUP, CollapsedBorderConflict.originOf("thead"))
        assertEquals(CollapsedBorderConflict.Origin.ROW_GROUP, CollapsedBorderConflict.originOf("tfoot"))
        assertEquals(CollapsedBorderConflict.Origin.COLUMN, CollapsedBorderConflict.originOf("col"))
        assertEquals(
            CollapsedBorderConflict.Origin.COLUMN_GROUP,
            CollapsedBorderConflict.originOf("colgroup")
        )
        assertEquals(CollapsedBorderConflict.Origin.TABLE, CollapsedBorderConflict.originOf("table"))
        // Not a table-internal element.
        assertNull(CollapsedBorderConflict.originOf("div"))
        assertNull(CollapsedBorderConflict.originOf(null))
    }

    // ── Collapsed geometry (§17.6.2) ───────────────────────────────────

    @Test
    fun `a collapsed border is centered on the grid line`() {
        val (inner, outer) = CollapsedBorderConflict.centeredHalves(25.0)
        assertEquals(12.5, inner, 0.0)
        assertEquals(12.5, outer, 0.0)
        // border-conflict-style-107's geometry, end to end: an empty cell
        // with a 25px collapsed border is 25 wide (0 + 12.5 + 12.5), its
        // table is 50 (25 + 12.5 + 12.5), and four floated tables make the
        // reference's 200px square. This is the number the natives' 390×1404
        // canvas is missing.
        val cellBox = 0.0 + inner * 2
        val tableBox = cellBox + outer * 2
        assertEquals(25.0, cellBox, 0.0)
        assertEquals(50.0, tableBox, 0.0)
        assertEquals(200.0, tableBox * 4, 0.0)
    }
}
