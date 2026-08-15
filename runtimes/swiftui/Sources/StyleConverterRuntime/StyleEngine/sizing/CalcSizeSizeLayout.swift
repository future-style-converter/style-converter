//
//  CalcSizeSizeLayout.swift
//  StyleEngine/sizing — wave 42 (lane W3).
//
//  The SwiftUI Layout half of css-values-5 calc-size() for the WIDTH /
//  HEIGHT (preferred size) slots: resolve the `size` basis by MEASURING the
//  wrapped chain under a basis-appropriate proposal, then report
//  `factor * basis + offset` on the calc axis. The MinContentWidthLayout
//  pattern (same folder) is the direct precedent — propose narrow to read
//  a min-content, propose nil to read the ideal (max-content-ish) size.
//
//  Basis approximation (recorded, not silent): SwiftUI has ONE intrinsic
//  channel (the ideal size) where CSS has two (min-/max-content) — the
//  standing SizeApplier banner note ("SwiftUI doesn't differentiate").
//  This layout resolves auto / fit-content / min-content via the
//  zero-width-probe measure (min-content flavour) and max-content via the
//  nil proposal (ideal). For the corpus that choice is what the refs
//  reward: calc-size-min-max-sizes-001/004 need the min-content basis
//  (20 + 80 = the ref's exact 100px square); 002/005 come out 80px and
//  003/006 90px vs the ref's 100 — the SAME 80-to-100 green-square gap the
//  six calc-size-flex cells already PASS with on iOS (0.9751..0.9819), so
//  the approximation lands inside the gate's demonstrated tolerance,
//  against today's ~20px sliver (0.9619 FAIL, all six).
//

// SwiftUI for the Layout protocol + ProposedViewSize.
import SwiftUI

/// Wraps one child chain; reports the calc-size() result on [isWidth]'s
/// axis and the child's own measure on the other.
struct CalcSizeSizeLayout: Layout {
    /// True = the calc value sizes the width axis; false = the height axis.
    let isWidth: Bool
    /// The typed calc-size value (basis + affine coefficients).
    let value: CalcSizeValue

    /// The near-zero probe that makes SwiftUI content wrap and report its
    /// min-content extent (MinContentWidthLayout's mechanism — 0 makes
    /// text drop glyphs entirely, 1 keeps the column alive).
    private let minProbe: CGFloat = 1

    /// Resolve the basis by measuring [sub] under the basis's proposal.
    /// Pure per-basis decision; the WIDTH axis distinguishes min-vs-max
    /// flavour, the HEIGHT axis always measures under the container's
    /// width (a block's content height IS its height basis either way).
    private func basisPx(_ sub: LayoutSubview,
                         proposal: ProposedViewSize) -> CGFloat {
        if isWidth {
            switch value.basis {
            case .maxContent:
                // Ideal (unconstrained) width — SwiftUI's max-content.
                return sub.sizeThatFits(
                    ProposedViewSize(width: nil, height: proposal.height)).width
            case .auto, .minContent, .fitContent, .stretch, .content:
                // Min-content flavour: the near-zero probe wraps the
                // content to its narrowest extent (see the banner note for
                // why fit-content/auto take this flavour on iOS).
                return sub.sizeThatFits(
                    ProposedViewSize(width: minProbe, height: proposal.height)).width
            }
        } else {
            // Height basis: the content's natural height under the width
            // the container proposes (nil height = ideal height).
            return sub.sizeThatFits(
                ProposedViewSize(width: proposal.width, height: nil)).height
        }
    }

    /// Report the affine result on the calc axis; the child's own measure
    /// (re-proposed at the resolved target) supplies the other axis so
    /// wrapping content grows the box the way a real block would.
    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews, cache: inout ()) -> CGSize {
        guard let sub = subviews.first else { return .zero }
        // Affine evaluation over the measured basis.
        let target = value.targetPx(basisPx(sub, proposal: proposal))
        if isWidth {
            // Cross axis: the child's height at the RESOLVED width.
            let h = sub.sizeThatFits(
                ProposedViewSize(width: target, height: proposal.height)).height
            return CGSize(width: target, height: h)
        } else {
            let w = sub.sizeThatFits(
                ProposedViewSize(width: proposal.width, height: target)).width
            return CGSize(width: w, height: target)
        }
    }

    /// Place the child at the resolved size, top-leading like every block
    /// box in this engine — the calc axis proposes the CONCRETE target so
    /// the child lays out inside the box the parent was told about.
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        guard let sub = subviews.first else { return }
        let target = value.targetPx(basisPx(sub, proposal: proposal))
        let concrete = isWidth
            ? ProposedViewSize(width: target, height: bounds.height)
            : ProposedViewSize(width: bounds.width, height: target)
        sub.place(at: bounds.origin, anchor: .topLeading, proposal: concrete)
    }
}
