//
//  DecorationSkipSpaces.swift
//  StyleEngine/typography/decoration — applier campaign wave 47,
//  lane Z7 (css-text-decor skip-spaces).
//
//  BYTE-PARALLEL TWIN of the Compose runtime's
//  runtimes/compose/src/main/java/com/styleconverter/runtime/typography/
//  DecorationSkipSpaces.kt — same spacer set, same split semantics, same
//  pin table (DecorationSkipSpacesTests ↔ DecorationSkipSpacesTest).
//  Change one, change both.
//
//  WHAT THIS IMPLEMENTS — css-text-decor-4 §2.6
//  `text-decoration-skip-spaces`, whose INITIAL value is `start end`:
//  underline / overline / line-through must not be painted over spacers
//  that sit at the START or the END of a line box. Mid-line spacers keep
//  their decoration (that is why WPT text-decoration-skip-spaces-002/003/
//  004 — spacers BETWEEN words — already passed on both natives while
//  -001, whose spacer runs sit at the line edges, painted long spurious
//  underline bands across them on iOS AND Android in the wave-46 gate
//  (iOS 0.934 / Android 0.902 vs web PASS 0.952).
//
//  MEASURED, not assumed (wave-46-final captures vs the frozen Chromium
//  ref tools/wpt/refs/9b54…/…/css-text-decor/text-decoration-skip-
//  spaces-001.png): the ref's blue underline spans ONLY the "ABCDEF"
//  glyph run; the natives painted three full-width bands — one across a
//  spacers-only wrapped line above, one across the trailing spacer run
//  of the text line, one across a spacers-only line below.
//
//  THE SPACER SET — the test's own character inventory is the pin: the
//  WPT source packs U+2000…U+200A (en quad … hair space), U+205F, U+3000,
//  U+1680 OGHAM SPACE MARK, U+00A0 NO-BREAK SPACE and plain U+0020 around
//  "ABCDEF", and Chromium skips ALL of them (the visible dash near "F"
//  in the ref is the ogham mark's own GLYPH ink, which the test's note
//  explicitly allows — not underline). All of those are Unicode general
//  category Zs; we take Zs plus TAB U+0009 (White_Space, decoration-
//  transparent in every engine) plus ZWSP U+200B (zero-width, category
//  Cf — including it keeps an edge run contiguous when a ZWSP sits
//  between two real spacers, and its own advance is zero either way).
//
//  Pure String/Unicode.Scalar code — no CoreGraphics, no view code — so
//  the XCTest suite pins every branch without a raster (the
//  DecorationOps / GreedyLineBreaker test pattern).
//

import Foundation

enum DecorationSkipSpaces {

    /// True for a "spacer" in the css-text-decor-4 §2.6 sense — see the
    /// file header for the exact set and why.
    static func isSpacer(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar.value {
        // TAB — White_Space, never decorated by any engine.
        case 0x0009: return true
        // ZWSP — zero-width (Cf); kept so an edge spacer run stays
        // contiguous across it (its own advance is zero regardless).
        case 0x200B: return true
        default:
            // Unicode general category Zs — the space separators:
            // U+0020, U+00A0 NBSP, U+1680 OGHAM, U+2000…U+200A,
            // U+202F NNBSP, U+205F MMSP, U+3000 IDEOGRAPHIC SPACE.
            return scalar.properties.generalCategory == .spaceSeparator
        }
    }

    /// A grapheme cluster counts as a spacer when EVERY scalar in it is
    /// one (a plain space is single-scalar; a cluster mixing a spacer
    /// with combining marks is visible ink and must keep decoration).
    private static func isSpacerCharacter(_ ch: Character) -> Bool {
        ch.unicodeScalars.allSatisfy(isSpacer)
    }

    /// Split one RENDERED line into (lead, core, trail): `lead` is the
    /// maximal spacer prefix, `trail` the maximal spacer suffix, and
    /// `core` everything between — the extent that keeps its decoration.
    /// A line of nothing but spacers comes back with an EMPTY core (and
    /// the whole line in `lead`): the caller paints no decoration at all
    /// on such a line, exactly like Chromium on -001's wrapped
    /// spacers-only lines.
    static func split(_ line: String)
        -> (lead: Substring, core: Substring, trail: Substring) {
        // First non-spacer character — everything before it is `lead`.
        guard let coreStart = line.firstIndex(where: { !isSpacerCharacter($0) }) else {
            // All spacers (or empty) → empty core, whole line as lead.
            return (line[...], line[line.endIndex...], line[line.endIndex...])
        }
        // Last non-spacer character — everything after it is `trail`.
        // (Force-unwrap is safe: coreStart proved one exists.)
        let coreLast = line.lastIndex(where: { !isSpacerCharacter($0) })!
        let coreEnd = line.index(after: coreLast)
        return (line[..<coreStart], line[coreStart..<coreEnd], line[coreEnd...])
    }
}
