//
//  ChUnitMetrics.swift
//  StyleEngine/spacing — wave-18 lane 2 (pin P1): the CSS `ch` unit basis.
//
//  css-values-4 §6.1.1 defines 1ch as the advance measure of the glyph '0'
//  in the element's font, "in the inline axis of the element". The old
//  resolver approximated ch as 1em, so `width: 63.1ch` at 16px monospace
//  produced a ~1010px box instead of the advance-based ~600px (css-overflow
//  block-ellipsis-001). This helper measures the real advance with CoreText
//  against the same font SwiftUI will render the text in, so a ch-sized box
//  fits exactly the character count the browser reference shows.
//
//  Wave 52 lane L8 (vertical-wedges) closed two gaps in that sentence:
//   • M-A — WHICH face. A design-default element (no `font-family`, or a
//     list with no monospace / serif / rounded generic and no document
//     face — `sans-serif`, `Arial`, …) measured the SYSTEM font (SF) while
//     the label paints `.custom("Inter")` for that design. Measured on wave51-fix
//     css-writing-modes/ch-units-vrl-005..008: every 5ch box was 60 px
//     against the ref's 63 (Inter '0' = 12.6 px at 20 px). The default
//     design now measures Inter when it is registered in the process (the
//     harness app bundles it) and keeps SF otherwise (unit-test bundle).
//   • M-B — WHICH axis. Under `vertical-*` + `text-orientation: upright` the
//     '0' stands up and its inline advance is its VERTICAL advance
//     (round(ascent) + round(descent), Chromium's arithmetic: 19 + 5 = 24 at
//     20 px Inter, so 5ch = 120 — the ref's orange square); every other mode
//     keeps the x-advance. The decision is `VerticalInlineAxis
//     .chAdvanceIsVertical`; StyleBuilder passes the answer as the flag.
//
//  Twin of the Compose ChUnitMetrics (Paint.measureText / Paint.FontMetrics
//  on the mapped Typeface); both fall back to nil → 0.5em (the same spec
//  section's mandated assumption) when metrics are unavailable.
//

// CoreText for CTFont glyph advances + ascent/descent; CoreGraphics for
// CGFloat/CGGlyph; Foundation for the NSLock guarding the memo cache.
import CoreGraphics
import CoreText
import Foundation
#if canImport(UIKit)
// UIFont gives us the exact fonts SwiftUI's designs resolve to (SF Mono /
// New York) and the registered Inter face by name; UIFont is toll-free
// bridged to CTFont so the CoreText APIs work on it directly.
import UIKit
#endif

enum ChUnitMetrics {

    // Memoized advances keyed by (face, size, axis). StyleBuilder.build runs
    // per component per recomposition; font creation + glyph lookup is not
    // free, and the corpus uses a handful of face × size combinations. The
    // axis is part of the key because one face at one size has TWO ch
    // advances (M-B).
    private static var cache: [String: Double?] = [:]
    // Plain lock — StyleBuilder may run on multiple render threads.
    private static let lock = NSLock()

