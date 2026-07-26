// InlineAtomFlow — the PURE half of the wave-20 W3 inline-level atom
// flow: UA form-control widgets (and text-only anchors) that the
// browser lays INLINE-WRAPPED inside a block container (css-ui
// appearance-auto-001's 500px row soup) while the natives block-stacked
// them one per line. Byte-parallel twin of Compose
// layout/InlineAtomFlow.kt — same names, same pinned semantics
// (P15-P18); skeptic probes diff the two.
//
// Model (CSS 2.1 §9.4.2 inline formatting context, atom-only subset):
//  • atoms are OPAQUE inline-level replaced-ish boxes (inline-block
//    baseline rules, §10.8.1) — no text interleaving, no line breaking
//    inside an atom;
//  • atoms pack left-to-right separated by one collapsed white-space
//    advance (UAWidgetIntrinsics.atomGapPx), wrapping greedily at
//    width exhaustion (§9.4.2 line box filling; first-in-row never
//    wraps — the same loose rule the float packer pins);
//  • within a row, atoms align BASELINE-ish (§10.8): each atom carries
//    a descent (baseline distance up from its border-box bottom, from
//    the shared UAWidgetIntrinsics table); the row baseline sits at the
//    max ascent, giving the ref's exact row pitches (P17).
// The SwiftUI adapter lives in Renderer/InlineAtomBlockLayout.swift;
// this file has no SwiftUI dependency so the XCTest pins run it
// directly.
enum InlineAtomFlow {

    // P15 — the widget tag set of the wave-20 widget-identity contract,
    // plus <textarea>: the contract's attrs channel covers it and the
    // appearance-auto ref packs it inline with the buttons (row 2) —
    // excluding it would split the ref rows. <option> is widget CONTENT
    // (W2's), never a flow atom.
    private static let widgetTags: Set<String> = [
        "button", "input", "textarea", "select", "meter", "progress"
    ]

    /// P15 — the atom predicate. An inline-level atom is:
    ///  • a widget tag (above) whose display is not overridden to a
    ///    block-level value (css-display-3 §2: declared block/flex/grid/
    ///    table/list-item displays leave the inline flow; the UA default
    ///    for all these controls is inline-block), or
    ///  • an <a> carrying ONLY text (no element children) — plain inline
    ///    content the ref lays on the same line as the widgets.
    /// `displayKeyword` is the SHOUTY IR Display keyword or nil when the
    /// component declares none; any non-INLINE-prefixed keyword (BLOCK,
    /// FLEX, GRID, TABLE, LIST_ITEM, NONE, …) disqualifies — NONE renders
    /// nothing and must keep the frozen path that already handles it.
    static func isAtom(
        tag: String?,
        displayKeyword: String?,
        hasElementChildren: Bool,
        hasText: Bool
    ) -> Bool {
        // A declared non-inline display takes the box out of the inline
        // flow (css-display-3 §2) — the frozen block loop owns it.
        if let d = displayKeyword, !d.hasPrefix("INLINE") { return false }
        // The tag decides the family (widget vs text-only anchor).
        guard let t = tag?.lowercased() else { return false }
        // Widget atoms are opaque regardless of children (a <select>'s
        // <option>s are its content, not flow siblings).
        if widgetTags.contains(t) { return true }
        // Anchors qualify only in the text-only shape (P15) — an <a>
        // wrapping elements is a real inline subtree, out of scope.
        return t == "a" && hasText && !hasElementChildren
    }

    /// One segment of the child list, in sibling order: either an atom
    /// RUN (≥2 consecutive inline atoms, packed into rows by the
    /// adapter) or a SINGLE child that keeps the frozen block path —
    /// the exact shape of FloatRowPacking.Segment so the renderers'
    /// segment walks mirror each other.
    struct Segment: Equatable {
        // Child indices (into the original sibling list) in order.
        let indices: [Int]
        // True for a packable atom run (always ≥2 indices then).
        let isRun: Bool
    }

    /// P15 — split the sibling list into maximal atom runs and singles.
    /// ≥2 CONSECUTIVE atoms form a run; a lone atom stays a single (the
    /// block path already renders one control acceptably, and the
    /// conservative gate mirrors the float packer's F4 discipline).
    static func segment(_ atomFlags: [Bool]) -> [Segment] {
        // Output accumulator — segments in sibling order.
        var out: [Segment] = []
        // Current streak of consecutive atom indices.
        var streak: [Int] = []
        // Flush the open streak: a run when ≥2, singles otherwise.
        func flush() {
            if streak.count >= 2 {
                out.append(Segment(indices: streak, isRun: true))
            } else {
                // 0 or 1 atoms — each keeps the plain block path.
                for i in streak { out.append(Segment(indices: [i], isRun: false)) }
            }
            streak.removeAll()
        }
        // Single pass over the siblings, building streaks.
        for (i, isAtom) in atomFlags.enumerated() {
            if isAtom {
                // Extend the current atom streak.
                streak.append(i)
            } else {
                // Streak broken — flush, then the breaker stays a single.
                flush()
                out.append(Segment(indices: [i], isRun: false))
            }
        }
        // Trailing streak.
        flush()
        return out
    }

