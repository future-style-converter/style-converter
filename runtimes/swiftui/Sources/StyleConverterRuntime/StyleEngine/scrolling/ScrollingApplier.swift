//
//  ScrollingApplier.swift
//  StyleEngine/scrolling — Phase 10.
//
//  Identity applier for the Phase 10 non-timeline scrolling family.
//  SwiftUI has a tiny, targeted scrolling-modifier vocabulary:
//
//  • scroll-snap-type / -align   → `.scrollTargetBehavior(.viewAligned)`
//    (iOS 17+). Honest best-effort; snap-stop is not expressible.
//  • overscroll-behavior         → `.scrollBounceBehavior(.basedOnSize)`
//    (iOS 16.4+). Only `auto|contain|none` have analogs.
//  • scrollbar-color / -width    → no public API; system-rendered only.
//  • scroll-margin / -padding    → no analog on SwiftUI ScrollView; the
//    programmatic `.scrollTargetLayout()` positions snap points but
//    doesn't take per-item margins.
//  • overflow-anchor, overflow-clip-margin, scroll-start(-target)(-x/-y/
//    -block/-inline), scroll-marker-group, scroll-target-group — no
//    SwiftUI analog.
//
//  Rather than partially-apply a couple of modifiers on the wrong view
//  level (the SDUI runtime doesn't know which ancestor hosts the
//  scrolling primitive), Phase 10 keeps this an identity contribution
//  and records the parsed payload on the Config for audit. A later
//  phase that introduces a ScrollingHostView wrapper can consume
//  `ScrollingConfig.rawByType` and pipe the two SwiftUI-capable
//  keywords through.
//

import Foundation

enum ScrollingApplier {
    /// Identity. The Phase 9 `ScrollTimelineApplier` is unchanged and
    /// continues to own the 3 scroll-timeline longhands.
    ///
    /// TODO(phase-11): wire snap-type/align and overscroll-behavior to
    /// the Phase-7 scroll container in ComponentRenderer once the SDUI
    /// runtime exposes a "this component is the scroll host" signal.
    static func contribute(_ cfg: ScrollingConfig?) {
        _ = cfg
    }
}
