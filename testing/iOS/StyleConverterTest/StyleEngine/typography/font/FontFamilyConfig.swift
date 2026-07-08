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
    /// True when the list contained `monospace` / `ui-monospace`.
    var hasMonospace: Bool = false
    /// True when the list contained `serif` / `ui-serif`.
    var hasSerif: Bool = false
    /// True when the list contained `ui-rounded`.
    var hasRounded: Bool = false
}
