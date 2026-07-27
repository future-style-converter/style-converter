//
//  BackgroundImageCenterTests.swift
//  StyleConverterRuntimeTests — wave-21 IMAGES lane.
//
//  iOS extraction pins for:
//    A-RC8 <length> gradient centers ({"px":N} and typed lh/em wire, with
//          the font context resolved from the component's OWN FontSize /
//          LineHeight properties — pinned against the live wave21-gate
//          conic-gradient-line-height-relative-units-001 artifact:
//          FontSize {"px":50} + LineHeight {"multiplier":2} → 1lh = 100px)
//    A-RC2 cross-fade() layer extraction (normalized effective weights,
//          color-layer args, whole-function drop on any bad arg)
//  Mirrors runtimes/compose .../color/ColorExtractorGradientCenterTest.kt.
//

import XCTest
@testable import StyleConverterRuntime

final class BackgroundImageCenterTests: XCTestCase {

    // Concise IRValue builders (same conventions as ColorBackgroundTests).
    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }
    // Two-stop red/blue stops array matching the converter wire.
    private var stops: IRValue {
        .array([
            obj(["color": obj(["srgb": obj(["r": .double(1), "g": .double(0), "b": .double(0)])]),
                 "position": .null]),
            obj(["color": obj(["srgb": obj(["r": .double(0), "g": .double(0), "b": .double(1)])]),
                 "position": .null]),
        ])
    }

    // Extract a single-layer BackgroundImage config from a data payload.
    private func layer(_ data: IRValue,
                       extra: [(String, IRValue)] = []) -> BackgroundImageLayer? {
        BackgroundImageExtractor.extract(
            from: props([("BackgroundImage", data)] + extra))?.layers.first
    }

    func testPercentCenterExtractsAsFractions() {
        // Live wave21-gate conic-gradient-center wire: pos:{x:25,y:25}.
        let l = layer(.array([obj([
            "type": .string("conic-gradient"),
            "pos": obj(["x": .double(25), "y": .double(25)]),
            "stops": stops])]))
        guard case .conic(_, _, let cx, let cy) = l else {
            return XCTFail("expected conic layer, got \(String(describing: l))")
        }
        XCTAssertEqual(cx, .fraction(0.25))
        XCTAssertEqual(cy, .fraction(0.25))
        // fraction() is identity for FRACTION coords.
        XCTAssertEqual(cx.fraction(200), 0.25, accuracy: 1e-9)
    }

    func testPxCenterResolvesAgainstAxisSize() {
        let l = layer(.array([obj([
            "type": .string("radial-gradient"),
            "pos": obj(["x": obj(["px": .double(100)]), "y": obj(["px": .double(50)])]),
            "stops": stops])]))
        guard case .radial(_, _, let cx, let cy) = l else {
            return XCTFail("expected radial layer")
        }
        XCTAssertEqual(cx, .px(100))
        // `at 100px` on a 200px axis = the 0.5 fraction; 400px → 0.25.
        XCTAssertEqual(cx.fraction(200), 0.5, accuracy: 1e-9)
        XCTAssertEqual(cx.fraction(400), 0.25, accuracy: 1e-9)
        // Degenerate axis → CSS default center, never division by zero.
        XCTAssertEqual(cy.fraction(0), 0.5, accuracy: 1e-9)
    }

    func testLhCenterResolvesThroughFontContext() {
        // Pinned against the live artifact: FontSize 50px + LineHeight
        // multiplier 2 → 1lh = 100px (NOT the 1.2 normal ratio).
        let l = layer(.array([obj([
            "type": .string("conic-gradient"),
            "pos": obj(["x": obj(["original": obj(["v": .double(1), "u": .string("LH")])]),
                        "y": obj(["px": .double(50)])]),
            "stops": stops])]),
            extra: [("FontSize", obj(["px": .double(50)])),
                    ("LineHeight", obj(["multiplier": .double(2)]))])
        guard case .conic(_, _, let cx, _) = l else {
            return XCTFail("expected conic layer")
        }
        XCTAssertEqual(cx, .px(100))
    }

    func testDefaultFontContextUsesCssInitials() {
        // No FontSize/LineHeight properties → 16px / 19.2px (pin P5 ratio).
        let ctx = BackgroundImageExtractor.fontContext(of: [])
        XCTAssertEqual(ctx.fontSizePx, 16, accuracy: 1e-9)
        XCTAssertEqual(ctx.lineHeightPx, 19.2, accuracy: 1e-9)
    }

    func testAbsentPositionKeepsCssDefaultCenter() {
        let l = layer(.array([obj([
            "type": .string("radial-gradient"), "stops": stops])]))
        guard case .radial(_, _, let cx, let cy) = l else {
            return XCTFail("expected radial layer")
        }
        XCTAssertEqual(cx, .center)
        XCTAssertEqual(cy, .center)
    }

    func testCrossFadeExtractsNormalizedArgs() {
        // Six 10% gradients (target-alpha wire) → six 0.1 entries.
        let arg = obj(["weight": .double(10),
                       "image": obj(["type": .string("linear-gradient"), "stops": stops])])
        let l = layer(.array([obj([
            "type": .string("cross-fade"),
            "args": .array(Array(repeating: arg, count: 6))])]))
        guard case .crossFade(let args) = l else {
            return XCTFail("expected crossFade layer")
        }
        XCTAssertEqual(args.count, 6)
        XCTAssertTrue(args.allSatisfy { abs($0.weight - 0.1) < 1e-9 })
        XCTAssertTrue(args.allSatisfy {
            if case .linear = $0.layer { return true } else { return false }
        })
    }

    func testCrossFadeColorArgsBecomeColorLayersAt5050() {
        // premultiplied-alpha wire: two color args, no weights → 0.5 each.
        func colorArg(_ r: Double, _ g: Double, _ a: Double) -> IRValue {
            obj(["image": obj(["type": .string("color"),
                               "color": obj(["srgb": obj([
                                   "r": .double(r), "g": .double(g),
                                   "b": .double(0), "a": .double(a)])])])])
        }
        let l = layer(.array([obj([
            "type": .string("cross-fade"),
            "args": .array([colorArg(1, 0, 0.01), colorArg(0, 1, 1)])])]))
        guard case .crossFade(let args) = l else {
            return XCTFail("expected crossFade layer")
        }
        XCTAssertEqual(args[0].weight, 0.5, accuracy: 1e-9)
        // Alpha must survive extraction — the premultiplied compositing
        // (opacity × plusLighter) depends on it.
        guard case .color(.srgb(_, _, _, let a)) = args[0].layer else {
            return XCTFail("expected color layer arg")
        }
        XCTAssertEqual(a, 0.01, accuracy: 1e-9)
    }

    func testCrossFadeAcceptsFlattenedColorLayerWire() {
        // IRPropertySerializer.deepFlatten inlines {"type":"color",
        // "color":{…}} into {"type":"color","srgb":…,"original":…} — the
        // EXACT wire the converter run on the premultiplied-alpha fixture
        // produced. Both shapes must extract identically.
        let l = layer(.array([obj([
            "type": .string("cross-fade"),
            "args": .array([
                obj(["image": obj(["type": .string("color"),
                                   "srgb": obj(["r": .double(1), "g": .double(0),
                                                "b": .double(0), "a": .double(0.01)])])]),
                obj(["image": obj(["type": .string("color"),
                                   "srgb": obj(["r": .double(0), "g": .double(1),
                                                "b": .double(0)])])]),
            ])])]))
        guard case .crossFade(let args) = l else {
            return XCTFail("expected crossFade layer from flattened wire")
        }
        guard case .color(.srgb(_, _, _, let a)) = args[0].layer else {
            return XCTFail("expected color layer from flattened wire")
        }
        XCTAssertEqual(a, 0.01, accuracy: 1e-9)
    }

    func testCrossFadeWithBadArgDropsWholeLayer() {
        // Partial arg lists would re-weight the rest — must drop whole.
        let l = layer(.array([obj([
            "type": .string("cross-fade"),
            "args": .array([
                obj(["weight": .double(50), "image": obj(["type": .string("mystery")])]),
                obj(["weight": .double(50),
                     "image": obj(["type": .string("color"),
                                   "color": obj(["srgb": obj(["r": .double(0), "g": .double(1), "b": .double(0)])])])]),
            ])])]))
        XCTAssertNil(l)
    }
}
