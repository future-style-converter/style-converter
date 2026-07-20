//
//  AxisClipRect.swift
//  StyleEngine/visibility — Wave 18 RC4: axis-selective overflow clipping.
//
//  CSS lets the two overflow axes disagree: `overflow-x: clip` with
//  `overflow-y: visible` must clip ink horizontally while letting it
//  spill vertically (css-overflow-3 §3; WPT css-overflow clip-003).
//  SwiftUI's `.clipped()` clips BOTH axes, so single-axis configs go
//  through `.clipShape(AxisClipRect(...))` with the visible axis extended
//  to a large finite bound instead.
//

import SwiftUI

// Pure clip-decision rules, kept signature-parallel with the Compose twin
// (companion functions on scrolling/OverflowConfig.kt) so cross-native
// probes can diff the two implementations directly.
enum OverflowClipRules {

    // css-overflow-3 §3.1 used-value coercion:
    //  - `visible` beside a non-{visible,clip} axis is used as `auto`
    //    (you cannot scroll one axis while the other paints unclipped);
    //  - `clip` beside a non-{visible,clip} axis is used as `hidden`
    //    (clip forbids scrolling, so it hardens to the scrollable clip);
    //  - any pair drawn from {visible, clip} keeps its specified value —
    //    THIS is the single-axis-clip case clip-003 exercises.
    static func usedOverflow(_ axis: OverflowKind, other: OverflowKind) -> OverflowKind {
        // The only partners §3.1 lets escape scroll-container coercion.
        let otherEscapes = (other == .visible || other == .clip)
        // visible must become a scroll container when its partner is one.
        if axis == .visible && !otherEscapes { return .auto }
        // clip cannot pair with a scroll container; it hardens to hidden.
        if axis == .clip && !otherEscapes { return .hidden }
        // Everything else is used as specified.
        return axis
    }

    // css-overflow-3 §3: every used value except `visible` clips painted
    // ink at the box edge — including `scroll`/`auto`, because a scroll
    // container always clips even before any scrolling happens.
    static func axisClips(_ used: OverflowKind) -> Bool { used != .visible }
}

// A rectangle clip that binds only the selected axes: the clipped axis is
// pinned to the box edge, the unclipped axis is extended far past it so
// `.clipShape` (which clips this view's rendering, children included —
// SwiftUI clipShape docs) never bites on that axis. Paint-only, exactly
// like CSS overflow clipping (no layout effect, css-overflow-3 §3).
struct AxisClipRect: Shape {
    // True → bind the horizontal axis to [0, width].
    let clipX: Bool
    // True → bind the vertical axis to [0, height].
    let clipY: Bool

    // How far (pt) the rect extends past the box on an UNCLIPPED axis.
    // Not .greatestFiniteMagnitude: CoreGraphics path math degrades at
    // huge magnitudes (and 2×greatest overflows to inf). 1e6 pt is a
    // large FINITE bound, ~100x any capture surface, and is the pinned
    // cross-native constant (Compose AxisClip.UNCLIPPED_EXTENT matches).
    static let unclippedExtent: CGFloat = 1_000_000

    // Pure geometry, split out so unit tests (and cross-native probes
    // against Compose `AxisClip.clipBounds(clipX, clipY, width, height)`)
    // can pin it without rendering a view.
    static func clipBounds(clipX: Bool, clipY: Bool, width: CGFloat, height: CGFloat) -> CGRect {
        // X axis: box edges when clipping, extended ±extent when not.
        let left = clipX ? 0 : -unclippedExtent
        let right = clipX ? width : width + unclippedExtent
        // Y axis: same rule vertically.
        let top = clipY ? 0 : -unclippedExtent
        let bottom = clipY ? height : height + unclippedExtent
        // CGRect wants origin+size; edges are converted losslessly.
        return CGRect(x: left, y: top, width: right - left, height: bottom - top)
    }

    // Shape conformance: emit the axis rect in the view's coordinate
    // space. `rect` is the proposed frame; offset by its origin so the
    // clip tracks the box wherever SwiftUI places it.
    func path(in rect: CGRect) -> Path {
        // Geometry relative to the box's own origin…
        let b = Self.clipBounds(clipX: clipX, clipY: clipY, width: rect.width, height: rect.height)
        // …then translated into the frame SwiftUI handed us.
        return Path(b.offsetBy(dx: rect.minX, dy: rect.minY))
    }
}
