//
//  ColorBackgroundSelfTest.swift
//  StyleEngine/color — Phase 4.
//
//  Launch-time asserts for every Phase 4 extractor: colour, opacity,
//  accent / caret, the seven background-* families, blend modes, and
//  isolation. Same PASS/FAIL pattern as SpacingSelfTest and
//  SizingSelfTest — prints the failing check names so the simulator
//  log is enough to diagnose a regression without attaching a debugger.
//

import SwiftUI

enum ColorBackgroundSelfTest {

    // Single entry point. Walks every sub-check and summarises.
    static func run() {
        var f: [String] = []
        f += runColorChecks()
        f += runOpacityChecks()
        f += runAccentCaretChecks()
        f += runBackgroundImageChecks()
        f += runBackgroundSidecarChecks()
        f += runBlendAndIsolationChecks()

        if f.isEmpty {
            print("[ColorBackgroundSelfTest] PASS — colour+background engine green")
        } else {
            print("[ColorBackgroundSelfTest] FAIL — \(f.count) check(s) failed:")
            f.forEach { print("  - \($0)") }
        }
    }

    // MARK: - Helpers

    // Concise IRValue.object builder.
    private static func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    // Property list builder from (type, data) pairs.
    private static func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }
    // Static sRGB colour blob matching the IR contract.
    private static func srgb(_ r: Double, _ g: Double, _ b: Double,
                             _ a: Double? = nil) -> IRValue {
        var dict: [String: IRValue] = [
            "r": .double(r), "g": .double(g), "b": .double(b),
        ]
        if let a = a { dict["a"] = .double(a) }
        return obj(["srgb": obj(dict), "original": .string("test")])
    }

    // MARK: - Colour

    private static func runColorChecks() -> [String] {
        var f: [String] = []
        // BackgroundColor + Color both present.
        let c = ColorExtractor.extract(from: props([
            ("BackgroundColor", srgb(1, 0, 0)),
            ("Color", srgb(0, 1, 0)),
        ]))
        if c?.background == nil { f.append("Color: background missing") }
        if c?.foreground == nil { f.append("Color: foreground missing") }
        // Absent → nil.
        if ColorExtractor.extract(from: props([])) != nil {
            f.append("Color: empty list should be nil")
        }
        return f
    }

    // MARK: - Opacity

    private static func runOpacityChecks() -> [String] {
        var f: [String] = []
        // Standard alpha/original shape.
        let o = OpacityExtractor.extract(from: props([
            ("Opacity", obj(["alpha": .double(0.25)])),
        ]))
        if o?.alpha != 0.25 { f.append("Opacity: alpha=0.25 missed") }
        // Out-of-range clamps.
        let hi = OpacityExtractor.extract(from: props([
            ("Opacity", obj(["alpha": .double(1.5)])),
        ]))
        if hi?.alpha != 1.0 { f.append("Opacity: upper clamp") }
        return f
    }

    // MARK: - Accent + Caret

    private static func runAccentCaretChecks() -> [String] {
        var f: [String] = []
        // Auto variant → `.auto`.
        if case .auto = AccentColorExtractor.extract(from: props([
            ("AccentColor", obj(["type": .string("auto")])),
        ])) ?? .inherit {} else { f.append("AccentColor: auto mismatch") }
        // Coloured variant.
        let cv = AccentColorExtractor.extract(from: props([
            ("AccentColor", obj([
                "type": .string("color"),
                "srgb": obj(["r": .double(1), "g": .double(0), "b": .double(0)]),
            ])),
        ]))
        if case .color = cv ?? .inherit {} else { f.append("AccentColor: colour mismatch") }
        // Caret passes colour through.
        let cc = CaretColorExtractor.extract(from: props([
            ("CaretColor", srgb(0, 0, 1)),
        ]))
        if cc?.hasAny != true { f.append("CaretColor: hasAny false") }
        return f
    }

    // MARK: - BackgroundImage

    private static func runBackgroundImageChecks() -> [String] {
        var f: [String] = []
        // Linear gradient, 45deg, two stops.
        let bi = BackgroundImageExtractor.extract(from: props([
            ("BackgroundImage", .array([
                obj([
                    "type": .string("linear-gradient"),
                    "angle": obj(["deg": .double(45)]),
                    "stops": .array([
                        obj(["color": srgb(1, 0, 0), "position": .null]),
                        obj(["color": srgb(0, 0, 1), "position": .null]),
                    ]),
                ])
            ])),
        ]))
        if bi?.layers.count != 1 { f.append("BgImage: one layer expected") }
        if case .linear(let angle, let stops) = bi?.layers.first ?? .none {
            if angle != 45 { f.append("BgImage: angle 45 missed") }
            if stops.count != 2 { f.append("BgImage: 2 stops expected") }
        } else { f.append("BgImage: not .linear") }
        // Radial with shape keyword as first fake-stop (brief's quirk).
        let radial = BackgroundImageExtractor.extract(from: props([
            ("BackgroundImage", .array([
                obj([
                    "type": .string("radial-gradient"),
                    "stops": .array([
                        obj(["color": obj(["original": .string("circle")]),
                             "position": .null]),
                        obj(["color": srgb(1, 0, 0), "position": .null]),
                        obj(["color": srgb(0, 0, 1), "position": .null]),
                    ]),
                ])
            ])),
        ]))
        if case .radial(let shape, let stops, _, _) = radial?.layers.first ?? .none {
            if shape != "circle" { f.append("BgImage: shape=circle missed") }
            if stops.count != 2 { f.append("BgImage: radial stops after shape-strip") }
        } else { f.append("BgImage: radial missed") }
        return f
    }

    // MARK: - Background sidecars

    private static func runBackgroundSidecarChecks() -> [String] {
        var f: [String] = []
        // Clip: PADDING_BOX → .paddingBox.
        let clip = BackgroundClipExtractor.extract(from: props([
            ("BackgroundClip", .array([.string("PADDING_BOX")])),
        ]))
        if clip?.mode != .paddingBox { f.append("Clip: paddingBox missed") }
        // Repeat: simple string form.
        let rep = BackgroundRepeatExtractor.extract(from: props([
            ("BackgroundRepeat", .array([.string("no-repeat")])),
        ]))
        if rep?.layers.first?.x != "no-repeat" { f.append("Repeat: no-repeat missed") }
        // Size: cover keyword.
        let sz = BackgroundSizeExtractor.extract(from: props([
            ("BackgroundSize", .array([.string("cover")])),
        ]))
        if sz?.layers.first != .cover { f.append("Size: cover missed") }
        return f
    }

    // MARK: - Blend + Isolation

    private static func runBlendAndIsolationChecks() -> [String] {
        var f: [String] = []
        // MixBlendMode: single uppercase string.
        let mb = BlendModeExtractor.extract(from: props([
            ("MixBlendMode", .string("MULTIPLY")),
        ]))
        if mb?.mix != .multiply { f.append("Blend: multiply missed") }
        // BackgroundBlendMode: array of strings → parallel list.
        let bb = BlendModeExtractor.extract(from: props([
            ("BackgroundBlendMode", .array([.string("SCREEN")])),
        ]))
        if bb?.background.first != .screen { f.append("Blend: bg screen missed") }
        // Isolation.
        let iso = IsolationExtractor.extract(from: props([
            ("Isolation", .string("ISOLATE")),
        ]))
        if iso?.mode != .isolate { f.append("Iso: isolate missed") }
        return f
    }
}
