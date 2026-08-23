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
        // wave-46 lane Y7: the chain handed to the walkers is `cfg.walk` —
        // generic keywords INCLUDED in author order — so the document
        // @font-face lookup (StyleBuilder / TypographyApplier consult
        // DocumentFontRegistry per name, wave-35 B2) sees a face registered
        // under a generic's own name where the generic sits. That is how the
        // monospace font-metric pin pilot (tools/titan/mono-pin.mjs) reaches
        // the label. A generic nobody registered misses the registry AND
        // `UIFont(name:)`, so it is skipped exactly as if absent, and
        // `fontFamilyPrimary` above stays concrete-only: the `.custom`
        // fallback is never handed a keyword.
        agg.fontFamilyNames = cfg.walk
        // Fold generic flags OR-wise so multiple triplets (e.g. a later
        // font shorthand) can't accidentally unset a prior flag.
        agg.fontFamilyMonospace = agg.fontFamilyMonospace || cfg.hasMonospace
        agg.fontFamilySerif     = agg.fontFamilySerif     || cfg.hasSerif
        agg.fontFamilyRounded   = agg.fontFamilyRounded   || cfg.hasRounded
        agg.touched = true
    }
}
