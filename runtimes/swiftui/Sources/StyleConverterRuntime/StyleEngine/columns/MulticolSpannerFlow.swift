//
//  MulticolSpannerFlow.swift
//  StyleEngine/columns — wave-21 lane MULTICOL.
//
//  Pure css-multicol-1 §6 spanner-flow geometry: the iOS TWIN of the
//  Android runtime's MulticolSpannerFlow.kt (runtimes/compose .../columns/).
//  Both natives sequence spanners, balance pre/post-spanner segments and
//  anchor abspos static positions byte-identically; the shared SP/E/F/X
//  pin table lives in MulticolSpannerFlowTests.swift and the Android
//  MulticolSpannerFlowTest.kt with the SAME rows.
//
//  The model (css-multicol-1 §6.2–§6.3, §7.1): a `column-span: all` child
//  interrupts the multicol flow; content BEFORE it is distributed into
//  columns that are BALANCED regardless of `column-fill` (§6.3), the
//  spanner spans the full content box, and the flow RESUMES in a fresh
//  row of column boxes below it. Out-of-flow (abspos/fixed) children take
//  no space but keep a STATIC POSITION at the flow point where they would
//  have been (css-position-3 §3.1) — the wave-21 diagnosis: an abspos
//  following a spanner anchors in the post-spanner flow of COLUMN 1, not
//  wherever the greedy heuristic drops it. Each spanner-free segment of
//  total flow height T gets the balanced column block-size H = ceil(T/N);
//  children fill SEQUENTIALLY — flow offset R starts column floor(R/H) at
//  block offset R mod H. Whole-child placement only: a child crossing a
//  balanced boundary either fragments via the wave-10 clone row (the
//  sole-flow-child case) or renders unfragmented from its start column
//  (anyFlowChildCrossesBoundary tells callers to log the bail — repo
//  no-silent-fallthrough rule).
//

// Foundation for ceil-free integer math (heights ride Double like the
// sibling MulticolDistribution so SwiftUI geometry plugs in directly;
// the shared pins use integral values for bit-identical answers).
import Foundation

// Namespacing enum — all members static + pure (MulticolMath pattern).
enum MulticolSpannerFlow {

    /// How a multicol child participates in the spanner flow.
    enum Role: Equatable {
        /// In-flow, span:none — distributes into column boxes (§2).
        case flow
        /// In-flow, column-span:all — interrupts the flow full-width (§6.2).
        case spanner
        /// Out-of-flow (abspos/fixed) — no space, static anchor only (css-position-3 §3.1).
        case `static`
    }

    /// One child as the planner sees it: measured block-size + role.
    struct Child: Equatable {
        /// Measured (or statically resolved) block-size in px; static heights are ignored.
        let heightPx: Double
        /// The child's flow participation — see Role.
        let role: Role
        /// Whether the child DECLARES its block-size (Height/BlockSize in
        /// the IR). The sole-flow balance gate requires it: a content-sized
        /// wrapper (e.g. abspos-containing-block-outside-spanner's relative
        /// wrapper holding a nested spanner) must keep the legacy render —
        /// on iOS the same gate is realized by ColumnsApplier.fragmentPlan's
        /// explicit-C resolution; the flag keeps the twins byte-parallel.
        var explicitBlockSize: Bool = true
    }

    /// Role plus the declared-block-size flag — what the Android measure
    /// pass consumes per measurable (kept in the twin for byte-parallel
    /// modules; iOS integrations use rolesFor + the fragment plan's own
    /// explicit-C resolution instead).
    struct ChildSpec: Equatable {
        /// The child's flow participation — see Role.
        let role: Role
        /// True iff the IR declares Height or BlockSize on the child.
        let explicitBlockSize: Bool
    }

    /// One child's planned position. x derives at the layout site as
    /// columnIndex · (usedColumnWidth + gap) — the MulticolDistribution
    /// convention (spanners render at x=0 full width; their columnIndex
    /// is pinned 0).
    struct Slot: Equatable {
        /// The child's Role, echoed so placement loops need no zip.
        let role: Role
        /// 0-based used-column index (0 for spanners by convention).
        let columnIndex: Int
        /// Block offset from the container's content-box top, in px.
        let yPx: Double
    }

    /// The full spanner-flow answer for one container.
    struct Plan: Equatable {
        /// One Slot per child, index-aligned with the input.
        let slots: [Slot]
        /// Container auto block-size: spanner heights + every segment's balanced H (§6.3).
        let containerBlockSizePx: Double
        /// The balanced column block-size H of the segment holding the
        /// container's ONLY flow child — the fragmentainer the wave-10
        /// clone-row pass slices with. Nil when the flow-child count is
        /// not exactly 1 (multi-child segments place whole).
        let soleFlowColumnBlockSizePx: Double?
    }

