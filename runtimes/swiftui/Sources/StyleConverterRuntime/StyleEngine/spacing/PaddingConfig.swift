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
// the rest of the engine. CoreGraphics supplies CGFloat for the
// containing-block basis accessor on SpacingContext (wave 3).
import CoreGraphics
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

    // Surface dimensions used for vw/vh resolution. Wave 6 (#39): these
    // literals are legacy DEFAULTS only — ComponentRenderer overwrites
    // all three geometry fields from the host-published `styleViewport`
    // Environment channel (StyleViewport.swift) whenever one exists, so
    // the numbers are the HOST's capture/window geometry, not values the
    // runtime made up. The 390×844 defaults keep pure unit tests and
    // channel-less consumers byte-identical to the pre-wave-6 renders.
    var viewportWidth: Double = 390.0
    var viewportHeight: Double = 844.0

    // Wave 6 (#39) — the host-published INITIAL containing block width
    // (StyleViewport.rootContainingBlock): what a root component's
    // percent width resolves against when no ancestor published one.
    // The harness supplies its canvas content width (358); nil keeps
    // the legacy `viewportWidth − 32` capture-canvas arithmetic below.
    var rootContainingBlockPx: Double? = nil

    // Fidelity wave 3 — the parent's CONTENT-BOX width in px, published
    // down the tree by ComponentRenderer via the `containingBlockWidth`
    // environment channel. css-sizing-3 §5.1: percentage inline sizes
    // resolve against the containing block (the parent's content box),
    // NOT the viewport — trees/block-flow B_Stack rendered `width: 75%`
    // of a 280px parent as 75% × canvas instead of 75% × 260. Nil means
    // "no ancestor with a definite width" (root components, or children
    // of fit-content parents whose laid-out width isn't statically
    // knowable) — resolution then falls back to the canvas content
    // width below.
    var containingBlockWidthPx: Double? = nil

    // Resolved percent-width basis: the ancestor-published containing
    // block when known, else the host-published initial containing
    // block (#39 — harness: 358 canvas content width), else the legacy
    // capture-canvas arithmetic (390 − 2×16 padding = 358) so channel-
    // less consumers keep the pre-wave-6 basis.
    var containingBlockWidth: CGFloat {
        CGFloat(containingBlockWidthPx
                ?? rootContainingBlockPx
                ?? (viewportWidth - 32))
    }
}
