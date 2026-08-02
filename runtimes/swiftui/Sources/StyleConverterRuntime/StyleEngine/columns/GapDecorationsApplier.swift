//
//  GapDecorationsApplier.swift
//  StyleEngine/columns — wave 24, lane GAPS-I. The DRAW HOOK.
//
//  The plumbing that feeds the pure GapDecorationSegments module real
//  flex-item geometry, and hands the resulting segment list to the
//  painter:
//
//    • GapItemFramesKey  — a PreferenceKey carrying each in-flow child's
//      Anchor<CGRect>. SwiftUI's Layout protocol cannot draw, so the
//      item rects have to travel OUT of the layout as anchors and be
//      resolved back into the container's own coordinate space.
//    • .gapDecorationItemFrame(index:active:) — attached to each flex
//      child by ComponentRenderer, and ONLY when the container really
//      declares gap decorations. Inactive containers attach nothing, so
//      the whole baseline corpus is byte-untouched.
//    • .gapDecorations(config:mainHorizontal:) — attached to the flex
//      container; resolves the anchors, calls the pure segment builder,
//      and overlays the painter. It then RESETS the preference so a
//      nested decorated container can never see its ancestor's items.
//
//  The INK half — the segment-list → pixels painter and its stroke
//  recipes — lives next door in GapDecorationsPainter.swift: wave 25's
//  paint-surface fix (CAL-RC3) pushed the combined file past the
//  300-line split threshold.
//

import SwiftUI

// MARK: - Item-frame channel

/// Per-child Anchor<CGRect>, keyed by flow index so the segment builder
/// receives items in DOM order (which is what line grouping needs).
struct GapItemFramesKey: PreferenceKey {
    /// No children measured yet.
    static let defaultValue: [Int: Anchor<CGRect>] = [:]
    /// Merge sibling contributions; identical keys cannot collide
    /// because the index is unique within one container.
    static func reduce(value: inout [Int: Anchor<CGRect>],
                       nextValue: () -> [Int: Anchor<CGRect>]) {
        value.merge(nextValue()) { a, _ in a }
    }
}

extension View {
    /// Publish this flex child's bounds for the gap-decorations painter.
    /// `active == false` returns the view UNCHANGED (not merely with an
    /// empty preference) so a non-decorated container pays nothing and
    /// its view tree is identical to the pre-wave-24 shape.
    @ViewBuilder
    func gapDecorationItemFrame(index: Int, active: Bool) -> some View {
        if active {
            // .anchorPreference is a transparent modifier: it reads the
            // resolved bounds and changes neither layout nor paint.
            anchorPreference(key: GapItemFramesKey.self, value: .bounds) {
                [index: $0]
            }
        } else {
            self
        }
    }

    /// Paint this flex container's gap decorations. Nil / inactive config
    /// returns the container unchanged.
    @ViewBuilder
    func gapDecorations(_ config: GapDecorationsConfig?,
                        mainHorizontal: Bool) -> some View {
        if let cfg = config, cfg.isActive {
            modifier(GapDecorationsOverlay(config: cfg, mainHorizontal: mainHorizontal))
        } else {
            self
        }
    }
}

/// Resolves the child anchors and overlays the painter.
struct GapDecorationsOverlay: ViewModifier {
    /// The container's resolved gap-decorations state.
    let config: GapDecorationsConfig
    /// True for flex-direction row/row-reverse.
    let mainHorizontal: Bool

    func body(content: Content) -> some View {
        content
            // overlayPreferenceValue sees exactly the preferences raised
            // by `content` — i.e. this container's own children.
            .overlayPreferenceValue(GapItemFramesKey.self) { anchors in
                GeometryReader { proxy in
                    // proxy's space IS the container's content box: the
                    // outer style chain applies padding/border further
                    // out, so (0,0) here is the CSS content origin —
                    // the space every ref coordinate is quoted in.
                    let frames = anchors.keys.sorted().map { proxy[anchors[$0]!] }
                    GapDecorationsPainter(
                        segments: GapDecorationSegments.build(
                            items: frames,
                            contentSize: proxy.size,
                            mainHorizontal: mainHorizontal,
                            config: config))
                }
                // Decorations are not interactive; never steal a tap.
                .allowsHitTesting(false)
            }
            // Stop the item anchors here. Without this reset a decorated
            // ANCESTOR would also receive these frames and group them
            // into phantom lines.
            .preference(key: GapItemFramesKey.self, value: [:])
    }
}

