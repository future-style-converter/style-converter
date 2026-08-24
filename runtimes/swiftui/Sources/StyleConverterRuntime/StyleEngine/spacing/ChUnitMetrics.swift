//
//  ChUnitMetrics.swift
//  StyleEngine/spacing — wave-18 lane 2 (pin P1): the CSS `ch` unit basis.
//
//  css-values-4 §6.1.3 defines 1ch as the advance width of the glyph '0'
//  in the element's font. The old resolver approximated ch as 1em, so
//  `width: 63.1ch` at 16px monospace produced a ~1010px box instead of the
//  ~advance-based ~600px (css-overflow block-ellipsis-001: wrong box width
//  AND wrong text wrap points). This helper measures the real advance with
//  CoreText against the same font design SwiftUI will render the text in
//  (FontMod maps CSS generic families onto system designs), so a ch-sized
//  box fits exactly the character count the browser reference shows.
//
//  Twin of the Compose ChUnitMetrics (Paint.measureText on the mapped
//  Typeface); both fall back to nil → 0.5em (the same spec section's
//  mandated assumption) when metrics are unavailable.
//

// CoreText for CTFont glyph advances; CoreGraphics for CGFloat/CGGlyph;
// Foundation for the NSLock guarding the memoization cache.
import CoreGraphics
import CoreText
import Foundation
#if canImport(UIKit)
// UIFont gives us the exact system fonts SwiftUI's .monospaced/.serif
// designs resolve to (SF Mono / New York); UIFont is toll-free bridged to
// CTFont so the CoreText advance APIs work on it directly.
import UIKit
#endif

enum ChUnitMetrics {

    // Memoized advances keyed by (design, size). StyleBuilder.build runs
    // per component per recomposition; font creation + glyph lookup is not
    // free, and the corpus uses a handful of design × size combinations.
    private static var cache: [String: Double?] = [:]
    // Plain lock — StyleBuilder may run on multiple render threads.
    private static let lock = NSLock()

    /// The advance width of '0' in px for the resolved font at [sizePx], or
    /// nil when metrics are unavailable. Callers must map nil to the 0.5em
    /// spec fallback — never to zero.
    /// The three booleans mirror TypographyAggregate's generic-family
    /// flags with FontMod.design(for:)'s precedence (rounded > monospaced
    /// > serif > default) so the measured font is the rendered font.
    /// - Parameter documentFaceName: wave-47 lane Z4 (SEAM 2 of the monospace
    ///   pin) — the platform name of the DOCUMENT @font-face the label will
    ///   actually render with (StyleBuilder's css-fonts-4 §5.2 registry walk,
    ///   the same one that fills `TextConfig.fontFaceName`). When set it
    ///   outranks every generic design, exactly as it does at render time:
    ///   wave-46 Y7 measured the old design-only basis keeping a pinned
    ///   `width: 10ch` box at SF Mono's 202px while the frozen Menlo ref —
    ///   and the painted DejaVu glyphs — advance to 197px. nil (the universal
    ///   face-free document) keeps the pre-wave-47 path byte-identical.
    static func zeroAdvancePx(rounded: Bool, monospaced: Bool,
                              serif: Bool, sizePx: Double,
                              documentFaceName: String? = nil) -> Double? {
        // Precedence mirror of FontMod.design(for:).
        let design = rounded ? "rounded" : monospaced ? "monospaced"
                   : serif ? "serif" : "default"
        // Document faces key on the registry EPOCH + the face name: the
        // registry re-registers wholesale per document, and two documents may
        // bind one PostScript name to different files — an epoch-less memo
        // could serve document N's advance to document N+1.
        let key = documentFaceName.map {
            "face:\(DocumentFontRegistry.shared.epoch):\($0)-\(sizePx)"
        } ?? "\(design)-\(sizePx)"
        lock.lock()
        if let hit = cache[key] { lock.unlock(); return hit }
        lock.unlock()
        let advance = measure(design: design, sizePx: sizePx,
                              documentFaceName: documentFaceName)
        lock.lock()
        cache[key] = advance
        lock.unlock()
        return advance
    }

