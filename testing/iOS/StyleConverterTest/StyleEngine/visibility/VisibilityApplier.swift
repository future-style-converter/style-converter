//
//  VisibilityApplier.swift
//  StyleEngine/visibility — Phase 8.
//
//  Maps the visibility + overflow aggregate onto SwiftUI modifiers.
//    visibility: visible → identity
//    visibility: hidden  → opacity(0) but layout preserved
//    visibility: collapse → frame(0,0) + .hidden() (layout removed)
//    overflow hidden|clip → .clipped()
//    overflow scroll|auto → ScrollView wrapping (best-effort; our
//      gallery cells aren't deep enough to benefit, so we just clip
//      and log a TODO until ComponentRenderer supports scroll containers.)
//

import SwiftUI

struct VisibilityApplier: ViewModifier {
    let config: VisibilityConfig?

    func body(content: Content) -> some View {
        // Short-circuit identity when not declared.
        guard let cfg = config, cfg.touched else { return AnyView(content) }

        var v: AnyView = AnyView(content)

        // Overflow — CSS treats hidden + clip identically for our
        // rendering purposes; scroll/auto route through ScrollView when
        // an axis was opted-in. We approximate by clipping when either
        // axis is non-visible, since SwiftUI has no per-axis clip.
        let ox = cfg.overflowX
        let oy = cfg.overflowY
        if shouldClip(ox) || shouldClip(oy) {
            v = AnyView(v.clipped())
        }
        if shouldScroll(ox) || shouldScroll(oy) {
            // TODO: wire a real ScrollView once ComponentRenderer can
            // host nested scrollers. For now we still .clipped() so
            // children don't bleed out of the frame.
            v = AnyView(v.clipped())
        }

        // Visibility last — it should take precedence over clip.
        switch cfg.visibility {
        case .visible, .none:
            // Nothing to do (or visibility never declared → default visible).
            break
        case .hidden:
            // CSS `hidden` preserves layout; `.opacity(0)` matches that.
            v = AnyView(v.opacity(0))
        case .collapse:
            // `collapse` on non-table elements behaves like hidden but
            // also removes layout. Approximate with frame(0,0) + hidden().
            v = AnyView(v.frame(width: 0, height: 0).hidden())
        }

        return v
    }

    // True for overflow values that clip painted content.
    private func shouldClip(_ k: OverflowKind?) -> Bool {
        switch k {
        case .hidden, .clip: return true
        default: return false
        }
    }
    // True for scrollable overflow values.
    private func shouldScroll(_ k: OverflowKind?) -> Bool {
        switch k {
        case .scroll, .auto: return true
        default: return false
        }
    }
}

extension View {
    // Identity when nil / untouched.
    func engineVisibility(_ config: VisibilityConfig?) -> some View {
        modifier(VisibilityApplier(config: config))
    }
}
