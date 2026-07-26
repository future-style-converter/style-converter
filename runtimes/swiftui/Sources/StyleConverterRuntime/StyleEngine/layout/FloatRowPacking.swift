// FloatRowPacking — the PURE half of CSS 2.1 §9.5 float row packing
// (wave-19 lane FLOAT, extended by wave-20 lane W3). Byte-parallel twin
// of Compose layout/FloatRowPacking.kt — same names, same pinned
// semantics (P1-P6 wave-19, P10-P14 wave-20); skeptic probes diff the
// two.
//
// Scope (narrow, corpus-sufficient — see the lane contracts):
//  • F1: consecutive same-direction LEFT floats pack into a horizontal
//    run, wrapping at width exhaustion (§9.5.1 rules 1-3,7 — greedy,
//    no interleaving with in-flow line boxes).
//  • F2: a childless clear sibling (the `<br clear>` wire shape) breaks
//    the run and marks it STRUTTED (§9.5.2 clearance + the br's empty
//    line box in the composed-WPT ref).
//  • F4 (wave-20 W3 revision): consecutive same-direction RIGHT floats
//    now ALSO pack — right-to-left from the containing block's right
//    edge (§9.5.1 rule 1 mirrored; css-grid descendant-static-position-
//    002/004's float:right pairs). Lone right floats and non-float
//    siblings keep the frozen paths (wave-5 end-alignment / block flow).
// The SwiftUI Layout adapter lives in Renderer/FloatBlockLayout.swift;
// this file has no SwiftUI dependency so the XCTest pins run it directly.
enum FloatRowPacking {

    // P6 — the composed-WPT ref line-box pin, in CSS px: 16px ref font ×
    // REF_DEFAULT_FONT_LINE_HEIGHT_RATIO 1.25 (Compose WptCaptureMode.kt)
    // == ComponentRenderer.wptRefLineBoxPx 20. A `<br clear:both>`
    // occupies one empty line box in the browser-ref, so the next float
    // row starts below max(float margin-bottom edge, line-box bottom).
    static let strutPx: Double = 20.0

    /// Per-sibling facts the segmenter consumes — derived ONCE per child
    /// from its IR Float/Clear keywords. (LayoutExtractor only reads the
    /// Float wire on this platform — Clear was registered-but-dropped —
    /// so this file owns the narrow clear-break read, mirroring Compose
    /// FloatExtractor's keyword table byte-for-byte.)
    struct ChildFacts: Equatable {
        // P1: Float ∈ {LEFT, INLINE_START} — the LTR-normalized engine
        // treats inline-start as left (css-logical-1 §2.1). Under an
        // rtl container the logical members swap (see `facts`).
        let floatsLeft: Bool
        // P2: childless + Float=NONE + Clear ∈ {BOTH, LEFT, INLINE_START}
        // — the `<br clear>` IR shape. Clear RIGHT/INLINE_END does NOT
        // clear a left run (§9.5.2: clearance only past same-side floats).
        let clearBreaksLeft: Bool
        // P10: Float ∈ {RIGHT, INLINE_END} (ltr) — the wave-20 right-run
        // family. Mutually exclusive with `floatsLeft` by construction.
        let floatsRight: Bool
        // P14: the §9.5.2 mirror of `clearBreaksLeft` — childless +
        // Float=NONE + Clear ∈ {BOTH, RIGHT, INLINE_END} (ltr).
        let clearBreaksRight: Bool

        // Explicit init with wave-20 defaults so every wave-19 call site
        // (and its pinned constructors) is byte-compatible — Swift's
        // memberwise init would otherwise demand the new members.
        init(floatsLeft: Bool, clearBreaksLeft: Bool,
             floatsRight: Bool = false, clearBreaksRight: Bool = false) {
            self.floatsLeft = floatsLeft
            self.clearBreaksLeft = clearBreaksLeft
            self.floatsRight = floatsRight
            self.clearBreaksRight = clearBreaksRight
        }
    }

