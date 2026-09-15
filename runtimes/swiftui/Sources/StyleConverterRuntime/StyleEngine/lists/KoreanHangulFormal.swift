//
//  KoreanHangulFormal.swift
//  StyleEngine/lists — wave 50, lane B4.
//
//  The `korean-hangul-formal` counter style, css-counter-styles-3 §7.1
//  (Longhand East Asian Counter Styles), which defines it as an ADDITIVE
//  system over `range: -9999 9999` with `suffix: ", "`:
//
//      9000 구천 … 1000 일천 · 900 구백 … 100 일백 · 90 구십 … 10 일십
//      · 9 구 … 1 일 · 0 영
//
//  ## The defect this repairs (measured, not assumed)
//  `ListMarkerResolver.markerType(fromKeyword:)` had no entry for
//  `korean-hangul-formal`, so it answered nil, the resolver KEPT the
//  running value — the `<ol>` UA default `decimal` (HTML §15.3.7) — and
//  iOS painted "1." / "2." where the browser reference paints "일," /
//  "이,". Visible in wave49-final on
//  css-counter-styles/counter-suffix (iOS 0.8883, Android 0.8901, both
//  scored honestly since retro R13 unexcluded the test; web 0.8867):
//  tools/titan/runs/wave49-final/sections/css-counter-styles/
//  ios-screenshots/wpt__css-counter-styles__counter-suffix.png rows 9–10
//  read "1. foo" / "2. bar" against the frozen ref's "일, foo" / "이, bar"
//  (tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//  white-black-ink-font-lh-imgpad-htmlpins/css-counter-styles/
//  counter-suffix.png, ink bands rows 165–204). WEB renders those two
//  rows CORRECTLY — it hands Chromium the real `list-style-type` — so
//  this is a NATIVE-only gap, not the shared marker defect the wave-49
//  refusal note grouped it with.
//
//  ## Corpus blast radius (censused, not estimated)
//  Exactly ONE component in all 1435 wave49-final per-test IR documents
//  declares a `korean-*` list-style-type: counter-suffix's fourth `<ol>`
//  (`counter-suffix__0__3`). No other cell can move.
//
//  ## TWIN DIVERGENCE — stated, not hidden
//  ListMarkerText's header calls itself a byte-for-byte twin of Compose
//  `lists/ListStyleApplier.getMarker`, and that twin has NO korean arm
//  either — its counter-style enum stops at `KATAKANA_IROHA`, same as
//  this package did. This lane owns the iOS half only, so after it the
//  two natives ANSWER DIFFERENTLY for `korean-hangul-formal`: iOS "일,",
//  Compose "1.". That is tracked work, not a silent divergence — the
//  lane reports it for the backlog, and the port is this file plus three
//  edits in runtimes/compose/src/main/java/com/styleconverter/runtime/
//  lists/ (sites named by SYMBOL, since line numbers rot; each verified
//  present by wave-50 skeptic S4 and re-grepped by fix lane F2):
//
//   1. `ListStyleConfig.kt` — the `ListStyleType` enum: add
//      `KOREAN_HANGUL_FORMAL` after `KATAKANA_IROHA`.
//   2. `ListStyleExtractor.kt` — `typeFromKeyword`'s `when`: the key must
//      be the UNDERSCORED `"korean_hangul_formal"`, NOT the hyphenated
//      wire spelling, because that function normalises with
//      `rawKeyword.lowercase().replace("-", "_")` before matching. (The
//      wire value the converter emits really is `"korean-hangul-formal"`
//      — the hyphen is removed by the normaliser, not by the author.)
//   3. `ListStyleApplier.kt` — `getMarker`'s `when`: one arm beside
//      `ListStyleType.KATAKANA_IROHA`, returning this file's `expand`
//      plus `suffix` rather than the `"$n."` the other arms use.
//
//  ## Scope boundary (no silent fallthrough)
//  Only `korean-hangul-formal` is modelled. Its two siblings
//  `korean-hanja-informal` / `korean-hanja-formal` share the system but
//  a different symbol set; they have ZERO corpus carriers, so adding
//  them would be untestable code. They stay unmodelled, which routes
//  them through `markerType(fromKeyword:)`'s documented nil → the
//  container's UA default, exactly as before.
//

import Foundation

enum KoreanHangulFormal {

    /// Digits 0–9 (영 일 이 삼 사 오 육 칠 팔 구), indexed by value —
    /// the additive-symbols table's 0…9 rows, css-counter-styles-3 §7.1.
    private static let digits = ["영", "일", "이", "삼", "사",
                                 "오", "육", "칠", "팔", "구"]

