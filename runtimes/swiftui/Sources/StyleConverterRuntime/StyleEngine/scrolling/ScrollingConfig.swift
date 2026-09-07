//
//  ScrollingConfig.swift
//  StyleEngine/scrolling — Phase 10.
//
//  Captures the non-timeline scrolling family: scroll-behavior, the
//  scroll-snap-* longhands, scroll-margin-* (physical + logical),
//  scroll-padding-* (physical + logical), overscroll-behavior(-x/-y/
//  -block/-inline), scrollbar-{color,gutter,width}, overflow-anchor,
//  overflow-clip-margin, scroll-start(-x/-y/-block/-inline/-target/
//  -target-x/-target-y/-target-block/-target-inline),
//  scroll-marker-group, scroll-target-group. Together these account
//  for ~42 IR property type names — see `ScrollingProperty.names` for
//  the exact list.
//
//  NOTHING in this family reaches a modifier today. The two members with
//  a SwiftUI analogue are scroll-snap-type/-align (`.scrollTargetBehavior
//  (.viewAligned)`, iOS 17+) and overscroll-behavior
//  (`.scrollBounceBehavior(.basedOnSize)`, iOS 16.4+); scrollbar-color/
//  -width are system-rendered with no public API, scroll-margin/-padding
//  have no ScrollView analogue, and overflow-anchor, overflow-clip-margin,
//  scroll-start*, scroll-marker-group and scroll-target-group have none at
//  all. Applying the two expressible ones needs a "this component is the
//  scroll host" signal the SDUI runtime does not carry, so the payload is
//  kept for audit only. Retro P2b (A6#10) deleted the identity
//  `ScrollingApplier` that used to hold these notes — it had no caller.
//

import Foundation

struct ScrollingConfig: Equatable {
    /// Raw string payload per IR property type — preserved so tooling
    /// can dump what the parser saw without re-parsing the IRValue.
    var rawByType: [String: String] = [:]
    /// True when the extractor touched at least one owned property.
    var touched: Bool = false
}
