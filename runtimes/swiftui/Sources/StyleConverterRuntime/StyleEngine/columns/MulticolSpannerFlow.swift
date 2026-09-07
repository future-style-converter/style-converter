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
//  The model (css-multicol-1 §6.1, §7.1): a `column-span: all` child
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
        /// In-flow with `break-after: column` (wave-42 lane W4) — flows
        /// like `flow` but FORCES a column break after itself: css-break-3
        /// §4.1 applied to the multicol fragmentation context, so the next
        /// flow content opens a fresh column chunk. Kept as a role (not a
        /// side flag) because the renderer call sites pass bare `[Role]`
        /// through to the platform layouts and must stay untouched (lane
        /// ownership: columns/ only).
        case flowBreakAfter
        /// In-flow, column-span:all — interrupts the flow full-width (§6.2).
        case spanner
        /// Out-of-flow (abspos/fixed) — no space, static anchor only (css-position-3 §3.1).
        case `static`

        /// Both in-flow non-spanner roles (wave-42) — every consumer that
        /// asks about flow PARTICIPATION (space consumption, sole-flow
        /// counting) treats a forced-break child like flow; only plan()'s
        /// chunk walk cares about the break itself. Twin of the Kotlin
        /// `Role.isFlow` extension.
        var isFlow: Bool { self == .flow || self == .flowBreakAfter }
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
        /// True iff the child's SUBTREE declares `float: left/right`
        /// anywhere (wave-42 lane W4). The multi-child run fragmenter must
        /// bail on such children: the natives still lay floats out as
        /// stacked in-flow boxes (the float lane owns css-break-3 §2.1 parallel-flow float
        /// fragmentation + CSS 2.1 §9.5.2 clearance), so slicing that
        /// stack across columns would replicate a wrong layout per column.
        var floatedContent: Bool = false
        /// True iff a forced `break-before/after: column` sits somewhere
        /// the container-level Role list cannot express (wave-42 lane W4):
        /// the child's OWN `break-before`, or EITHER break side on any
        /// descendant. (Its own `break-after: column` is not here — that
        /// becomes `.flowBreakAfter`, which plan() honours.)
        ///
        /// The run fragmenter must bail on it: WPT css-break
        /// block-in-inline-013/014 are `columns:4; column-fill:auto;
        /// height:400px` with four `<span>` children each wrapping a 100px
        /// `break-before/after: column` div, so the multicol's direct
        /// children look like plain flow while Chromium's
        /// one-green-box-per-column reference comes ENTIRELY from honoring
        /// the hidden breaks. Twin of the Kotlin
        /// ChildSpec.forcedBreakContent.
        var forcedBreakContent: Bool = false
        /// True iff the child box has NOTHING inside it — no IR children
        /// and no text (wave-42 lane W4). Such a box has neither a class-A
        /// breakpoint (between block-level siblings) nor a class-B one
        /// (between line boxes) inside it (css-break-3 §4.1), so it is
        /// MONOLITHIC: a column boundary may not pass through it, and the
        /// run fragmenter pushes it whole into the next column
        /// (MulticolRunFragment.runPlan's `monolithic` argument).
        var monolithicContent: Bool = false
        /// The wave-44 lane-U8 FLOAT-STRIP facts (leading out-of-flow
        /// floats, §9.5.2 clear sides, trailing paint ink) — or nil when
        /// any wire on this child is outside that lane's proven scope
        /// (MulticolFloatStrip.factsFor's strict-bail contract). A single
        /// nil fact disables the whole container's strip. Twin of the
        /// Kotlin ChildSpec.floatStrip; consumed on BOTH natives — see
        /// MulticolFloatStrip.swift's CONSUMPTION STATUS banner (iOS:
        /// the X3 engagement + the wave-48 slice-replay measure half).
        var floatStrip: MulticolFloatStrip.ChildFacts? = nil
    }

    /// One child's planned position. x derives at the layout site as
    /// columnIndex · (usedColumnWidth + gap) — the MulticolDistribution
    /// convention (spanners render at x=0 full width; their columnIndex
    /// is pinned 0).
    struct Slot: Equatable {
        /// The child's Role, echoed so placement loops need no zip.
        let role: Role
        /// 0-based used-column index (0 for spanners by convention). May
        /// EXCEED N-1 for a forced-break chunk that ran out of columns —
        /// a css-multicol-1 §8.1 OVERFLOW column, painted past the
        /// container's inline end exactly where i·(W+G) lands it.
        let columnIndex: Int
        /// Block offset from the container's content-box top, in px.
        let yPx: Double
        /// True when css-overflow-4 §5.3 `continue: discard` dropped this
        /// child (content from the first overflow column on, and everything
        /// after it in flow order). Placement loops must skip — or park
        /// offscreen (SwiftUI Layout places every subview) — such slots.
        var discarded: Bool = false
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
            // Wave-42 lane W4: `break-after: column` forces a column break
            // after this box (css-break-3 §4.1). Only the COLUMN keyword is
            // classified — page/region keywords have no multicol meaning,
            // and `avoid*` values are break AVOIDANCE, not force.
            let breakAfter = child.properties.first { $0.type == "BreakAfter" }
                .flatMap { ValueExtractors.extractKeyword($0.data)?.uppercased() }
            // Out-of-flow first — see the precedence note above.
            if position == "ABSOLUTE" || position == "FIXED" { return .static }
            // §6.2: only `all` spans; `none` (and unknown) stays in flow.
            if span == "ALL" { return .spanner }
            // Forced column break AFTER an in-flow box (wave-42).
            if breakAfter == "COLUMN" { return .flowBreakAfter }
            return .flow
        }
    }

    /// In-flow non-spanner child count — the routing gate both natives share.
    /// Wave-42: a forced-break child is still IN FLOW (it consumes column
    /// space like any block), so it counts here.
    static func flowCount(_ roles: [Role]) -> Int {
        roles.filter { $0 == .flow || $0 == .flowBreakAfter }.count
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
        // Wave-44 lane U8: one float-presence read per child (shared by
        // the wave-42 flag below AND the strip-facts gate — the strip is
        // only relevant when SOME child floats, and skipping the facts
        // walk for float-less containers keeps their specs byte-identical
        // to wave-42, pinned by the R1 row on both natives).
        let floated = children.map { hasFloatedDescendant($0) }
        let anyFloated = floated.contains(true)
        // Pair each child's role with its Height/BlockSize declaration —
        // presence is the signal (the measure pass supplies the value).
        return head + children.enumerated().map { index, child in
            ChildSpec(
                role: roles[index + head.count],
                explicitBlockSize: child.properties.contains {
                    $0.type == "Height" || $0.type == "BlockSize"
                },
                // Wave-42: the run fragmenter's float bail rides this flag
                // (see ChildSpec.floatedContent for why floats disqualify).
                floatedContent: floated[index],
                // Wave-42: the run fragmenter's FORCED-BREAK bail — a break
                // the container-level role list cannot see (the child's own
                // break-before, or either side anywhere below it).
                forcedBreakContent: hasHiddenForcedColumnBreak(child),
                // Wave-42: empty box ⇒ no class-A/B breakpoint inside it ⇒
                // MONOLITHIC. Text counts as content because line boxes ARE
                // class-B break points, and generated ::before/::after
                // content is content too — the same three inputs the Kotlin
                // twin reads (`_text` there is `text` here).
                monolithicContent: isLeafBox(child),
                // Wave-44 lane U8: the float-strip facts (or the strict
                // nil bail) — computed only for containers with a float
                // somewhere (the `anyFloated` gate above).
                floatStrip: anyFloated ? MulticolFloatStrip.factsFor(child) : nil)
        }
    }

    /// True when `child` has NOTHING inside it — no IR children, no text
    /// (line boxes ARE class-B break points) and no generated
    /// ::before/::after content. The wave-42 `monolithicContent` predicate,
    /// named so the wave-46 clone branch (ColumnsApplier.fragmentPlan's
    /// `childIsLeaf`) and specsFor share ONE definition — the same three
    /// inputs the Kotlin twin reads (`_text` there is `text` here).
    static func isLeafBox(_ child: IRComponent) -> Bool {
        (child.children?.isEmpty ?? true)
            && (child.text?.isEmpty ?? true)
            && child.pseudos == nil
    }

    /// True when a forced COLUMN break lives in `component`'s box tree in a
    /// place rolesFor cannot express: the child's own `break-before:
    /// column` (css-break-3 §4.1 — the break ends the PRECEDING sibling's
    /// column, which an after-only role list never encodes), or either
    /// break side on any descendant. Twin of the Kotlin
    /// hasHiddenForcedColumnBreak.
    private static func hasHiddenForcedColumnBreak(_ component: IRComponent) -> Bool {
        // The child's OWN break-before (its own break-after is the role).
        if forcedColumnBreak(component, "BreakBefore") { return true }
        // …then the whole subtree, both directions.
        return component.children?.contains(where: { forcedColumnBreakDeep($0) }) == true
    }

    /// hasHiddenForcedColumnBreak's subtree half — both break sides count.
    private static func forcedColumnBreakDeep(_ component: IRComponent) -> Bool {
        if forcedColumnBreak(component, "BreakBefore") { return true }
        if forcedColumnBreak(component, "BreakAfter") { return true }
        return component.children?.contains(where: { forcedColumnBreakDeep($0) }) == true
    }

    /// Whether `component` declares `property` (BreakBefore/BreakAfter)
    /// with the `column` keyword — the only value that FORCES a multicol
    /// break (css-break-3 §4.1); `page`/`region` target other fragmentation
    /// contexts and `avoid*` is avoidance, not a forced break.
    private static func forcedColumnBreak(_ component: IRComponent,
                                          _ property: String) -> Bool {
        component.properties.first { $0.type == property }
            .flatMap { ValueExtractors.extractKeyword($0.data)?.uppercased() } == "COLUMN"
    }

    /// True when `component` or ANY descendant declares `float: left/right`
    /// (the IR `Float` keyword — the converter's FloatPropertyParser).
    /// `none` (and unknown keywords) do not count: only an actually floated
    /// box triggers the run fragmenter's float bail. Twin of the Kotlin
    /// hasFloatedDescendant.
    private static func hasFloatedDescendant(_ component: IRComponent) -> Bool {
        // The component's own declaration first (cheap, no recursion)…
        if component.properties.contains(where: { p in
            // Only the Float wire with an actually-floated keyword counts.
            guard p.type == "Float",
                  let kw = ValueExtractors.extractKeyword(p.data)?.uppercased()
            else { return false }
            return kw == "LEFT" || kw == "RIGHT"
        }) { return true }
        // …then the subtree — a float any depth down still means the
        // stacked-not-floated layout defect lives inside this child.
        return component.children?.contains(where: { hasFloatedDescendant($0) }) == true
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
        // Wave-42: a forced column break always takes the plan too — the
        // greedy min-height heuristic cannot honor css-break-3 §4.1. For
        // the ≤N-chunks / one-child-per-chunk shapes the corpus's GREEN
        // cells exercise, the chunk walk provably reproduces the greedy
        // assignment (pinned BRK4), so engaging is render-neutral there.
        if children.contains(where: { $0.role == .flowBreakAfter }) { return true }
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
        // The clone row slices exactly one continuous child paint (a
        // forced-break sole child counts — its chunk is its whole extent).
        children.filter { $0.role.isFlow }.count == 1
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
            // Wave-42: a segment with FORCED breaks places one whole chunk
            // per column (H = the tallest chunk), so no flow child can
            // straddle a column boundary by construction — skip it.
            if segment.contains(where: { $0.role == .flowBreakAfter }) { return }
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
    ///
    /// Wave-42 lane W4 additions (byte-twin of the Kotlin plan):
    ///  - a segment containing `.flowBreakAfter` children splits into
    ///    CHUNKS at the forced breaks (css-break-3 §4.1); chunk j owns
    ///    column j whole, the segment's used column block-size is the
    ///    TALLEST chunk (css-multicol-1 §7.1 — forced breaks become the
    ///    only break opportunities), and chunks past column N-1 land in
    ///    §8.2 OVERFLOW columns (columnIndex ≥ N);
    ///  - `discardOverflow` = the container declared `continue: discard`
    ///    (css-overflow-4 §5.3): content from the FIRST overflow column on —
    ///    everything after it in flow order, spanners included — is marked
    ///    `Slot.discarded` and contributes no container block-size. The
    ///    default false keeps every pre-wave-42 caller (and the SP pin
    ///    table) byte-identical.
    static func plan(children: [Child], columnCount: Int,
                     discardOverflow: Bool = false) -> Plan {
        // §3.2 used counts are ≥ 1; floor defensively for direct callers.
        let n = max(1, columnCount)
        var slots: [Slot] = []
        slots.reserveCapacity(children.count)
        // Running container block offset (spanner tops / segment starts).
        var y = 0.0
        // Sole-flow bookkeeping for the replay fragmentainer.
        let soleFlow = children.filter { $0.role.isFlow }.count == 1
        var soleFlowH: Double? = nil
        // css-overflow-4 §5.3 latch: once discard triggers, EVERYTHING after
        // is dropped — the flag never resets within one container.
        var discarding = false
        var i = 0
        while i < children.count {
            let c = children[i]
            // Post-discard tail: emit an index-aligned discarded slot (the
            // y is the frozen flow bottom — placement parks it offscreen).
            if discarding {
                slots.append(Slot(role: c.role, columnIndex: 0, yPx: y, discarded: true))
                i += 1
                continue
            }
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
                if children[j].role.isFlow { t += max(0, children[j].heightPx) }
                j += 1
            }
            if children[i..<j].contains(where: { $0.role == .flowBreakAfter }) {
                // ── Wave-42 CHUNK walk (forced breaks present) ─────────
                // col = the chunk's column; r = flow offset INSIDE the
                // current chunk; h = tallest RETAINED (col < N) chunk.
                var col = 0
                var r = 0.0
                var h = 0.0
                for k in i..<j {
                    let ck = children[k]
                    // css-overflow-4 §5.3: the first overflow column starts
                    // the discard — from here on everything drops.
                    if discardOverflow && col >= n { discarding = true }
                    if discarding {
                        slots.append(Slot(role: ck.role, columnIndex: 0, yPx: y, discarded: true))
                        continue
                    }
                    // The chunk owns its column whole: block offset = the
                    // running flow offset within the chunk.
                    slots.append(Slot(role: ck.role, columnIndex: col, yPx: y + r))
                    if ck.role.isFlow { r += max(0, ck.heightPx) }
                    if ck.role == .flowBreakAfter {
                        // §7.1 forced-break balancing: only chunks that got
                        // a real column grow the container's block-size —
                        // §8.2 overflow columns never do.
                        if col < n { h = max(h, r) }
                        // The forced break: next content opens a new chunk.
                        col += 1
                        r = 0
                    }
                }
                // The trailing (breakless) chunk, when it kept a column.
                if !discarding && col < n { h = max(h, r) }
                // A sole flow child's fragmentainer is its segment H (the
                // replay gate needs C > H, which a whole chunk never is).
                if soleFlow && children[i..<j].contains(where: { $0.role.isFlow }) { soleFlowH = h }
                // The segment occupies the tallest retained chunk.
                y += h
                i = j
                continue
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