    /// Place markers for 10^1 / 10^2 / 10^3 (십 백 천). The FORMAL style
    /// always writes the digit before the place ("일십", not "십") —
    /// that is the whole difference from the informal siblings, and it
    /// is why the table lists `10 일십` rather than `10 십`.
    private static let places = ["", "십", "백", "천"]

    /// The additive expansion of `num` in `korean-hangul-formal`.
    ///
    /// - Parameter num: the counter value. The style's `range` is
    ///   -9999…9999; this renderer's marker path only ever asks for
    ///   1…n (`ListMarkerText.marker` takes a 0-based index), so the
    ///   negative half of the range — spelled `negative: "마이너스 "` —
    ///   is unreachable here and deliberately not modelled.
    /// - Returns: the hangul string for 1…9999 and for 0; the DECIMAL
    ///   spelling for everything else, INCLUDING the in-range negative
    ///   half (-9999…-1), whose `negative: "마이너스 "` prefix is
    ///   deliberately unmodelled because the marker path only ever asks
    ///   for 1…n. So the decimal answer has TWO different meanings and
    ///   only one of them is the range rule: for |num| > 9999 it is the
    ///   genuine css-counter-styles-3 §3.2 out-of-range bottom-out, the
    ///   same one every other additive style in ListMarkerText already
    ///   takes (`armenian`, `georgian`, `hebrew`); for -9999…-1 the
    ///   value is INSIDE the style's declared `range: -9999 9999` and the
    ///   decimal spelling is this renderer's unmodelled-negative
    ///   fallback, not §3.2.
    static func expand(_ num: Int) -> String {
        // Zero has its own symbol in the table; it is unreachable from
        // the marker path (indices start at 1) but a counter() caller
        // could ask for it, so the row is honoured rather than dropped.
        if num == 0 { return digits[0] }
        guard num > 0, num <= 9999 else { return "\(num)" }
        var out = ""
        var n = num
        // Highest place first — the additive system consumes the
        // largest weight that fits, and for a positional-decimal digit
        // set that is exactly "one symbol pair per decimal digit".
        for place in stride(from: 3, through: 0, by: -1) {
            let weight = Int(pow(10.0, Double(place)))
            let digit = n / weight
            n %= weight
            // A zero digit contributes NOTHING (the table has no
            // `0 영` row at a non-unit weight), which is what makes
            // 1001 read 일천일 rather than 일천영백영십일.
            guard digit > 0 else { continue }
            out += digits[digit] + places[place]
        }
        return out
    }

    /// The style's `suffix`, as this renderer models it.
    ///
    /// The descriptor is literally `", "` — comma plus space. The space
    /// is dropped here for the SAME reason every numeric style in
    /// ListMarkerText emits "1." and not "1. ": in this renderer an
    /// `outside` marker is painted as a LEADING INLINE BOX and the gap
    /// to the item's content is supplied once, by `ListMarkerRow.gapPt`
    /// (4pt). Emitting the suffix space as well would double it.
    ///
    /// RE-MEASURED (fix lane F5) from the frozen ref
    /// tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
    /// white-black-ink-font-lh-imgpad-htmlpins/css-counter-styles/
    /// counter-suffix.png — column ink bands over the `.kor` rows (row
    /// bands 165–180 / 189–204) and the `.dec` rows (22–33 / 46–57),
    /// ink counted at a mean < 200 threshold so antialiased glyph edges
    /// are included: the frozen ref places the korean marker ink at cols
    /// [42,58] and the decimal marker ink at [46,58], with the item text
    /// starting at 64/65 in BOTH cases, so the descriptor's trailing
    /// space costs no advance in the reference.
    ///
    /// DECISION LABEL: dropping the descriptor's literal trailing space
    /// is REFERENCE-FITTED against that PNG, not spec-derived.
    /// css-counter-styles-3 §7.1 declares `suffix: ", "` and the
    /// `suffix` descriptor is appended to the marker string, so a
    /// spec-faithful reader would emit ", "; it is dropped only because
    /// this renderer supplies its own gap — a property of
    /// `ListMarkerRow`, not of the spec — and the measurement above is
    /// what shows one gap, not two, matches the reference. The
    /// margin-area hang that would make the suffix space observable is
    /// the still-deferred B-RC3 part 3 geometry documented at
    /// ComponentRenderer's marker branch.
    static let suffix = ","
}
