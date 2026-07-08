//
//  CoreTypesSelfTest.swift
//  StyleConverterTest — StyleEngine/core/types
//
//  Launch-time self-test harness for the Phase 1 primitive extractors.
//  Chosen over an XCTest bundle because this project has a known Xcode
//  26.x scheme-resolution bug and `test-all.sh` already uses the target-
//  based build pattern. Adding a unit-test target would require surgery
//  we want to avoid until Phase 2+.
//
//  Each `assert(...)` invocation exercises one IR shape quirk. Output is
//  printed to stderr and — under DEBUG — a summary count. Call from
//  `StyleConverterTestApp.init`. Zero cost in release (whole function is
//  wrapped in `#if DEBUG`).
//

import Foundation

enum CoreTypesSelfTest {

    // Single entry point. Prints a one-line PASS/FAIL summary per section.
    static func run() {
        var failures: [String] = []

        failures += runLengthChecks()
        failures += runColorChecks()
        failures += runAngleChecks()
        failures += runTimeChecks()
        failures += runNumberChecks()
        failures += runKeywordChecks()

        if failures.isEmpty {
            print("[CoreTypesSelfTest] PASS — all primitive extractors green")
        } else {
            print("[CoreTypesSelfTest] FAIL — \(failures.count) check(s) failed:")
            failures.forEach { print("  - \($0)") }
        }
    }

    // MARK: - Length checks (lengths-*.json)

    private static func runLengthChecks() -> [String] {
        var f: [String] = []

        // Quirk #1a: wrapped sizing shape { type: length, px: 200 }.
        let wrapped = obj(["type": .string("length"), "px": .double(200)])
        if case .exact(let px) = extractLength(wrapped), px == 200 { } else { f.append("wrapped px") }

        // Quirk #1b: raw { px: 16 } (spacing props).
        if case .exact(let px) = extractLength(obj(["px": .double(16)])), px == 16 { } else { f.append("raw px") }

        // Quirk #2: { type: percentage, value: 50 }.
        if case .relative(50, .percent, nil) = extractLength(obj(["type": .string("percentage"), "value": .double(50)])) { } else { f.append("percentage") }

        // Quirk #3: bare string "auto" / "min-content" / "max-content".
        if case .auto = extractLength(.string("auto")) { } else { f.append("auto") }
        if case .intrinsic(.minContent) = extractLength(.string("min-content")) { } else { f.append("min-content") }
        if case .intrinsic(.maxContent) = extractLength(.string("max-content")) { } else { f.append("max-content") }

        // Quirk #4: { fr: 1 }.
        if case .fraction(1) = extractLength(obj(["fr": .double(1)])) { } else { f.append("fr") }

        // Viewport (lengths-viewport.json): only original present.
        let vw50 = obj(["type": .string("length"),
                        "original": obj(["v": .double(50), "u": .string("VW")])])
        if case .relative(50, .vw, nil) = extractLength(vw50) { } else { f.append("vw-50") }

        // Large-viewport variant.
        let lvmax = obj(["type": .string("length"),
                         "original": obj(["v": .double(80), "u": .string("LVMAX")])])
        if case .relative(80, .lvmax, nil) = extractLength(lvmax) { } else { f.append("lvmax") }

        // Container-query unit.
        let cqi = obj(["type": .string("length"),
                       "original": obj(["v": .double(25), "u": .string("CQI")])])
        if case .relative(25, .cqi, nil) = extractLength(cqi) { } else { f.append("cqi") }

        // Font-relative with px fallback present (converter occasionally resolves em).
        let em = obj(["px": .double(32),
                      "original": obj(["v": .double(2), "u": .string("EM")])])
        if case .relative(2, .em, 32) = extractLength(em) { } else { f.append("em w/ px fallback") }

        // Absolute pt → normalised px, original retained.
        let pt120 = obj(["type": .string("length"), "px": .double(160),
                         "original": obj(["v": .double(120), "u": .string("PT")])])
        if case .relative(120, .pt, 160) = extractLength(pt120) { } else { f.append("pt-120") }

        // Unknown garbage.
        if case .unknown = extractLength(.bool(true)) { } else { f.append("unknown-bool") }

        // Phase 2 shapes — `{ expr: "calc(...)" }` spacing calc form.
        if case .calc(let e) = extractLength(obj(["expr": .string("calc(10px + 5px)")])),
           e == "calc(10px + 5px)" { } else { f.append("calc-expr") }

        // Phase 2 shapes — bare-number percent variant used by spacing IR.
        if case .relative(25, .percent, nil) = extractLengthPercentDefault(.double(25)) { } else { f.append("bare-percent-double") }
        if case .relative(10, .percent, nil) = extractLengthPercentDefault(.int(10)) { } else { f.append("bare-percent-int") }

        // And the same variant still delegates for non-bare shapes.
        if case .exact(20) = extractLengthPercentDefault(obj(["px": .double(20)])) { } else { f.append("percent-default-delegates") }

        return f.map { "length/\($0)" }
    }

    // MARK: - Color checks (colors-*.json)

