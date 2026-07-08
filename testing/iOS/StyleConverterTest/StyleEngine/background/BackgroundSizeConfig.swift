//
//  BackgroundSizeConfig.swift
//  StyleEngine/background — Phase 4.
//
//  CSS `background-size` is array-per-layer. Each entry is one of:
//    "auto" / "cover" / "contain"  (bare string)
//    {w: <LengthOrPct>}
//    {w: <LengthOrPct>, h: <LengthOrPct>}
//  Where each dimension is either {px: N} or a bare number (percent).
//

import Foundation

// One length dimension inside the size tuple.
enum BackgroundSizeDim: Equatable {
    // Absolute pixels.
    case px(Double)
    // Percent 0..100. Stored verbatim to match the IR for debuggability.
    case percent(Double)
    // Explicit `auto` — lets the other axis size via aspect ratio.
    case auto
}

// A single layer's size value.
enum BackgroundSizeLayer: Equatable {
    // CSS `auto` — natural size if source known, else stretch.
    case auto
    // CSS `cover` — fill, clip overflow.
    case cover
    // CSS `contain` — fit inside, letterbox if needed.
    case contain
    // Explicit dimensions. Either side may be `.auto` for aspect-ratio
    // resolution.
    case explicit(w: BackgroundSizeDim, h: BackgroundSizeDim)
}

struct BackgroundSizeConfig: Equatable {
    // One entry per background-image layer. SwiftUI support for sizing
    // gradient backgrounds is limited; the applier implements `cover`
    // and `contain` for raster URL layers (future) and a no-op for
    // gradient layers (they already fill naturally).
    var layers: [BackgroundSizeLayer] = []

    // Short-circuit helper.
    var hasAny: Bool { !layers.isEmpty }
}
