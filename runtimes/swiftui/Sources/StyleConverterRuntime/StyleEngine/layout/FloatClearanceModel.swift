//
//  FloatClearanceModel.swift
//  StyleEngine/layout — wave-42 lane W5.
//
//  The IR→box projection half of the CSS 2.2 §9.5.2 clearance emulation
//  (the pure math lives in FloatClearance.swift; the SwiftUI adapters in
//  ClearanceZeroFlow.swift). Byte-parallel twin of Compose
//  layout/FloatClearanceModel.kt — same bail set, same field names, so
//  the two natives' pin tables diff line-for-line.
//
//  The projection is deliberately STRICT: any subtree box whose geometry
//  this lane cannot prove from plain-px wire values makes the WHOLE
//  scope resolve to nil (identity — the frozen pre-wave-42 rendering).
//  That is the honesty contract: the §9.5.2 plan only fires when every
//  simulated position is exact, never as a guess.
//

// Foundation for Double math only — no SwiftUI in the pure half.
import Foundation

/// One projected box of a clearance scope: the §9.5.2-relevant facts of
/// an IRComponent with every length already reduced to plain CSS px.
/// Built ONLY by `FloatClearanceModel.project`; a failed projection of
/// ANY subtree box aborts the whole scope (see file banner).
final class ClearanceNode {
    /// The projected component — id keys the plan, children drive walks.
    let comp: IRComponent
    /// Tree link for the §8.3.1 ascent (nil only for the scope root).
    weak var parent: ClearanceNode?
    /// Physical float side, LTR-normalized: "LEFT"/"RIGHT"/nil (the
    /// Direction bail pins the scope LTR, so inline-start→left per
    /// css-logical-1 §2.1 — same table as FloatRowPacking.facts).
    let floatSide: String?
    /// Clear keyword ("LEFT"/"RIGHT"/"BOTH"/logical) or nil.
    let clear: String?
    /// Declared block-axis margins in px (0 when undeclared — the CSS
    /// initial). Negative values are LEGAL here (unlike the §8.3.1 plan)
    /// because §9.5.2's absorbed-chain math needs them
    /// (clear-on-parent-with-margins' -1000px child).
    let marginTopPx: Double
    let marginBottomPx: Double
    /// Block-axis paddings in px (0 when undeclared). paddingTop gates
    /// both the §8.3.1 descent (adjoining needs "no top padding") and
    /// the ascent crossing; paddingBottom feeds the zero-outer predicate.
    let paddingTopPx: Double
    let paddingBottomPx: Double
    /// Explicit used height in px, or nil for height:auto.
    let heightPx: Double?
    /// Establishes a block formatting context (overflow non-visible /
    /// display:flow-root): §8.3.1 — a BFC root's margins never collapse
    /// with its in-flow children — blocks both descent and ascent here.
    let bfcRoot: Bool
    /// Children in wire order (the order both natives render block flow).
    var children: [ClearanceNode] = []

    // Memberwise init for the projector below (and the XCTest pins).
    init(comp: IRComponent, parent: ClearanceNode?, floatSide: String?,
         clear: String?, marginTopPx: Double, marginBottomPx: Double,
         paddingTopPx: Double, paddingBottomPx: Double,
         heightPx: Double?, bfcRoot: Bool) {
        self.comp = comp
        self.parent = parent
        self.floatSide = floatSide
        self.clear = clear
        self.marginTopPx = marginTopPx
        self.marginBottomPx = marginBottomPx
        self.paddingTopPx = paddingTopPx
        self.paddingBottomPx = paddingBottomPx
        self.heightPx = heightPx
        self.bfcRoot = bfcRoot
    }
}

enum FloatClearanceModel {

