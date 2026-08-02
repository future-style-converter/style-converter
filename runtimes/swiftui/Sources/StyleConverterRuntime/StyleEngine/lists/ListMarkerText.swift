//
//  ListMarkerText.swift
//  StyleEngine/lists — wave 24, lane LF (B-RC3 parts 1+2).
//
//  Counter-style → marker string. Byte-for-byte twin of Compose
//  lists/ListStyleApplier.getMarker: same glyphs, same "n." suffix, same
//  overflow-to-decimal behaviour, so a `<li>` renders the same characters
//  on both natives. css-counter-styles-3 §6 (predefined counter styles).
//

import Foundation

enum ListMarkerText {

    /// The marker string for the list item at `index` (0-based).
    /// Returns "" for `list-style-type: none` — css-lists-3 §3.1: the item
    /// has NO marker box, so the caller must not reserve space for one.
    static func marker(index: Int, config: ListMarkerConfig) -> String {
        switch config.type {
        // §6.1 symbolic — U+2022 BULLET / U+25CB WHITE CIRCLE /
        // U+25A0 BLACK SQUARE, the glyphs Chromium's UA sheet uses.
        case .noMarker: return ""
        case .disc: return "\u{2022}"
        case .circle: return "\u{25CB}"
        case .square: return "\u{25A0}"
        // §6.2 numeric — the "." is the predefined styles' `suffix`.
        case .decimal: return "\(index + 1)."
        case .decimalLeadingZero:
            return String(format: "%02d.", index + 1)
        case .lowerAlpha: return "\(alpha(index))."
        case .upperAlpha: return "\(alpha(index).uppercased())."
        case .lowerRoman: return "\(roman(index + 1))."
        case .upperRoman: return "\(roman(index + 1).uppercased())."
        case .lowerGreek: return "\(cyclic(index, Self.greekLower))."
        case .upperGreek: return "\(cyclic(index, Self.greekUpper))."
        case .armenian: return "\(armenian(index + 1))."
        case .georgian: return "\(georgian(index + 1))."
        case .hebrew: return "\(hebrew(index + 1))."
        case .cjkDecimal: return "\(cjkDecimal(index + 1))."
        case .hiragana: return "\(cyclic(index, Self.hiragana))."
        case .katakana: return "\(cyclic(index, Self.katakana))."
        case .hiraganaIroha: return "\(cyclic(index, Self.hiraganaIroha))."
        case .katakanaIroha: return "\(cyclic(index, Self.katakanaIroha))."
        }
    }

    // MARK: - Alphabetic (§6.2 "alphabetic" system: bijective base-26)

    /// a…z, aa, ab, … — the bijective mapping the alphabetic system
    /// defines. Recursive on `index / 26 - 1`, identical to the Compose
    /// twin's `toLowerAlpha`.
    private static func alpha(_ index: Int) -> String {
        guard index >= 0 else { return "" }
        let letter = String(UnicodeScalar(UInt8(97 + index % 26)))
        return index < 26 ? letter : alpha(index / 26 - 1) + letter
    }

    // MARK: - Roman (§6.2 "additive" system)

    private static let romanValues = [1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1]
    private static let romanSymbols = ["m", "cm", "d", "cd", "c", "xc", "l",
                                       "xl", "x", "ix", "v", "iv", "i"]

    private static func roman(_ num: Int) -> String {
        guard num > 0 else { return "" }
        var out = "", n = num
        for (i, v) in romanValues.enumerated() {
            while n >= v { out += romanSymbols[i]; n -= v }
        }
        return out
    }

    // MARK: - Cyclic / fixed symbol runs (§6.1 "cyclic", §6.2 "fixed")

    /// Index into a fixed symbol run; past the end the predefined styles
    /// fall back to `decimal` (§6.2 fallback), matching the Compose twin.
    private static func cyclic(_ index: Int, _ symbols: [String]) -> String {
        index >= 0 && index < symbols.count ? symbols[index] : "\(index + 1)"
    }

