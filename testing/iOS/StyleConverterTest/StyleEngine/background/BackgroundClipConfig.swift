//
//  BackgroundClipConfig.swift
//  StyleEngine/background — Phase 4.
//
//  CSS `background-clip` sets where the background extends to:
//    border-box   → default; extends under borders.
//    padding-box  → clips to the inside of the border.
//    content-box  → clips further to the padding edge.
//    text         → masks the background to the text glyphs (complex).
//  IR shape: an array of UPPERCASE strings, one per layer. We collapse
//  to the first layer because SwiftUI clips the whole view uniformly —
//  per-layer clip masks would require the applier to become an ordered
//  foreach, which the gradient-rendering path would have to replicate.
//

import Foundation

// Final enum — normalised from the IR's UPPERCASE strings.
enum BackgroundClipMode: Equatable {
    // Default — no extra clip needed.
    case borderBox
    // Clip to padding edge.
    case paddingBox
    // Clip to content edge.
    case contentBox
    // Mask to text. Recognised but stubbed at render time.
    case text
}

struct BackgroundClipConfig: Equatable {
    // First layer's mode; see file header for the single-mode rationale.
    var mode: BackgroundClipMode = .borderBox
    // Raw array captured for diagnostics / future per-layer support.
    var layers: [BackgroundClipMode] = []
    // True when the IR actually specified a clip mode.
    var hasAny: Bool = false
}
