//
//  IsolationConfig.swift
//  StyleEngine/performance — Phase 4.
//
//  CSS `isolation: isolate` creates a new stacking / blending context so
//  descendant mix-blend-modes don't reach through to ancestors. In
//  SwiftUI this maps to `.compositingGroup()`. The IR encodes a single
//  bare string ("AUTO" or "ISOLATE").
//

import Foundation

// Two-state enum with an explicit "not present" signal handled via
// Optional in the extractor.
enum IsolationMode: Equatable {
    case auto       // Inherit behaviour — no compositing boundary.
    case isolate    // Force a new compositing group.
}

struct IsolationConfig: Equatable {
    // Parsed mode. Nil at the optional level means "property absent".
    var mode: IsolationMode = .auto
    // True only when the extractor actually saw an `Isolation` entry.
    var hasAny: Bool = false
}
