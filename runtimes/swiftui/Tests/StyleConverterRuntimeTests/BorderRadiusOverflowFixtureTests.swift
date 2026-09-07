//
//  BorderRadiusOverflowFixtureTests.swift
//  Retro R4 (A12#0 prescription) — the clip-policy gate on the REAL
//  overflow fixture, pinned on the converter's verbatim IR.
//
//  fixtures/properties/borders/border-radius-overflow-visible.json puts a
//  130×30 in-flow child inside a 100×40 radius-12 box three ways: overflow
//  visible (the child must spill past the curve — css-backgrounds-3 §4.3
//  curves only the element's own background), overflow hidden (the
//  element's own clip cuts the child at the curved padding edge —
//  css-overflow-3 §3.1.2), and a nowrap `_text` label that spills. The
//  visual-test rounded boxes only ever exposed the old unconditional
//  `.clipShape` through the harness debug label; this fixture makes the
//  gate measure the runtime.
//
//  The payload is the converter's output for that fixture, byte-for-byte
//  (`./gradlew :converter:run --args="convert --from css --to ir -i
//  fixtures/properties/borders/border-radius-overflow-visible.json -o
//  <dir>"`, committed converter @ 5aa92ed6), decoded through the runtime's
//  real IRDocument path (whitespace compacted to one component per
//  line — every token verbatim; the pretty-printed original is
//  scratch fixes/R4/fixture/ir-head/tmpOutput.json). The gate expression below is the SAME one
//  StyleBuilder.applyBoxDecoration passes to engineBorderRadius (retro R4
//  seam), so the pin covers the seam's inputs too. Mutation-proven: with
//  clipsDescendants' overflow branch and final return inverted the two
//  box assertions fail (fixes/R4 README, M-fixture).
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class BorderRadiusOverflowFixtureTests: XCTestCase {

    /// The whole IR v2 document, verbatim.
    static let fixtureIR = """
    {"irVersion": 2, "minReaderVersion": 2, "components": [
    {"id": "radiusoverflow_visible_childspills-001", "name": "RadiusOverflow_Visible_ChildSpills", "properties": [{"type": "Width", "data": {"type": "length", "px": 100.0}}, {"type": "Height", "data": {"type": "length", "px": 40.0}}, {"type": "BorderTopLeftRadius", "data": {"px": 12.0}}, {"type": "BorderTopRightRadius", "data": {"px": 12.0}}, {"type": "BorderBottomRightRadius", "data": {"px": 12.0}}, {"type": "BorderBottomLeftRadius", "data": {"px": 12.0}}, {"type": "BackgroundColor", "data": {"srgb": {"r": 0.6078431372549019, "g": 0.34901960784313724, "b": 0.7137254901960784}, "original": "#9b59b6"}}, {"type": "PaddingTop", "data": {"px": 5.0}}, {"type": "OverflowX", "data": "VISIBLE"}, {"type": "OverflowY", "data": "VISIBLE"}]},
    {"id": "spill-002", "name": "spill", "properties": [{"type": "Width", "data": {"type": "length", "px": 130.0}}, {"type": "Height", "data": {"type": "length", "px": 30.0}}, {"type": "BackgroundColor", "data": {"srgb": {"r": 0.1803921568627451, "g": 0.8, "b": 0.44313725490196076}, "original": "#2ecc71"}}], "slot": {"parent": "radiusoverflow_visible_childspills-001"}},
    {"id": "radiusoverflow_hidden_childclipped-003", "name": "RadiusOverflow_Hidden_ChildClipped", "properties": [{"type": "Width", "data": {"type": "length", "px": 100.0}}, {"type": "Height", "data": {"type": "length", "px": 40.0}}, {"type": "BorderTopLeftRadius", "data": {"px": 12.0}}, {"type": "BorderTopRightRadius", "data": {"px": 12.0}}, {"type": "BorderBottomRightRadius", "data": {"px": 12.0}}, {"type": "BorderBottomLeftRadius", "data": {"px": 12.0}}, {"type": "BackgroundColor", "data": {"srgb": {"r": 0.6078431372549019, "g": 0.34901960784313724, "b": 0.7137254901960784}, "original": "#9b59b6"}}, {"type": "PaddingTop", "data": {"px": 5.0}}, {"type": "OverflowX", "data": "HIDDEN"}, {"type": "OverflowY", "data": "HIDDEN"}]},
    {"id": "spill-004", "name": "spill", "properties": [{"type": "Width", "data": {"type": "length", "px": 130.0}}, {"type": "Height", "data": {"type": "length", "px": 30.0}}, {"type": "BackgroundColor", "data": {"srgb": {"r": 0.1803921568627451, "g": 0.8, "b": 0.44313725490196076}, "original": "#2ecc71"}}], "slot": {"parent": "radiusoverflow_hidden_childclipped-003"}},
    {"id": "radiusoverflow_visible_textspills-005", "name": "RadiusOverflow_Visible_TextSpills", "properties": [{"type": "Width", "data": {"type": "length", "px": 100.0}}, {"type": "Height", "data": {"type": "length", "px": 40.0}}, {"type": "BorderTopLeftRadius", "data": {"px": 12.0}}, {"type": "BorderTopRightRadius", "data": {"px": 12.0}}, {"type": "BorderBottomRightRadius", "data": {"px": 12.0}}, {"type": "BorderBottomLeftRadius", "data": {"px": 12.0}}, {"type": "BackgroundColor", "data": {"srgb": {"r": 0.6078431372549019, "g": 0.34901960784313724, "b": 0.7137254901960784}, "original": "#9b59b6"}}, {"type": "Color", "data": {"srgb": {"r": 1.0, "g": 1.0, "b": 1.0}, "original": "#ffffff"}}, {"type": "FontSize", "data": {"px": 16.0, "original": {"type": "length", "px": 16.0}}}, {"type": "LineHeight", "data": {"original": {"type": "length", "px": 40.0}}}, {"type": "WhiteSpace", "data": "NOWRAP"}, {"type": "OverflowX", "data": "VISIBLE"}, {"type": "OverflowY", "data": "VISIBLE"}], "text": "OVERFLOWING LABEL TEXT"}
    ]}
    """

    /// Decode through the runtime's real loader (JSONDecoder → IRDocument),
    /// then index root components by fixture name.
    private func roots() throws -> [String: IRComponent] {
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(Self.fixtureIR.utf8))
        XCTAssertEqual(doc.irVersion, 2)
        return Dictionary(uniqueKeysWithValues: doc.components.filter { $0.slot == nil }.map { ($0.name, $0) })
    }

    /// Exactly the expression StyleBuilder feeds the applier (retro R4
    /// seam patch): the element's own overflow clip after css-overflow-3
    /// §3.1 coercion, and "rectangular background layers paint" unless
    /// background-clip: text routes the gradient through the label.
    private func clips(_ c: IRComponent) -> Bool {
        let s = StyleBuilder.build(from: c.properties)
        return BorderRadiusApplier.clipsDescendants(
            radius: s.borderRadius,
            sides: s.borderSides,
            overflowClips: StyleBuilder.ownOverflowClips(s.visibility),
            hasBackgroundLayers: s.backgroundClip?.mode != .text && s.backgroundImage?.hasAny == true)
    }

    /// overflow: visible spelled out (`OverflowX/Y VISIBLE` on the wire, so
    /// the visibility config IS touched) → self-paint, no descendant clip.
    func testVisibleOverflowSelfPaints() throws {
        let c = try XCTUnwrap(roots()["RadiusOverflow_Visible_ChildSpills"])
        let s = StyleBuilder.build(from: c.properties)
        XCTAssertEqual(s.borderRadius?.hasAny, true, "radius 12 on all four corners")
        XCTAssertEqual(s.visibility?.touched, true, "explicit overflow: visible reaches the config")
        XCTAssertFalse(StyleBuilder.ownOverflowClips(s.visibility), "visible establishes no clip")
        XCTAssertFalse(clips(c), "the 130px child must spill past the 12px curve (css-backgrounds-3 §4.3)")
    }

    /// The control: overflow: hidden → the element's own clip → legacy mode.
    func testHiddenOverflowKeepsTheClip() throws {
        let c = try XCTUnwrap(roots()["RadiusOverflow_Hidden_ChildClipped"])
        XCTAssertTrue(StyleBuilder.ownOverflowClips(StyleBuilder.build(from: c.properties).visibility))
        XCTAssertTrue(clips(c), "hidden clips the child at the curved padding edge (css-overflow-3 §3.1.2)")
    }

    /// Text variant: colour / font / nowrap change nothing about the gate.
    func testTextVariantSelfPaints() throws {
        let c = try XCTUnwrap(roots()["RadiusOverflow_Visible_TextSpills"])
        XCTAssertEqual(c.text, "OVERFLOWING LABEL TEXT", "`_text` survives the converter")
        XCTAssertFalse(clips(c))
    }

    /// The spilling child itself has no radius → the modifier is identity
    /// either way, and the gate answers false so nothing installs a clip.
    /// The v2 decoder folds slotted components into their parent's
    /// `children` (schema/spec/03-children.md — flat wire in, tree out), so
    /// the child is reached through each root, not the flat list.
    func testSpillChildHasNoRadiusAndNeverClips() throws {
        let r = try roots()
        let spills = ["RadiusOverflow_Visible_ChildSpills", "RadiusOverflow_Hidden_ChildClipped"]
            .compactMap { r[$0]?.children?.first { $0.name == "spill" } }
        XCTAssertEqual(spills.count, 2, "one folded `spill` child under each box")
        for child in spills {
            XCTAssertNil(StyleBuilder.build(from: child.properties).borderRadius)
            XCTAssertFalse(clips(child))
        }
    }
}