    /// The advance of '0' in px for the resolved font at [sizePx] along the
    /// element's inline axis — the x-advance, or with `inlineAxisUpright`
    /// the vertical advance (M-B) — or nil when metrics are unavailable.
    /// Callers must map nil to the 0.5em spec fallback — never to zero.
    /// The three booleans mirror TypographyAggregate's generic-family
    /// flags with FontMod.design(for:)'s precedence (rounded > monospaced
    /// > serif > default) so the measured font is the rendered font.
    /// - Parameter documentFaceName: wave-47 lane Z4 (SEAM 2 of the monospace
    ///   pin) — the platform name of the DOCUMENT @font-face the label will
    ///   actually render with; when set it outranks every generic design,
    ///   exactly as it does at render time. nil keeps the design path.
    /// - Parameter inlineAxisUpright: M-B — true when `ch` measures the
    ///   vertical advance (`VerticalInlineAxis.chAdvanceIsVertical`).
    ///   Defaults to false so every pre-wave-52 caller is byte-identical.
    static func zeroAdvancePx(rounded: Bool, monospaced: Bool,
                              serif: Bool, sizePx: Double,
                              documentFaceName: String? = nil,
                              inlineAxisUpright: Bool = false) -> Double? {
        // Precedence mirror of FontMod.design(for:).
        let design = rounded ? "rounded" : monospaced ? "monospaced"
                   : serif ? "serif" : "default"
        // M-A: the default design keys on the face it MEASURES — "inter"
        // when the registered Inter face answers, "default" (SF) otherwise —
        // so a face change can never be served from the other face's memo.
        let designKey = design == "default" ? defaultMeasuringFaceKey(sizePx: sizePx) : design
        // M-B: the axis suffix keeps the two advances apart in the memo.
        let axis = inlineAxisUpright ? "-v" : ""
        // Document faces key on the registry EPOCH + the face name: the
        // registry re-registers wholesale per document, and two documents may
        // bind one PostScript name to different files — an epoch-less memo
        // could serve document N's advance to document N+1.
        let key = documentFaceName.map {
            "face:\(DocumentFontRegistry.shared.epoch):\($0)-\(sizePx)\(axis)"
        } ?? "\(designKey)-\(sizePx)\(axis)"
        lock.lock()
        if let hit = cache[key] { lock.unlock(); return hit }
        lock.unlock()
        let advance = measure(design: design, sizePx: sizePx,
                              documentFaceName: documentFaceName,
                              inlineAxisUpright: inlineAxisUpright)
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

    /// M-A: the memo key of the DEFAULT design — "inter" when the Inter face
    /// the label paints is registered in this process, else "default" (the
    /// system font, the pre-wave-52 measurement). Internal so the Catalyst
    /// pin can observe which face the default design measures.
    internal static func defaultMeasuringFaceKey(sizePx: Double) -> String {
        interFont(size: CGFloat(sizePx)) == nil ? "default" : "inter"
    }

    /// M-A: the registered Inter face by name — the same lookup the label
    /// bridge makes (`UIFont(name: "Inter", …)` in ComponentRenderer) — or
    /// nil when the process has no Inter (unit-test bundle, product apps
    /// that did not bundle it).
    private static func interFont(size: CGFloat) -> CTFont? {
        #if canImport(UIKit)
        return UIFont(name: "Inter", size: size).map { $0 as CTFont }
        #else
        return nil
        #endif
    }

    // The actual CoreText measurement — isolated so zeroAdvancePx can
    // memoize. Returns nil on any failure (missing glyph, degenerate
    // advance) so the resolver's 0.5em fallback takes over.
    private static func measure(design: String, sizePx: Double,
                                documentFaceName: String? = nil,
                                inlineAxisUpright: Bool = false) -> Double? {
        let size = CGFloat(sizePx)
        // Resolve the CTFont — the document face when one is registered
        // (wave-47 Z4), else the generic design.
        guard let font = resolvedFont(design: design, size: size,
                                      documentFaceName: documentFaceName) else { return nil }
        // M-B: an upright '0' advances by its em box along the vertical
        // inline axis — no glyph lookup involved (Inter has no vmtx).
        if inlineAxisUpright { return verticalAdvance(font) }
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

    /// M-B: the vertical advance of an upright '0' = round(ascent) +
    /// round(descent) — the glyph's em box as Chromium sizes an upright
    /// line (19 + 5 = 24 at 20 px Inter, whose hhea 1984/494 over 2048 give
    /// 19.375 / 4.82). Each half rounds separately, as the ref's 24 (not
    /// 24.2) shows. Internal so the Catalyst pin can call it on a known font.
    internal static func verticalAdvance(_ font: CTFont) -> Double? {
        // `.rounded()` is schoolbook half-away-from-zero — the Kotlin twin's
        // Math.round on these positive halves agrees.
        let ascent = Double(CTFontGetAscent(font)).rounded()
        let descent = Double(CTFontGetDescent(font)).rounded()
        let advance = ascent + descent
        // A broken font (NaN/∞/0) is "unavailable", never a zero box.
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
        // basis and the paint share one set of glyph advances. A name that no
        // longer resolves falls through to the design below.
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
        // Default design — M-A: EVERY design-default label paints
        // `.custom("Inter")` (ComponentRenderer: `fontDesign == .default` →
        // Inter) — face-less, `sans-serif`, or an unlisted name like `Arial`
        // alike — so measure Inter when the process has it; the plain system
        // font (SF) otherwise, exactly as before. (Unlike Compose, iOS has no
        // design-default family that paints the platform face, so the
        // Compose fix-pass `paintsInter` split has no twin to mirror here.)
        default:
            return interFont(size: size) ?? (UIFont.systemFont(ofSize: size) as CTFont)
        }
        #else
        // Non-UIKit fallback (defensive — every supported platform has
        // UIKit under Catalyst): the CoreText system UI font.
        return CTFontCreateUIFontForLanguage(.system, size, nil)
        #endif
    }
}
