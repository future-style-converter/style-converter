//
//  ScriptRunSegmenter.swift
//  StyleEngine/typography/font — wave 34, lane F1.
//
//  PER-SCRIPT RUN SEGMENTATION — the layout-level substitute for the
//  per-character font fallback neither native runtime has (the machinery
//  item (e) of the Rule-43 banner in tools/titan/wpt-not-applicable.mjs).
//
//  ## The problem this solves
//  css-fonts-4 §5.2 matches a font stack PER CHARACTER: the UA walks the
//  family list for every codepoint and uses the first family that can
//  supply a glyph for it. Chromium implements that by shaping the whole
//  run with family #1 and re-shaping the `.notdef` sub-runs with the next
//  family. SwiftUI's `.custom(name:size:)` instead picks ONE face for a
//  whole `Text` and hands everything it cannot draw to CoreText's opaque
//  default cascade (there is no `kCTFontCascadeListAttribute` hook on
//  `Font`), and Compose has the same shape of gap. So a WPT document
//  painting Armenian / Arabic-Indic / Bengali / Khmer / Hebrew text
//  resolved Apple's system faces on the simulator and the emulator's Noto
//  subset on Android, while the browser-ref resolved Chromium-on-macOS's
//  own pick — different advances, different wrap points, and a native
//  SSIM bounded by typography rather than by anything the runtimes compute.
//
//  This file is the mechanism: split the string into SCRIPT RUNS, and let
//  the caller install a bundled face per run (ScriptFallbackFonts). No
//  blocked API is involved — per-run `.font` attributes on an
//  AttributedString are ordinary SwiftUI text layout.
//
//  ## The rule (and where it deviates from the wave-31 sketch, with the
//  measurement that forced the deviation)
//
//   1. A scalar inside one of the five TARGET script blocks below resolves
//      to that script. Inter — the face pinned end-to-end by the harness
//      (the iOS registered "Inter", Compose's InterFontFamily, the web
//      @font-face, capture-browser-ref.mjs REF_FONT_STACK) — covers Latin,
//      Greek and Cyrillic and NOTHING of these five (cmap-verified against
//      apps/web-harness/public/fonts/Inter-Regular.ttf: U+0531, U+0561,
//      U+0660, U+060C, U+0640, U+09E6, U+05D0, U+17E0, U+17D4 all absent),
//      so every one of them is a genuine §5.2 fallback position.
//   2. EVERY other scalar — Latin, Greek, Cyrillic, ASCII punctuation and
//      digits, spaces, and every other Common/Inherited character —
//      resolves to `.default`, i.e. keeps the primary face.
//   3. Common/Inherited characters therefore attach to the preceding run
//      ONLY when the primary face cannot supply them. Within these five
//      scripts that set is exactly the script-neutral characters that LIVE
//      INSIDE the target blocks — U+060C ARABIC COMMA, U+061B ARABIC
//      SEMICOLON, U+061F ARABIC QUESTION MARK, U+0640 ARABIC TATWEEL,
//      U+17D4…U+17DA KHMER punctuation, and every combining mark in the
//      Arabic / Bengali / Hebrew / Khmer blocks — and the block ranges
//      below already assign them. So rule 3 is realised BY rule 1 and
//      needs no separate pass.
//
//  The wave-31 sketch said "keep Common/Inherited with the PRECEDING run"
//  unconditionally. MEASURED (fontTools cmap dump of the five bundled
//  faces): NotoSansArmenian, NotoSansBengali and NotoSansHebrew carry NO
//  U+002E FULL STOP. The css-counter-styles markers this lane exists to
//  fix are spelled `Ժ.` / `ԺԱ.` / `০.` — marker glyphs plus a period. The
//  unconditional rule would have handed that period to a face with no
//  glyph for it, producing `.notdef` tofu or an unpredictable platform
//  cascade, where Chromium (shape-then-refallback) keeps it in Inter.
//  Rule 2 is both the ref-parity choice and the only one that cannot tofu.
//
//  ## Honest scope
//  The classifier is a BLOCK table, not a UAX #24 `Script` property
//  lookup. That is a deliberate approximation: it is exact for the five
//  scripts the Rule-43 corpus actually exercises, and any scalar it does
//  not know answers `.default` — i.e. the pre-lane behaviour, never worse.
//  It is NOT a general script-itemiser and must not be sold as one.
//
//  Twin: runtimes/compose/…/typography/font/ScriptRunSegmenter.kt (same
//  tables, same rule, same pin names).
//

import Foundation

public enum ScriptRunSegmenter {

    /// The five scripts with a bundled fallback face, plus the primary.
    public enum TextScript: Equatable {
        case `default`, arabic, armenian, bengali, hebrew, khmer
    }