    /// Project `comp`'s subtree into ClearanceNodes, or nil when any box
    /// carries a value flavor outside this lane's proven scope (strict
    /// bail contract, file banner). Pure — XCTest pins run device-free.
    static func project(_ comp: IRComponent, parent: ClearanceNode?) -> ClearanceNode? {
        // Text makes flow heights unknowable to a pure pass (glyph
        // metrics are a device concern) — bail the whole scope.
        if comp.text?.isEmpty == false { return nil }
        // Interleaved inline runs re-order painting (wave 32) — bail.
        if comp.meta?.runs?.isEmpty == false { return nil }
        // Live selector/media buckets can re-style any read below after
        // the static plan baked — bail (mirrors collapse bail B6).
        if comp.selectors?.isEmpty == false || comp.media?.isEmpty == false { return nil }
        // Generated content paints boxes the projection cannot see.
        if comp.pseudos != nil { return nil }
        // UA default margins are tag-keyed (wave-25 lane UAM): any tag
        // outside the margin-free set would need the UA fold modeled
        // here too — only bare/div boxes are in scope (all six fixtures).
        if let tag = comp.meta?.sourceTag?.lowercased(), tag != "div" { return nil }
        // Per-property bails + keyword reads, one pass over the wire.
        var floatKw: String? = nil
        var clearKw: String? = nil
        var bfc = false
        var heightRaw: IRValue? = nil
        // Per-axis overflow behavior, folded in WIRE ORDER exactly like the
        // Compose twin's single owner (scrolling/OverflowExtractor): later
        // declarations win per axis, so `overflow:hidden; overflow-x:visible`
        // leaves only the block axis non-visible. Reading each Overflow* wire
        // straight into a boolean (what this file did before) could not model
        // that override and diverged from Kotlin.
        var overflowX = "VISIBLE"
        var overflowY = "VISIBLE"
        for p in comp.properties {
            switch p.type {
            // Any border could add block-start bands the position math
            // would have to model — none of the in-scope fixtures carry
            // borders, so presence bails (conservative).
            case let t where t.hasPrefix("Border"): return nil
            // Unmodeled block-size pins / ratios (only Height is read).
            case "MinHeight", "MaxHeight", "MinBlockSize", "MaxBlockSize",
                 "BlockSize", "AspectRatio": return nil
            // Vertical writing modes / rtl re-map the block axis this
            // lane's y-math assumes (css-writing-modes-4 §2).
            case "WritingMode", "Direction": return nil
            // `order` re-sorts the ForEach (FlexboxApplier.sorted) — wire
            // order would no longer be render order.
            case "Order": return nil
            // Transforms establish containing blocks + move ink.
            case "Transform", "Translate", "Scale", "Rotate": return nil
            // Row gaps insert stack spacing the simulation omits.
            case "Gap", "RowGap", "ColumnGap": return nil
            // Multicol containers leave the plain block stack entirely.
            case "ColumnCount", "ColumnWidth": return nil
            // Any positioning scheme but static: positioned containers
            // take the overlay branch, and even `relative` children can
            // paint away from their flow slot (CSS 2.1 §9.4.3).
            case "Position":
                if keyword(p.data) != "STATIC" { return nil }
            // Display: only plain block flow (and flow-root = block flow
            // + BFC, folded below) is modeled.
            case "Display":
                let d = keyword(p.data)
                guard d == "BLOCK" || d == "FLOW_ROOT" else { return nil }
                if d == "FLOW_ROOT" { bfc = true }
            // BFC roots: non-visible overflow on either axis
            // (css-overflow-3 §2.1 — one non-visible axis implies a
            // scroll container; CSS 2.1 §9.4.1 makes it a BFC root).
            // The LOGICAL aliases count too: OverflowBlock/OverflowInline
            // are the same used values under the horizontal-tb + LTR pin the
            // WritingMode/Direction bails enforce (css-overflow-3 §3), and
            // the Compose twin's OverflowExtractor has always mapped them —
            // omitting them here made `overflow-block: hidden` a BFC root on
            // Android and NOT on iOS, changing the §8.3.1 ascent.
            case "Overflow":
                let b = overflowBehavior(p.data); overflowX = b; overflowY = b
            case "OverflowX": overflowX = overflowBehavior(p.data)
            case "OverflowY": overflowY = overflowBehavior(p.data)
            case "OverflowBlock": overflowY = overflowBehavior(p.data)
            case "OverflowInline": overflowX = overflowBehavior(p.data)
            // Float/clear keywords, SHOUTY-normalized like FloatRowPacking.
            case "Float": floatKw = keyword(p.data)
            case "Clear": clearKw = keyword(p.data)
            // Explicit height wire, decoded to px below.
            case "Height": heightRaw = p.data
            default: break
            }
        }
        // Fold the two axes into the BFC flag (display:flow-root already set
        // it above): one non-visible axis is enough, same expression as the
        // Compose twin's `overflowX != VISIBLE || overflowY != VISIBLE`.
        if overflowX != "VISIBLE" || overflowY != "VISIBLE" { bfc = true }
        // Block-axis margins via the production extractor (single owner),
        // reduced to SIGNED plain px — auto/relative/calc bail.
        let mcfg = MarginExtractor.extract(from: comp.properties)
        guard let mt = signedPx(mcfg?.top), let mb = signedPx(mcfg?.bottom) else { return nil }
        // Block-axis paddings, same single-owner discipline (negative
        // padding is invalid CSS, css-box-4 §4.2 — require ≥ 0).
        let pcfg = PaddingExtractor.extract(from: comp.properties)
        guard let pt = nonNegativePx(pcfg?.top), let pb = nonNegativePx(pcfg?.bottom) else { return nil }
        // Explicit height: plain-px length or absent; % / keywords bail.
        var height: Double? = nil
        if let raw = heightRaw {
            guard raw["type"]?.stringValue != "percentage",
                  let px = raw["px"]?.doubleValue else { return nil }
            height = px
        }
        // A box that both floats and clears (floats-bfc-003's stacked
        // cleared floats) needs §9.5.1+§9.5.2 combined — out of scope.
        let floating: Set<String> = ["LEFT", "RIGHT", "INLINE_START", "INLINE_END"]
        let clearing: Set<String> = ["LEFT", "RIGHT", "BOTH", "INLINE_START", "INLINE_END"]
        let isFloat = floatKw.map { floating.contains($0) } ?? false
        let isClear = clearKw.map { clearing.contains($0) } ?? false
        if isFloat && isClear { return nil }
        // Physical side under the LTR pin (css-logical-1 §2.1).
        let side: String? = !isFloat ? nil :
            (floatKw == "LEFT" || floatKw == "INLINE_START") ? "LEFT" : "RIGHT"
        // Assemble this node, then project children depth-first; ANY
        // child bail aborts the whole scope (strict contract).
        let node = ClearanceNode(comp: comp, parent: parent, floatSide: side,
                                 clear: isClear ? clearKw : nil,
                                 marginTopPx: mt, marginBottomPx: mb,
                                 paddingTopPx: pt, paddingBottomPx: pb,
                                 heightPx: height, bfcRoot: bfc)
        for child in comp.children ?? [] {
            guard let projected = project(child, parent: node) else { return nil }
            node.children.append(projected)
        }
        return node
    }

