//
//  BordersTests.swift
//  StyleConverterRuntimeTests
//
//  XCTest port of the Phase 5 borders launch self-test (formerly
//  StyleEngine/borders/BordersSelfTest.swift): sides (width/colour
//  /style across physical + logical), radius (physical + logical +
//  elliptical), outline, box-shadow (multi-layer + inset), border-image
//  (source/slice/width/outset/repeat), and the three keyword-only
//  miscellaneous properties. Check bodies unchanged — every failure-
//  collection site survives 1:1; the print-PASS/FAIL summary becomes
//  one XCTFail per collected failure name.
//

import Foundation
import SwiftUI
import XCTest
// @testable: the Phase 5 extractors are internal to the runtime module.
@testable import StyleConverterRuntime

final class BordersTests: XCTestCase {

    // One test per section of the original self-test.
    func testSidesChecks()   { for failure in Self.runSidesChecks()   { XCTFail(failure) } }
    func testRadiusChecks()  { for failure in Self.runRadiusChecks()  { XCTFail(failure) } }
    func testOutlineChecks() { for failure in Self.runOutlineChecks() { XCTFail(failure) } }
    func testShadowChecks()  { for failure in Self.runShadowChecks()  { XCTFail(failure) } }
    func testImageChecks()   { for failure in Self.runImageChecks()   { XCTFail(failure) } }
    func testMiscChecks()    { for failure in Self.runMiscChecks()    { XCTFail(failure) } }

    // Width-conditional dashed intervals — the ONE shared helper both the
    // uniform fast path and the per-side Canvas path now consume (they
    // previously hard-coded 6w:4w vs 3w:2w and drifted). Pins the same
    // pairs as the Android test (BorderFidelityWave2Test) so the two
    // natives cannot diverge without a test failing on each side.
    func testDashedIntervals() {
        // Thin branch (w < 3): the w=2 Chromium-measured tuning must stay
        // byte-identical so committed thin-dash baselines don't shift.
        XCTAssertEqual(BorderSideApplier.dashedIntervals(width: 2), [12, 8])
        // Thick branch: at the fixture's w=5 Chromium paints ~10px on /
        // 5px off (~11 dashes on a 160px edge) — a 2w:1w rhythm.
        XCTAssertEqual(BorderSideApplier.dashedIntervals(width: 5), [10, 5])
        // Boundary: w=3 (CSS `medium`) is the first thick width — 2w:1w.
        XCTAssertEqual(BorderSideApplier.dashedIntervals(width: 3), [6, 3])
    }

    // Per-edge dash fitting — the edge must start AND end on a full dash
    // (Chromium adjusts the pair per edge; the fixed nominal rhythm
    // truncated the final dash mid-way at three corners). Pins the same
    // values as the Android test (BorderFidelityWave3Test) so the two
    // natives share one fit rule.
    func testFittedDashIntervals() {
        // Exact fit: 160pt edge at w=5 → 11 dashes + 10 gaps = 160
        // exactly with the nominal [10, 5]; scale stays 1.0.
        XCTAssertEqual(BorderSideApplier.fittedDashIntervals(length: 160, width: 5), [10, 5])
        // Non-exact: 97pt edge at w=5 → n = round-half-up(102/15) = 7
        // dashes; nominal span 100 shrinks by 0.97 → [9.7, 4.85].
        let fitted = BorderSideApplier.fittedDashIntervals(length: 97, width: 5)
        XCTAssertEqual(fitted[0], 9.7, accuracy: 0.001)
        XCTAssertEqual(fitted[1], 4.85, accuracy: 0.001)
        // The invariant the fit exists for: 7 dashes + 6 gaps == edge.
        XCTAssertEqual(7 * fitted[0] + 6 * fitted[1], 97, accuracy: 0.001)
        // Edge shorter than one dash → the lone dash spans it (solid).
        XCTAssertEqual(BorderSideApplier.fittedDashIntervals(length: 8, width: 5)[0],
                       8, accuracy: 0.001)
        // Degenerate zero-length edge falls back to the nominal rhythm
        // so callers never see NaN intervals.
        XCTAssertEqual(BorderSideApplier.fittedDashIntervals(length: 0, width: 5), [10, 5])
    }