    /// One maximal span of scalars resolving to the same script.
    /// `start`/`end` are UTF-16 offsets into the source string (half-open),
    /// because that is the index space both `NSRange` and
    /// `AttributedString`'s UTF-16-backed conversions speak.
    public struct Run: Equatable {
        public let start: Int
        public let end: Int
        public let script: TextScript
        public init(start: Int, end: Int, script: TextScript) {
            self.start = start
            self.end = end
            self.script = script
        }
    }

    /// The script of ONE Unicode scalar under the block table above.
    ///
    /// Ranges are the Unicode blocks (Unicode 15.1 `Blocks.txt`) trimmed of
    /// the one position whose block membership would mis-assign it:
    /// U+FEFF ZERO WIDTH NO-BREAK SPACE sits at the top of the Arabic
    /// Presentation Forms-B block but is Script=Common — a document-order
    /// BOM must NOT drag the following Latin text onto the Arabic face, so
    /// the range stops at U+FEFE.
    public static func script(of cp: UInt32) -> TextScript {
        switch cp {
        // ── Arabic (css-counter-styles-3 §6 `arabic-indic` digits
        // U+0660–U+0669 and `persian` U+06F0–U+06F9 both live here), plus
        // the supplement / extended / presentation-form blocks a shaped
        // Arabic run can reach. The block-internal NEUTRALS (U+060C, U+061B,
        // U+061F, U+0640 TATWEEL) are deliberately INSIDE this range — see
        // rule 3 in the file banner.
        case 0x0600...0x06FF, 0x0750...0x077F, 0x08A0...0x08FF,
             0xFB50...0xFDFF, 0xFE70...0xFEFE:
            return .arabic
        // ── Armenian. U+0531–U+058A are the letters/ligatures the §6.2
        // additive `armenian` / `upper-armenian` / `lower-armenian` systems
        // spell their ordinals with; U+058D–U+058F are Armenian symbols.
        case 0x0530...0x058F, 0xFB13...0xFB17:
            return .armenian
        // ── Hebrew (the §6.2 additive `hebrew` system + the literal-ink
        // Hebrew documents the Rule-43 banner's item (a) enumerates), plus
        // the Alphabetic Presentation Forms Hebrew sub-range.
        case 0x0590...0x05FF, 0xFB1D...0xFB4F:
            return .hebrew
        // ── Bengali (§6.2 simple-numeric `bengali`, digits U+09E6–U+09EF).
        case 0x0980...0x09FF:
            return .bengali
        // ── Khmer (§6.2 `khmer`/`cambodian`, digits U+17E0–U+17E9). The
        // Khmer Symbols block carries the lunar-date signs; both are in the
        // bundled face.
        case 0x1780...0x17FF, 0x19E0...0x19FF:
            return .khmer
        // Everything else — Latin, Greek, Cyrillic, ASCII, general
        // punctuation, CJK, emoji — keeps the primary face. Rule 2.
        default:
            return .default
        }
    }

    /// Split `text` into maximal same-script runs, iterating by SCALAR so a
    /// surrogate pair is classified once and never severed between its two
    /// UTF-16 units (a split there would hand half a character to a
    /// different `.font` attribute and render two replacement glyphs).
    ///
    /// Returns a single `.default` run for the empty string — callers treat
    /// "one default run" as "nothing to do" and skip the rebuild entirely,
    /// which is what keeps every non-target-script capture byte-identical.
    public static func segment(_ text: String) -> [Run] {
        if text.isEmpty { return [Run(start: 0, end: 0, script: .default)] }
        var runs: [Run] = []
        var runStart = 0                 // UTF-16 offset of the open run
        var runScript: TextScript? = nil // nil until the first scalar
        var offset = 0                   // UTF-16 offset of the cursor
        for scalar in text.unicodeScalars {
            let width = UTF16.width(scalar)   // 2 for a non-BMP scalar
            let s = script(of: scalar.value)
            if runScript == nil {
                runScript = s
            } else if s != runScript! {
                // Script changed → close the open run at THIS scalar's
                // start and open a new one. No merging of adjacent equal
                // runs is needed: they cannot occur by construction.
                runs.append(Run(start: runStart, end: offset, script: runScript!))
                runStart = offset
                runScript = s
            }
            offset += width
        }
        runs.append(Run(start: runStart, end: offset, script: runScript ?? .default))
        return runs
    }

    /// Does `text` contain any scalar that needs a bundled fallback face?
    /// The cheap pre-check every call site runs first: false ⇒ the caller
    /// returns its input untouched, so a Latin-only document takes exactly
    /// the pre-lane code path and cannot move a single pixel.
    public static func needsFallback(_ text: String) -> Bool {
        for scalar in text.unicodeScalars where script(of: scalar.value) != .default {
            return true
        }
        return false
    }
}