    /// True when any of [values] carries a ch-unit length — the gate
    /// StyleBuilder uses so the CoreText measurement only runs for styles
    /// that will actually consume it (zero overhead on the rest of the
    /// corpus). Twin of Compose's usesChUnit().
    static func usesCh(_ values: [LengthValue?]) -> Bool {
        values.contains { v in
            if case .relative(_, .ch, _) = v { return true }
            return false
        }
    }

    // The actual CoreText measurement — isolated so zeroAdvancePx can
    // memoize. Returns nil on any failure (missing glyph, degenerate
    // advance) so the resolver's 0.5em fallback takes over.
    private static func measure(design: String, sizePx: Double,
                                documentFaceName: String? = nil) -> Double? {
        let size = CGFloat(sizePx)
        // Resolve the CTFont — the document face when one is registered
        // (wave-47 Z4), else the generic design.
        guard let font = resolvedFont(design: design, size: size,
                                      documentFaceName: documentFaceName) else { return nil }
        // Glyph for U+0030 DIGIT ZERO — the css-values-4 measuring glyph.
        var chars: [UniChar] = [0x30]
        var glyphs: [CGGlyph] = [0]
        guard CTFontGetGlyphsForCharacters(font, &chars, &glyphs, 1),
              glyphs[0] != 0 else { return nil }
        // CTFontGetAdvancesForGlyphs returns the summed advance (Double) —
        // for one glyph that IS the advance measure the spec asks for.
        let advance = CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, nil, 1)
        // Degenerate/zero advances mean a broken font — report unavailable.
        return advance.isFinite && advance > 0 ? advance : nil
    }

    // Map the resolved family onto the font SwiftUI renders with: the
    // document @font-face when one is registered, else the generic design.
    private static func resolvedFont(design: String, size: CGFloat,
                                     documentFaceName: String? = nil) -> CTFont? {
        #if canImport(UIKit)
        // wave-47 lane Z4 (SEAM 2): the document face FIRST — the same
        // precedence PlaceholderLabel's `font` gives `fontFaceName` (a
        // declared face outranks Inter and every system design), so the ch
        // basis and the paint share one set of glyph advances. UIFont(name:)
        // resolves the PostScript name CoreText reported at registration
        // (DocumentFontRegistry.platformFontName); a name that no longer
        // resolves (cleared registry, stale config) falls through to the
        // design below — the exact pre-wave-47 answer, never nil-by-surprise.
        if let name = documentFaceName, let f = UIFont(name: name, size: size) {
            return f as CTFont
        }
        // UIFont is toll-free bridged to CTFont, so the explicit `as`
        // casts below hand the exact rendered font to the CoreText APIs.
        switch design {
        // SwiftUI .monospaced = the system monospaced font (SF Mono) —
        // exactly what UIFont.monospacedSystemFont resolves to.
        case "monospaced":
            return UIFont.monospacedSystemFont(ofSize: size, weight: .regular) as CTFont
        // .serif / .rounded ride the system font's design descriptor
        // (New York / SF Rounded); fall back to the plain system font
        // when the design isn't available on this OS.
        case "serif", "rounded":
            let base = UIFont.systemFont(ofSize: size)
            let d: UIFontDescriptor.SystemDesign = design == "serif" ? .serif : .rounded
            if let desc = base.fontDescriptor.withDesign(d) {
                return UIFont(descriptor: desc, size: size) as CTFont
            }
            return base as CTFont
        // Default design: the plain system font (SF).
        default:
            return UIFont.systemFont(ofSize: size) as CTFont
        }
        #else
        // Non-UIKit fallback (defensive — every supported platform has
        // UIKit under Catalyst): the CoreText system UI font.
        return CTFontCreateUIFontForLanguage(.system, size, nil)
        #endif
    }
}