    /// Resolved geometry for one run: per-atom origins + reported size.
    struct FlowLayout: Equatable {
        // Per-atom x origin (border-box left edge), run-relative px.
        let x: [Double]
        // Per-atom y origin (border-box top edge), run-relative px.
        let y: [Double]
        // Widest row's margin-box extent.
        let width: Double
        // Σ row heights (ascent + descent per row, P17).
        let height: Double
    }

    /// P16/P17 — greedy inline packing of one atom run.
    ///
    /// Inputs are BORDER-box sizes plus per-atom `descents` and UA
    /// margins (all from the adapter's UAWidgetIntrinsics resolution).
    /// Packing (P16): cursor advances by margin-box width; atoms in the
    /// same row are separated by `gapPx` (the collapsed source
    /// white-space); an atom that would end past `availableWidth` wraps
    /// to a new row UNLESS it is the row's first (the same loose
    /// first-never-wraps rule the float packer pins — §9.4.2's line box
    /// always holds at least one atom).
    /// Baselines (P17, §10.8): row ascent A = max(h − descent); atom top
    /// y = rowTop + A − (h_i − descent_i); row height = A + max(descent),
    /// which reproduces the composed-WPT ref pitches exactly (row 1 → 2
    /// pitch 21 = field ascent 15 + descent 6; row 2 → 3 pitch 42 =
    /// textarea ascent 36 + button descent 6).
    ///
    /// Strut (P17b, wave-20 fix 4 — CSS 2.1 §10.8.1): every line box in
    /// an IFC contains a zero-width inline box with the block's font
    /// metrics, so each row's ascent/descent are FLOORED at
    /// `strutAscentPx`/`strutDescentPx`. The adapters pass the shared
    /// UAWidgetIntrinsics strut pins (15/5 — the 20px composed-WPT line
    /// box); the defaults 0.0 keep every strut-free geometry pin (and
    /// any non-corpus caller) byte-identical to wave-20's cut.
    static func layout(
        widths: [Double],
        heights: [Double],
        descents: [Double],
        marginStarts: [Double],
        marginEnds: [Double],
        availableWidth: Double,
        gapPx: Double,
        strutAscentPx: Double = 0.0,
        strutDescentPx: Double = 0.0
    ) -> FlowLayout {
        // Row assignment + border-box x from the greedy cursor.
        var rowOf = [Int](repeating: 0, count: widths.count)
        var xs = [Double](repeating: 0.0, count: widths.count)
        // Cursor = the current row's consumed extent (margin boxes+gaps).
        var x = 0.0
        var row = 0
        for (i, w) in widths.enumerated() {
            // Margin-box advance for this atom (P16).
            let outer = marginStarts[i] + w + marginEnds[i]
            // A non-first atom needs the inter-atom gap before it.
            let lead = x > 0.0 ? gapPx : 0.0
            // Wrap when the atom would overrun — never for a row-first.
            if x > 0.0 && x + lead + outer > availableWidth { row += 1; x = 0.0 }
            // Border-box origin = cursor + (gap when mid-row) + margin.
            xs[i] = x + (x > 0.0 ? gapPx : 0.0) + marginStarts[i]
            rowOf[i] = row
            // Advance past the margin box (+ the gap actually consumed).
            x += (x > 0.0 ? gapPx : 0.0) + outer
        }
        // P17 — per-row ascent/descent maxima over the members.
        // P17b — seed every row with the strut metrics (CSS 2.1 §10.8.1:
        // the line box's zero-width strut participates in the max() like
        // any member); 0.0 defaults make this a no-op for legacy pins.
        var rowAscent = [Double](repeating: strutAscentPx, count: row + 1)
        var rowDescent = [Double](repeating: strutDescentPx, count: row + 1)
        for i in widths.indices {
            // Atom ascent = border-box height above its baseline (§10.8.1:
            // inline-block baseline; the table's descent encodes it).
            let ascent = heights[i] - descents[i]
            if ascent > rowAscent[rowOf[i]] { rowAscent[rowOf[i]] = ascent }
            if descents[i] > rowDescent[rowOf[i]] { rowDescent[rowOf[i]] = descents[i] }
        }
        // Row tops = running sum of previous row heights (A + D each).
        // (Kotlin's `for (r in 1..row)` — empty when there is one row.)
        var rowTops = [Double](repeating: 0.0, count: row + 1)
        for r in stride(from: 1, through: row, by: 1) {
            rowTops[r] = rowTops[r - 1] + rowAscent[r - 1] + rowDescent[r - 1]
        }
        // Atom top: baseline alignment against the row baseline (P17).
        let ys = widths.indices.map { i in
            rowTops[rowOf[i]] + rowAscent[rowOf[i]] - (heights[i] - descents[i])
        }
        // Extent bookkeeping: widest row's consumed cursor extent.
        var rowExtents = [Double](repeating: 0.0, count: row + 1)
        for i in widths.indices {
            // Recompute the row-relative right edge from the atom's
            // border-box origin + width + its end margin.
            let rightEdge = xs[i] + widths[i] + marginEnds[i]
            if rightEdge > rowExtents[rowOf[i]] { rowExtents[rowOf[i]] = rightEdge }
        }
        let width = rowExtents.max() ?? 0.0
        // Total height = bottom of the last row.
        let height = rowTops[row] + rowAscent[row] + rowDescent[row]
        return FlowLayout(x: xs, y: ys, width: width, height: height)
    }
}
