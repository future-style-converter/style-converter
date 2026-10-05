package com.styleconverter.runtime.lists

// The `korean-hangul-formal` counter style — wave 52, lane L6 (T1, the
// BACKLOG's "(c³) The Compose twin does not exist").
//
// BYTE-PARALLEL TWIN of runtimes/swiftui/Sources/StyleConverterRuntime/
// StyleEngine/lists/KoreanHangulFormal.swift (wave 50, lane B4): same
// table, same skip rule, same bottom-outs, same suffix decision, so the
// two natives answer identically for this keyword. css-counter-styles-3
// §7.1 (Longhand East Asian Counter Styles) defines it as an ADDITIVE
// system over `range: -9999 9999` with `suffix: ", "`:
//
//     9000 구천 … 1000 일천 · 900 구백 … 100 일백 · 90 구십 … 10 일십
//     · 9 구 … 1 일 · 0 영
//
// ## The defect this repairs (measured, not assumed)
// `ListStyleExtractor.typeFromKeyword` had no `korean_hangul_formal` arm,
// so it answered null, `resolveMarkerConfig` KEPT the running value — the
// `<ol>` UA default `decimal` (HTML §15.3.7) — and Android painted
// "1. foo" / "2. bar" where the frozen browser reference paints "일, foo"
// / "이, bar". Visible in wave51-fix on css-counter-styles/counter-suffix
// (`android f 0.9051`): tools/titan/runs/wave51-fix/sections/
// css-counter-styles/android-screenshots/wpt__css-counter-styles__
// counter-suffix.png rows 7–8 (y 165–178 / 190–202) read "1." / "2."
// against tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
// white-black-ink-font-lh-imgpad-htmlpins/css-counter-styles/
// counter-suffix.png ink bands 165–180 / 189–204. iOS has painted the
// hangul since wave 50; this closes the twin gap.
//
// ## Corpus blast radius (censused over the 1435 wave51-fix per-test IR
// docs by tools/titan/results/wave52-counters-and-lists/census.mjs)
// Exactly ONE component declares a `korean-*` list-style-type:
// counter-suffix's fourth `<ol>` (`counter-suffix__0__3`), reaching its
// two `<li>` children by inheritance. No other cell can move.
//
// ## Scope boundary (no silent fallthrough)
// Only `korean-hangul-formal` is modelled. Its siblings
// `korean-hanja-informal` / `korean-hanja-formal` share the system but a
// different symbol set; they have ZERO corpus carriers, so adding them
// would be untestable code. They stay unmodelled, which routes them
// through `typeFromKeyword`'s documented null → the container's UA
// default, exactly as before (pinned as a negative control in
// KoreanHangulFormalTest).

object KoreanHangulFormal {

    /** Digits 0–9 (영 일 이 삼 사 오 육 칠 팔 구), indexed by value — the
     *  additive-symbols table's 0…9 rows, css-counter-styles-3 §7.1. */
    private val digits = arrayOf(
        "영", "일", "이", "삼", "사",
        "오", "육", "칠", "팔", "구"
    )

    /** Place markers for 10^1 / 10^2 / 10^3 (십 백 천). The FORMAL style
     *  always writes the digit before the place ("일십", not "십") — that
     *  is the whole difference from the informal siblings, and it is why
     *  the table lists `10 일십` rather than `10 십`. */
    private val places = arrayOf("", "십", "백", "천")

    /**
     * The additive expansion of [num] in `korean-hangul-formal`.
     *
     * @param num the counter value. The style's `range` is -9999…9999;
     *   this runtime's marker path only ever asks for 1…n
     *   ([ListStyleApplier.getMarker] takes a 0-based index), so the
     *   negative half of the range — spelled `negative: "마이너스 "` — is
     *   unreachable here and deliberately not modelled.
     * @return the hangul string for 1…9999 and for 0; the DECIMAL spelling
     *   for everything else, INCLUDING the in-range negative half
     *   (-9999…-1). So the decimal answer has TWO meanings and only one is
     *   the range rule: for |num| > 9999 it is the genuine §3.2
     *   out-of-range bottom-out every other additive style here takes
     *   (`toArmenian`, `toGeorgian`, `toHebrew`); for -9999…-1 the value
     *   is INSIDE the declared range and the decimal spelling is this
     *   runtime's unmodelled-negative fallback, not §3.2 — pinned so the
     *   choice cannot change silently (the iOS twin pins the same).
     */
    fun expand(num: Int): String {
        // Zero has its own symbol in the table; it is unreachable from the
        // marker path (indices start at 1) but a counter() caller could
        // ask for it, so the row is honoured rather than dropped.
        if (num == 0) return digits[0]
        if (num < 1 || num > 9999) return num.toString()
        val out = StringBuilder()
        var n = num
        // Highest place first — the additive system consumes the largest
        // weight that fits, and for a positional-decimal digit set that is
        // exactly "one symbol pair per decimal digit".
        for (place in 3 downTo 0) {
            var weight = 1
            repeat(place) { weight *= 10 }
            val digit = n / weight
            n %= weight
            // A zero digit contributes NOTHING (the table has no `0 영` row
            // at a non-unit weight), which is what makes 1001 read 일천일
            // rather than 일천영백영십일.
            if (digit == 0) continue
            out.append(digits[digit]).append(places[place])
        }
        return out.toString()
    }

    /**
     * The style's `suffix`, as this runtime models it.
     *
     * The descriptor is literally `", "` — comma plus space. The space is
     * dropped for the SAME reason every numeric arm of
     * [ListStyleApplier.getMarker] emits "1." and not "1. ": an `outside`
     * marker is painted as a leading inline box and the gap to the item's
     * content is supplied once, by [ListMarkerRow.gapDp] (4dp). Emitting
     * the suffix space as well would double it.
     *
     * DECISION LABEL (carried over from the Swift twin, which RE-MEASURED
     * it against the frozen counter-suffix ref: korean marker ink at cols
     * [42,58], decimal marker ink at [46,58], item text starting at 64/65
     * in BOTH cases — the descriptor's trailing space costs no advance in
     * the reference): dropping the literal trailing space is
     * REFERENCE-FITTED, not spec-derived. The margin-area hang that would
     * make the suffix space observable is T5 of this same lane
     * (ListMarkerOutsideHang), staged as a device A/B.
     */
    const val SUFFIX = ","
}
