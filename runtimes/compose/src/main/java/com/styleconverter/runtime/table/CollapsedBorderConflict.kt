package com.styleconverter.runtime.table

// Wave 34 (lane T, T2) — CSS 2.1 §17.6.2.1 BORDER CONFLICT RESOLUTION,
// as a pure decision table. Twin: `runtimes/swiftui/Sources/
// StyleConverterRuntime/StyleEngine/table/CollapsedBorderConflict.swift`.
//
// ## The measured gap (frozen wave33-final pixel evidence)
// CSS2/borders/border-conflict-style-107 is the worst-scoring test in the
// frozen corpus: web-ref 0.782, android-ref 0.2825, ios-ref 0.2881. It
// floats SIXTEEN `border-collapse: collapse` tables, each a single empty
// cell, and puts a 25px `solid green` border on the element that CSS says
// wins and a 25px `solid red` border on the one that loses. The reference
// is one filled 200×200 green square (four tables per row, four rows) and
// NO red at all.
//
// Both natives paint 390×1404 canvases carrying 288 500 red pixels and
// 176 900 green — ink coverage 85.4% against the reference's 7.755%. The
// captures show why: every one of `<table>`/`<tbody>`/`<tr>`/`<td>` paints
// its OWN 25px border as an ordinary block box, nested one inside the
// next, and the sixteen tables stack vertically instead of packing four to
// a row. There is no collapsing model anywhere in either runtime — not the
// conflict resolution, not the shared-grid-line geometry.
//
// This file is the first of those two halves: given every border that
// meets at one grid line, WHICH one paints. It is deliberately pure — no
// Compose types, no colour type, no geometry — so it pins on the JVM and
// stays byte-parallel with the Swift twin.
//
// ## Honest scope (what this does NOT do yet)
// Landing this module does not move border-conflict-style-107's score,
// and this comment exists so nobody reads the test list and assumes it
// did. Three things still stand between here and that green square, none
// of them in this lane's owned regions:
//   1. the collapsed BOX MODEL — a collapsed table's used size is the
//      cell content plus the centred border halves ([centeredHalves]),
//      which neither runtime computes;
//   2. FLOAT packing of the sixteen `float: left` tables plus the
//      `br { clear: both }` breaks, which is why the natives' canvas is
//      1404px tall instead of 600;
//   3. the resolver's INPUTS — the `<col>` / `<colgroup>` / row-group
//      declarations reach a cell edge only once the renderers walk the
//      table box tree, and on Compose these tables never even reach
//      `DisplayType.TABLE` (the wire carries no `Display` on them, only
//      `meta.sourceTag`).
// What IS live: the decision table below, pinned on both natives against
// the sixteen cases the test itself enumerates.

/**
 * CSS 2.1 §17.6.2.1 — "Border conflict resolution".
 *
 * > Borders are resolved as follows: … the border styles of the cells,
 * > rows, row groups, columns, column groups and the table itself that
 * > meet at each edge are compared, and the one that wins is painted.
 */
object CollapsedBorderConflict {

    /**
     * The `border-style` keywords §17.6.2.1 ranks, worst to best.
     *
     * `HIDDEN` is NOT part of the rank — rule 1 removes the edge entirely
     * before ranking starts — but it is a member of the enum because it is
     * a real keyword the wire can carry, and giving it a rank would let a
     * caller accidentally treat it as "the strongest style" instead of
     * "no border at all".
     */
    enum class Style {
        /** Rule 1: suppresses every other border at this location. */
        HIDDEN,

        /** Rule 2: lowest priority; an all-`none` edge paints nothing. */
        NONE,

        // Rule 3's preference order, strongest first.
        DOUBLE, SOLID, DASHED, DOTTED, RIDGE, OUTSET, GROOVE, INSET
    }

    /**
     * The element a border declaration came from — rule 4's precedence
     * chain, strongest first.
     */
    enum class Origin { CELL, ROW, ROW_GROUP, COLUMN, COLUMN_GROUP, TABLE }