    /// Role classification for a multicol container's rendered content, in
    /// the exact order the platform layout receives its subviews: leading
    /// text (when present) renders FIRST as an anonymous flow box
    /// (contentOrPlaceholder's Bug-1 branch), then the children in order.
    /// Precedence: out-of-flow beats span — css-position-3 §2.1 takes the
    /// box out of flow entirely, so `column-span` on an abspos is moot.
    static func rolesFor(children: [IRComponent], leadingText: Bool = false) -> [Role] {
        // The anonymous leading text box participates as plain flow content.
        let head: [Role] = leadingText ? [.flow] : []
        // Classify each child from its own declared IR properties.
        return head + children.map { child in
            // Wire shapes: Position → "ABSOLUTE"/"FIXED"/… keyword;
            // ColumnSpan → "ALL"/"NONE" keyword (converter serializers).
            let position = child.properties.first { $0.type == "Position" }
                .flatMap { ValueExtractors.extractKeyword($0.data)?.uppercased() }
            let span = child.properties.first { $0.type == "ColumnSpan" }
                .flatMap { ValueExtractors.extractKeyword($0.data)?.uppercased() }
            // Out-of-flow first — see the precedence note above.
            if position == "ABSOLUTE" || position == "FIXED" { return .static }
            // §6.2: only `all` spans; `none` (and unknown) stays in flow.
            if span == "ALL" { return .spanner }
            return .flow
        }
    }

    /// In-flow non-spanner child count — the routing gate both natives share.
    static func flowCount(_ roles: [Role]) -> Int {
        roles.filter { $0 == .flow }.count
    }

    /// rolesFor plus the per-child declared-block-size flag (ChildSpec) —
    /// the Android render hook's one-stop classification, mirrored here
    /// for twin parity. The anonymous leading-text box is content-sized
    /// by definition (explicit = false).
    static func specsFor(children: [IRComponent], leadingText: Bool = false) -> [ChildSpec] {
        // Role classification through the single shared classifier.
        let roles = rolesFor(children: children, leadingText: leadingText)
        // The leading text spec (when present) heads the list.
        let head: [ChildSpec] = leadingText ? [ChildSpec(role: .flow, explicitBlockSize: false)] : []
        // Pair each child's role with its Height/BlockSize declaration —
        // presence is the signal (the measure pass supplies the value).
        return head + children.enumerated().map { index, child in
            ChildSpec(
                role: roles[index + head.count],
                explicitBlockSize: child.properties.contains {
                    $0.type == "Height" || $0.type == "BlockSize"
                })
        }
    }

    /// Whether the spanner-flow plan replaces the legacy greedy/stack
    /// layout for this container. Deliberately NARROW (dark-stage 327
    /// protection — callers additionally gate on WPT capture mode):
    ///  - any spanner present → yes (§6.2 sequencing is unconditionally
    ///    wrong under the greedy heuristic);
    ///  - else only the sole-flow-child auto-height balance case (§7.1:
    ///    an unconstrained multicol container ALWAYS balances), and only
    ///    when the child DECLARES its block-size (content-sized wrappers —
    ///    e.g. one hiding a nested spanner — keep the legacy render) and
    ///    balancing actually shortens the column (h > ceil(h/N)).
    /// Multi-child spanner-less containers keep the greedy heuristic
    /// (documented Android-parity approximation, D-table).
    static func engages(children: [Child], columnCount: Int) -> Bool {
        // §6.2 — a spanner always takes the plan.
        if children.contains(where: { $0.role == .spanner }) { return true }
        // Sole-flow-child balance: exactly one child, in flow, with a
        // DECLARED block-size (the fragment plan's explicit-C twin gate).
        guard children.count == 1, children[0].role == .flow else { return false }
        guard children[0].explicitBlockSize else { return false }
        // Balancing needs ≥ 2 columns to differ from the identity render.
        let n = max(1, columnCount)
        guard n >= 2 else { return false }
        // ceil(h / n) < h ⇔ fragmenting across columns changes geometry.
        let h = max(0, children[0].heightPx)
        return h > (h / Double(n)).rounded(.up)
    }

