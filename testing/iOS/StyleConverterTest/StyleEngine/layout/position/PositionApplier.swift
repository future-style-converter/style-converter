//
//  PositionApplier.swift
//  StyleEngine/layout/position — Phase 7, step 4 (position).
//
//  Emits per-child SwiftUI modifiers for Position + Inset + ZIndex:
//    • .staticPos              → identity.
//    • .relative with insets   → .offset(x:left, y:top) approximation
//                                 (offset preserves flow, matches CSS).
//    • .absolute / .fixed      → .offset(x: left ?? -right, y: top ?? -bottom)
//                                 while requiring the parent to be a ZStack.
//                                 The parent's ContainerDecision exposes
//                                 `needsZStackWrap` for this purpose.
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

        case .absolute, .fixed:
            // Absolute / fixed positioning needs a ZStack(alignment:
            // .topLeading) parent; the renderer wraps when
            // ContainerDecision.needsZStackWrap is true. Once wrapped,
            // the child sits in the upper-left and we offset from there.
            //
            // left / top → straight offset.
            // right / bottom without left / top → offset is resolved
            // against the parent's right/bottom edges. Since we have no
            // parent-size at this layer, the closest approximation is to
            // use .frame(maxWidth: .infinity, alignment: .trailing) +
            // offset. A proper implementation would need GeometryReader.
            if let r = rect {
                let hasLeftOrRight = (r.left != nil) || (r.right != nil)
                let hasTopOrBottom = (r.top != nil)  || (r.bottom != nil)
                let x = r.left ?? 0
                let y = r.top  ?? 0
                if hasLeftOrRight || hasTopOrBottom {
                    // Pick alignment based on which sides were set so
                    // right:/bottom: work reasonably without measuring
                    // the parent. TODO: replace with GeometryReader-based
                    // true positioning.
                    let h: HorizontalAlignment = (r.left != nil) ? .leading
                                                : (r.right != nil) ? .trailing
                                                : .leading
                    let v: VerticalAlignment = (r.top != nil) ? .top
                                             : (r.bottom != nil) ? .bottom
                                             : .top
                    let dx = r.left ?? (r.right.map { -$0 } ?? 0)
                    let dy = r.top  ?? (r.bottom.map { -$0 } ?? 0)
                    _ = (x, y)
                    out = AnyView(
                        out
                            // Flow-filling frame so we can anchor to a corner.
                            .frame(maxWidth: .infinity,
                                   maxHeight: .infinity,
                                   alignment: Alignment(horizontal: h, vertical: v))
                            .offset(x: dx, y: dy)
                    )
                }
            }

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