    // Per-side dotted dot fitting — port of Android's dottedDotCount.
    // Pins the same shapes as BorderFidelityWave2Test on Android so the
    // natives cannot disagree on a dot count (a one-dot delta puts the
    // whole edge out of phase against the web capture).
    func testDottedDotCount() {
        // Borders_C01 shape: 240px side, 8px dots → (240-8)/16 = 14.5
        // pitches; round-half-UP gives 15 pitches → 16 dots (Chromium).
        XCTAssertEqual(BorderSideApplier.dottedDotCount(length: 240, width: 8), 16)
        // Borders_C04 shape: 200px side, 6px dots → 17 dots.
        XCTAssertEqual(BorderSideApplier.dottedDotCount(length: 200, width: 6), 17)
        // Degenerate shapes: one dot when the edge fits only itself,
        // nothing on zero-length edges or zero-width dots.
        XCTAssertEqual(BorderSideApplier.dottedDotCount(length: 6, width: 6), 1)
        XCTAssertEqual(BorderSideApplier.dottedDotCount(length: 0, width: 6), 0)
        XCTAssertEqual(BorderSideApplier.dottedDotCount(length: 100, width: 0), 0)
    }

    // Blink Dark()/Light() 3D palette (color.cc) — dark band scales
    // every channel by the SUBTRACTIVE multiplier max(0, (v−0.33)/v)
    // with v = max channel; light band is the declared colour except
    // black, which falls back to Blink's kLightenedBlack rgb(84,84,84).
    // Mirrors the Android shade() test so the groove/ridge/inset/outset
    // bands stay identical across natives.
    func testShadePalette() {
        let base = Color(red: 239.0 / 255.0, green: 100.0 / 255.0,
                         blue: 50.0 / 255.0, opacity: 0.8)
        // Light band: exactly the declared colour, no blend.
        XCTAssertEqual(BorderSideApplier.shade(base, light: true), base)
        // Dark band: read back components via UIColor bridging (same
        // conversion the applier itself uses). v = 239/255 here, so the
        // multiplier is (0.937−0.33)/0.937 = 0.648 — the point the old
        // flat ×0.65 was measured at, so these three pins carry over.
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        XCTAssertTrue(UIColor(BorderSideApplier.shade(base, light: false))
            .getRed(&r, green: &g, blue: &b, alpha: &a))
        XCTAssertEqual(r * 255, 155, accuracy: 1)   // 239 × 0.648 ≈ 155
        XCTAssertEqual(g * 255, 65, accuracy: 1)    // 100 × 0.648 ≈ 65
        XCTAssertEqual(b * 255, 32.5, accuracy: 1)  // 50 × 0.648 ≈ 32.4
        XCTAssertEqual(a, 0.8, accuracy: 0.001)     // alpha untouched
    }

    // The two points where Blink's model DIVERGES from the old flat
    // ×0.65 — a mid grey (subtractive shift darkens it twice as hard)
    // and black (light band falls back to kLightenedBlack instead of
    // collapsing both bands to black).
    func testShadeBlinkModelDivergesFromFlatMultiplier() {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        // Mid grey 0.5: Dark() = 0.5 − 0.33 = 0.17 (flat ×0.65 gave 0.325).
        let grey = Color(red: 0.5, green: 0.5, blue: 0.5, opacity: 1)
        XCTAssertTrue(UIColor(BorderSideApplier.shade(grey, light: false))
            .getRed(&r, green: &g, blue: &b, alpha: &a))
        XCTAssertEqual(r, 0.17, accuracy: 0.005)
        XCTAssertEqual(g, 0.17, accuracy: 0.005)
        XCTAssertEqual(b, 0.17, accuracy: 0.005)
        // Black: dark band clamps at black (max(0, ·))…
        let black = Color(red: 0, green: 0, blue: 0, opacity: 1)
        XCTAssertTrue(UIColor(BorderSideApplier.shade(black, light: false))
            .getRed(&r, green: &g, blue: &b, alpha: &a))
        XCTAssertEqual(r, 0, accuracy: 0.001)
        // …and the LIGHT band lifts to rgb(84,84,84) so `groove black`
        // still shows two bands (Blink Color::Light() black fast path).
        XCTAssertTrue(UIColor(BorderSideApplier.shade(black, light: true))
            .getRed(&r, green: &g, blue: &b, alpha: &a))
        XCTAssertEqual(r * 255, 84, accuracy: 1)
        XCTAssertEqual(g * 255, 84, accuracy: 1)
        XCTAssertEqual(b * 255, 84, accuracy: 1)
    }

