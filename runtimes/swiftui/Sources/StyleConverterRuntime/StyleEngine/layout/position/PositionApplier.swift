//
//  PositionApplier.swift
//  StyleEngine/layout/position — Phase 7, step 4 (position).
//
//  Emits per-child SwiftUI modifiers for Position + Inset + ZIndex:
//    • .staticPos              → identity.
//    • .relative with insets   → .offset(x:left, y:top) approximation
//                                 (offset preserves flow, matches CSS).
//    • .absolute               → .offset(x: left ?? -right, y: top ?? -bottom)
//                                 anchored at the positioned ancestor's
//                                 padding-box overlay (the wave-8 mount;
//                                 `needsZStackWrap` flags the container).
//    • .fixed                  → SAME anchored offset, but the box is
//                                 MOUNTED at the canvas root: wave 17's
//                                 FixedHoist strips fixed boxes out of
//                                 their parent tree and hoist-aware
//                                 canvases mount them in FixedHoistOverlay
//                                 at the UNPADDED canvas origin (the
//                                 viewport containing block,
//                                 css-position-3 §3.1). Un-hoisted hosts
//                                 keep the legacy parent anchor verbatim.
//    • .sticky                 → identity + TODO (no SwiftUI equivalent).
//    • zIndex                  → .zIndex(Double(value)).
//
//  Caveats: SwiftUI's .position(x:y:) is coordinate-absolute (centre of
//  the view at the point), which doesn't map cleanly to CSS `top: 0;
//  left: 0` (CSS positions the top-left edge). We use .offset from a
//  ZStack(alignment: .topLeading) so the child's natural frame sits in
//  the upper-left corner and the offset is relative to that corner —
//  which does match CSS exactly for left/top.
//

import SwiftUI
import CoreGraphics

enum PositionApplier {

    /// Apply positioning to a single child view. `agg` is the child's
    /// own LayoutAggregate; `isRTL` comes from the SwiftUI Environment
    /// at the call site.
    static func apply(_ view: AnyView,
                      aggregate agg: LayoutAggregate?,
                      isRTL: Bool = false) -> AnyView {
        // Identity pass when no position fields were set — matches the
        // typography/sizing pattern of "no IR → no modifier overhead".
        guard let agg = agg else { return view }

        // Resolve logical sides against the current layout direction.
        // The extractor defaulted to LTR; flip for RTL here where we can
        // actually see the Environment.
        let rect = agg.inset?.resolved(isRTL: isRTL)

        // Start from the incoming view and layer modifiers. Use AnyView
        // erasures at each step — consistent with the other engine
        // appliers in the project that chain into a single return path.
        var out = view

        switch agg.position ?? .staticPos {
        case .staticPos:
            // No offset. Identity (zIndex handled below).
            break

        case .relative:
            // Relative offset keeps the element in flow and nudges its
            // paint by (left, top). Right/Bottom are ignored when the
            // corresponding opposite side is present — matches CSS.
            if let r = rect {
                let x = r.left ?? (r.right.map { -$0 } ?? 0)
                let y = r.top  ?? (r.bottom.map { -$0 } ?? 0)
                if x != 0 || y != 0 {
                    out = AnyView(out.offset(x: x, y: y))
                }
            }

        case .absolute:
            // F2 (css-position-3 §3.1): an absolute box anchors at its
            // containing block's corner + insets. The renderer mounts it
            // in the positioned ancestor's padding-box overlay (the
            // wave-8 anchor, wave-9/11 containing-block channels), or —
            // for a ROOT-level box — a hoist-aware canvas mounts it in
            // the canvas-root FixedHoistOverlay (wave 17: the initial
            // containing block is the UNPADDED canvas, per the measured
            // web behavior). Either way THIS modifier only expresses
            // "corner of my anchoring ZStack + inset offset".
            out = anchoredInsetOffset(out, rect: rect)

        case .fixed:
            // F1 (wave 17, css-position-3 §3.1): a fixed box's containing
            // block is the VIEWPORT — never the parent. The containing-
            // block difference from .absolute is established by WHERE the
            // box is MOUNTED, not by a different offset formula:
            // FixedHoist strips fixed boxes out of their parent's tree
            // and a hoist-aware canvas (the composed WPT canvas) mounts
            // them in the canvas-root FixedHoistOverlay ABOVE its
            // padding, so this identical anchored offset resolves
            // against the unpadded canvas origin — insets as absolute
            // anchors, no parent flow slot, no compounding (pin S3).
            // A host that renders WITHOUT the hoist (the dark-stage
            // per-component canvas, plain SDUI trees) keeps the legacy
            // parent-anchored geometry byte-identically — the
            // 327-baseline gate — because the math below is shared.
            out = anchoredInsetOffset(out, rect: rect)

        case .sticky:
            // SwiftUI has no sticky modifier; the closest thing is a
            // ScrollView with a pinned header. Log a TODO and render
            // identity so the rest of the test suite still passes.
            // TODO: integrate with the sticky-header TOC PR once it lands.
            break
        }

        // zIndex applies regardless of positioning scheme — CSS allows
        // z-index on any positioned element, and SwiftUI's .zIndex works
        // on any view (paint-order tie-breaker at the parent stack).
        if let z = agg.zIndex {
            out = AnyView(out.zIndex(z))
        }

        return out
    }

