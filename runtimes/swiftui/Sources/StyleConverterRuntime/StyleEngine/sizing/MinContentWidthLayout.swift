//
//  MinContentWidthLayout.swift
//  StyleEngine/sizing — fidelity wave 5.
//
//  Split out of SizeApplier.swift verbatim when Lane BX (box-sizing)
//  pushed that file past the ~300-line split rule. No behaviour change —
//  same struct, same folder, SwiftPM picks the file up automatically.
//

// SwiftUI for the Layout protocol + ProposedViewSize.
import SwiftUI

/// Proposes width 0 to its single child and adopts whatever size the
/// child reports back — SwiftUI text under a zero-width proposal wraps
/// at every soft-wrap opportunity and measures its longest word, which
/// IS the CSS min-content inline size (css-sizing-3 §5.1). `.fixedSize`
/// can't express this (it reports the IDEAL = max-content size), so
/// `width: min-content` boxes rendered a single wide line on iOS while
/// web/Android wrapped (PW_Sizing_Spacing_02, i-w 0.703).
struct MinContentWidthLayout: Layout {
    /// Horizontal padding band inside the wrapped chain — the proposal
    /// must survive `.padding`'s subtraction so ~1px reaches the text.
    var inset: CGFloat = 0

    /// The narrowest proposal that still renders glyphs: the padding
    /// band plus one pixel for the character column.
    private var probe: CGFloat { inset + 1 }

    /// Report the child's size under the narrow proposal. Height still
    /// follows the container's proposal so explicit heights and
    /// min/max clamps inside the child chain behave unchanged.
    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews, cache: inout ()) -> CGSize {
        guard let sub = subviews.first else { return .zero }
        return sub.sizeThatFits(ProposedViewSize(width: probe,
                                                 height: proposal.height))
    }

    /// Place the child at the size it measured under the narrow
    /// proposal (re-proposing the CONCRETE measured size so the wrap
    /// layout is reproduced at render time), anchored top-leading like
    /// every other block box.
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        guard let sub = subviews.first else { return }
        let sz = sub.sizeThatFits(ProposedViewSize(width: probe,
                                                   height: proposal.height))
        sub.place(at: bounds.origin, anchor: .topLeading,
                  proposal: ProposedViewSize(sz))
    }
}
