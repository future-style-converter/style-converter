//
//  UAControlFontMetrics.swift
//  StyleEngine/widgets — wave-22 lane INK, pin A-RC2 (UA-METRIC FONTS).
//
//  WHY THIS FILE EXISTS. Through wave 21 both natives measured UA form
//  control labels with their own Inter face, because Inter is what the
//  composed-WPT ref pins for PROSE (capture-browser-ref.mjs injects
//  `:where(body) { font-family: 'Inter', … }` at zero specificity). But
//  form controls do NOT inherit that: Chromium's html.css gives
//  input/button/select/textarea `font: 13.3333px <UA control face>`, and
//  the UA control face is ARIAL-METRIC (Helvetica's advance set), not
//  Inter. Measured on the oracle — the css-ui appearance-auto-001
//  white-black-ink-font-lh ref, which the WEB capture reproduces at
//  SSIM 1.000 — the `input-text` value ink spans x91..145 (55px) inside
//  the field box at x87: Arial's advance sum for "input-text" at
//  13.3333px is 54.84px, Inter's is 61.43px. Inter therefore
//  over-measured every label-driven box (the ref button box is x29..82 =
//  54px, the natives painted 55) and every downstream atom in the row
//  inherited the error.
//
//  WHAT IT PINS. The Arial/Helvetica ASCII advance table (units per em
//  2048, the standard Helvetica width set that Arial, Liberation Sans
//  and Arimo all share by design) plus the 0.6em monospace advance
//  Chromium's UA textarea font uses. Both natives call THIS table for
//  the pure geometry's `measure` probe, so widget box sizes are now
//  IDENTICAL on Android and iOS by construction. Byte-parallel twin:
//  runtimes/compose .../widgets/UAControlFontMetrics.kt.
//

import Foundation

enum UAControlFontMetrics {

    /// Design units per em of the pinned table (Arial/Helvetica: 2048).
    static let UPM: CGFloat = 2048

    /// Chromium's UA control font-size. html.css resolves `font:
    /// -webkit-small-control` to 13.3333px at the default 16px root (the
    /// classic 13⅓ = 16 × 5/6); the ref's control glyph bands measure to
    /// that size (x-height 6.9px ≈ Arial's 1062/2048 em at 13.3333).
    static let CONTROL_FONT_PX: CGFloat = 13.3333

    /// Advance widths in 2048-unit em for printable ASCII 0x20..0x7E,
    /// read out of Arial's `hmtx` table (macOS /System/Library/Fonts/
    /// Supplemental/Arial.ttf, unitsPerEm 2048). Only the ASCII band is
    /// pinned: every string the css-ui corpus feeds a widget label
    /// ("button", "input-submit", "select-multiple", "Choose File", …) is
    /// ASCII, and a table covering more would be unverifiable against the
    /// ref. Out-of-band scalars take DEFAULT_ADVANCE_2048 below — loudly
    /// documented, never silently zero-width.
    private static let ASCII_ADVANCE_2048: [Int] = [
        569, 569, 727, 1139, 1139, 1821, 1366, 391, 682, 682,       // ' ' ! " # $ % & ' ( )
        797, 1196, 569, 682, 569, 569, 1139, 1139, 1139, 1139,      // * + , - . / 0 1 2 3
        1139, 1139, 1139, 1139, 1139, 1139, 569, 569, 1196, 1196,   // 4 5 6 7 8 9 : ; < =
        1196, 1139, 2079, 1366, 1366, 1479, 1479, 1366, 1251, 1593, // > ? @ A B C D E F G
        1479, 569, 1024, 1366, 1139, 1706, 1479, 1593, 1366, 1593,  // H I J K L M N O P Q
        1479, 1366, 1251, 1479, 1366, 1933, 1366, 1366, 1251, 569,  // R S T U V W X Y Z [
        569, 569, 961, 1139, 682, 1139, 1139, 1024, 1139, 1139,     // \ ] ^ _ ` a b c d e
        569, 1139, 1139, 455, 455, 1024, 455, 1706, 1139, 1139,     // f g h i j k l m n o
        1139, 1139, 682, 1024, 569, 1139, 1024, 1479, 1024, 1024,   // p q r s t u v w x y
        1024, 684, 532, 684, 1196,                                  // z { | } ~
    ]

    /// First code point the table covers (U+0020 SPACE).
    private static let FIRST_CP: Int = 0x20