    /// Derive [ChildFacts] from a child's IR property list.
    /// `hasChildren` guards the clear-break shape: a clear on a CONTENT
    /// box is real layout (out of the narrow contract), only the
    /// childless `<br>` marker is a pure row break.
    /// `rtl` maps the logical keywords per css-logical-1 §2.1: under
    /// direction:rtl inline-start→right and inline-end→left (wave-20 W3;
    /// defaults false so wave-19 call sites stay LTR-normalized verbatim).
    static func facts(from properties: [IRProperty], hasChildren: Bool,
                      rtl: Bool = false) -> ChildFacts {
        // Last-write-wins over the wire, same cascade FloatExtractor
        // applies on Compose (and the web engine's foldLast).
        var floatKw: String? = nil
        var clearKw: String? = nil
        for p in properties {
            // The IR carries SHOUTY keywords ("LEFT" / "INLINE_START").
            // ValueExtractors.normalize (uppercase + "-"→"_") mirrors
            // Compose FloatExtractor's `.uppercase().replace("-","_")`
            // byte-for-byte, so a kebab wire ("inline-start") reads the
            // same on both natives (skeptic cross-probe pin).
            if p.type == "Float" { floatKw = ValueExtractors.extractKeyword(p.data).map { ValueExtractors.normalize($0) } }
            if p.type == "Clear" { clearKw = ValueExtractors.extractKeyword(p.data).map { ValueExtractors.normalize($0) } }
        }
        // css-logical-1 §2.1 — the physical side each logical keyword
        // resolves to under the container's direction.
        let startIsLeft = !rtl
        // P1 — left-family floats: physical LEFT plus whichever logical
        // keyword maps to left under `rtl`.
        let floatsLeft = floatKw == "LEFT" ||
            (floatKw == "INLINE_START" && startIsLeft) ||
            (floatKw == "INLINE_END" && !startIsLeft)
        // P10 — right-family floats: the exact mirror.
        let floatsRight = floatKw == "RIGHT" ||
            (floatKw == "INLINE_END" && startIsLeft) ||
            (floatKw == "INLINE_START" && !startIsLeft)
        // §9.5.2 same-side clearance: BOTH clears either side, the
        // logical members resolve per `rtl`.
        let clearsLeft = clearKw == "BOTH" || clearKw == "LEFT" ||
            (clearKw == "INLINE_START" && startIsLeft) ||
            (clearKw == "INLINE_END" && !startIsLeft)
        let clearsRight = clearKw == "BOTH" || clearKw == "RIGHT" ||
            (clearKw == "INLINE_END" && startIsLeft) ||
            (clearKw == "INLINE_START" && !startIsLeft)
        // Unknown/absent float keywords fold to the CSS initial `none`
        // (invalid declaration → initial value), exactly like Compose's
        // FloatExtractor `else -> FloatValue.NONE` arm.
        let floating: Set<String> = ["LEFT", "RIGHT", "INLINE_START", "INLINE_END"]
        let floatNone = floatKw.map { !floating.contains($0) } ?? true
        // P2/P14 — the break marker must not itself float and must be
        // childless (the wire's `<br clear>` has neither).
        let marker = !hasChildren && floatNone
        return ChildFacts(
            floatsLeft: floatsLeft,
            clearBreaksLeft: marker && clearsLeft,
            floatsRight: floatsRight,
            clearBreaksRight: marker && clearsRight
        )
    }

    /// One segment of the child list, in sibling order: either a float
    /// RUN (≥2 consecutive same-side floats, rendered side-by-side by
    /// the adapter) or a SINGLE child that keeps the block-flow path.
    struct Segment: Equatable {
        // Child indices (into the original sibling list) in order.
        let indices: [Int]
        // True for a packable float run (always ≥2 indices then).
        let isRun: Bool
        // P2/P6: the sibling immediately after the run is a clear-break
        // marker — the adapter applies the line-box strut to this run.
        let strutted: Bool
        // P11: true when the run packs right-to-left from the containing
        // block's right edge (§9.5.1 rule 1 mirrored, wave-20 W3).
        let rightRun: Bool

        // Explicit init with a wave-20 default so wave-19 pinned Segment
        // constructions stay source- and value-compatible.
        init(indices: [Int], isRun: Bool, strutted: Bool, rightRun: Bool = false) {
            self.indices = indices
            self.isRun = isRun
            self.strutted = strutted
            self.rightRun = rightRun
        }
    }

    /// P1/P11 — split the sibling list into maximal float runs and
    /// singles. A run is ≥2 CONSECUTIVE same-side floating siblings;
    /// anything else (clear marker, opposite-side float, in-flow box)
    /// ends the streak and renders as a single. Lone floats stay singles
    /// too — today's path already renders one float correctly, and
    /// keeping it identical minimizes the gated surface (F4
    /// conservatism, both sides).
    static func segment(_ children: [ChildFacts]) -> [Segment] {
        // Output accumulator — segments in sibling order.
        var out: [Segment] = []
        // Current streak of consecutive same-side float indices.
        var streak: [Int] = []
        // The streak's side (true = right-family); meaningless when empty.
        var streakRight = false
        // Flush the open streak: a run when ≥2, singles otherwise.
        // `next` is the index AFTER the streak (or nil at the end) —
        // it decides the strut (P2/P14: a trailing same-side clear-break).
        func flush(next: Int?) {
            if streak.count >= 2 {
                // The strut fires only when the run is terminated by a
                // SAME-SIDE clear-break sibling (§9.5.2's same-side rule;
                // the `<br clear>` line box, P6).
                let strut = next.map {
                    streakRight ? children[$0].clearBreaksRight : children[$0].clearBreaksLeft
                } ?? false
                out.append(Segment(indices: streak, isRun: true, strutted: strut, rightRun: streakRight))
            } else {
                // 0 or 1 floats — each keeps the plain block path.
                for i in streak { out.append(Segment(indices: [i], isRun: false, strutted: false)) }
            }
            streak.removeAll()
        }
        // Single pass over the siblings, building same-side streaks.
        for (i, c) in children.enumerated() {
            if c.floatsLeft || c.floatsRight {
                // A side flip ends the current streak — left and right
                // runs never merge (§9.5.1 rule 1 vs its mirror).
                if !streak.isEmpty && streakRight != c.floatsRight { flush(next: i) }
                // Extend (or begin) the streak on this child's side.
                streakRight = c.floatsRight
                streak.append(i)
            } else {
                // Streak broken by this sibling — flush, then emit the
                // breaker itself as a single (clear markers render ~0-size).
                flush(next: i)
                out.append(Segment(indices: [i], isRun: false, strutted: false))
            }
        }
        // Trailing streak: no next sibling → never strutted.
        flush(next: nil)
        return out
    }