    private static func runColorChecks() -> [String] {
        var f: [String] = []

        // Static hex (colors-legacy.json).
        let hex = obj(["srgb": obj(["r": .double(1), "g": .double(0.2), "b": .double(0.4), "a": .double(1)]),
                       "original": .string("#ff3366")])
        if case .srgb(1, 0.2, 0.4, 1) = extractColor(hex) { } else { f.append("hex") }

        // Hex with 8-digit alpha (colors-legacy.json).
        let hex8 = obj(["srgb": obj(["r": .double(1), "g": .double(0.2), "b": .double(0.4), "a": .double(0.5)])])
        if case .srgb(_, _, _, 0.5) = extractColor(hex8) { } else { f.append("hex8-alpha") }

        // currentColor dynamic (colors-named.json).
        let cc = obj(["original": .string("currentColor")])
        if case .dynamic(.currentColor, _) = extractColor(cc) { } else { f.append("currentColor") }

        // color-mix dynamic (colors-modern.json).
        let cm = obj(["original": obj(["type": .string("color-mix")])])
        if case .dynamic(.colorMix, _) = extractColor(cm) { } else { f.append("color-mix") }

        // light-dark dynamic.
        let ld = obj(["original": obj(["type": .string("light-dark")])])
        if case .dynamic(.lightDark, _) = extractColor(ld) { } else { f.append("light-dark") }

        // Static oklch (has srgb filled).
        let ok = obj(["srgb": obj(["r": .double(0.5), "g": .double(0.5), "b": .double(0.5)]),
                      "original": obj(["type": .string("oklch")])])
        if case .srgb(_, _, _, 1.0) = extractColor(ok) { } else { f.append("oklch-static") }

        // Unknown.
        if case .unknown = extractColor(obj([:])) { } else { f.append("unknown-empty") }

        return f.map { "color/\($0)" }
    }

    // MARK: - Angle / Time / Number / Keyword checks

    private static func runAngleChecks() -> [String] {
        var f: [String] = []
        // { deg: 45 }.
        if extractAngle(obj(["deg": .double(45)]))?.degrees == 45 { } else { f.append("deg-45") }
        // Rad via original.
        let rad = obj(["original": obj(["v": .double(.pi), "u": .string("RAD")])])
        if let v = extractAngle(rad)?.degrees, abs(v - 180) < 0.0001 { } else { f.append("rad→deg") }
        // Turn via original.
        let turn = obj(["original": obj(["v": .double(0.25), "u": .string("TURN")])])
        if extractAngle(turn)?.degrees == 90 { } else { f.append("turn→deg") }
        return f.map { "angle/\($0)" }
    }

    private static func runTimeChecks() -> [String] {
        var f: [String] = []
        if extractTime(obj(["ms": .double(500)]))?.milliseconds == 500 { } else { f.append("ms-500") }
        let sec = obj(["original": obj(["v": .double(0.3), "u": .string("S")])])
        if let v = extractTime(sec)?.milliseconds, abs(v - 300) < 0.0001 { } else { f.append("s→ms") }
        // Quirk #7: list of times.
        let list: IRValue = .array([obj(["ms": .double(100)]), obj(["ms": .double(200)])])
        if extractTimes(list).count == 2 { } else { f.append("time-list") }
        return f.map { "time/\($0)" }
    }

    private static func runNumberChecks() -> [String] {
        var f: [String] = []
        // Opacity.
        if NumberExtractors.opacity(obj(["alpha": .double(0.5)]))?.value == 0.5 { } else { f.append("opacity") }
        // LineHeight multiplier.
        if NumberExtractors.lineHeightMultiplier(obj(["multiplier": .double(1.5)]))?.value == 1.5 { } else { f.append("lineHeight") }
        // FlexGrow normalizedValue.
        if NumberExtractors.flexGrow(obj(["normalizedValue": .double(1)]))?.value == 1 { } else { f.append("flexGrow") }
        // ZIndex.
        if NumberExtractors.zIndex(obj(["value": .int(10)]))?.value == 10 { } else { f.append("zIndex") }
        // FontWeight bare integer (quirk).
        if NumberExtractors.fontWeight(.int(700))?.value == 700 { } else { f.append("fontWeight-bare") }
        if NumberExtractors.fontWeight(obj(["keyword": .string("bold")]))?.value == 700 { } else { f.append("fontWeight-bold") }
        // FontSize → LengthValue.
        if case .exact(16) = NumberExtractors.fontSize(obj(["px": .double(16)])) { } else { f.append("fontSize") }
        return f.map { "number/\($0)" }
    }

    private static func runKeywordChecks() -> [String] {
        var f: [String] = []
        // Bare string.
        if extractKeyword(.string("FLEX"))?.normalized == "flex" { } else { f.append("bare-upper") }
        // { keyword: ... }.
        if extractKeyword(obj(["keyword": .string("space-between")]))?.normalized == "space-between" { } else { f.append("keyword-field") }
        // Underscore → hyphen normalisation (Kotlin enum names).
        if extractKeyword(.string("MIN_CONTENT"))?.normalized == "min-content" { } else { f.append("underscore→hyphen") }
        // matches(...) helper.
        let kw = extractKeyword(.string("center"))
        if kw?.matches("flex-start", "center") == true { } else { f.append("matches") }
        return f.map { "keyword/\($0)" }
    }

    // Convenience builder so test bodies stay readable.
    private static func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
}
