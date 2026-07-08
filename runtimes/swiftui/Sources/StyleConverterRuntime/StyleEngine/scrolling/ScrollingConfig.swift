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
//  The applier best-efforts the small subset SwiftUI can express
//  (scroll-snap-type / -align via `.scrollTargetBehavior`, overscroll
//  via `.scrollBounceBehavior`). Everything else is stored for audit
//  only; see the per-case TODO notes in ScrollingApplier.swift.
//

import Foundation

struct ScrollingConfig: Equatable {
    /// Raw string payload per IR property type — preserved so tooling
    /// can dump what the parser saw without re-parsing the IRValue.
    var rawByType: [String: String] = [:]
    /// True when the extractor touched at least one owned property.
    var touched: Bool = false
}
