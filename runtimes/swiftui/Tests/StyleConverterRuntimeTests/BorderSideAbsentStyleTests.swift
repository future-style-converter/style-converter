//
//  BorderSideAbsentStyleTests.swift
//  Retro R4 (A11#5) — a width-only border side paints NOTHING.
//
//  CSS 2.1 §8.5.3 / css-backgrounds-3 §3.2: the initial `border-style` is
//  `none`, and `none` makes the used border-width zero ("Color and width
//  are ignored"). So `border-block-start-width: 6px` with no style is no
//  border at all — no band, no content inset. iOS painted it as a solid
//  currentColor band: BorderSideConfig.hasBorder let a nil style through
//  and BorderSideApplier defaulted `style ?? .solid`. Measured on
//  pairs-02 PW_Borders_Color_01: iOS (238,238,238) top/right bands where
//  web and Android show the fill (167,139,250); ≥8 IOS-ODD rows in that
//  fixture with Android == web at 1.0.
//
//  Payloads are VERBATIM converter output for
//  fixtures/fidelity/pairwise/pairs-02.json
//  (`./gradlew :converter:run --args="convert --from css --to ir -i
//  fixtures/fidelity/pairwise/pairs-02.json -o <dir>"`), border longhands
//  only, decoded through the runtime's own IRProperty Decodable.
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class BorderSideAbsentStyleTests: XCTestCase {

    /// PW_Borders_Color_01: `border-inline-start-color:#111;
    /// border-block-start-width:6px; border-inline-end-width:6px` — two
    /// width-only sides and one colour-only side, NO style anywhere.
    static let pwBordersColor01 = """
    [{"type": "BorderInlineStartColor", "data": {"srgb": {"r": 0.06666666666666667, "g": 0.06666666666666667, "b": 0.06666666666666667}, "original": "#111"}},
     {"type": "BorderBlockStartWidth", "data": {"px": 6.0}},
     {"type": "BorderInlineEndWidth", "data": {"px": 6.0}}]
    """

    /// PW_Borders_Images_03: `border-top-width: 8px` (+ a colour-only
    /// inline-start) — the white 8px band iOS alone painted.
    static let pwBordersImages03 = """
    [{"type": "BorderTopWidth", "data": {"px": 8.0}},
     {"type": "BorderInlineStartColor", "data": {"srgb": {"r": 0.06666666666666667, "g": 0.06666666666666667, "b": 0.06666666666666667}, "original": "#111"}}]
    """

    /// Decode a verbatim wire fragment through the runtime's IR model.
    private func decode(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// The defect: both width-only sides must report no border, so the
    /// applier's `hasAny` fast path returns identity and the content inset
    /// (bandInsets) stays zero — used width 0 per §8.5.3.
    func testWidthOnlySidesAreNoBorder_PWBordersColor01() throws {
        let cfg = try XCTUnwrap(BorderSideExtractor.extract(from: decode(Self.pwBordersColor01)))
        // The extractor still records the declared width — only the
        // paint/inset decision changes.
        XCTAssertEqual(cfg.top.width, 6)
        XCTAssertNil(cfg.top.style, "wire carries no style → nil, i.e. CSS `none`")
        XCTAssertFalse(cfg.top.hasBorder, "block-start: width-only → no border")
        XCTAssertFalse(cfg.end.hasBorder, "inline-end: width-only → no border")
        XCTAssertFalse(cfg.start.hasBorder, "colour-only → no border")
        XCTAssertFalse(cfg.hasAny, "nothing paints → BorderSideApplier identity")
        XCTAssertEqual(AllBordersConfig.bandInsets(cfg), EdgeInsets(), "used width 0 → no content inset")
    }

    /// Second corpus carrier — a single width-only physical side.
    func testWidthOnlyTopIsNoBorder_PWBordersImages03() throws {
        let cfg = try XCTUnwrap(BorderSideExtractor.extract(from: decode(Self.pwBordersImages03)))
        XCTAssertEqual(cfg.top.width, 8)
        XCTAssertFalse(cfg.top.hasBorder)
        XCTAssertFalse(cfg.hasAny)
    }

    /// Control: the SAME payload plus a declared visible style restores
    /// the band on exactly that side — proving the gate is the style, not
    /// the width or the colour.
    func testDeclaredStyleRestoresTheBand() throws {
        let withStyle = Self.pwBordersColor01.replacingOccurrences(
            of: "]", with: ", {\"type\": \"BorderBlockStartStyle\", \"data\": \"SOLID\"}]")
        let cfg = try XCTUnwrap(BorderSideExtractor.extract(from: decode(withStyle)))
        XCTAssertTrue(cfg.top.hasBorder, "declared solid + 6px → paints")
        XCTAssertFalse(cfg.end.hasBorder, "inline-end still width-only → still none")
        XCTAssertEqual(AllBordersConfig.bandInsets(cfg).top, 6)
        XCTAssertEqual(AllBordersConfig.bandInsets(cfg).trailing, 0)
    }

    /// Explicit `none` / `hidden` keep behaving like the absent style —
    /// §8.5.3 groups all three as width 0.
    func testExplicitNoneAndHiddenStayOff() {
        var s = BorderSideConfig()
        s.width = 6
        // Spelled out: on an Optional<BorderStyleValue> a bare `.none`
        // resolves to Optional.none (nil) — the ABSENT case, not the CSS
        // keyword. (Caught by the M2 mutation run: this line failed as a
        // nil-style case would, which is not what the pin claims.)
        s.style = BorderStyleValue.none
        XCTAssertFalse(s.hasBorder)
        s.style = .hidden
        XCTAssertFalse(s.hasBorder)
        s.style = .solid
        XCTAssertTrue(s.hasBorder, "control: solid at 6px paints")
    }

    /// Regression guard for the OTHER half of the rule: a style-only side
    /// (`border-*-style: dotted`, no width) still paints at the CSS
    /// `medium` 3px initial (css-backgrounds-3 §3.3) — borders/016's
    /// dotted block-end border must not disappear again.
    func testStyleOnlySideKeepsMediumWidth() {
        var s = BorderSideConfig()
        s.style = .dotted
        XCTAssertEqual(s.effectiveWidth, 3)
        XCTAssertTrue(s.hasBorder)
    }

    /// The shared routing predicate BorderRadiusApplier's clip gate reads:
    /// TRUE iff BorderSideApplier's own stroke follows the rounded shape
    /// (fast path B: uniform solid with explicit width; fast path C:
    /// uniform dashed/dotted at a non-zero radius). Pinned so the two
    /// appliers can never disagree about which borders are self-rounding.
    func testPaintsAlongRoundedShapeMirrorsBodyRouting() throws {
        var r = BorderRadiusConfig()
        r.topLeft = BorderRadiusCorner(uniform: 12)
        // No border config at all → nothing can poke past the curve.
        XCTAssertTrue(BorderSideApplier.paintsAlongRoundedShape(cfg: nil, radius: r))
        // Width-only sides (hasAny false) → nothing paints → true.
        let widthOnly = try XCTUnwrap(BorderSideExtractor.extract(from: decode(Self.pwBordersColor01)))
        XCTAssertTrue(BorderSideApplier.paintsAlongRoundedShape(cfg: widthOnly, radius: r))
        // Uniform solid 3px (Avatar_Circle's `border: 3px solid white`).
        func uniform(_ style: BorderStyleValue?, width: CGFloat?) -> AllBordersConfig {
            var side = BorderSideConfig()
            side.width = width; side.style = style; side.color = .white
            var c = AllBordersConfig()
            c.top = side; c.end = side; c.bottom = side; c.start = side
            return c
        }
        XCTAssertTrue(BorderSideApplier.paintsAlongRoundedShape(cfg: uniform(.solid, width: 3), radius: r))
        // Uniform dashed WITH radius → rounded strokeBorder perimeter.
        XCTAssertTrue(BorderSideApplier.paintsAlongRoundedShape(cfg: uniform(.dashed, width: 2), radius: r))
        // Uniform dashed at radius zero → per-side Canvas (straight edges).
        XCTAssertFalse(BorderSideApplier.paintsAlongRoundedShape(cfg: uniform(.dashed, width: 2), radius: nil))
        // Style-only uniform solid (no explicit width) → Canvas path.
        XCTAssertFalse(BorderSideApplier.paintsAlongRoundedShape(cfg: uniform(.solid, width: nil), radius: r))
        // 3D / double styles → Canvas path.
        XCTAssertFalse(BorderSideApplier.paintsAlongRoundedShape(cfg: uniform(.double, width: 4), radius: r))
        // Per-side: only the top declared → straight top stroke.
        var perSide = AllBordersConfig()
        perSide.top.width = 3; perSide.top.style = .solid
        XCTAssertFalse(BorderSideApplier.paintsAlongRoundedShape(cfg: perSide, radius: r))
    }
}
