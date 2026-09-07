//
//  RadialGradientShapeKeyTests.swift
//  retro R5 (audit A11#13) — `radial-gradient(circle, …)` keeps its circle.
//
//  The audit measured pairs-01 011_PW_Background_Effects_04
//  (`radial-gradient(circle, red, blue)`, `background-size: 100px` on a
//  200×80 box): web/Android ramp to the 100×80 tile's farthest corner
//  (√(100²+80²)/2 = 64.0px) while iOS ramped at 70.7px — and prescribed
//  fixing the `auto` height. The auto height was already right
//  (gradientTileSize → 100×80, pinned below); 70.7 is √2·100/2, the
//  ELLIPSE path's horizontal radius on that same tile. The real defect:
//  BackgroundImageExtractor read the ending shape only from the legacy
//  "shape-as-stop" recovery and never from the first-class `"shape"` key
//  the converter has emitted since Phase 12, so every modern `circle`
//  rendered as the ellipse default (css-images-3 §3.2.1).
//
//  The component below is the VERBATIM A11 run IR
//  (fidelity__pairwise__pairs-01/ir.json, pw_background_effects_04-012).
//

import XCTest
@testable import StyleConverterRuntime

final class RadialGradientShapeKeyTests: XCTestCase {

    private let verbatim011 = #"""
    {"id": "pw_background_effects_04-012", "name": "PW_Background_Effects_04", "properties": [
      {"type": "BackgroundImage", "data": [{"type": "radial-gradient", "shape": "circle", "stops": [
        {"color": {"srgb": {"r": 1.0, "g": 0.0, "b": 0.0}, "original": "red"}, "position": null},
        {"color": {"srgb": {"r": 0.0, "g": 0.0, "b": 1.0}, "original": "blue"}, "position": null}]}]},
      {"type": "BackgroundSize", "data": [{"w": {"px": 100.0}}]},
      {"type": "MaskBorderSlice", "data": 30.0},
      {"type": "BackdropFilter", "data": []},
      {"type": "MaskPositionX", "data": {"type": "left"}},
      {"type": "Width", "data": {"type": "length", "px": 200.0}},
      {"type": "Height", "data": {"type": "length", "px": 80.0}},
      {"type": "BackgroundColor", "data": {"srgb": {"r": 0.9058823529411765, "g": 0.2980392156862745, "b": 0.23529411764705882}, "original": "#e74c3c"}}
    ]}
    """#

    private func properties() throws -> [IRProperty] {
        try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion": 2, "minReaderVersion": 2, "components": [\(verbatim011)]}
        """.utf8)).components[0].properties
    }

    func testTheFirstClassShapeKeyReachesTheLayer() throws {
        let cfg = try XCTUnwrap(BackgroundImageExtractor.extract(from: try properties()))
        guard case .radial(let shape, let stops, _, _) = try XCTUnwrap(cfg.layers.first) else {
            return XCTFail("expected a radial layer")
        }
        XCTAssertEqual(shape, "circle", "`\"shape\": \"circle\"` must not be dropped for the ellipse default")
        XCTAssertEqual(stops.count, 2, "no stop was mistaken for a shape word")
    }

    func testTheAutoHeightWasAlreadyThePositioningAreaHeight() throws {
        // The half of the finding that was NOT the defect, pinned on the
        // 011 numbers so it cannot regress either: `100px` → w 100, h auto
        // → the box height (css-backgrounds-3 §2.9: no natural ratio or
        // size → auto is treated as 100%).
        let size = try XCTUnwrap(BackgroundSizeExtractor.extract(from: try properties()))
        XCTAssertEqual(size.layers, [.explicit(w: .px(100), h: .auto)])
        let tile = BackgroundImageGeometry.gradientTileSize(box: CGSize(width: 200, height: 80),
                                                            size: size.layers[0])
        XCTAssertEqual(tile, CGSize(width: 100, height: 80))
        // Circle end radius on that tile = the web/Android ramp…
        XCTAssertEqual(BackgroundGradientTileView.circleEndRadius(tile), 64.03, accuracy: 0.01)
        // …and the ellipse path's horizontal radius is the 70.7 the audit
        // measured on iOS — the number that identified the shape bug.
        XCTAssertEqual(GradientApplier.ellipseEndRadius(tile), 70.71, accuracy: 0.01)
    }

    func testLegacyShapeAsStopRecoveryStillWorks() throws {
        // Pre-Phase-12 wire: no `shape` key, the keyword leaked into stops[0].
        let cfg = try XCTUnwrap(BackgroundImageExtractor.extract(from: [IRProperty(
            type: "BackgroundImage",
            data: .array([.object(["type": .string("radial-gradient"), "stops": .array([
                .object(["color": .object(["original": .string("circle")]), "position": .null]),
                .object(["color": .object(["srgb": .object(["r": .double(1), "g": .double(0), "b": .double(0)])]), "position": .null]),
                .object(["color": .object(["srgb": .object(["r": .double(0), "g": .double(0), "b": .double(1)])]), "position": .null]),
            ])])]))]))
        guard case .radial(let shape, let stops, _, _) = try XCTUnwrap(cfg.layers.first) else {
            return XCTFail("expected a radial layer")
        }
        XCTAssertEqual(shape, "circle")
        XCTAssertEqual(stops.count, 2)
    }

    func testNoShapeKeyMeansTheEllipseDefault() throws {
        // `radial-gradient(red, blue)`: no key, no leak → nil → ellipse
        // (§3.2.1: the ending shape defaults to ellipse when omitted).
        let cfg = try XCTUnwrap(BackgroundImageExtractor.extract(from: [IRProperty(
            type: "BackgroundImage",
            data: .array([.object(["type": .string("radial-gradient"), "stops": .array([
                .object(["color": .object(["srgb": .object(["r": .double(1), "g": .double(0), "b": .double(0)])]), "position": .null]),
                .object(["color": .object(["srgb": .object(["r": .double(0), "g": .double(0), "b": .double(1)])]), "position": .null]),
            ])])]))]))
        guard case .radial(let shape, _, _, _) = try XCTUnwrap(cfg.layers.first) else {
            return XCTFail("expected a radial layer")
        }
        XCTAssertNil(shape)
    }

    func testANonDefaultSizeKeywordLeavesABreadcrumb() throws {
        // `size` is still unread on iOS; the breadcrumb is the honest signal.
        _ = BackgroundImageExtractor.extract(from: [IRProperty(
            type: "BackgroundImage",
            data: .array([.object(["type": .string("radial-gradient"), "shape": .string("circle"),
                                   "size": .string("closest-side"), "stops": .array([])])]))])
        // logOnce returns false once the key has already been logged.
        XCTAssertFalse(PropertyTracker.logOnce(key: "radial-gradient-size:closest-side", message: "dup"))
    }
}
