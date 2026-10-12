//
//  ListMarkerBidi.swift
//  StyleEngine/lists — wave 55, lane L5 (U1-ios): the `::marker` is a bidi
//  ISOLATE whose base direction is the ITEM's `direction`, not the marker
//  string's first strong character. iOS-only: Compose already resolves an
//  unspecified paragraph direction from the ltr layout direction
//  (lists/ListMarkerTextStyle.kt `forItem` sets no `textDirection`).
//
//  ## The defect (measured on wave54-final, ink columns of the PNGs)
//  css-counter-styles/counter-suffix rows 3–4 (`list-style-type: hebrew`
//  on an ltr `<ol>`, baked markers `א.` / `ב.`): the ref and Android paint
//  the letter at x46-53 and the period AFTER it at x57; iOS paints the
//  period FIRST (x48) and the letter at x51-57 — `.א foo`. SwiftUI's
//  `Text` picks its paragraph direction from the string's first strong
//  character (UAX #9 P2/P3), and `א` is strong R.
//
//  ## The spec
//  css-lists-3 §3.1.1 (the UA rule `::marker { unicode-bidi: isolate; … }`)
//  makes the marker an isolate, and css-writing-modes-4 §2.2 says an
//  isolate's content "is ordered as if inside an independent paragraph
//  with the base directionality specified by the box's `direction`
//  property" — the item's, here ltr. Its control-code table maps
//  isolate + ltr to LRI U+2066 … PDI U+2069 and isolate + rtl to RLI
//  U+2067 … PDI U+2069. UAX #9 on `א.`: with base L the period (CS → ON by
//  W6) sits between R and eos = L, so N2 gives it L and it follows the
//  letter (the ref); with a first-strong R base N1 makes it R and it
//  precedes the letter (the iOS capture).
//
//  ## Why conditional (blast radius by construction)
//  When the string's natural (P2/P3) direction already equals the item's,
//  the isolate is the identity — P2/P3 pick that same direction — so
//  `painted` returns the string UNCHANGED and the Text is byte-identical:
//  215 of the 217 baked markers in wave54-final and every native-table
//  marker under an ltr item. Census: tools/titan/results/
//  wave55-counter-suffix-rows/census-l5.out.txt (U1 section).
//
//  ## Scope boundary (no silent fallthrough)
//  TODO(wave55-L5): `ComponentRenderer.ownListMarker` (`display: list-item`
//  on a non-`<li>` box, css-lists-3 §3.1) still hands its bare string to
//  `Text` — deliberately NOT wrapped this wave: its corpus reach under an
//  rtl box is 2 boxes in css-cascade/all-prop-001, unmeasured, and that
//  call site is a seam this unit does not need.
//

import Foundation

enum ListMarkerBidi {

    /// LEFT-TO-RIGHT ISOLATE — css-writing-modes-4 §2.2, isolate + ltr.
    static let lri = "\u{2066}"
    /// RIGHT-TO-LEFT ISOLATE — css-writing-modes-4 §2.2, isolate + rtl.
    static let rli = "\u{2067}"
    /// POP DIRECTIONAL ISOLATE — closes either initiator above.
    static let pdi = "\u{2069}"

    /// Is the list ITEM right-to-left? `direction` is "Inherited: yes"
    /// (css-writing-modes-4 §2.1) and rides InheritedText's channel, so the
    /// item's resolved value is its own declaration, else the container's
    /// inheritance-merged one — exactly `InheritedText.merge`'s own-wins
    /// fold, read by the package's one `direction` decoder
    /// (`ListStyleUaRule.isRtl`: anything but the `rtl` keyword is ltr, the
    /// CSS initial value), so the two reads can never disagree.
    ///
    /// - Parameters:
    ///   - own: the item's own declarations (`child.properties`).
    ///   - inherited: the container's inheritable set (`childInherited`).
    static func itemIsRTL(own: [IRProperty], inherited: [IRProperty]) -> Bool {
        // Merge own OVER inherited (own declarations block inheritance).
        ListStyleUaRule.isRtl(InheritedText.merge(own: own, inherited: inherited))
    }