    /// The shared out-of-flow geometry (wave 17 split of the old fused
    /// `.absolute, .fixed` case — same math, now cited per scheme at the
    /// call sites): anchor the box at the corner of its mounting ZStack
    /// picked by WHICH insets are declared, then offset by them.
    ///
    /// left / top → straight offset from the top-leading corner.
    /// right / bottom without left / top → anchor at the trailing/bottom
    /// edge and offset NEGATIVELY. Since we have no container size at
    /// this layer, the flexible frame + alignment stands in for true
    /// edge resolution. TODO: replace with GeometryReader-based
    /// right/bottom inset math when a corpus test pins it.
    ///
    /// A nil rect (no insets at all) is IDENTITY: the box stays at its
    /// static position (CSS 2.1 §10.3.7/§10.6.4 — all-auto insets),
    /// which the renderer's static-position shifts then own.
    private static func anchoredInsetOffset(_ view: AnyView,
                                            rect r: InsetRect?) -> AnyView {
        // No insets → static position; nothing for this modifier to do.
        guard let r = r else { return view }
        // Any declared side on an axis claims that axis's anchor.
        let hasLeftOrRight = (r.left != nil) || (r.right != nil)
        let hasTopOrBottom = (r.top != nil)  || (r.bottom != nil)
        // All four auto (empty rect object) → identity, same as nil.
        guard hasLeftOrRight || hasTopOrBottom else { return view }
        // Pick the anchoring corner per axis: a declared left/top wins
        // (CSS resolves over-constrained insets in favor of the
        // inline/block start in LTR — CSS 2.1 §10.3.7 rule order).
        let h: HorizontalAlignment = (r.left != nil) ? .leading
                                    : (r.right != nil) ? .trailing
                                    : .leading
        let v: VerticalAlignment = (r.top != nil) ? .top
                                 : (r.bottom != nil) ? .bottom
                                 : .top
        // Offset magnitude: start insets push positively; end insets
        // (right/bottom) pull NEGATIVELY from the trailing anchor.
        let dx = r.left ?? (r.right.map { -$0 } ?? 0)
        let dy = r.top  ?? (r.bottom.map { -$0 } ?? 0)
        return AnyView(
            view
                // Flow-filling frame so we can anchor to a corner of
                // whatever ZStack mounts this box (parent overlay for
                // absolute, canvas-root FixedHoistOverlay for hoisted
                // fixed/absolute roots).
                .frame(maxWidth: .infinity,
                       maxHeight: .infinity,
                       alignment: Alignment(horizontal: h, vertical: v))
                .offset(x: dx, y: dy)
        )
    }

    /// Returns true when at least one child of a container carries
    /// position: absolute | fixed — signalling the container must be a
    /// ZStack(alignment: .topLeading) so offsets resolve from the
    /// top-left corner of the parent's bounds.
    ///
    /// Callers: ComponentRenderer inspects children's parsed aggregates
    /// before choosing a container shape.
    static func needsZStackWrap(forChildren childAggregates: [LayoutAggregate?]) -> Bool {
        for agg in childAggregates {
            guard let p = agg?.position else { continue }
            if p == .absolute || p == .fixed { return true }
        }
        return false
    }
}
