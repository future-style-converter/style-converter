//
//  FontFamilyConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  CSS `font-family` is a comma-separated list of quoted names or
//  generic keywords (`serif`, `sans-serif`, `monospace`, `cursive`,
//  `fantasy`, `system-ui`, `ui-serif`, `ui-sans-serif`, `ui-monospace`,
//  `ui-rounded`, `emoji`, `math`, `fangsong`). Parser emits an array of
//  { name } or { keyword } entries; we flatten to a list of strings.
//

// Foundation for Array/String.
import Foundation

/// List of family names in declaration order, plus derived generic flags
/// that let the applier pick a SwiftUI Font design without a name match.
struct FontFamilyConfig: Equatable {
    /// Raw family names as declared (strings only — generic keywords go
    /// into the flags below). Preserved so the applier can try each name
    /// against the installed UIFont set in order.
    var names: [String] = []
    /// wave-46 lane Y7 — the css-fonts-4 §5.2 WALK: every declared entry in
    /// author order, generic keywords INCLUDED. Consumed ONLY by the
    /// document-@font-face lookup (StyleBuilder / TypographyApplier walk it
    /// through DocumentFontRegistry), so a document that registered a face
    /// under a generic's own name — the monospace font-metric pin pilot
    /// (tools/titan/mono-pin.mjs) delivers DejaVu Sans Mono as a face named
    /// `monospace` — is consulted where the generic sits in the list, exactly
    /// as Compose's CssFontFamilyResolver already does. `names` stays
    /// concrete-only: `fontFamilyPrimary` (the `.custom` fallback) must never
    /// be handed a keyword, or `font-family: monospace` would silently render
    /// the system default WITHOUT the monospaced design. A generic a document
    /// did NOT register misses the registry and is skipped (UIFont(name:)
    /// knows no "monospace"), so every face-free document walks exactly as
    /// before; the wave-45 corpus declares one family ("test"), no generics.
    var walk: [String] = []
    /// True when the list contained `monospace` / `ui-monospace`.
    var hasMonospace: Bool = false
    /// True when the list contained `serif` / `ui-serif`.
    var hasSerif: Bool = false
    /// True when the list contained `ui-rounded`.
    var hasRounded: Bool = false
}