    /// UAX #9 P2/P3: the paragraph level a string would take on its own —
    /// true iff its first STRONG character (skipping isolate runs) is R or
    /// AL. No strong character ⇒ level 0 (ltr), P3's default.
    ///
    /// Swift exposes no Bidi_Class, so this is an approximation stated
    /// here: a scalar is strong iff its general category is a LETTER (or it
    /// is one of the three marks LRM / RLM / ALM), and a strong letter is
    /// R/AL iff it lies in the Unicode DEFAULT R/AL blocks
    /// (DerivedBidiClass.txt `@missing` ranges: U+0590–08FF, FB1D–FDFF,
    /// FE70–FEFF, 10800–10FFF, 1E800–1EFFF). Digits (EN / AN — e.g. the
    /// arabic-indic `١`) and punctuation are not strong, exactly as in P2.
    static func naturalIsRTL(_ s: String) -> Bool {
        // Depth of open isolates: P2 ignores everything between an isolate
        // initiator and its matching PDI (so `painted` is idempotent).
        var isolateDepth = 0
        // Scalar walk: bidi classes are per code point, never per grapheme.
        for scalar in s.unicodeScalars {
            // The value drives both the control checks and the range table.
            let v = scalar.value
            switch v {
            // LRI / RLI / FSI open an isolate (UAX #9 BD8).
            case 0x2066, 0x2067, 0x2068:
                isolateDepth += 1
                continue
            // PDI closes the innermost open isolate; an unmatched PDI is
            // ignored for the walk (it is not strong either way).
            case 0x2069:
                if isolateDepth > 0 { isolateDepth -= 1 }
                continue
            default:
                break
            }
            // Inside an isolate run: invisible to P2.
            if isolateDepth > 0 { continue }
            // LRM is strong L, RLM strong R, ALM strong AL (UAX #9 Table 4).
            if v == 0x200E { return false }
            if v == 0x200F || v == 0x061C { return true }
            // Only letters are strong in this approximation (see the doc).
            guard isLetter(scalar) else { continue }
            // First strong letter decides — P2 stops here.
            return inDefaultRTLBlock(v)
        }
        // No strong character: P3 gives paragraph level 0 (ltr).
        return false
    }

    /// The marker string SwiftUI must paint for an item of the given
    /// direction: unchanged when its natural direction already equals the
    /// item's (the isolate would be the identity), otherwise wrapped in the
    /// §2.2 isolate controls for the item's direction.
    static func painted(_ s: String, itemIsRTL: Bool) -> String {
        // An empty marker is no marker box at all (css-lists-3 §3.1).
        guard !s.isEmpty else { return s }
        // P2/P3 already pick the item's direction: byte-identical Text.
        if naturalIsRTL(s) == itemIsRTL { return s }
        // Isolate + the item's direction (css-writing-modes-4 §2.2 table).
        return (itemIsRTL ? rli : lri) + s + pdi
    }

    /// General category L* — the strong-character stand-in (see above).
    private static func isLetter(_ scalar: Unicode.Scalar) -> Bool {
        // Unicode.Scalar.Properties (Swift 5) carries the UCD category.
        switch scalar.properties.generalCategory {
        // Lu / Ll / Lt / Lm / Lo — every letter class.
        case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter,
             .modifierLetter, .otherLetter:
            return true
        // Marks, digits, punctuation, symbols, separators, controls.
        default:
            return false
        }
    }

    /// DerivedBidiClass.txt's default R / AL ranges (`@missing` lines).
    private static func inDefaultRTLBlock(_ v: UInt32) -> Bool {
        // Hebrew … Arabic Extended-A, Hebrew/Arabic presentation forms,
        // the SMP RTL scripts (Cypriot … ) and the Adlam / Arabic math block.
        switch v {
        case 0x0590...0x08FF, 0xFB1D...0xFDFF, 0xFE70...0xFEFF,
             0x10800...0x10FFF, 0x1E800...0x1EFFF:
            return true
        // Everything else a letter can be is L by default.
        default:
            return false
        }
    }
}
