//
//  TextDecorationLineConfig.swift
//  StyleEngine/typography/decoration — Phase 6.
//
//  CSS `text-decoration-line` (css-text-decor-3 §2.1): `none` or any
//  combination of `underline`, `overline`, `line-through`, `blink`.
//
//  SwiftUI `Text` exposes only `.underline(_:color:)` and
//  `.strikethrough(_:color:)`, so `overline` HAS no built-in — but it is
//  painted all the same, and has been since the wave-5 owned-decoration
//  pass: ComponentRenderer's `.overlay` draws one band per declared line
//  through DecorationColorOps.resolve(wire:underline:overline:lineThrough:)
//  → DecorationOps.styleOps, at Chromium-measured geometry (a first-line
//  overline gets a NEGATIVE y so it hangs above the line box the way
//  Blink's ink-overflow paint does). Retro P2e (finding A6#15) deleted the
//  "overline … captured but not painted" claim that survived here for
//  forty-odd waves; `blink` is the one arm that genuinely has no path.
//

import Foundation

struct TextDecorationLineConfig: Equatable {
    var underline: Bool = false
    // RENDERED — by the owned overlay above, not by a SwiftUI built-in.
    var overline: Bool = false
    var lineThrough: Bool = false
    // Captured, not rendered, and deliberately so: `blink` is the one
    // text-decoration-line keyword with no static rendering at all (CSS 2.1
    // §16.3.1 already let a UA ignore it, and css-text-decor-3 §2.1 keeps it
    // only for legacy parsing), so there is nothing for a still capture to
    // paint. DecorationColorOps.decorationLine answers nil for it, which is
    // why it never reaches the band emitter.
    var blink: Bool = false
}
