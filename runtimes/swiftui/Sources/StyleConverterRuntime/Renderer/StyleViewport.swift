//
//  StyleViewport.swift
//  StyleConverterRuntime — wave 6 (issue #39).
//
//  Environment channel supplying the RENDER-SURFACE geometry the style
//  engine resolves vw/vh and root-level percentages against. Before
//  this channel, the 390×844 capture-canvas frame (and its 358px
//  content width) was hardcoded inside SpacingContext — harness
//  geometry baked into the product runtime. Now the HOST publishes it:
//  the capture harness pins its fixed canvas numbers (keeping every
//  committed baseline byte-stable), and a real SDUI app publishes its
//  own window/screen geometry instead of inheriting a phone-sized lie.
//
//  Nothing resets this channel down the tree — viewport geometry is a
//  per-surface constant, unlike the per-level containingBlockWidth.
//

import SwiftUI

/// One render surface's geometry, in CSS pixels (pt on iOS).
// public: host apps (the harness, any SDUI shell) construct and publish
// this via `.environment(\.styleViewport, …)`.
public struct StyleViewport: Equatable {
    /// vw basis — surface width (css-values-4 §6.2: 1vw = 1% of the
    /// viewport width).
    public var width: Double
    /// vh basis — surface height.
    public var height: Double
    /// The initial containing block's usable CONTENT width: what a
    /// ROOT component's percentage width resolves against when no
    /// ancestor published a containing block (css-display / CSS 2.1
    /// §10.1 — the initial containing block). The capture harness
    /// passes its canvas content width (390 − 2×16 = 358); a plain
    /// app usually passes its container width or leaves the default.
    public var rootContainingBlock: Double

    // public: memberwise init for cross-module hosts. The containing-
    // block fallback defaults to the full width (CSS: the initial
    // containing block spans the viewport) unless the host insets it.
    public init(width: Double, height: Double,
                rootContainingBlock: Double? = nil) {
        self.width = width
        self.height = height
        self.rootContainingBlock = rootContainingBlock ?? width
    }
}

/// Environment key. Default nil = "host published nothing" — the
/// renderer then keeps SpacingContext's legacy 390×844/358 defaults so
/// pre-wave-6 consumers render byte-identically without any setup.
private struct StyleViewportKey: EnvironmentKey {
    static let defaultValue: StyleViewport? = nil
}

public extension EnvironmentValues {
    /// The host-published surface geometry (nil when unset — legacy
    /// capture-canvas defaults apply). See StyleViewport above.
    var styleViewport: StyleViewport? {
        get { self[StyleViewportKey.self] }
        set { self[StyleViewportKey.self] = newValue }
    }
}
