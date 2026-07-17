//
//  BackgroundImageURLTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 8 (#36) — url() raster background pins:
//    • BackgroundURLImageResolver: percent-decode to raw bytes, data-URI
//      payloads (base64 + fully percent-encoded), remote no-op, broken
//      payloads logged (the converter's lowercased-base64 quirk)
//    • BackgroundImageGeometry: cover/contain/explicit sizing (§3.9),
//      position anchors (§3.6), repeat lattice enumeration (§3.4)
//

import Foundation
import XCTest
import UIKit
@testable import StyleConverterRuntime

final class BackgroundImageURLTests: XCTestCase {

    // MARK: - Helpers

    /// A real PNG payload for decode tests: 8×4, solid opaque red,
    /// rendered through UIKit so the bytes are always a valid PNG on the
    /// test host (no hardcoded fixture bytes to rot).
    private func makePNG(width: Int = 8, height: Int = 4) -> Data {
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        let img = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: fmt)
            .image { ctx in
                UIColor.red.setFill()
                ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
            }
        return img.pngData()!
    }

    /// Fully percent-encode bytes with LOWERCASE hex — the data-URI form
    /// that survives the converter's whole-value lowercasing (the same
    /// form the wave-8 fixture uses).
    private func percentEncode(_ data: Data) -> String {
        data.map { String(format: "%%%02x", $0) }.joined()
    }

    // MARK: - Percent decoding (raw bytes, not UTF-8)

    func testPercentDecodeRawBytes() {
        // 0x89 is invalid UTF-8 as a lone byte — the String-based
        // removingPercentEncoding path would destroy it.
        let d = BackgroundURLImageResolver.percentDecode("%89png%0d%0a")!
        XCTAssertEqual([UInt8](d), [0x89, 0x70, 0x6E, 0x67, 0x0D, 0x0A])
        // Mixed literals and escapes; uppercase hex accepted too.
        XCTAssertEqual([UInt8](BackgroundURLImageResolver.percentDecode("a%2Bb")!),
                       [UInt8]("a+b".utf8))
    }

    // MARK: - Data URI decode

    func testPercentEncodedDataURIDecodes() {
        let png = makePNG()
        let uri = "data:image/png,\(percentEncode(png))"
        let img = BackgroundURLImageResolver.image(for: uri)
        XCTAssertNotNil(img, "fully percent-encoded PNG data URI must decode")
        XCTAssertEqual(img!.size.width * img!.scale, 8)
        XCTAssertEqual(img!.size.height * img!.scale, 4)
        // Cache path returns the same object.
        XCTAssertTrue(BackgroundURLImageResolver.image(for: uri) === img)
    }

    func testBase64DataURIDecodes() {
        let uri = "data:image/png;base64,\(makePNG().base64EncodedString())"
        XCTAssertNotNil(BackgroundURLImageResolver.image(for: uri))
    }

    func testLowercasedBase64FailsLoudlyNotSilently() {
        PropertyTracker._resetForTests()
        // The converter's lowercase quirk corrupts base64 payloads — the
        // decode must fail AND leave a breadcrumb (never a silent clear).
        let uri = "data:image/png;base64,\(makePNG().base64EncodedString().lowercased())"
        XCTAssertNil(BackgroundURLImageResolver.image(for: uri))
        XCTAssertFalse(PropertyTracker.logOnce(key: "bg-url-decode:\(uri.prefix(48))", message: "dup"))
    }

    func testRemoteURLIsDefinedNoOp() {
        PropertyTracker._resetForTests()
        let url = "https://example.com/tile.png"
        XCTAssertNil(BackgroundURLImageResolver.image(for: url))
        XCTAssertFalse(PropertyTracker.logOnce(key: "bg-url-remote:\(url)", message: "dup"))
    }

    // MARK: - Geometry: background-size (§3.9)

    func testTileSizeCoverContainExplicit() {
        let img = CGSize(width: 10, height: 5) // 2:1 raster
        let box = CGSize(width: 100, height: 100)
        // cover: the LARGER scale wins → 200×100 (height covers).
        XCTAssertEqual(BackgroundImageGeometry.tileSize(imageSize: img, box: box, size: .cover),
                       CGSize(width: 200, height: 100))
        // contain: the SMALLER scale wins → 100×50 (width fits).
        XCTAssertEqual(BackgroundImageGeometry.tileSize(imageSize: img, box: box, size: .contain),
                       CGSize(width: 100, height: 50))
        // auto: intrinsic pixels.
        XCTAssertEqual(BackgroundImageGeometry.tileSize(imageSize: img, box: box, size: nil), img)
        // explicit px × percent.
        XCTAssertEqual(BackgroundImageGeometry.tileSize(imageSize: img, box: box,
                                                        size: .explicit(w: .px(40), h: .percent(25))),
                       CGSize(width: 40, height: 25))
        // one-axis auto preserves the intrinsic ratio (§3.9).
        XCTAssertEqual(BackgroundImageGeometry.tileSize(imageSize: img, box: box,
                                                        size: .explicit(w: .px(40), h: .auto)),
                       CGSize(width: 40, height: 20))
    }

    // MARK: - Geometry: background-position (§3.6)

    func testOriginAnchors() {
        let tile = CGSize(width: 20, height: 10)
        let box = CGSize(width: 100, height: 50)
        // center/center: (box − tile) × 0.5.
        XCTAssertEqual(BackgroundImageGeometry.origin(tile: tile, box: box,
                                                      x: .keyword("CENTER"), y: .keyword("CENTER")),
                       CGPoint(x: 40, y: 20))
        // right/bottom flush.
        XCTAssertEqual(BackgroundImageGeometry.origin(tile: tile, box: box,
                                                      x: .keyword("RIGHT"), y: .keyword("BOTTOM")),
                       CGPoint(x: 80, y: 40))
        // percent uses the same edge-alignment identity; px is an inset.
        XCTAssertEqual(BackgroundImageGeometry.origin(tile: tile, box: box,
                                                      x: .percent(25), y: .px(7)),
                       CGPoint(x: 20, y: 7))
        // initial (absent) = 0%.
        XCTAssertEqual(BackgroundImageGeometry.origin(tile: tile, box: box, x: nil, y: nil), .zero)
    }

    // MARK: - Geometry: repeat lattice (§3.4)

    func testTileRectsLattice() {
        // Default repeat both: 80×80 box, 10×10 tile → 8×8 = 64 rects.
        let both = BackgroundImageGeometry.Placement(
            tileSize: CGSize(width: 10, height: 10), origin: .zero, repeatX: .repeat, repeatY: .repeat)
        XCTAssertEqual(BackgroundImageGeometry.tileRects(placement: both,
                                                         box: CGSize(width: 80, height: 80)).count, 64)
        // no-repeat: exactly the anchor copy.
        let none = BackgroundImageGeometry.Placement(
            tileSize: CGSize(width: 10, height: 10), origin: CGPoint(x: 35, y: 35),
            repeatX: .noRepeat, repeatY: .noRepeat)
        let single = BackgroundImageGeometry.tileRects(placement: none, box: CGSize(width: 80, height: 80))
        XCTAssertEqual(single, [CGRect(x: 35, y: 35, width: 10, height: 10)])
        // repeat-x only: one row.
        let xOnly = BackgroundImageGeometry.Placement(
            tileSize: CGSize(width: 10, height: 10), origin: .zero, repeatX: .repeat, repeatY: .noRepeat)
        XCTAssertEqual(BackgroundImageGeometry.tileRects(placement: xOnly,
                                                         box: CGSize(width: 80, height: 80)).count, 8)
        // An offset anchor extends the lattice into negative space (CSS
        // tiles infinitely): anchor x=5 over an 80-wide box needs the
        // x=−5 column too → 9 columns.
        let offset = BackgroundImageGeometry.Placement(
            tileSize: CGSize(width: 10, height: 10), origin: CGPoint(x: 5, y: 0),
            repeatX: .repeat, repeatY: .noRepeat)
        let rects = BackgroundImageGeometry.tileRects(placement: offset, box: CGSize(width: 80, height: 80))
        XCTAssertEqual(rects.count, 9)
        XCTAssertEqual(rects.first?.origin.x, -5)
    }

    func testRepeatModesVocabulary() {
        XCTAssertEqual(BackgroundImageGeometry.repeatModes(nil).x, .repeat) // CSS initial = repeat
        let nr = BackgroundImageGeometry.repeatModes(BackgroundRepeatLayer(x: "no-repeat", y: "repeat"))
        XCTAssertEqual(nr.x, .noRepeat); XCTAssertEqual(nr.y, .repeat)
        // space/round map to their REAL §3.7 modes now — the wave-8
        // "approximated as repeat" logOnce breadcrumb is retired.
        PropertyTracker._resetForTests()
        let sp = BackgroundImageGeometry.repeatModes(BackgroundRepeatLayer(x: "space", y: "round"))
        XCTAssertEqual(sp.x, .space); XCTAssertEqual(sp.y, .round)
        XCTAssertTrue(PropertyTracker.logOnce(key: "bg-repeat:space", message: "must be unused"),
                      "space/round must no longer emit the approximation breadcrumb")
    }

    /// Cap guard: a 1×1 tile over a canvas-sized box must pre-count past
    /// the cap (the renderer switches to the tiled-shading fill there).
    func testTileCountCapPrecheck() {
        let tiny = BackgroundImageGeometry.Placement(
            tileSize: CGSize(width: 1, height: 1), origin: .zero, repeatX: .repeat, repeatY: .repeat)
        XCTAssertGreaterThan(BackgroundImageGeometry.tileCount(placement: tiny,
                                                               box: CGSize(width: 390, height: 844)),
                             BackgroundImageGeometry.tileCap)
    }

    // MARK: - End-to-end fixture pin

    /// The wave-8 images fixture's data URI must decode on iOS EVEN
    /// AFTER the converter's whole-value lowercasing (the reason the
    /// fixture is fully percent-encoded with lowercase hex). This is the
    /// non-visual half of the "renders non-clear" gate: the wire bytes →
    /// a real 10×6 raster.
    func testImagesFixtureURIDecodesPostLowercasing() throws {
        let fixture = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()              // StyleConverterRuntimeTests/
            .deletingLastPathComponent()              // Tests/
            .deletingLastPathComponent()              // swiftui/
            .deletingLastPathComponent()              // runtimes/
            .deletingLastPathComponent()              // repo root
            .appendingPathComponent("fixtures/properties/background/background-image-url-data.json")
        let doc = try JSONSerialization.jsonObject(with: Data(contentsOf: fixture)) as! [String: Any]
        let comps = doc["components"] as! [String: Any]
        let tile = comps["BgData_Tile"] as! [String: Any]
        let props = tile["properties"] as! [String: Any]
        let declaration = props["background-image"] as! String
        // Strip url("…") exactly like UrlParser, then apply the
        // converter's lowercasing to simulate the wire bytes.
        let inner = declaration
            .replacingOccurrences(of: "url(\"", with: "")
            .replacingOccurrences(of: "\")", with: "")
        let wireForm = inner.lowercased()
        XCTAssertEqual(wireForm, inner, "fixture URI must be lowercase-stable (percent-encoded, lowercase hex)")
        let img = try XCTUnwrap(BackgroundURLImageResolver.image(for: wireForm),
                                "the images fixture's wire URI must decode to a raster")
        XCTAssertEqual(img.size.width * img.scale, 10)
        XCTAssertEqual(img.size.height * img.scale, 6)
    }
}
