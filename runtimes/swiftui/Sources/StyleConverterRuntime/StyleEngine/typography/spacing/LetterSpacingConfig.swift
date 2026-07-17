//
//  LetterSpacingConfig.swift
//  StyleEngine/typography/spacing — Phase 6.
//
//  `letter-spacing` adds a per-glyph tracking offset. `normal` → no
//  override. A length (px/em→resolved-to-px) maps to SwiftUI's
//  `.tracking(_:)`.
//

import CoreGraphics

/// Lane IOS-TEXT fix 3 — a font-relative spacing value the converter
/// could not pre-resolve. The wire carries a BOGUS `px: 0.0` for em/rem
/// letter/word-spacing (LetterSpacingSerializer.kt: `pixels = length.pixels
/// ?? 0.0`) with the true value nested at `original.original.{v,u}` —
/// live-verified on fixtures/fidelity/typography.combos
/// (`{"px":0.0,"original":{"type":"length","original":{"v":0.25,"u":"REM"}}}`).
/// Resolution happens in TypographyExtractor once the element font size
/// is known: em × element font-size, rem × 16 (harness root font-size —
/// the same bases Compose's extractLetterSpacing workaround uses).
struct RelativeFontLength: Equatable {
    /// The declared scalar (the `v` of the nested original).
    var value: CGFloat
    /// true → rem (root-relative, ×16); false → em (element font size).
    var isRem: Bool

    /// Parse the nested relative-length wire shape out of a spacing
    /// property's data blob. Returns nil for real px values (px != 0),
    /// keywords, and non-em/rem units — those keep their existing lanes.
    static func parse(_ data: IRValue) -> RelativeFontLength? {
        guard case .object(let o) = data else { return nil }
        // A genuine non-zero px value is authoritative — only the bogus
        // 0.0 (or an absent px) can hide a relative original underneath.
        if let px = o["px"]?.doubleValue, px != 0 { return nil }
        // Unwrap the once-nested envelope: original.original.{v,u}
        // (the IRPropertySerializer deep-flatten keeps exactly one level).
        guard case .object(let outer)? = o["original"],
              case .object(let inner)? = outer["original"],
              let v = inner["v"]?.doubleValue,
              let u = inner["u"]?.stringValue?.uppercased(),
              u == "EM" || u == "REM",
              v != 0 else { return nil }
        return RelativeFontLength(value: CGFloat(v), isRem: u == "REM")
    }
}

struct LetterSpacingConfig: Equatable {
    /// Absolute tracking in px, when the converter pre-resolved one.
    var px: CGFloat? = nil
    /// Lane IOS-TEXT fix 3 — unresolved em/rem tracking; resolved to px
    /// in TypographyExtractor once the element font size is known.
    var relative: RelativeFontLength? = nil
}
