//
//  CurrentColorBackgroundTests.swift
//  StyleConverterRuntimeTests — wave-49 lane A5.
//
//  `background-color: currentcolor` (css-color-4 §6.4). Every IR payload is
//  copied VERBATIM out of the wave-48 corpus run:
//    tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//      wpt__css-color__currentcolor-001.json  (inner div: Color green +
//                                              BackgroundColor currentColor)
//      wpt__css-color__currentcolor-002.json  (outer div: Color red +
//                                              BackgroundColor currentColor,
//                                              in THAT declaration order)
//      wpt__css-color__color-mix-currentcolor-001.json (the mix wire)
//  plus the bare-string shape from fixtures/properties/color/color-named.json.
//
//  The defect these pin: extractColor classifies the keyword
//  `.dynamic(.currentColor)`, `toSwiftUIColor()` returns nil for it, and
//  ColorApplier.fillView skipped the paint — the ancestor's `background-color:
//  red` showed through and the runtime rendered the WPT FAIL square
//  (ssim 0.9988, colorFailed, labDeltaE.mean 11.24 on the wave-48 gate).
//

import XCTest
@testable import StyleConverterRuntime

final class CurrentColorBackgroundTests: XCTestCase {

    // MARK: - Verbatim wire builders

    // `{"original":"currentColor"}` — the srgb-less keyword envelope.
    private let currentColor = IRValue.object(["original": .string("currentColor")])
    // `{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}`
    private let green = IRValue.object([
        "srgb": .object(["r": .double(0), "g": .double(0.5019607843137255), "b": .double(0)]),
        "original": .string("green"),
    ])
    // `{"srgb":{"r":1,"g":0,"b":0},"original":"red"}`
    private let red = IRValue.object([
        "srgb": .object(["r": .double(1), "g": .double(0), "b": .double(0)]),
        "original": .string("red"),
    ])

    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // MARK: - §6.4 resolution

    func testCurrentColorBackgroundTakesTheElementsOwnColor() {
        // currentcolor-001's inner div: color green, background currentcolor.
        let cfg = ColorExtractor.extract(from: props([
            ("Color", green),
            ("BackgroundColor", currentColor),
        ]))
        XCTAssertEqual(cfg?.background, .srgb(r: 0, g: 0.5019607843137255, b: 0, a: 1))
        // The foreground channel is untouched — the text renderer reads it.
        XCTAssertEqual(cfg?.foreground, .srgb(r: 0, g: 0.5019607843137255, b: 0, a: 1))
    }

    func testResolutionIsOrderIndependent() {
        // currentcolor-002's outer div lists BackgroundColor FIRST.
        let cfg = ColorExtractor.extract(from: props([
            ("BackgroundColor", currentColor),
            ("Color", red),
        ]))
        XCTAssertEqual(cfg?.background, .srgb(r: 1, g: 0, b: 0, a: 1))
    }

    func testBareStringKeywordIsRecognised() {
        // fixtures/properties/color/color-named.json ships the bare form.
        let cfg = ColorExtractor.extract(from: props([
            ("Color", red),
            ("BackgroundColor", .string("currentColor")),
        ]))
        XCTAssertEqual(cfg?.background, .srgb(r: 1, g: 0, b: 0, a: 1))
    }

    func testKeywordMatchIsCaseInsensitive() {
        // css-values-4 §4.1: CSS keywords are ASCII case-insensitive.
        let cfg = ColorExtractor.extract(from: props([
            ("Color", red),
            ("BackgroundColor", .object(["original": .string("CURRENTCOLOR")])),
        ]))
        XCTAssertEqual(cfg?.background, .srgb(r: 1, g: 0, b: 0, a: 1))
    }

    // MARK: - Deliberate non-resolutions

    func testCurrentColorWithNoColorStaysDynamic() {
        // Nothing to resolve against: the UA-ink bottom-out is capture-mode
        // split and belongs to the renderer, so the marker survives.
        let cfg = ColorExtractor.extract(from: props([
            ("BackgroundColor", currentColor),
        ]))
        guard case .dynamic(kind: .currentColor, raw: _)? = cfg?.background else {
            return XCTFail("expected the currentColor marker to survive")
        }
        XCTAssertNil(cfg?.background?.toSwiftUIColor())
    }

    func testColorMixContainingCurrentColorResolvesAgainstOwnColor() {
        // §6.4 applies INSIDE color-mix() too (css-color-5 §3 is defined on
        // the used colours). This is color-mix-currentcolor-001's PARENT,
        // whose `color` is red — Chrome 151 computes color(srgb 0.5 0.25098
        // 0). Evaluating it is only safe because BackgroundColorInheritance
        // now hands the CHILD the same mix unresolved, so the child
        // re-resolves it to green and the visible box is green.
        let mix = IRValue.object(["original": .object([
            "type": .string("color-mix"), "colorSpace": .string("srgb"),
            "color1": .string("currentColor"), "percent1": .double(50),
            "color2": .string("green"),
        ])])
        let cfg = ColorExtractor.extract(from: props([
            ("Color", red), ("BackgroundColor", mix),
        ]))
        guard case .srgb(let r, let g, let b, _)? = cfg?.background else {
            return XCTFail("expected the mix to resolve")
        }
        XCTAssertEqual(r, 0.5, accuracy: 0.002)
        XCTAssertEqual(g, 0.25098, accuracy: 0.002)
        XCTAssertEqual(b, 0, accuracy: 0.002)
    }

    func testTheSameMixOnAGreenElementIsGreen() {
        // …and the child's answer: color-mix(in srgb, green 50%, green).
        let mix = IRValue.object(["original": .object([
            "type": .string("color-mix"), "colorSpace": .string("srgb"),
            "color1": .string("currentColor"), "percent1": .double(50),
            "color2": .string("green"),
        ])])
        let cfg = ColorExtractor.extract(from: props([
            ("Color", green), ("BackgroundColor", mix),
        ]))
        guard case .srgb(let r, let g, let b, _)? = cfg?.background else {
            return XCTFail("expected the mix to resolve")
        }
        XCTAssertEqual(r, 0, accuracy: 0.002)
        XCTAssertEqual(g, 0.501961, accuracy: 0.002)
        XCTAssertEqual(b, 0, accuracy: 0.002)
    }

    // MARK: - Blast-radius guards

    func testStaticBackgroundsAreUnchanged() {
        // A real colour must never be hijacked by the element's `color`.
        let cfg = ColorExtractor.extract(from: props([
            ("Color", green), ("BackgroundColor", red),
        ]))
        XCTAssertEqual(cfg?.background, .srgb(r: 1, g: 0, b: 0, a: 1))
    }

    func testColorOnlyListStillYieldsNoBackground() {
        let cfg = ColorExtractor.extract(from: props([("Color", red)]))
        XCTAssertNil(cfg?.background)
        XCTAssertEqual(cfg?.foreground, .srgb(r: 1, g: 0, b: 0, a: 1))
    }

    func testEmptyListStillReturnsNil() {
        XCTAssertNil(ColorExtractor.extract(from: props([])))
    }
}