    private static let greekLower = split("αβγδεζηθικλμνξοπρστυφχψω")
    private static let greekUpper = split("ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ")
    private static let hiragana = split(
        "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん")
    private static let katakana = split(
        "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン")
    private static let hiraganaIroha = split(
        "いろはにほへとちりぬるをわかよたれそつねならむうゐのおくやまけふこえてあさきゆめみしゑひもせす")
    private static let katakanaIroha = split(
        "イロハニホヘトチリヌルヲワカヨタレソツネナラムウヰノオクヤマケフコエテアサキユメミシヱヒモセス")

    private static func split(_ s: String) -> [String] { s.map(String.init) }

    // MARK: - Additive international systems (§6.2)

    private static let armenianDigits = [
        split("_ԱԲԳԴԵԶԷԸԹ"), split("_ԺԻԼԽԾԿՀՁՂ"),
        split("_ՃՄՅՆՇՈՉՊՋ"), split("_ՌՍՎՏՐՑՒՓՔ"),
    ]
    // Mkhedruli (U+10D0…), NOT Asomtavruli — the same glyphs the Compose
    // twin's georgianDigits table carries, position for position.
    private static let georgianDigits = [
        split("_აბგდევზჰთ"), split("_იკლმნჲოპჟ"),
        split("_რსტჳფქღყშ"), split("_ჩცძწჭხჴჯჰ"),
    ]

    /// Positional additive expansion shared by armenian/georgian: digit
    /// tables are indexed [power-of-ten][digit], "_" marking the unused
    /// zero slot. Out-of-range falls back to decimal, like the twin.
    private static func additive(_ num: Int, _ table: [[String]], max: Int) -> String {
        guard num > 0, num <= max else { return "\(num)" }
        var out = "", n = num
        for i in stride(from: 3, through: 0, by: -1) {
            let divisor = Int(pow(10.0, Double(i)))
            let digit = n / divisor
            if digit > 0, i < table.count, digit < table[i].count { out += table[i][digit] }
            n %= divisor
        }
        return out
    }

    private static func armenian(_ num: Int) -> String {
        additive(num, armenianDigits, max: 9999)
    }

    private static func georgian(_ num: Int) -> String {
        guard num > 0, num <= 19999 else { return "\(num)" }
        // U+10F5 is the 10000 digit; the rest is the shared expansion.
        let prefix = num >= 10000 ? "\u{10F5}" : ""
        let rest = num >= 10000 ? num - 10000 : num
        // `rest == 0` (num EXACTLY 10000) contributes no further digits —
        // the Compose twin's digit loop simply appends nothing there.
        // Calling additive(0, …) would trip its own `num > 0` guard and
        // append a literal "0", so 10000 printed "ჵ0" on iOS and "ჵ" on
        // Android. Caught by the wave-24 skeptic twin-table diff (the ONLY
        // divergence in 22 counter styles × 317 indices).
        guard rest > 0 else { return prefix }
        return prefix + additive(rest, georgianDigits, max: 19999)
    }

    private static let hebrewUnits = split("_אבגדהוזחט")
    private static let hebrewTens = split("_יכלמנסעפצ")
    private static let hebrewHundreds = ["_", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק"]

    private static func hebrew(_ num: Int) -> String {
        guard num > 0, num <= 999 else { return "\(num)" }
        // "_" is the zero placeholder in each table — drop it so a zero
        // digit contributes nothing (e.g. 101 → ק + א, no tens glyph).
        func at(_ t: [String], _ i: Int) -> String {
            i > 0 && i < t.count ? t[i] : ""
        }
        return at(hebrewHundreds, num / 100)
            + at(hebrewTens, (num % 100) / 10)
            + at(hebrewUnits, num % 10)
    }

    private static let cjkDigits = split("〇一二三四五六七八九")

    private static func cjkDecimal(_ num: Int) -> String {
        // §6.2 cjk-decimal is positional, one ideograph per decimal digit.
        String("\(num)".compactMap { $0.wholeNumberValue }.map { cjkDigits[$0] }.joined())
    }
}
