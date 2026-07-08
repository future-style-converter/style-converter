//
//  PaddingConfig.swift
//  StyleEngine/spacing — Phase 2.
//
//  Canonical CSS-padding config for iOS. Each of the four physical edges
//  carries a full `LengthValue` so we can defer unit-resolution until we
//  know the rendering context (parent width for %, font-size for em/rem,
//  viewport for vw/vh, etc.). Logical edges (block/inline) resolve to
//  physical at extract time assuming LTR top-to-bottom writing mode — the
//  only mode the current iOS renderer supports. Any unit we can't resolve
//  (auto, unknown calc, intrinsic) degrades to zero rather than crashing.
//

// Foundation gives us nothing specific but keeps call-sites uniform with
// the rest of the engine.
import Foundation

// One canonical per-edge record. Values are the raw Phase 1 enum so we
// keep the polymorphic IR shape all the way into the applier.
struct PaddingConfig: Equatable {
    // Each side defaults to `.exact(px: 0)` which renders as no padding.
    var top: LengthValue = .exact(px: 0)
    var right: LengthValue = .exact(px: 0)
    var bottom: LengthValue = .exact(px: 0)
    var left: LengthValue = .exact(px: 0)

    // Convenience: true when at least one side would contribute non-zero
    // space. Used by the applier to short-circuit the modifier chain when
    // padding is entirely absent — keeps release renders cheap.
    var hasAny: Bool {
        !isZero(top) || !isZero(right) || !isZero(bottom) || !isZero(left)
    }

    // Single-source test for "would resolve to exactly zero pixels". Used
    // by `hasAny` above; also called from the applier when it decides to
    // skip attaching a GeometryReader for percent resolution.
    private func isZero(_ v: LengthValue) -> Bool {
        if case .exact(0) = v { return true }
        return false
    }
}

// SpacingContext threads render-time information (currently the resolved
// font-size in pt) from the StyleBuilder down into the appliers. The
// StyleBuilder extracts FontSize first so this value is always populated
// by the time padding/margin resolve em / rem / lh. Default is 16pt per
// the CSS spec root-em fallback.
struct SpacingContext: Equatable {
    // Resolved element-level font size in pt. Root-em is always 16pt on
    // the 390×844 canvas — we intentionally do not propagate inheritance
    // since the renderer already flattens inheritance at convert time.
    var fontSizePx: Double = 16.0

    // Canvas dimensions used for vw/vh resolution. Matches the phone frame
    // in `StyleConverterTestApp.swift`.
    var viewportWidth: Double = 390.0
    var viewportHeight: Double = 844.0
}
