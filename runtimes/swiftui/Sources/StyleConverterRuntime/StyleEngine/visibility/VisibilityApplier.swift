//
//  VisibilityApplier.swift
//  StyleEngine/visibility — Phase 8.
//
//  Maps the visibility + overflow aggregate onto SwiftUI modifiers.
//    visibility: visible → identity
//    visibility: hidden  → opacity(0) but layout preserved
//    visibility: collapse → table row/column: frame(0,0) + .hidden()
//                           (layout removed, CSS 2.2 §11.2); every other
//                           box: treated as `hidden` (layout PRESERVED)
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
        //
        // Wave 50 lane B11: the branch is now the shared rule table
        // (VisibilityBoxRules.treatment — iOS-only: the Compose twin is
        // DEFERRED, see that file's TWIN STATUS banner) rather than a
        // per-keyword switch, because CSS 2.2 §11.2's `collapse` depends on
        // the BOX TYPE and the old code got the common case backwards — it
        // removed the layout of every collapsed element, table track or
        // not, while §11.2 says "for other elements, `collapse` is treated
        // the same as `hidden`" (layout preserved). Compose folds collapse
        // into its `isHidden` alpha, so iOS was the divergent twin.
        // `if let` rather than `switch cfg.visibility` on the Optional: the
        // extractor's own comment warns about the `.none` pitfall, and this
        // spelling cannot trip over it. A nil means `visibility` was never
        // declared on this component — it may still be hidden by
        // INHERITANCE from an ancestor, but that channel does not exist yet
        // on either native (this lane's seam patch; the ancestor currently
        // hides the whole subtree with one opacity layer).
        if let declared = cfg.visibility {
            // A declared keyword: ask the rule table what the box does.
            switch VisibilityBoxRules.treatment(
                declared, isTableTrackBox: cfg.isTableTrackBox
            ) {
            case .painted:
                // `visibility: visible` — identity.
                break
            case .inkSuppressed:
                // §11.2 "the generated box still affects layout": the box
                // must keep its slot, so hide ink WITHOUT touching the
                // frame. `.opacity(0)` is the SwiftUI spelling of that.
                //
                // KNOWN GAP, not a silent one: opacity is a subtree layer,
                // so a descendant that declares `visibility: visible`
                // cannot paint through it (§11.2 says it must). Measured on
                // css-view-transitions/capture-with-visibility-mixed-
                // descendants — see VisibilityBoxRules.swift's header and
                // `subtreeAlphaEquivalent`, which is the predicate the
                // renderer seam will consult.
                v = AnyView(v.opacity(0))
            case .boxRemoved:
                // §11.2 on a table row / row group / column / column group:
                // the track is removed "as if display: none were applied",
                // without re-laying the rest of the table. frame(0,0) +
                // .hidden() is the closest SwiftUI equivalent — it is what
                // this applier used to do for EVERY collapsed element.
                v = AnyView(v.frame(width: 0, height: 0).hidden())
            }
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
