//
//  FontFamilyApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  Writes family data into TypographyAggregate. The actual font selection
//  (system design vs. custom font) is deferred to TypographyApplier so it
//  can fuse with FontSize/Weight/Style into a single `.font(...)` call.
//

import Foundation

enum FontFamilyApplier {
    static func contribute(_ cfg: FontFamilyConfig?, into agg: inout TypographyAggregate) {
        // Nothing to contribute when no font-family property was declared.
        guard let cfg = cfg else { return }
        // Prefer the first concrete face name (if any) — the applier tries
        // it against the installed UIFont set at emit time.
        agg.fontFamilyPrimary = cfg.names.first
        // Preserve the full fallback chain so TypographyApplier can walk it
        // in order and pick the first face UIFont(name:) actually resolves.
        // Without this, CSS `font-family: "Missing", "Installed", sans-serif`
        // silently fell back to the system font instead of Installed.
        agg.fontFamilyNames = cfg.names
        // Fold generic flags OR-wise so multiple triplets (e.g. a later
        // font shorthand) can't accidentally unset a prior flag.
        agg.fontFamilyMonospace = agg.fontFamilyMonospace || cfg.hasMonospace
        agg.fontFamilySerif     = agg.fontFamilySerif     || cfg.hasSerif
        agg.fontFamilyRounded   = agg.fontFamilyRounded   || cfg.hasRounded
        agg.touched = true
    }
}