    /**
     * One border declaration meeting at a grid line.
     *
     * @property style its used `border-style`.
     * @property widthPx its used `border-width` in px. `NONE`/`HIDDEN`
     *   compute to 0 per §8.5.3, but the width is kept independent here so
     *   the caller can hand over exactly what the wire said and let the
     *   rules — not the caller — decide what that means.
     * @property origin which element declared it (rule 4).
     * @property order its position in document order among declarations of
     *   the SAME origin — rule 4's final tie-break, "the one further to the
     *   left … and further to the top wins". Smaller is earlier.
     */
    data class Edge(
        val style: Style,
        val widthPx: Double,
        val origin: Origin,
        val order: Int = 0
    )

    /**
     * Rule 3's style preference, strongest first:
     * `double`, `solid`, `dashed`, `dotted`, `ridge`, `outset`, `groove`,
     * `inset`. Lower is stronger. `NONE`/`HIDDEN` never reach the ranking
     * (rules 2 and 1 handle them), so they sit past the end of the scale.
     */
    fun styleRank(style: Style): Int = when (style) {
        Style.DOUBLE -> 0
        Style.SOLID -> 1
        Style.DASHED -> 2
        Style.DOTTED -> 3
        Style.RIDGE -> 4
        Style.OUTSET -> 5
        Style.GROOVE -> 6
        Style.INSET -> 7
        // Off the scale on purpose — see the enum's doc.
        Style.NONE, Style.HIDDEN -> 8
    }

    /**
     * Rule 4's origin precedence: "a style set on a cell wins over one on
     * a row, which wins over a row group, column, column group and,
     * lastly, table". Lower is stronger.
     */
    fun originRank(origin: Origin): Int = when (origin) {
        Origin.CELL -> 0
        Origin.ROW -> 1
        Origin.ROW_GROUP -> 2
        Origin.COLUMN -> 3
        Origin.COLUMN_GROUP -> 4
        Origin.TABLE -> 5
    }

    /**
     * The winning declaration's INDEX in [edges], or null when nothing is
     * painted at this grid line.
     *
     * Returning an index rather than a resolved border keeps this module
     * free of any colour or unit type, so the two natives can share one
     * decision table and each read its own `BorderTopColor` off the
     * winner. Rule 4 is expressed exactly that way in the spec — "if
     * border styles differ only in color" — the rules never inspect the
     * colour, they only decide whose colour paints.
     *
     * The four rules, in the spec's own order:
     *  1. any `hidden` suppresses the whole edge → null;
     *  2. `none` has the lowest priority, and an all-`none` edge paints
     *     nothing → null (a zero used width is treated the same: §8.5.3
     *     gives `none` a computed width of 0, so a 0-width declaration
     *     carries no border to win with);
     *  3. wider wins; on a width tie, the better style rank wins;
     *  4. on a style tie, the stronger origin wins; on an origin tie, the
     *     earlier declaration in document order wins.
     */
    fun winner(edges: List<Edge>): Int? {
        // Rule 1 — one `hidden` anywhere removes the edge for everyone.
        if (edges.any { it.style == Style.HIDDEN }) return null
        var bestIndex: Int? = null
        var best: Edge? = null
        for ((i, e) in edges.withIndex()) {
            // Rule 2 — `none` never wins, and neither does a declaration
            // whose used width is zero: there is no border there to paint.
            if (e.style == Style.NONE || e.widthPx <= 0.0) continue
            val b = best
            if (b == null || beats(e, i, b, bestIndex!!)) {
                best = e
                bestIndex = i
            }
        }
        // Rule 2's tail — every declaration was `none` (or zero-width).
        return bestIndex
    }

