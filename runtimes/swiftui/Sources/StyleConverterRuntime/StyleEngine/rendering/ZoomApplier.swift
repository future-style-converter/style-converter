//
//  ZoomApplier.swift
//  StyleEngine/rendering — ZoomConfig → a SwiftUI modifier implementing
//  bounded css-viewport-1 `zoom` semantics.
//
//  ## Why a plain `.scaleEffect` is the WRONG answer
//  `.scaleEffect` is a GeometryEffect: it warps the rendered layer and
//  leaves the reported layout size untouched, so the parent keeps
//  measuring the unzoomed frame — a zoomed box overflows its slot (or the
//  slot clips it) and every sibling below it stays put. CSS `zoom` moves
//  the slot too: it multiplies the element's used values AND the space it
//  occupies (css-viewport-1 §"The zoom property"). That mismatch is why
//  this applier used to not exist and iOS shipped an honest no-op + TODO.
//
//  ## The mechanism: MEASURE-THEN-SCALE
//  The only primitive that can move a SwiftUI layout slot is a custom
//  `Layout` (iOS 16), so ZoomLayout:
//
//   1. **De-scales the incoming proposal** (`proposal / zoom`). This is
//      what makes a zoomed box behave like the browser's: under
//      `zoom: 1.5` it still fits the parent's 390pt, because in its OWN
//      (zoomed) coordinate space the available room is 260pt. It is also
//      what makes percentages inside resolve against the de-scaled
//      containing block, matching the used-value rule.
//   2. **Measures the subtree** at that proposal — every length inside
//      (padding bands, border widths, the 50×30 placeholder floor, text
//      line boxes) is still in unzoomed points, so it lands at its
//      authored size in the child's coordinate space.
//   3. **Reports `size * zoom`** to the parent. That is the slot change:
//      the next sibling in a VStack now starts `height * zoom` lower,
//      exactly like the browser's zoomed block box.
//   4. **Places the subview at the slot's top-leading**, where
//      `.scaleEffect(factor, anchor: .topLeading)` — attached to the
//      content BEFORE it enters the layout, so it is size-transparent —
//      grows the paint into the slot step 3 just claimed. Top-leading is
//      the correct anchor because CSS zoom is not a centred transform:
//      the border-box ORIGIN stays put and the box grows right/down.
//
//  Steps 2–4 compose the way CSS composes effective zoom: a `zoom: 2` box
//  nested in a `zoom: 1.5` box gets 1.5 from the ancestor's effect and 2
//  from its own → 3×, which is what the browser's inherited effective
//  zoom produces.
//
//  ## Fidelity note (stated, not hidden)
//  The browser re-lays-out text at `font-size × zoom`, so hinting and
//  line breaking are recomputed. Here the subtree is laid out at the
//  unzoomed font size and the geometry effect magnifies it, so a line
//  that would have broken differently at the zoomed size will not. No
//  fixture in the corpus exercises that; when one does the fix is
//  font-size threading, not a change to this Layout.
//
//  runtimes/compose/.../rendering/ZoomApplier.kt is the byte-parallel
//  twin — same four steps, same top-left anchor, same identity guard.
//

import SwiftUI

/// The slot-moving half of `zoom`. Measures its subviews against the
/// de-scaled proposal and reports the scaled size (see the file header).
@available(iOS 16.0, *)
struct ZoomLayout: Layout {
    /// Used scale factor; guaranteed finite and > 0 by ZoomApplier.
    let factor: CGFloat

    /// Step 1 — the de-scaled proposal handed to the subtree. `nil`
    /// (unspecified) dimensions must survive as `nil`: an intrinsic pass
    /// legitimately proposes nothing on an axis, and dividing that by the
    /// factor would invent a definite constraint.
    private func inner(_ p: ProposedViewSize) -> ProposedViewSize {
        ProposedViewSize(
            width:  p.width.map  { $0.isFinite ? $0 / factor : $0 },
            height: p.height.map { $0.isFinite ? $0 / factor : $0 }
        )
    }

    /// Steps 2–3 — measure at the de-scaled proposal, report `× factor`.
    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        let p = inner(proposal)
        // Normally exactly one subview (the modifier's Content). Taking
        // the union keeps a TupleView/Group content honest rather than
        // silently rendering only its first element.
        var w: CGFloat = 0
        var h: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(p)
            w = max(w, size.width)
            h = max(h, size.height)
        }
        return CGSize(width: w * factor, height: h * factor)
    }

    /// Step 4 — place at the slot origin, unscaled. The scale itself is
    /// the `.scaleEffect` the modifier wrapped the content in, which is
    /// size-transparent and therefore invisible to `sizeThatFits` above.
    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout ()) {
        let p = inner(proposal)
        for s in subviews {
            // Propose the child its own measured size so the placement
            // proposal matches what step 2 measured — placing with the
            // de-scaled parent proposal would let a flexible child expand
            // to the full de-scaled width and then get scaled past the
            // slot the parent already committed to.
            let size = s.sizeThatFits(p)
            s.place(at: CGPoint(x: bounds.minX, y: bounds.minY),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(size))
        }
    }
}

/// The `ViewModifier` half: identity unless the config carries a real
/// factor, so every un-zoomed element in the corpus keeps a byte-identical
/// view tree.
struct ZoomApplier: ViewModifier {
    let config: ZoomConfig?

    func body(content: Content) -> some View {
        if let c = config, c.hasZoom, c.factor.isFinite, c.factor > 0,
           #available(iOS 16.0, *) {
            ZoomLayout(factor: c.factor) {
                // The paint half. Attached INSIDE the layout so it stays
                // size-transparent: ZoomLayout measures the unscaled
                // content and owns the reported size.
                content.scaleEffect(c.factor, anchor: .topLeading)
            }
        } else {
            content
        }
    }
}

extension View {
    /// Chain helper — identity when the config is nil or carries no scale.
    ///
    /// MUST be chained OUTERMOST (SwiftUI order is inner → outer, so
    /// "outermost" = last). Everything the element paints — background,
    /// borders, padding band, sizing frame, transforms, margin — has to
    /// sit inside this node for the used-value multiplication to cover it.
    func engineZoom(_ config: ZoomConfig?) -> some View {
        modifier(ZoomApplier(config: config))
    }
}
