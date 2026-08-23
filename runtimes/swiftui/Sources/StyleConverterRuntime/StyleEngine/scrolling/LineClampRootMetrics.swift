//
//  LineClampRootMetrics.swift
//  StyleEngine/scrolling — Wave 46 (lane Y1): the clamp ROOT's resolved
//  text metrics the census inherits from, and the renderer-facing
//  resolver `LineClampCap.resolveCapPx` that ties wire gate, explicit-
//  height gate, census and uniform fallback together. Split from
//  LineClampChildMetrics.swift under the ≤200-line rule.
//

import CoreGraphics
import Foundation

/// The clamp root's resolved text metrics the census inherits from —
/// handed in by the renderer seam off the SAME TextConfig the root's
/// label lays out with, so cap and leaf can never disagree.
struct LineClampRootMetrics: Equatable {
    /// The root's used font size in px (monospace-UA 13px folded in).
    let fontSizePx: CGFloat
    /// The root's DECLARED px line-height (nil: calibrated / normal).
    let declaredLineHeightPx: CGFloat?
    /// The root's one-line box (LineClampCap.lineBoxPx of the two above).
    let lineBoxPx: CGFloat
    /// The root's `white-space` keyword (extractor-lowercased), nil = normal.
    let whiteSpace: String?
    /// The root's DECLARED unitless line-height multiplier, when that is
    /// how it declared one: it inherits as the NUMBER and recomputes
    /// against each child's own font size (CSS 2.1 §10.8). nil for a px
    /// / absent / `normal` declaration.
    let declaredMultiplier: CGFloat?
}

extension LineClampRootMetrics {
    /// Build the root metrics off the root's RESOLVED property list and
    /// the two numbers its own label lays out with (TextConfig.fontSize —
    /// monospace-UA folded — and TextConfig.lineHeight; `declaredNormal`
    /// is TextConfig.lineHeightIsNormal, which routes the legacy 1.2
    /// stand-in the wire still ships for `normal` back to "undeclared").
    init(rootProperties: [IRProperty],
         fontSizePx: CGFloat,
         declaredLineHeightPx: CGFloat?,
         declaredNormal: Bool) {
        // `normal` is NOT a declared length: the cap uses the calibrated
        // ratio (the face's natural box is unknown without a layout pass).
        let declared = declaredNormal ? nil : declaredLineHeightPx
        // A root unitless multiplier inherits as the NUMBER (CSS 2.1 §10.8)
        // — read off the raw wire, which is the only place the number
        // survives (the aggregate resolves it to px for the root's label).
        let multiplier = rootProperties
            .last(where: { $0.type == LineHeightProperty.name })
            .flatMap { p -> CGFloat? in
                guard !declaredNormal, case .object(let o) = p.data,
                      o["px"]?.doubleValue == nil,
                      let m = o["multiplier"]?.doubleValue, m > 0 else { return nil }
                return CGFloat(m)
            }
        self.fontSizePx = fontSizePx
        self.declaredLineHeightPx = declared
        self.declaredMultiplier = multiplier
        // The root's line box: LineClampCap's single pick (declared px
        // wins, else the multiplier × the root's size, else the 1.25
        // ratio); a broken (≤0) size bottoms out at the 16px CSS default
        // so the root always has SOME line box.
        self.lineBoxPx = LineClampCap.lineBoxPx(
            declaredLineHeightPx: declared ?? multiplier.map { $0 * fontSizePx },
            fontSizePx: fontSizePx)
            ?? LineClampCap.lineBoxPx(declaredLineHeightPx: nil, fontSizePx: 16)!
        // The root's white-space keyword (extractor-lowercased), nil = normal.
        self.whiteSpace = WhiteSpaceExtractor.extract(from: rootProperties)?.keyword
    }
}

extension LineClampCap {
    /// The renderer-facing resolver: the content-box cap in px for this
    /// clamp root, or nil when no cap applies (no fixed-count clamp, an
    /// explicit height, the census PROVES the content is shorter than the
    /// clamp, or the Nth line box's edge cannot be known).
    ///
    /// Order of decision: the wire gate (`linesCount`) → the explicit-
    /// height gate → the census over the in-flow inline content
    /// (`LineClampCensus`) → on an unprovable count, the uniform
    /// N × root-line-box model when every run shares that box (the
    /// Compose wave-41 cap), else no cap.
    static func resolveCapPx(component: IRComponent,
                             inFlowChildren: [IRComponent],
                             rootProperties: [IRProperty],
                             root: LineClampRootMetrics) -> CGFloat? {
        // No fixed-count clamp on the wire → no cap (the common fast path).
        guard let lines = linesCount(rootProperties) else { return nil }
        // A definite `height` is the used box height: CSS used-height wins
        // over max-lines (Compose's cap coerces into a fixed constraint the
        // same way), and a post-load-extracted wire's browser-measured
        // height ALREADY encodes the clamp (block-ellipsis-007/-008/-009
        // ship `height: 50/90px` with their `::first-line` 1.5em lines —
        // a 1 × 20px uniform cap inside that box would slice "Line 1").
        if let h = rootProperties.last(where: { $0.type == "Height" }),
           ValueExtractors.extractKeyword(h.data)?.lowercased() != "auto" { return nil }
        // The document-order census over text runs and in-flow children.
        let runs = LineClampCensus.runs(component: component,
                                        inFlowChildren: inFlowChildren,
                                        root: root)
        switch LineClampCensus.verdict(lines: lines, runs: runs, rootLineBoxPx: root.lineBoxPx) {
        // The Nth line box is proven: its bottom edge is the cap.
        case .capped(let h): return h
        // Fewer than N proven line boxes: nothing to discard.
        case .short: return nil
        // Every run on the root's box: N uniform root line boxes.
        case .uniform: return capPx(lines: lines, lineBoxPx: root.lineBoxPx)
        // Heterogeneous and unprovable: a guess would clip a visible line.
        case .unbounded: return nil
        }
    }
}
