//
//  RootCanvasClipTests.swift
//  Wave 49 (lane A4) — pins for the document-element clip resolver
//  (css-masking-1 §5 on the root element).
//
//  The document below is the VERBATIM wave-48 gate IR for WPT
//  css-masking/clip-path/clip-path-document-element
//  (tools/titan/runs/wave48-final/sections/css-masking/per-test-ir/
//  wpt__css-masking__clip-path__clip-path-document-element.json), decoded
//  through the real v2 wire decoder — so a converter wire change fails here
//  before it silently un-clips the page again.
//
//  The SHAPE half (the `.clipShape` geometry) is not pinned: SwiftUI resolves
//  a Shape only inside a live layout pass, so its pixels are a device-run
//  concern — exactly as they already are for every other clip shape in this
//  suite.
//

import XCTest
@testable import StyleConverterRuntime

final class RootCanvasClipTests: XCTestCase {

    /// The verbatim per-test IR, with the root's declarations parameterised
    /// so the negative cases exercise the same decoder path.
    private func document(rootProperties: String) throws -> IRDocument {
        try JSONDecoder().decode(IRDocument.self, from: Data("""
        {
          "irVersion": 2,
          "minReaderVersion": 2,
          "components": [
            {
              "id": "wpt__css-masking__clip-path__clip-path-document-element__0-047",
              "name": "wpt__css-masking__clip-path__clip-path-document-element__0",
              "properties": [\(rootProperties)],
              "meta": { "role": "body-root" }
            },
            {
              "id": "wpt__css-masking__clip-path__clip-path-document-element__1-048",
              "name": "wpt__css-masking__clip-path__clip-path-document-element__1",
              "properties": [
                { "type": "Width",  "data": { "type": "length", "px": 500 } },
                { "type": "Height", "data": { "type": "length", "px": 500 } },
                { "type": "BackgroundColor",
                  "data": { "srgb": { "r": 0, "g": 0.5019607843137255, "b": 0 },
                            "original": "green" } }
              ]
            }
          ]
        }
        """.utf8))
    }

    /// `html { background: red; clip-path: polygon(…) }` — the L shape the
    /// Chromium ref draws at image [66,66]-[165,165].
    private let redPlusPolygon = """
      { "type": "BackgroundColor",
        "data": { "srgb": { "r": 1, "g": 0, "b": 0 }, "original": "red" } },
      { "type": "ClipPath",
        "data": { "type": "polygon", "points": [
          { "x": { "px": 50 },  "y": { "px": 50 } },
          { "x": { "px": 100 }, "y": { "px": 50 } },
          { "x": { "px": 100 }, "y": { "px": 100 } },
          { "x": { "px": 150 }, "y": { "px": 100 } },
          { "x": { "px": 150 }, "y": { "px": 150 } },
          { "x": { "px": 50 },  "y": { "px": 150 } } ] } }
    """

    func testDocumentElementPolygonIsResolvedAsThePageClip() throws {
        let doc = try document(rootProperties: redPlusPolygon)
        let cfg = try XCTUnwrap(RootCanvasClip.config(doc.components))
        guard case .some(.polygon(let points)) = cfg.shape else {
            return XCTFail("expected the root clip to decode as a polygon")
        }
        XCTAssertEqual(points.count, 6, "the L shape has six vertices")
        XCTAssertTrue(RootCanvasClip.declaresRootClip(doc.components))
    }

    func testABodyRootWithoutAClipPathLeavesTheCanvasChainUntouched() throws {
        // The shape of 1433 of the corpus's 1435 documents — this nil is what
        // keeps every other composed capture pixel-identical.
        let doc = try document(rootProperties: """
          { "type": "BackgroundColor",
            "data": { "srgb": { "r": 1, "g": 0, "b": 0 }, "original": "red" } }
        """)
        XCTAssertNil(RootCanvasClip.config(doc.components))
        XCTAssertFalse(RootCanvasClip.declaresRootClip(doc.components))
    }

    func testTheInitialNoneKeywordIsNotAPageClip() throws {
        let doc = try document(rootProperties: """
          { "type": "ClipPath", "data": "none" }
        """)
        XCTAssertNil(RootCanvasClip.config(doc.components))
    }

    func testAClipOnAnOrdinaryComponentIsNotThePageClip() throws {
        // Only the merged `meta.role: body-root` bag is the document element.
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data("""
        {
          "irVersion": 2, "minReaderVersion": 2,
          "components": [
            { "id": "r", "name": "r", "properties": [], "meta": { "role": "body-root" } },
            { "id": "c", "name": "c", "properties": [\(redPlusPolygon)] }
          ]
        }
        """.utf8))
        XCTAssertNil(RootCanvasClip.config(doc.components))
    }

    func testContainmentDoesNotBlockTheRootClip() throws {
        // css-contain-2 §2 / css-contain-1 §2 remove a contained root from
        // the background / writing-mode / direction PROPAGATION path. A
        // clip-path is the root's own clip over its own subtree, so no
        // containment clause applies to it.
        let doc = try document(rootProperties: redPlusPolygon + """
          , { "type": "Contain", "data": ["LAYOUT"] }
        """)
        XCTAssertNotNil(RootCanvasClip.config(doc.components))
    }
}
