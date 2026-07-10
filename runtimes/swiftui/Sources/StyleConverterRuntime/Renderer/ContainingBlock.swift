//
//  ContainingBlock.swift
//  StyleConverterRuntime — fidelity wave 3.
//
//  Environment channel carrying the parent's CONTENT-BOX width down the
//  render tree so children can resolve percentage inline sizes against
//  their actual containing block (css-sizing-3 §5.1 / CSS 2.1 §10.2:
//  "<percentage>: refers to width of containing block").
//
//  Before this channel existed, SizeApplier resolved every percent
//  width against the capture-canvas content width (390 − 32 = 358),
//  which is only correct for ROOT components. trees/block-flow B_Stack
//  (a 280px parent with `width: 100% / 75% / 50%` children) rendered
//  its bars against the canvas basis — all three overflowing the
//  parent's right edge (wave-3 measurement, 0.897 SSIM / 9.7% px).
//
//  ComponentRenderer publishes the value for its children whenever its
//  OWN border-box width is statically definite (explicit px width,
//  percent of an inherited basis, or a stretch-injected width), minus
//  the padding band and painted border widths — the CSS content box the
//  harness's border-box sizing produces. Parents whose width is
//  fit-content/auto publish nil (their laid-out width isn't statically
//  knowable), and children then fall back to the canvas basis exactly
//  as before, so this channel never invents geometry.
//

import SwiftUI

/// Environment key for the parent's content-box width. Nil at the root
/// (the capture canvas itself is the containing block there).
private struct ContainingBlockWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat? = nil
}

extension EnvironmentValues {
    /// Parent-published containing-block content width in px, or nil
    /// when the nearest ancestor's width is not statically definite.
    var containingBlockWidth: CGFloat? {
        get { self[ContainingBlockWidthKey.self] }
        set { self[ContainingBlockWidthKey.self] = newValue }
    }
}