    /// Fallback advance for anything outside the pinned ASCII band:
    /// Arial's 'n'/'o' advance (1139/2048 ≈ 0.556em), the width the
    /// classic Helvetica set uses for most lowercase. Chosen over 0 so a
    /// non-ASCII label still reserves plausible box width instead of
    /// collapsing the control; a KNOWN stated limit of this pin rather
    /// than a silent fallthrough (no css-ui corpus label reaches it).
    private static let DEFAULT_ADVANCE_2048: Int = 1139

    /// Monospace advance in em. Chromium's UA textarea font is a 0.6em
    /// advance face (Courier New, Menlo, Cousine and Droid Sans Mono all
    /// pin 0.6em); ref-checked on appearance-auto-001, whose textarea
    /// content "textarea" (8 chars) inks x19..81 = 8 × 7.9px against
    /// 8 × 8.0px predicted at 13.3333px.
    static let MONO_ADVANCE_EM: CGFloat = 0.6

    /// One UTF-16 CODE UNIT's advance in CSS px at `sizePx`.
    ///
    /// The unit is UTF-16, not the Swift `Character`, because the Kotlin
    /// twin's `for (ch in text)` walks UTF-16 code units — and this table
    /// exists precisely so that a wire label measures to the SAME box
    /// width on both natives. Walking graphemes here made a non-BMP or
    /// combining label measure one fallback advance where Kotlin took
    /// two (a surrogate-pair label came out 7.4px narrower on iOS, and
    /// every atom after it in the inline row inherited the shift).
    /// Surrogate halves are outside the pinned band and take the
    /// documented fallback, exactly as they do on the Kotlin side.
    static func charAdvance(_ unit: UInt16, _ sizePx: CGFloat) -> CGFloat {
        // Index into the ASCII band; anything else takes the fallback.
        let i = Int(unit) - FIRST_CP
        let units = (i >= 0 && i < ASCII_ADVANCE_2048.count) ? ASCII_ADVANCE_2048[i]
                                                            : DEFAULT_ADVANCE_2048
        return CGFloat(units) * sizePx / UPM
    }

    /// Convenience lookup for a single `Character` (the boundary probes
    /// and any caller holding a grapheme). A multi-unit grapheme cannot
    /// index a per-code-unit table, so it takes ONE fallback here; sums
    /// over a whole string must go through `advance` below, which walks
    /// code units and therefore stays in lockstep with Kotlin.
    static func charAdvance(_ ch: Character, _ sizePx: CGFloat) -> CGFloat {
        guard let scalar = ch.unicodeScalars.first,
              ch.unicodeScalars.count == 1, scalar.value <= 0xFFFF else {
            return CGFloat(DEFAULT_ADVANCE_2048) * sizePx / UPM
        }
        // Index into the ASCII band; anything else takes the fallback.
        let i = Int(scalar.value) - FIRST_CP
        let units = (i >= 0 && i < ASCII_ADVANCE_2048.count) ? ASCII_ADVANCE_2048[i]
                                                            : DEFAULT_ADVANCE_2048
        // Design units → CSS px. No hinting/rounding: Chromium lays text
        // out in fractional units and only snaps the painted box.
        return CGFloat(units) * sizePx / UPM
    }

    /// Advance width of a whole label in the UA control face. Kerning is
    /// deliberately ignored: Arial/Helvetica apply no kern pairs to the
    /// corpus' label strings, and the ref widths reproduce to <0.1px from
    /// the plain advance sum (see the header's 54.84 vs 55 check).
    static func advance(_ text: String, _ sizePx: CGFloat) -> CGFloat {
        // Straight sum over UTF-16 CODE UNITS — the same unit, in the
        // same order, as the Kotlin twin's `for (ch in text)`.
        var total: CGFloat = 0
        for unit in text.utf16 { total += charAdvance(unit, sizePx) }
        return total
    }

    /// Monospace advance of a whole label (textarea content, 0.6em/char).
    /// Counted in UTF-16 code units for the same cross-native reason as
    /// `advance` — Kotlin's `String.length` is a UTF-16 count, so
    /// `text.count` (graphemes) would under-measure the same label.
    static func monoAdvance(_ text: String, _ sizePx: CGFloat) -> CGFloat {
        CGFloat(text.utf16.count) * MONO_ADVANCE_EM * sizePx
    }
}