    /// Resolved geometry for one run: per-child origins + reported size.
    struct RunLayout: Equatable {
        // Per-child x origin (margin-box left edge), run-relative px.
        let x: [Double]
        // Per-child y origin (top of the child's float row), px.
        let y: [Double]
        // Run extent: the widest row's summed margin-box widths.
        let width: Double
        // Σ row heights, floored at `strutPx` (P6) when > 0.
        let height: Double
    }

    /// P3/P4/P5 — greedy §9.5.1 packing of one run.
    ///
    /// Cursor packing (rules 1-3): each float sits at the current x and
    /// advances by its margin-box width. Wrap (rule 7): when the float
    /// does not fit the remaining `availableWidth` AND it is not the
    /// first in its row, it starts a new row (a row's first float may
    /// overflow — rule 7's "as far left as possible" loose case).
    /// `availableWidth` is +∞ in the composed-WPT adapters (P4): the
    /// browser-ref laid these floats against the BODY (≥360px) while the
    /// captured IR wraps roots in synthetic 100px frames — packing at the
    /// artifact width would reproduce browser-at-100px geometry, not the
    /// ref. Finite widths stay exercised by the pinning suites.
    /// `strutPx` > 0 floors the reported height (P6) for strutted runs;
    /// pass 0.0 for un-strutted runs (the abspos shrink-to-fit case).
    static func layout(
        widths: [Double],
        heights: [Double],
        availableWidth: Double,
        strutPx: Double
    ) -> RunLayout {
        // Per-child row assignment + x origin from the greedy cursor.
        var rowOf = [Int](repeating: 0, count: widths.count)
        var xs = [Double](repeating: 0.0, count: widths.count)
        var x = 0.0
        var row = 0
        for (i, w) in widths.enumerated() {
            // Rule 7 wrap: only a NON-first float wraps; x > 0 encodes
            // "someone already sits in this row".
            if x > 0.0 && x + w > availableWidth { row += 1; x = 0.0 }
            xs[i] = x
            rowOf[i] = row
            // Rule 2: the next float packs against this one's right
            // margin edge (widths are margin-box widths).
            x += w
        }
        // P5 — row height = max child margin-box height in the row.
        var rowHeights = [Double](repeating: 0.0, count: row + 1)
        for i in widths.indices where heights[i] > rowHeights[rowOf[i]] {
            rowHeights[rowOf[i]] = heights[i]
        }
        // P5 — row tops are the running sum of previous row heights.
        // (Kotlin's `for (r in 1..row)` — empty when there is one row.)
        var rowTops = [Double](repeating: 0.0, count: row + 1)
        for r in stride(from: 1, through: row, by: 1) {
            rowTops[r] = rowTops[r - 1] + rowHeights[r - 1]
        }
        // Per-child y = its row's top edge (floats align to the row top —
        // §9.5.1 rule 6: no higher than preceding floats' tops).
        let ys = widths.indices.map { rowTops[rowOf[$0]] }
        // P5 — run width = the widest row's extent (Σ widths per row).
        var rowExtents = [Double](repeating: 0.0, count: row + 1)
        for i in widths.indices { rowExtents[rowOf[i]] += widths[i] }
        let width = rowExtents.max() ?? 0.0
        // P6 — strut floor on the total height for strutted runs.
        let height = max(rowTops[row] + rowHeights[row], strutPx)
        return RunLayout(x: xs, y: ys, width: width, height: height)
    }

    /// P12 — the RIGHT-run mirror of `layout` (wave-20 W3, CSS 2.1
    /// §9.5.1 rule 1 mirrored: a right float's RIGHT outer edge sits
    /// against the containing block's right edge, the next one packs to
    /// its LEFT). Implemented as a pure reflection of the greedy left
    /// plan against the run extent — row assignment, wrap rule, row
    /// heights and the strut floor are shared byte-for-byte, so every
    /// left-side pin transfers: x'[i] = width − x[i] − w[i]. Each row
    /// right-aligns at the run's right edge (rule 1 per row); the adapter
    /// then anchors that edge at the containing block's right edge (P13).
    static func layoutEnd(
        widths: [Double],
        heights: [Double],
        availableWidth: Double,
        strutPx: Double
    ) -> RunLayout {
        // The shared greedy plan — identical rows/heights/extent (P12).
        let base = layout(widths: widths, heights: heights,
                          availableWidth: availableWidth, strutPx: strutPx)
        // Mirror each x against the run extent: first float flush right,
        // successors extend leftward (§9.5.1 rules 1-3 mirrored).
        let xs = widths.indices.map { base.width - base.x[$0] - widths[$0] }
        return RunLayout(x: xs, y: base.y, width: base.width, height: base.height)
    }
}
