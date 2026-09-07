//
//  ClipNoneKeywordTests.swift
//  retro R5 (audit A6#4) — pins the ClipConfig nil / .some(.none) contract.
//
//  ClipConfig documents "`nil` ≡ not set; `.some(.none)` ≡ explicit
//  `clip-path: none`" (css-masking-1 §5.1: `none` = no clipping path), but
//  ClipExtractor wrote `cfg.shape = .none`, which on an Optional<ClipShape>
//  is Optional.none — ClipShape.none was never constructed anywhere in
//  Sources, so ClipApplier's `.none` arm and RootCanvasClip's `.some(.none)`
//  arm were dead code and the contract did not exist at runtime.
//
//  Payload: the VERBATIM RootCanvasClipTests document shape (wpt
//  css-masking/clip-path/clip-path-document-element's root bag) carrying
//  `"data": "none"`, decoded through the real v2 wire decoder.
//

import XCTest
@testable import StyleConverterRuntime

final class ClipNoneKeywordTests: XCTestCase {

    private func document(_ rootProperties: String) throws -> IRDocument {
        try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion": 2, "minReaderVersion": 2, "components": [
          {"id": "wpt__css-masking__clip-path__clip-path-document-element__0-047",
           "name": "wpt__css-masking__clip-path__clip-path-document-element__0",
           "properties": [\(rootProperties)], "meta": {"role": "body-root"}}
        ]}
        """.utf8))
    }

    func testExplicitNoneIsAConstructedClipShape() throws {
        let doc = try document(#"{ "type": "ClipPath", "data": "none" }"#)
        let cfg = try XCTUnwrap(ClipExtractor.extract(from: doc.components[0].properties))
        XCTAssertTrue(cfg.touched, "the property was recognised")
        // The half of the contract that used to be missing: SET to none,
        // not unset — a nil here means the Optional.none trap is back.
        XCTAssertNotNil(cfg.shape, "clip-path: none must produce ClipShape.none, not nil")
        XCTAssertEqual(cfg.shape, ClipShape.none)
        // And the document-element resolver still refuses it as a page clip
        // — RootCanvasClip's `.some(.none)` arm is now the live path.
        XCTAssertNil(RootCanvasClip.config(doc.components))
    }

    func testUnsetClipPathStaysNil() throws {
        // The other half: no ClipPath at all → no config (nil ≡ not set).
        let doc = try document("")
        XCTAssertNil(ClipExtractor.extract(from: doc.components[0].properties))
        XCTAssertNil(RootCanvasClip.config(doc.components))
    }

    func testAShapeStillResolves() throws {
        // Control: a real shape is untouched by the qualification.
        let doc = try document(#"{ "type": "ClipPath", "data": { "type": "circle", "px": 40 } }"#)
        let cfg = try XCTUnwrap(ClipExtractor.extract(from: doc.components[0].properties))
        guard case .some(.circle) = cfg.shape else { return XCTFail("expected a circle shape") }
        XCTAssertNotNil(RootCanvasClip.config(doc.components), "a real shape IS a page clip")
    }
}
