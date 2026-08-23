//
//  LineClampCensusRuns.swift
//  StyleEngine/scrolling — Wave 46 (lane Y1): the document-order walk
//  that turns a clamp root's inline content into LineClampCensus runs.
//  Split from LineClampCensus.swift (the verdict) under the ≤200-line
//  rule; same enum, same folder, SwiftPM picks the file up automatically.
//

import CoreGraphics
import Foundation

extension LineClampCensus {

    /// The container's inline content as census runs, in document order.
    ///
    /// `meta.runs` is AUTHORITATIVE when present (schema/spec/03 §4.1:
    /// the renderer paints its entries in order and nothing else). The
    /// order reproduced here is the one InlineRunPlan.resolve paints for
    /// the stacked path — children in index order, the text runs that
    /// precede a child's reference slotted before it, the trailing text
    /// after the LAST referenced child — and a reference is the child's
    /// AUTHORING KEY (its `name` on the converter wire, its `id` in the
    /// extractor-direct pipeline; name first, exactly like the plan). A
    /// dangling key (an out-of-flow child: abspos/fixed — no line box in
    /// this container, css-position-3 §2.1) is skipped. Without runs the
    /// renderer paints the leading text, then the in-flow children.
    static func runs(component: IRComponent,
                     inFlowChildren: [IRComponent],
                     root: LineClampRootMetrics) -> [LineClampRun] {
        // The root's own line box — every root text run lays out on it.
        let rootRun = { (text: String) -> LineClampRun in
            LineClampRun(lineBoxPx: root.lineBoxPx,
                         exactLines: exactLineCount(text, whiteSpace: root.whiteSpace))
        }
        guard let runs = component.meta?.runs, !runs.isEmpty else {
            // No runs: leading text (when any) then the in-flow children.
            var out: [LineClampRun] = []
            if let t = component.text, !t.isEmpty { out.append(rootRun(t)) }
            out.append(contentsOf: inFlowChildren.map { childRun($0, root: root) })
            return out
        }
        // Authoring-key lookup: name first, id second (InlineRunPlan rule).
        var index: [String: Int] = [:]
        for (i, child) in inFlowChildren.enumerated() {
            if !child.name.isEmpty, index[child.name] == nil { index[child.name] = i }
        }
        for (i, child) in inFlowChildren.enumerated() {
            if !child.id.isEmpty, index[child.id] == nil { index[child.id] = i }
        }
        // Texts slotted before each referenced child, and after the last.
        var before: [Int: [String]] = [:]
        var pending: [String] = []
        var lastReferenced: Int? = nil
        for run in runs {
            if let t = run.text {
                // Empty runs contribute nothing; whitespace-only runs are
                // real inter-run spaces and stay (they prove nothing but
                // cost no line box on their own — see exactLineCount).
                if !t.isEmpty { pending.append(t) }
                continue
            }
            // Dangling / duplicate references are skipped, as the plan does.
            guard let key = run.child, let i = index[key], before[i] == nil,
                  lastReferenced.map({ i > $0 }) ?? true else { continue }
            before[i] = pending; pending = []
            lastReferenced = i
        }
        // Emit in paint order.
        var out: [LineClampRun] = []
        for (i, child) in inFlowChildren.enumerated() {
            for t in before[i] ?? [] { out.append(rootRun(t)) }
            out.append(childRun(child, root: root))
            // The trailing text paints right after the last referenced child.
            if i == lastReferenced { for t in pending { out.append(rootRun(t)) } }
        }
        // No child was referenced at all: the texts are the whole content.
        if lastReferenced == nil { out = pending.map(rootRun) + out }
        return out
    }

    /// One in-flow child as a census run. Inherited text properties
    /// (font-size, line-height, white-space — all `inherited: yes`) come
    /// from the root unless the child declares its own.
    ///   • a scroll container (used overflow ≠ visible) or a box with an
    ///     explicit height is MONOLITHIC: its full height, no line boxes,
    ///     provable only when its lines and bands are;
    ///   • a leaf child with text yields its line boxes plus its top band;
    ///   • a child that nests its own children (the count would need its
    ///     subtree's runs) is unprovable.
    static func childRun(_ child: IRComponent, root: LineClampRootMetrics) -> LineClampRun {
        // `display: none` generates no box at all (CSS 2.1 §9.2.4) — the
        // renderer emits EmptyView for it, so it costs no height here.
        if LineClampChildMetrics.isDisplayNone(child.properties) {
            return LineClampRun(lineBoxPx: root.lineBoxPx, exactLines: 0, leadingBandPx: 0, monolithicPx: 0)
        }
        // Typography of the child: its own declaration, else the root's.
        let fontSize = LineClampChildMetrics.fontSizePx(child.properties, root: root)
        let lineBox = LineClampChildMetrics.lineBoxPx(child.properties, fontSizePx: fontSize, root: root)
        let ws = WhiteSpaceExtractor.extract(from: child.properties)?.keyword ?? root.whiteSpace
        let bands = LineClampChildMetrics.verticalBands(child.properties)
        let box = lineBox ?? root.lineBoxPx
        // The child's own line boxes, when a leaf with text.
        let lines: Int? = (child.children?.isEmpty ?? true) && lineBox != nil
            ? child.text.flatMap { exactLineCount($0, whiteSpace: ws) } : nil
        // Explicit height: the box is exactly that tall, whatever its text.
        if let h = LineClampChildMetrics.explicitHeightPx(child.properties) {
            return LineClampRun(lineBoxPx: box, exactLines: 0, leadingBandPx: bands?.top,
                                monolithicPx: bands.map { h + $0.top + $0.bottom })
        }
        // Scroll container: whole box = its lines + both bands, if provable.
        // Unprovable → an unknown run whose band is ALSO nil: its lines are
        // not this container's line boxes, so it must never be judged a
        // "uniform" run by the fallback (a uniform cap would cut inside it).
        if LineClampChildMetrics.isScrollContainer(child.properties) {
            guard let n = lines, let b = bands else {
                return LineClampRun(lineBoxPx: box, exactLines: nil, leadingBandPx: nil)
            }
            return LineClampRun(lineBoxPx: box, exactLines: 0, leadingBandPx: b.top,
                                monolithicPx: CGFloat(n) * box + b.top + b.bottom)
        }
        // Plain block / inline child: its line boxes under its top band.
        return LineClampRun(lineBoxPx: box, exactLines: lines, leadingBandPx: bands?.top)
    }
}
