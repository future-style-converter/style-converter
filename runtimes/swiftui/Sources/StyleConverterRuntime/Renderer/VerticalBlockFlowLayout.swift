//
//  VerticalBlockFlowLayout.swift
//  Renderer — wave 47 (lane Z2).
//
//  The vertical-writing-mode BLOCK-FLOW seam (css-writing-modes-4 §6: under
//  `vertical-rl`/`vertical-lr` the block-flow direction is HORIZONTAL, so a
//  block container's in-flow children stack side-by-side — right→left for
//  vertical-rl, left→right for vertical-lr — instead of the VStack that is
//  correct for horizontal-tb). Companion of VerticalTextFlowLayout.swift
//  (the inline-axis half); consumed only by ComponentRenderer's block
//  branch, behind the Z2 capture gate documented there. The Compose twin is
//  core/renderer/VerticalBlockFlowLayout.kt — same placement table
//  (VerticalBlockFlowMathTest pins it).
//

// SwiftUI for the Layout protocol — same availability discipline as
// FloatBlockLayout / InlineAtomBlockLayout — and the environment key that
// marks the scope the vertical fill folds are allowed to act in.
import SwiftUI

/// Wave 47 (lane Z2) — true only INSIDE a flow this lane actually models:
/// the VerticalBlockFlowLayout seam's content and a vertical multicol
/// fragment column (both publish it in ComponentRenderer). The renderer's
/// vertical fill decisions — suppressing the §10.3.3 WIDTH fill and
/// applying the inline-axis (height) stretch — are gated on it, so every
/// vertical box still rendered by the FROZEN horizontal-tb paths (legacy
/// VStacks, flex/grid items — the css-writing-modes available-size family,
/// which PASSES on those paths today) keeps its pre-Z2 fills
/// byte-identically. Compose twin: LocalVerticalFlowScope.
private struct VerticalFlowScopeZ2Key: EnvironmentKey {
    static let defaultValue: Bool = false
}

extension EnvironmentValues {
    /// See VerticalFlowScopeZ2Key's doc above.
    var verticalFlowScopeZ2: Bool {
        get { self[VerticalFlowScopeZ2Key.self] }
        set { self[VerticalFlowScopeZ2Key.self] = newValue }
    }
}

/// Block-flow container for a VERTICAL writing mode: subviews measure at
/// their IDEAL width (a box's width is its BLOCK size — own declaration or
/// content — never a stretch; the §10.3.3 stretch fit belongs to the inline
/// axis, i.e. the height here) and place sequentially along the horizontal
/// axis. §8.3.1 margin COLLAPSE along this axis is NOT modeled — the
/// caller logs that honestly; margins ride inside each subview's own box
/// (the repo's padding-based margin emulation), so plain summing reproduces
/// single-sided block-axis margins exactly.
@available(iOS 16.0, *)
struct VerticalBlockFlowLayout: Layout {
    /// true = vertical-rl / sideways-rl: the FIRST child sits at the
    /// container's RIGHT edge (block-start, §6.4) and flow walks leftward.
    let blockRtl: Bool

    /// Per-subview proposal: ideal width (nil = "whatever you want", the
    /// box's own block size), the container's proposed height as the
    /// inline-axis cap (the stretch-fit basis the vertical fill fold
    /// targets statically).
    private func childProposal(_ proposal: ProposedViewSize) -> ProposedViewSize {
        ProposedViewSize(width: nil, height: proposal.height)
    }

    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        // The container's used block extent = the SUM of child extents
        // (css-writing-modes-4 §7.3 — an orthogonal box's auto block size
        // hugs its content, even past the ICB); its inline extent = the
        // proposal (the style chain's height pin) or the tallest child.
        let sizes = subviews.map { $0.sizeThatFits(childProposal(proposal)) }
        let total = sizes.reduce(CGFloat(0)) { $0 + $1.width }
        let maxH = sizes.map(\.height).max() ?? 0
        return CGSize(width: total, height: proposal.height ?? maxH)
    }

    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout ()) {
        // Re-measure with the same proposal the sizing pass used so the
        // two passes can never drift (the CSSFlexLayout discipline).
        let sizes = subviews.map { $0.sizeThatFits(childProposal(proposal)) }
        // The anchor: bounds.maxX IS the container's block-start edge for
        // rl — for an auto-sized box bounds.width is our reported content
        // sum, and for an author-pinned narrower box overflow then escapes
        // past block-end (LEFT), exactly the browser's escape direction
        // (Compose twin: VerticalBlockFlowMath.positions' anchor rule).
        var x = blockRtl ? bounds.maxX : bounds.minX
        for (index, subview) in subviews.enumerated() {
            let w = sizes[index].width
            // rl walks leftward from the right edge; lr rightward from 0.
            if blockRtl { x -= w }
            // In-flow block boxes share the container's inline-start
            // (top) edge; each keeps its own measured size.
            subview.place(
                at: CGPoint(x: x, y: bounds.minY),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: w, height: sizes[index].height))
            if !blockRtl { x += w }
        }
    }
}
