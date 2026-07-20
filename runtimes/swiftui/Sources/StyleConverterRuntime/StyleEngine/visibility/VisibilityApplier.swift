//
//  VisibilityApplier.swift
//  StyleEngine/visibility — Phase 8.
//
//  Maps the visibility + overflow aggregate onto SwiftUI modifiers.
//    visibility: visible → identity
//    visibility: hidden  → opacity(0) but layout preserved
//    visibility: collapse → frame(0,0) + .hidden() (layout removed)
//    overflow (used value ≠ visible on BOTH axes) → .clipped()
//    overflow (used value ≠ visible on ONE axis) → .clipShape(AxisClipRect)
//      — axis-selective clip, css-overflow-3 §3/§3.1 (wave 18 RC4)
//    overflow scroll|auto → clip only, ScrollView TODO until
//      ComponentRenderer supports scroll containers
//

import SwiftUI

struct VisibilityApplier: ViewModifier {
    let config: VisibilityConfig?

    func body(content: Content) -> some View {
        // Short-circuit identity when not declared.
        guard let cfg = config, cfg.touched else { return AnyView(content) }

        var v: AnyView = AnyView(content)

        // Overflow — axis-selective since wave 18 (RC4). Decisions run on
        // USED values after the css-overflow-3 §3.1 coercion (see
        // OverflowClipRules): every non-visible used value clips its axis
        // (§3 — scroll/auto included: a scroll container always clips).
        // nil = property undeclared → CSS initial `visible`.
        let ox = cfg.overflowX ?? OverflowKind.visible
        let oy = cfg.overflowY ?? OverflowKind.visible
        // Per-axis clip flags from the §3.1-coerced used values.
        let cx = OverflowClipRules.axisClips(OverflowClipRules.usedOverflow(ox, other: oy))
        let cy = OverflowClipRules.axisClips(OverflowClipRules.usedOverflow(oy, other: ox))
        if cx && cy {
            // Both axes clip → SwiftUI's stock rectangle clip is exact.
            v = AnyView(v.clipped())
        } else if cx || cy {
            // Exactly one axis clips (only reachable as visible+clip per
            // §3.1) → rect clip extended past the box on the visible axis
            // so ink spills there but not across the clipped axis
            // (WPT css-overflow clip-003: overflow-x:clip must not clip Y).
            v = AnyView(v.clipShape(AxisClipRect(clipX: cx, clipY: cy)))
        }
        // TODO: wire a real ScrollView once ComponentRenderer can host
        // nested scrollers — scroll/auto currently only clip (via the
        // axisClips branch above), which matches the unscrolled state.

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

    // Wave 18: the old shouldClip/shouldScroll helpers collapsed both
    // axes into one .clipped() (and double-clipped for scroll/auto).
    // Replaced by OverflowClipRules (AxisClipRect.swift), whose pure
    // functions are pinned by unit tests and mirrored on Compose.
}

extension View {
    // Identity when nil / untouched.
    func engineVisibility(_ config: VisibilityConfig?) -> some View {
        modifier(VisibilityApplier(config: config))
    }
}
