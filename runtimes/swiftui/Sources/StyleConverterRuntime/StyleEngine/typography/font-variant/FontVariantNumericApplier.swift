//
//  FontVariantNumericApplier.swift
//  StyleEngine/typography/font-variant — Phase 6; wave 55, lane L5 (U3-ios).
//
//  css-fonts-4 `font-variant-numeric`: the numeric-spacing pair
//  `tabular-nums` / `proportional-nums` (OpenType `tnum` / `pnum`) is
//  routed to `TypographyAggregate.tabularNums`; StyleBuilder mirrors it into
//  `TextConfig.tabularNums`, and the label's own face composes SwiftUI's
//  `Font.monospacedDigit()` (iOS 15+, fixed-width digits) on it — the
//  header's old claim that "SwiftUI Text has no direct API" was false.
//
//  ## The defect (measured on wave54-final)
//  css-counter-styles/counter-suffix row 9: the bidi bake stamps the UA
//  `::marker { font-variant-numeric: tabular-nums }` rule (css-lists-3
//  §3.1.1) on each root-owned RTL marker run, and Chromium measured the
//  tabular glyphs (`1` run 10.38 px = Inter's `one.tf`). Ref and Android
//  paint the tabular `1` (ink x138-144/145, foot serif); iOS painted the
//  proportional one (x138-141) because this applier was an identity.
//  Reach: 6 components DECLARE it, all in counter-suffix — the property is
//  not inherited on either native (InheritedText.inheritedTypes has no
//  `FontVariantNumeric`), so it is declared-only here too.
//
//  ## Radius inside a run (fix-r1: more than the digit)
//  `monospacedDigit()` turns on the face's whole `tnum` feature for the
//  run, and Inter's `tnum` maps punctuation as well as digits (61 single
//  substitutions): `period` → `period.tf` (advance 4.609 → 4.297 px at
//  16 px) and `two` → `two.tf` repaint too. So all six carrier runs — the
//  `.` / `1` / `.` / `2` runs of rows 9-10 and the period of rows 11-12's
//  `א.` / `ב.` (the Hebrew letters come from a fallback face, no Inter
//  `tnum`) — change ink, toward the ref: Chromium applied the same feature
//  (the wire's `.` runs are 4.3 px wide = `period.tf`).
//
//  ## Scope boundary (no silent fallthrough)
//  - The other keyword groups (lining/oldstyle figures, fractions,
//    ordinal, slashed-zero) stay unrouted: no Font API, no corpus carrier.
//  - TODO(wave55-L5): the CONTAINER-level font TypographyApplier builds
//    (its `smallCaps` line) does not compose the flag — no corpus carrier;
//    only the label's own face (PlaceholderLabel.labelFont) does.
//  - The greedy pre-break's measurement UIFont does not carry the feature.
//    Harmless for the 6 carriers: `white-space: pre` runs of one or two
//    glyphs that never break (the run's wire width sizes its frame).
//

import Foundation

enum FontVariantNumericApplier {

    /// The wire spellings of the two numeric-spacing keywords: the
    /// converter emits the SHOUTY enum (`TABULAR_NUMS`), which
    /// FontVariantKeywordParse lower-cases to `tabular_nums`; the
    /// hyphenated CSS spelling is accepted too.
    private static let tabular: Set<String> = ["tabular_nums", "tabular-nums"]
    /// `proportional-nums` — the explicit opt-out (OpenType `pnum`).
    private static let proportional: Set<String> = ["proportional_nums", "proportional-nums"]

    static func contribute(_ cfg: FontVariantKeywordListConfig?, into agg: inout TypographyAggregate) {
        // Undeclared → leave `tabularNums` unset (the face's own default).
        guard let keywords = cfg?.keywords else { return }
        if keywords.contains(where: { tabular.contains($0) }) {
            // `tnum`: fixed-width digits; flip `touched` like the caps
            // sibling so a component declaring only this still reaches
            // TextConfig (no silent drop).
            agg.tabularNums = true
            agg.touched = true
        } else if keywords.contains(where: { proportional.contains($0) }) {
            // `pnum`: an explicit proportional request clears the flag.
            agg.tabularNums = false
        }
        // `normal` and the other groups: unset (see the scope boundary).
    }
}