    // Blink per-side 3D band order for groove/ridge: each half is a
    // sub-border (groove = inset-outer + outset-inner, ridge inverted)
    // darkened via `dark ⇔ (side==top||side==left) == (substyle==inset)`
    // — so bottom/right sides INVERT the band order. The wave-2 code
    // painted the top/left order on all four sides; this truth table
    // pins all 2 styles × 2 side classes.
    func testGrooveRidgePerSideBands() {
        let base = Color(red: 0.8, green: 0.4, blue: 0.2, opacity: 1)
        let dark = BorderSideApplier.shade(base, light: false)
        let light = BorderSideApplier.shade(base, light: true)
        // groove, top/left: dark outer + light inner (carved in).
        let gTL = BorderSideApplier.grooveRidgeBandColours(
            style: .groove, isTopLeft: true, base: base)
        XCTAssertEqual(gTL.outer, dark)
        XCTAssertEqual(gTL.inner, light)
        // groove, bottom/right: INVERTED — light outer + dark inner.
        let gBR = BorderSideApplier.grooveRidgeBandColours(
            style: .groove, isTopLeft: false, base: base)
        XCTAssertEqual(gBR.outer, light)
        XCTAssertEqual(gBR.inner, dark)
        // ridge is the exact inverse of groove on the same side.
        let rTL = BorderSideApplier.grooveRidgeBandColours(
            style: .ridge, isTopLeft: true, base: base)
        XCTAssertEqual(rTL.outer, light)
        XCTAssertEqual(rTL.inner, dark)
        let rBR = BorderSideApplier.grooveRidgeBandColours(
            style: .ridge, isTopLeft: false, base: base)
        XCTAssertEqual(rBR.outer, dark)
        XCTAssertEqual(rBR.inner, light)
    }

    // Routing pin for uniform dashed/dotted borders (wave-2 regression
    // guard): any non-zero border-radius routes to the continuous
    // rounded strokeBorder perimeter (phase accrual is the lesser evil
    // on rounded corners); radius zero keeps the per-side Canvas with
    // its Chromium-style per-edge pattern reset.
    func testDashedDottedRadiusRouting() {
        // Uniform 4pt dashed border on all four sides.
        let dashed = BorderSideConfig(width: 4, color: nil, style: .dashed)
        let cfg = AllBordersConfig(top: dashed, end: dashed,
                                   bottom: dashed, start: dashed)
        // Non-zero radius (one rounded corner suffices — hasAny).
        var rounded = BorderRadiusConfig()
        rounded.topLeft = BorderRadiusCorner(uniform: 8)
        // radius>0 → continuous strokeBorder perimeter.
        XCTAssertTrue(BorderSideApplier.usesRoundedDashPerimeter(
            cfg: cfg, radius: rounded))
        // radius==0 (all-square config OR nil) → per-side Canvas.
        XCTAssertFalse(BorderSideApplier.usesRoundedDashPerimeter(
            cfg: cfg, radius: BorderRadiusConfig()))
        XCTAssertFalse(BorderSideApplier.usesRoundedDashPerimeter(
            cfg: cfg, radius: nil))
        // Dotted routes identically to dashed.
        let dotted = BorderSideConfig(width: 4, color: nil, style: .dotted)
        let dottedCfg = AllBordersConfig(top: dotted, end: dotted,
                                         bottom: dotted, start: dotted)
        XCTAssertTrue(BorderSideApplier.usesRoundedDashPerimeter(
            cfg: dottedCfg, radius: rounded))
        // Solid never takes the dash perimeter (it has its own path B).
        let solid = BorderSideConfig(width: 4, color: nil, style: .solid)
        let solidCfg = AllBordersConfig(top: solid, end: solid,
                                        bottom: solid, start: solid)
        XCTAssertFalse(BorderSideApplier.usesRoundedDashPerimeter(
            cfg: solidCfg, radius: rounded))
        // Non-uniform dashed needs the per-side Canvas even when rounded.
        var mixed = cfg
        mixed.bottom.width = 8
        XCTAssertFalse(BorderSideApplier.usesRoundedDashPerimeter(
            cfg: mixed, radius: rounded))
        // Recipe pins for the continuous path: dashed reuses the shared
        // nominal rhythm; dotted keeps the round-cap near-zero dash.
        XCTAssertEqual(BorderSideApplier.roundedDashStrokeStyle(.dashed, width: 5).dash,
                       [10, 5])
        let dotStyle = BorderSideApplier.roundedDashStrokeStyle(.dotted, width: 6)
        XCTAssertEqual(dotStyle.dash, [0.01, 12])
        XCTAssertEqual(dotStyle.lineCap, .round)
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
