//
//  OpacityConfig.swift
//  StyleEngine/color — Phase 4.
//
//  CSS `opacity` has a single numeric value, 0..1 (clamped by the IR).
//  We store it as a Double so the applier can pass it straight into
//  SwiftUI's `.opacity(_:)`. Nil means "no opacity property present" —
//  important because painting `.opacity(1.0)` still allocates a
//  compositing group and fractionally changes rendering.
//

// Foundation for plain Swift types.
import Foundation

// Extractor output. Equatable for ComponentStyle diffing in tests.
struct OpacityConfig: Equatable {
    // Resolved alpha, already clamped 0..1 by the Kotlin side.
    // Nil = "not in the IR", the applier should be a no-op.
    var alpha: Double? = nil

    // Convenience for the applier's fast-path check.
    var hasAny: Bool { alpha != nil }
}