    /// IR keyword, SHOUTY-normalized like every keyword read here.
    private static func keyword(_ v: IRValue?) -> String? {
        ValueExtractors.extractKeyword(v).map { ValueExtractors.normalize($0) }
    }

    /// One overflow payload → its used behavior keyword, mirroring the
    /// Compose OverflowExtractor.extractOverflowBehavior fold: only the five
    /// css-overflow-3 §3 keywords are recognized and EVERYTHING else — an
    /// unknown keyword, or a non-keyword payload the extractor cannot decode
    /// (a length object, a number) — resolves to the VISIBLE initial. The
    /// old `keyword(p.data) != "VISIBLE"` test made both of those a BFC root
    /// on iOS while Kotlin kept them visible; a BFC root blocks the §8.3.1
    /// ascent, so the two natives computed different clearance.
    private static func overflowBehavior(_ v: IRValue?) -> String {
        guard let kw = keyword(v) else { return "VISIBLE" }
        switch kw {
        case "VISIBLE", "HIDDEN", "SCROLL", "AUTO", "CLIP": return kw
        default: return "VISIBLE"
        }
    }

    /// Margin side → px: unset = 0 (CSS initial), exact px passes SIGNED.
    private static func signedPx(_ v: LengthValue?) -> Double? {
        guard let v = v else { return 0 }
        if case .exact(let px) = v { return px }
        return nil
    }

    /// Padding side → px: unset = 0, exact non-negative px only.
    private static func nonNegativePx(_ v: LengthValue?) -> Double? {
        guard let v = v else { return 0 }
        if case .exact(let px) = v, px >= 0 { return px }
        return nil
    }
}
