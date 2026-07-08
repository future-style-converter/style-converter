//
//  FontSizeConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  CSS `font-size` carries several flavours: absolute keywords
//  (`x-small`..`xx-large`), relative keywords (`smaller`/`larger`),
//  a length (px/pt/em/rem/%), or a `calc()` expression. The parser
//  pre-resolves absolute keywords to a numeric pt value and drops
//  unresolvable forms (`em`/`%`/`calc()` with variables) to nil — so
//  on the iOS side we only see a plain `{ "px": N }` blob.
//
//  See `src/main/kotlin/app/parsing/css/properties/longhands/typography/
//  FontSizePropertyParser.kt` for the full grammar.
//

// CoreGraphics for CGFloat — Config structs stay SwiftUI-free so tests
// don't need a UI environment.
import CoreGraphics

/// Extracted font-size, in points. `nil` means the CSS value couldn't be
/// pre-resolved (em / % / calc with var()), in which case the applier
/// leaves the font size unchanged.
struct FontSizeConfig: Equatable {
    /// Font size in SwiftUI points (== CSS px). Defaults to nil → inherit.
    var px: CGFloat? = nil
}
