//
//  FontFaceMatcher.swift
//  StyleEngine/typography/font — lane IOS-TEXT fix 5.
//
//  css-fonts-4 §5.2 font-style/weight matching over the INSTALLED faces
//  of a family. Needed because SwiftUI's `.custom("Inter").weight(_:)`
//  delegates the pick to CoreText's nearest-face heuristic, which with
//  the harness's 4-face Inter family (Regular 400 / Medium 500 /
//  Bold 700 / Black 900) rounds DOWN at the gaps: `font-weight: 800`
//  rendered Bold-visual while Chromium and Compose both select Black,
//  and 600 rendered Medium vs their Bold (pixel-verified divergence).
//  The §5.2 algorithm rounds the other way above 500 — desired-or-
//  heavier first — so we implement it directly over
//  `UIFont.fontNames(forFamilyName:)` and hand SwiftUI the CONCRETE
//  face name, taking CoreText's heuristic out of the loop.
//

// UIKit for the installed-face introspection; the pure pieces below
// stay UIKit-free so XCTest pins them without registered fonts.
import UIKit

enum FontFaceMatcher {

    // MARK: - §5.2 weight selection (pure)

    /// Pick the rendered weight for `desired` from the `available`
    /// distinct weights, per css-fonts-4 §5.2's desired-weight rules:
    ///   • exact match wins;
    ///   • 400 ≤ desired ≤ 500: weights ≥ desired ascending up to 500,
    ///     then weights < desired descending, then weights > 500 ascending;
    ///   • desired < 400: weights < desired descending, then ascending above;
    ///   • desired > 500: weights > desired ascending, then descending below.
    /// Pure — pinned by IOSTextLaneTests (600 → 700, 800 → 900 on the
    /// Inter 4-face set, the exact divergence this lane fixes).
    static func pick(desired: Int, available: [Int]) -> Int? {
        // Distinct sorted ladder; empty input has no answer.
        let sorted = Set(available).sorted()
        guard !sorted.isEmpty else { return nil }
        // Exact match short-circuit (§5.2 first clause).
        if sorted.contains(desired) { return desired }
        // Split around the target once; each branch reads off the ends.
        let below = sorted.filter { $0 < desired }   // ascending
        let above = sorted.filter { $0 > desired }   // ascending
        if desired >= 400 && desired <= 500 {
            // The normal-weight window: nearest-above capped at 500 first…
            if let m = above.first(where: { $0 <= 500 }) { return m }
            // …then below (descending = .last of ascending)…
            if let m = below.last { return m }
            // …then anything heavier than 500.
            return above.first
        } else if desired < 400 {
            // Light targets prefer lighter faces (descending below first).
            return below.last ?? above.first
        } else {
            // Heavy targets prefer heavier faces (ascending above first) —
            // the clause CoreText's round-down heuristic violates.
            return above.first ?? below.last
        }
    }

    // MARK: - Face-name → weight (pure)

    /// Ordered (token, weight) table — longest/most-specific tokens
    /// FIRST so "extrabold"/"semibold" never false-match on "bold".
    /// Weights follow the OpenType usWeightClass naming conventions
    /// css-fonts-4 §2.2 cites (Thin 100 … Black 900); "heavy" maps to
    /// 800 matching Apple's SF ladder (UIFont.Weight.heavy < .black).
    private static let suffixWeights: [(token: String, weight: Int)] = [
        ("extralight", 200), ("ultralight", 200),
        ("extrabold", 800), ("ultrabold", 800), ("heavy", 800),
        ("semibold", 600), ("demibold", 600),
        ("thin", 100), ("light", 300),
        ("regular", 400), ("normal", 400), ("book", 400), ("roman", 400),
        ("medium", 500), ("bold", 700), ("black", 900), ("ultra", 900),
    ]

    /// Numeric weight encoded in a PostScript-style face name (e.g.
    /// "Inter-SemiBold" → 600), or nil for faces we must not match
    /// (italic/oblique variants — §5.2 matches style before weight and
    /// this lane only selects upright faces; unknown suffixes). A name
    /// that equals the bare family (no suffix) is the Regular face (400).
    /// Pure — pinned by IOSTextLaneTests.
    static func weight(ofFaceName name: String, family: String) -> Int? {
        // Case/punctuation-insensitive comparison space: fold hyphens,
        // spaces and case away so "Inter-SemiBold" == "inter semibold".
        func fold(_ s: String) -> String {
            s.lowercased()
                .replacingOccurrences(of: "-", with: "")
                .replacingOccurrences(of: " ", with: "")
        }
        let n = fold(name), f = fold(family)
        // The style suffix is whatever follows the family prefix.
        guard n.hasPrefix(f) else { return nil }
        let suffix = String(n.dropFirst(f.count))
        // Bare family name → the Regular face per PostScript convention.
        if suffix.isEmpty { return 400 }
        // Italic/oblique faces are a different STYLE axis — never pick
        // them for an upright run (italic rides `.italic()` separately).
        if suffix.contains("italic") || suffix.contains("oblique") { return nil }
        // First (most-specific) token contained in the suffix wins.
        for (token, w) in suffixWeights where suffix.contains(token) {
            return w
        }
        // "DisplayCondensed" etc — unknown axis, skip honestly rather
        // than guessing a weight (no silent fallthrough: the caller
        // falls back to the legacy `.weight()` pick and logs nothing
        // new, which is the pre-lane behaviour for such families).
        return nil
    }

    // MARK: - Installed-face resolution (UIKit introspection)

    /// The concrete installed face name of `family` that css-fonts-4
    /// §5.2 selects for `desiredWeight`, or nil when the family isn't
    /// registered (unit-test bundle) / exposes no weight-parsable faces —
    /// callers then fall back to the legacy `.weight()` path so behaviour
    /// off-harness is unchanged.
    static func faceName(family: String, desiredWeight: Int) -> String? {
        // All registered face names for the family — empty when the
        // family isn't installed in this process.
        let names = UIFont.fontNames(forFamilyName: family)
        guard !names.isEmpty else { return nil }
        // Map each upright face to its named weight.
        var byWeight: [Int: String] = [:]
        for n in names {
            guard let w = weight(ofFaceName: n, family: family) else { continue }
            // First name per weight wins (families don't normally ship
            // duplicate upright weights).
            if byWeight[w] == nil { byWeight[w] = n }
        }
        // §5.2 pick over the weights that actually exist.
        guard let chosen = pick(desired: desiredWeight,
                                available: Array(byWeight.keys)) else { return nil }
        return byWeight[chosen]
    }
}