    /**
     * Does declaration [a] (at index [ai]) beat the current best [b] (at
     * index [bi])? Rules 3 and 4, applied in order and short-circuiting on
     * the first difference — the shape a `Comparator` would have if this
     * file were allowed to allocate one per edge.
     */
    private fun beats(a: Edge, ai: Int, b: Edge, bi: Int): Boolean {
        // Rule 3, first half — "narrow borders are discarded in favor of
        // wider ones".
        if (a.widthPx != b.widthPx) return a.widthPx > b.widthPx
        // Rule 3, second half — "if several have the same border-width
        // then styles are preferred in this order: double, solid, dashed,
        // dotted, ridge, outset, groove, and the lowest: inset".
        val ra = styleRank(a.style)
        val rb = styleRank(b.style)
        if (ra != rb) return ra < rb
        // Rule 4, first half — cell > row > row group > column >
        // column group > table.
        val oa = originRank(a.origin)
        val ob = originRank(b.origin)
        if (oa != ob) return oa < ob
        // Rule 4, second half — "when two elements of the same type
        // conflict, then the one further to the left … and further to the
        // top wins". The caller supplies that as document order.
        if (a.order != b.order) return a.order < b.order
        // Fully tied: keep the FIRST one seen, so the resolution is
        // deterministic and both natives pick the same declaration.
        return ai < bi
    }

    // ── Wire → enum decoding ───────────────────────────────────────────

    /**
     * A `border-*-style` keyword from the wire → [Style].
     *
     * The IR emits the CSS keyword uppercased (`"SOLID"`, `"DOUBLE"`).
     * Unknown keywords answer [Style.NONE] rather than throwing: §17.6.2.1
     * only ever needs to know whether a declaration can win, and one it
     * cannot name cannot win.
     */
    fun styleOf(keyword: String?): Style = when (keyword?.uppercase()) {
        "HIDDEN" -> Style.HIDDEN
        "DOUBLE" -> Style.DOUBLE
        "SOLID" -> Style.SOLID
        "DASHED" -> Style.DASHED
        "DOTTED" -> Style.DOTTED
        "RIDGE" -> Style.RIDGE
        "OUTSET" -> Style.OUTSET
        "GROOVE" -> Style.GROOVE
        "INSET" -> Style.INSET
        else -> Style.NONE
    }

    /**
     * An element's `meta.sourceTag` → the [Origin] its border declarations
     * carry, or null for a tag that is not a table-internal element.
     *
     * This is the ONE place the tag channel is authoritative for tables:
     * §17.6.2.1's rule 4 is written in terms of the ELEMENTS (cell, row,
     * row group, column, column group, table), and `<col>` / `<colgroup>`
     * generate no boxes at all (css-tables-3 §2.1), so a display keyword
     * could never identify them.
     */
    fun originOf(sourceTag: String?): Origin? = when (sourceTag?.lowercase()) {
        "td", "th" -> Origin.CELL
        "tr" -> Origin.ROW
        "tbody", "thead", "tfoot" -> Origin.ROW_GROUP
        "col" -> Origin.COLUMN
        "colgroup" -> Origin.COLUMN_GROUP
        "table" -> Origin.TABLE
        else -> null
    }

    // ── Collapsed geometry (§17.6.2) ───────────────────────────────────

    /**
     * The two halves of a collapsed border, which "is centered on the grid
     * line" (§17.6.2).
     *
     * @return `(inner, outer)` in px — the part painted on the CELL side of
     *   the grid line and the part painted on the other side. The split is
     *   exact halves; the caller rounds when it rasterises.
     *
     * This is the arithmetic border-conflict-style-107's geometry needs:
     * its `<td>` has zero content and a 25px collapsed border, so the cell
     * box is 0 + 12.5 + 12.5 = 25, the table box adds the outer halves at
     * its own edges for 25 + 12.5 + 12.5 = 50, and four such tables floated
     * in a row make the reference's 200×200 square exactly.
     */
    fun centeredHalves(widthPx: Double): Pair<Double, Double> {
        val half = widthPx / 2.0
        return Pair(half, half)
    }
}
