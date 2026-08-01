//
//  DecorationWire.swift
//  StyleEngine/typography/decoration — applier campaign wave 22,
//  lane DECOR.
//
//  The `meta.decorations` WIRE → paint-request bridge.
//
//  BYTE-PARALLEL TWIN of the Compose runtime's
//  runtimes/compose/src/main/java/com/styleconverter/runtime/typography/
//  DecorationWire.kt — same filter order, same drop rules, same
//  currentColor fallback, same tracker key. Change one, change both.
//
//  WHY A SEPARATE FILE: DecorationColorOps is deliberately
//  dependency-free (no IR types, no SwiftUI) so the XCTest suite pins it
//  without a raster. This bridge is where the two dependencies it refuses
//  meet: the decoded IR model (`IRDecoration`) on one side and the
//  runtime's CSS colour-token parser on the other. Keeping it here means
//  BOTH PlaceholderLabel construction sites share one conversion.
//
//  WHY THE COLOUR IS RESOLVED HERE AND NOT IN THE CONVERTER:
//  `meta.decorations` carries the colour token AS AUTHORED ("blue",
//  "#00f") — see schema/spec/04-metadata-fields.md. `meta` members are
//  extractor-owned payloads the converter forwards verbatim (the wave-20
//  `meta.attrs` precedent); normalizing this one would make the converter
//  interpret a hint it is contractually opaque to. Every runtime already
//  owns a token parser for exactly this shape — the one that reads
//  substituted `var()` values — so resolution lands there, ONCE.
//

import Foundation

enum DecorationWire {

    /// The wire list → the ordered request list `DecorationColorOps.resolve`
    /// consumes. ORDER IS PRESERVED (outermost-first, css-text-decor-3
    /// §2.1) because paint order is the whole point of the list.
    ///
    /// TWO drop rules, both LOUD (`PropertyTracker.logOnce`, never a
    /// silent fallthrough):
    ///
    ///  1. An unrecognised `line` keyword (a future §2.1 keyword, `blink`,
    ///     junk) drops the ENTRY. Painting a guessed line would be worse
    ///     than painting none, and the caller still sees an authoritative
    ///     — possibly EMPTY — list, which by contract paints nothing.
    ///  2. An unresolvable `color` token drops the COLOUR ONLY, not the
    ///     line: the entry keeps its line and falls back to nil, which is
    ///     what an absent colour means anyway. NOTE what nil actually
    ///     paints — NOT plain `currentColor`: the overlay substitutes
    ///     `textConfig.decorationColor ?? resolvedColor`, i.e. the run's
    ///     merged `text-decoration-color` leaf, else the text colour (the
    ///     Compose twin's `ownedDecorationColor` does the same). For the
    ///     OUTERMOST entry that is exactly right; for any other entry it
    ///     is the root's colour, not §2.2's initial.
    ///
    ///     `CSSTokenParser.color` covers #hex (3/4/6/8), `rgb()`/`rgba()`,
    ///     `transparent`, the css-color-4 §6.1 basic named set and a
    ///     handful of extended names. It is a var()-substitution reader,
    ///     NOT a CSS colour parser, and the gap is LIVE, not theoretical:
    ///
    ///       • fixtures/wpt/css-text-decor/text-decoration-style-multiple
    ///         .json ships `coral` and `skyblue` today. NEITHER runtime
    ///         table has them, so that run's overline paints the merged
    ///         coral instead of skyblue on BOTH natives while web (which
    ///         hands the token to Chromium) paints it correctly.
    ///       • `rgb()`/`rgba()`: parsed here, NOT by the Compose twin.
    ///       • `crimson`: parsed by the Compose twin, NOT here.
    ///
    ///     The converter DOES resolve all of these (ColorConversion.kt
    ///     carries the full 148-name table) — the loss is a consequence
    ///     of forwarding the token verbatim, see the header. Pinned by
    ///     SkepticDecorWireSeamTests / SkepticDecorWireSeamTest.kt so the
    ///     divergence cannot rot silently.
    ///
    /// A nil/empty input maps to nil/empty output: the EMPTY list is a
    /// meaningful state ("authoritative, and it says no lines"), never
    /// silently upgraded to "absent".
    static func decorationLines(from wire: [IRDecoration]?) -> [DecorationColorOps.DecorationLine]? {
        // Absent stays absent — the painter then synthesizes the
        // component's own flags (the pre-wave-22 legacy path).
        guard let wire = wire else { return nil }
        return wire.compactMap { entry -> DecorationColorOps.DecorationLine? in
            // Rule 1: the line keyword decides whether the entry survives.
            guard let kind = DecorationColorOps.lineKind(from: entry.line) else {
                _ = PropertyTracker.logOnce(
                    key: "decoration-wire-line-\(entry.line)",
                    message: "meta.decorations: unpaintable line keyword "
                        + "'\(entry.line)' — entry dropped")
                return nil
            }
            // Rule 2: the colour is best-effort; a failure degrades to
            // currentColor rather than dropping the line.
            var color: DecorationColorOps.Rgba? = nil
            if let token = entry.color {
                if let parsed = CSSTokenParser.color(token) {
                    // IR/CSS sRGB 0..1 is exactly the painter's space —
                    // straight component copy, no conversion.
                    color = DecorationColorOps.Rgba(r: CGFloat(parsed.r), g: CGFloat(parsed.g),
                                                    b: CGFloat(parsed.b), a: CGFloat(parsed.a))
                } else {
                    _ = PropertyTracker.logOnce(
                        key: "decoration-wire-color-\(token)",
                        message: "meta.decorations: unresolvable colour token "
                            + "'\(token)' — painting currentColor")
                }
            }
            return DecorationColorOps.DecorationLine(kind: kind, color: color)
        }
    }
}