    /// Whether the sole-flow-child clone-row pass (multicolFragmentRow)
    /// may fragment this container's flow child: exactly one flow child,
    /// NO static anchors, and every spanner measured at 0 height (a
    /// painted spanner would need its own full-width slot between the
    /// clone row and the flow — the mixed shape places whole instead).
    static func soleFlowFragmentReplay(children: [Child]) -> Bool {
        // The clone row slices exactly one continuous child paint.
        children.filter { $0.role == .flow }.count == 1
            // Static anchors ride the overlay on iOS, but the twin gate
            // stays byte-parallel with Android (where their ink would
            // replay into every column).
            && !children.contains(where: { $0.role == .static })
            // Only inkless (0-height) spanners coexist with the replay.
            && children.filter { $0.role == .spanner }.allSatisfy { $0.heightPx <= 0 }
    }

    /// True when some flow child crosses a balanced column boundary (or
    /// overflows the last column) — the whole-child placement path is
    /// then an approximation and callers must log the bail once.
    static func anyFlowChildCrossesBoundary(children: [Child], columnCount: Int) -> Bool {
        let n = max(1, columnCount)
        var crosses = false
        // Walk the same segments as plan(), tracking each child's R.
        forEachSegment(children) { segment in
            // Balanced H for this segment: ceil(T / N), 0 for empty runs.
            let t = segment.filter { $0.role == .flow }.map { max(0, $0.heightPx) }.reduce(0, +)
            let h = t == 0 ? 0 : (t / Double(n)).rounded(.up)
            var r = 0.0
            for c in segment where c.role == .flow {
                let height = max(0, c.heightPx)
                // Crossing ⇔ the child's band [R, R+h) straddles a k·H line.
                if h > 0 && height > 0 && r.truncatingRemainder(dividingBy: h) + height > h {
                    crosses = true
                }
                r += height
            }
        }
        return crosses
    }

    /// Shared segment walk: maximal spanner-free runs, in child order.
    private static func forEachSegment(_ children: [Child], _ body: ([Child]) -> Void) {
        var i = 0
        while i < children.count {
            // Skip the spanner separators themselves.
            if children[i].role == .spanner { i += 1; continue }
            var j = i
            // Extend the run until the next spanner (or the end).
            while j < children.count && children[j].role != .spanner { j += 1 }
            body(Array(children[i..<j]))
            i = j
        }
    }

    /// The plan itself — see the file header for the model. SP-table pinned.
    static func plan(children: [Child], columnCount: Int) -> Plan {
        // §3.2 used counts are ≥ 1; floor defensively for direct callers.
        let n = max(1, columnCount)
        var slots: [Slot] = []
        slots.reserveCapacity(children.count)
        // Running container block offset (spanner tops / segment starts).
        var y = 0.0
        // Sole-flow bookkeeping for the replay fragmentainer.
        let soleFlow = children.filter { $0.role == .flow }.count == 1
        var soleFlowH: Double? = nil
        var i = 0
        while i < children.count {
            let c = children[i]
            if c.role == .spanner {
                // §6.2: the spanner spans all columns at the current flow
                // bottom; the flow resumes below it.
                slots.append(Slot(role: .spanner, columnIndex: 0, yPx: y))
                y += max(0, c.heightPx)
                i += 1
                continue
            }
            // Segment [i, j): the maximal spanner-free run starting here.
            var j = i
            var t = 0.0
            while j < children.count && children[j].role != .spanner {
                // Only in-flow children consume column space (§2).
                if children[j].role == .flow { t += max(0, children[j].heightPx) }
                j += 1
            }
            // §6.3/§7.1 balanced column block-size: ceil(T / N); an empty
            // segment (all-static, or nothing) contributes no height.
            let h = t == 0 ? 0 : (t / Double(n)).rounded(.up)
            // Sequential fill: R is the running flow offset in the segment.
            var r = 0.0
            for k in i..<j {
                // Column floor(R/H), capped at the last column (overflowing
                // content stays in column N-1, column-fill:auto overflow).
                let col = h > 0 ? min(Int((r / h).rounded(.down)), n - 1) : 0
                // Block offset inside that column: R − col·H, from the
                // segment's container-level start y.
                slots.append(Slot(role: children[k].role, columnIndex: col,
                                  yPx: y + r - Double(col) * h))
                if children[k].role == .flow {
                    // The sole flow child's segment H is the replay's
                    // fragmentainer block-size (wave-10 machinery).
                    if soleFlow { soleFlowH = h }
                    // Static children never advance R (no space taken).
                    r += max(0, children[k].heightPx)
                }
            }
            // The whole segment occupies exactly H of container block-size.
            y += h
            i = j
        }
        return Plan(slots: slots, containerBlockSizePx: y, soleFlowColumnBlockSizePx: soleFlowH)
    }
}
