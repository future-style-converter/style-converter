//
//  BordersSelfTest.swift
//  StyleEngine/borders — Phase 5.
//
//  Launch-time asserts for every Phase 5 extractor: sides (width/colour
//  /style across physical + logical), radius (physical + logical +
//  elliptical), outline, box-shadow (multi-layer + inset), border-image
//  (source/slice/width/outset/repeat), and the three keyword-only
//  miscellaneous properties. Same PASS/FAIL pattern as the prior
//  SelfTest modules.
//

import Foundation
import SwiftUI

enum BordersSelfTest {

    static func run() {
        var f: [String] = []
        f += runSidesChecks()
        f += runRadiusChecks()
        f += runOutlineChecks()
        f += runShadowChecks()
        f += runImageChecks()
        f += runMiscChecks()

        if f.isEmpty {
            print("[BordersSelfTest] PASS — border engine green")
        } else {
            print("[BordersSelfTest] FAIL — \(f.count) check(s) failed:")
            f.forEach { print("  - \($0)") }
        }
    }

    // MARK: - Helpers

    // Concise IRValue.object builder matching ColorBackgroundSelfTest.
    private static func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    // Property list builder.
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
        return obj(["srgb": obj(dict)])
    }

    // MARK: - Sides

    private static func runSidesChecks() -> [String] {
        var f: [String] = []
        // Physical widths + styles. All four sides, uniform.
        let c = BorderSideExtractor.extract(from: props([
            ("BorderTopWidth",    obj(["px": .double(3)])),
            ("BorderRightWidth",  obj(["px": .double(3)])),
            ("BorderBottomWidth", obj(["px": .double(3)])),
            ("BorderLeftWidth",   obj(["px": .double(3)])),
            ("BorderTopStyle",    .string("SOLID")),
            ("BorderRightStyle",  .string("SOLID")),
            ("BorderBottomStyle", .string("SOLID")),
            ("BorderLeftStyle",   .string("SOLID")),
            ("BorderTopColor",    srgb(1, 0, 0)),
            ("BorderRightColor",  srgb(1, 0, 0)),
            ("BorderBottomColor", srgb(1, 0, 0)),
            ("BorderLeftColor",   srgb(1, 0, 0)),
        ]))
        if c == nil { f.append("Sides: uniform config nil") }
        if c?.isUniform != true { f.append("Sides: isUniform false for identical sides") }
        if c?.top.width != 3 { f.append("Sides: top width != 3") }
        if c?.top.style != .solid { f.append("Sides: top style not .solid") }

        // Logical → physical mapping (LTR). BorderInlineStart should hit start.
        let log = BorderSideExtractor.extract(from: props([
            ("BorderInlineStartWidth", obj(["px": .double(4)])),
            ("BorderInlineStartStyle", .string("DASHED")),
        ]))
        if log?.start.width != 4 { f.append("Sides: logical inline-start width") }
        if log?.start.style != .dashed { f.append("Sides: logical inline-start style") }

        // Shorthand fan-out: BorderStyle applies to sides without explicit style.
        let sh = BorderSideExtractor.extract(from: props([
            ("BorderWidth", obj(["px": .double(2)])),
            ("BorderStyle", .string("DOUBLE")),
            ("BorderColor", srgb(0, 1, 0)),
        ]))
        if sh?.top.style != .double { f.append("Sides: shorthand style missed") }
        if sh?.end.width != 2 { f.append("Sides: shorthand width fan-out missed") }

        // Ten-keyword coverage — style parse.
        let keywords: [(String, BorderStyleValue)] = [
            ("NONE", .none), ("HIDDEN", .hidden), ("SOLID", .solid),
            ("DASHED", .dashed), ("DOTTED", .dotted), ("DOUBLE", .double),
            ("GROOVE", .groove), ("RIDGE", .ridge),
            ("INSET", .inset), ("OUTSET", .outset),
        ]
        for (raw, expected) in keywords {
            if extractBorderStyle(.string(raw)) != expected {
                f.append("Sides: keyword \(raw) parse")
            }
        }
        return f
    }

    // MARK: - Radius

    private static func runRadiusChecks() -> [String] {
        var f: [String] = []
        // Physical corners, 20pt each.
        let c = BorderRadiusExtractor.extract(from: props([
            ("BorderTopLeftRadius",     obj(["px": .double(20)])),
            ("BorderTopRightRadius",    obj(["px": .double(20)])),
            ("BorderBottomRightRadius", obj(["px": .double(20)])),
            ("BorderBottomLeftRadius",  obj(["px": .double(20)])),
        ]))
        if c?.topLeft.x != 20 { f.append("Radius: top-left x != 20") }
        if c?.hasAny != true { f.append("Radius: hasAny false") }
        if c?.maxRadius != 20 { f.append("Radius: maxRadius != 20") }

        // Elliptical "20 × 10".
        let ell = BorderRadiusExtractor.extract(from: props([
            ("BorderTopLeftRadius", obj([
                "x": obj(["px": .double(20)]),
                "y": obj(["px": .double(10)]),
            ])),
        ]))
        if ell?.topLeft.x != 20 || ell?.topLeft.y != 10 {
            f.append("Radius: elliptical x/y")
        }

        // Logical → physical mapping (LTR).
        let log = BorderRadiusExtractor.extract(from: props([
            ("BorderStartStartRadius", obj(["px": .double(8)])),
        ]))
        if log?.topLeft.x != 8 { f.append("Radius: logical start-start") }
        return f
    }

    // MARK: - Outline

    private static func runOutlineChecks() -> [String] {
        var f: [String] = []
        // Keyword-widths.
        let thin = OutlineExtractor.extract(from: props([
            ("OutlineWidth", obj(["type": .string("keyword"),
                                  "value": .string("THIN")])),
            ("OutlineStyle", .string("SOLID")),
        ]))
        if thin?.width != 1 { f.append("Outline: THIN != 1pt") }
        if thin?.style != .solid { f.append("Outline: style not solid") }
        if thin?.hasOutline != true { f.append("Outline: hasOutline false") }

        // Px width + offset + colour.
        let full = OutlineExtractor.extract(from: props([
            ("OutlineWidth",  obj(["type": .string("length"), "px": .double(5)])),
            ("OutlineStyle",  .string("DASHED")),
            ("OutlineColor",  srgb(0, 0, 1)),
            ("OutlineOffset", obj(["px": .double(4)])),
        ]))
        if full?.width != 5 || full?.offset != 4 { f.append("Outline: width/offset") }
        if full?.style != .dashed { f.append("Outline: dashed style") }
        if full?.color == nil { f.append("Outline: colour missing") }

        // Absent → nil.
        if OutlineExtractor.extract(from: props([])) != nil {
            f.append("Outline: absent should be nil")
        }
        return f
    }

    // MARK: - Shadow

    private static func runShadowChecks() -> [String] {
        var f: [String] = []
        // Empty array → nil (property present but no layers).
        if BoxShadowExtractor.extract(from: props([
            ("BoxShadow", .array([])),
        ])) != nil {
            f.append("Shadow: empty array should be nil")
        }

        // Single layer with spread + inset.
        let full = BoxShadowExtractor.extract(from: props([
            ("BoxShadow", .array([
                obj([
                    "x": obj(["px": .double(4)]),
                    "y": obj(["px": .double(6)]),
                    "blur": obj(["px": .double(10)]),
                    "spread": obj(["px": .double(2)]),
                    "c": srgb(0, 0, 0, 0.5),
                    "inset": .bool(true),
                ]),
            ])),
        ]))
        if full?.layers.count != 1 { f.append("Shadow: single layer missed") }
        if full?.layers.first?.inset != true { f.append("Shadow: inset flag lost") }
        if full?.layers.first?.spread != 2 { f.append("Shadow: spread lost") }

        // Multi-layer composition.
        let multi = BoxShadowExtractor.extract(from: props([
            ("BoxShadow", .array([
                obj(["x": obj(["px": .double(1)]), "y": obj(["px": .double(1)])]),
                obj(["x": obj(["px": .double(2)]), "y": obj(["px": .double(2)])]),
            ])),
        ]))
        if multi?.layers.count != 2 { f.append("Shadow: two-layer count") }

        // String fallback (calc expression) → nil.
        if BoxShadowExtractor.extract(from: props([
            ("BoxShadow", .string("calc(2px + 2px) 2px 8px #111")),
        ])) != nil {
            f.append("Shadow: string fallback should be nil")
        }
        return f
    }

    // MARK: - Image

    private static func runImageChecks() -> [String] {
        var f: [String] = []
        // `none` → nil via hasBorderImage.
        let none = BorderImageExtractor.extract(from: props([
            ("BorderImageSource", obj(["type": .string("none")])),
        ]))
        if none?.hasBorderImage == true { f.append("Image: none still has image") }

        // `url(border.png)` captured.
        let u = BorderImageExtractor.extract(from: props([
            ("BorderImageSource", obj(["type": .string("url"),
                                       "url": .string("border.png")])),
        ]))
        if case .url(let s) = u?.source ?? .none {
            if s != "border.png" { f.append("Image: url value") }
        } else { f.append("Image: url variant missed") }

        // Slice with fill.
        let sl = BorderImageExtractor.extract(from: props([
            ("BorderImageSlice", obj([
                "top":    obj(["type": .string("number"), "value": .double(30)]),
                "right":  obj(["type": .string("number"), "value": .double(30)]),
                "bottom": obj(["type": .string("number"), "value": .double(30)]),
                "left":   obj(["type": .string("number"), "value": .double(30)]),
                "fill":   .bool(true),
            ])),
        ]))
        if sl?.sliceFill != true { f.append("Image: fill flag lost") }
        if sl?.sliceTop?.value != 30 { f.append("Image: slice top value") }

        // Repeat split-axis.
        let rp = BorderImageExtractor.extract(from: props([
            ("BorderImageRepeat", obj([
                "horizontal": .string("REPEAT"),
                "vertical":   .string("STRETCH"),
            ])),
        ]))
        if rp?.repeatHorizontal != .repeatTile { f.append("Image: repeat H") }
        if rp?.repeatVertical != .stretch { f.append("Image: repeat V") }
        return f
    }

    // MARK: - Misc (box-decoration-break, corner-shape, border-boundary)

    private static func runMiscChecks() -> [String] {
        var f: [String] = []
        let m = BorderMiscExtractor.extract(from: props([
            ("BoxDecorationBreak", .string("CLONE")),
            ("CornerShape",        .string("BEVEL")),
        ]))
        if m?.decorationBreak != .clone { f.append("Misc: decoration-break CLONE") }
        if m?.cornerShape != .bevel { f.append("Misc: corner-shape BEVEL") }
        // Absent → nil.
        if BorderMiscExtractor.extract(from: props([])) != nil {
            f.append("Misc: absent should be nil")
        }
        return f
    }
}
