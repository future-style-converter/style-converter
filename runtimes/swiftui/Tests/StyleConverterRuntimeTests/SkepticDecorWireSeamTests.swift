//
//  SkepticDecorWireSeamTests.swift
//  StyleConverterRuntimeTests
//
//  SKEPTIC SEAM PROBE (wave-22 lane DECOR adversarial review) — the iOS
//  twin of the Compose suite SkepticDecorWireSeamTest.kt. Same five
//  cases, same expected values EXCEPT the two the lane's own risk list
//  calls out as per-platform (rgb() resolution), which is asserted from
//  the opposite side here so the asymmetry cannot drift unnoticed.
//
//  Every case drives the LIVE golden through the REAL decode path
//  (JSONDecoder → IRWireV2Reader → IRMeta.decorations), not a literal.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class SkepticDecorWireSeamTests: XCTestCase {

    // Repo-root/schema/conformance/fixtures/v2 from this file's path.
    private static var fixturesV2: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // StyleConverterRuntimeTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // swiftui/
            .deletingLastPathComponent()   // runtimes/
            .deletingLastPathComponent()   // repo root
            .appendingPathComponent("schema/conformance/fixtures/v2", isDirectory: true)
    }

    private func loadV2(_ name: String) throws -> IRDocument {
        let data = try Data(contentsOf: Self.fixturesV2.appendingPathComponent(name))
        return try JSONDecoder().decode(IRDocument.self, from: data)
    }

    private func decodeDoc(_ json: String) throws -> IRDocument {
        try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8))
    }

    /// Depth-first walk of the COMPOSED tree (v2 children come from
    /// IRComposer, so a slotted component is NOT at document top level).
    private func flatten(_ components: [IRComponent]) -> [IRComponent] {
        components.flatMap { [$0] + flatten($0.children ?? []) }
    }

    /// P1 — the LIVE golden through the real decoder into the real bridge.
    func testLiveGoldenDecodesToTheThreeColourChain() throws {
        let doc = try loadV2("decorations.json")
        let chain = try XCTUnwrap(
            flatten(doc.components).first { $0.id == "decor-chain-002" })
        let lines = try XCTUnwrap(DecorationWire.decorationLines(from: chain.meta?.decorations))
        XCTAssertEqual(lines, [
            DecorationColorOps.DecorationLine(
                kind: .underline, color: DecorationColorOps.Rgba(r: 0, g: 0, b: 1)),
            DecorationColorOps.DecorationLine(
                kind: .overline,
                color: DecorationColorOps.Rgba(r: 128 / 255.0, g: 128 / 255.0, b: 128 / 255.0)),
            DecorationColorOps.DecorationLine(
                kind: .lineThrough,
                color: DecorationColorOps.Rgba(r: 0, g: 128 / 255.0, b: 0)),
        ])
    }

    /// P2 — EMPTY-WIRE HOLE end to end: a fully-filtered chain is PRESENT
    /// but EMPTY, and `resolve` must not fall back to the component flags.
    func testBlinkOnlyChainResolvesToPresentButEmpty() {
        let wire = DecorationWire.decorationLines(
            from: [IRDecoration(line: "blink", color: "blue"), IRDecoration(line: "none")])
        XCTAssertNotNil(wire, "a fully-filtered wire is still PRESENT")
        XCTAssertTrue(wire!.isEmpty)
        let requests = DecorationColorOps.resolve(
            wire: wire, underline: true, overline: true, lineThrough: true)
        XCTAssertTrue(requests.isEmpty, "authoritative empty wire must paint nothing")
    }

    /// P3 — the COLOUR-COVERAGE HOLE the lane's risk list does not name.
    /// fixtures/wpt/css-text-decor/text-decoration-style-multiple.json is
    /// live in the corpus and authors `coral` / `skyblue`; the converter
    /// resolves both into the IR sRGB leaf, but `meta.decorations` carries
    /// the raw token and CSSTokenParser's named table (a var()-substitution
    /// subset, not a CSS colour parser) knows neither. DOCUMENTS the
    /// current behaviour — see the review notes, not an endorsement.
    func testExtendedNamedColoursInTheLiveCorpusDoNotResolve() throws {
        let lines = try XCTUnwrap(DecorationWire.decorationLines(from: [
            IRDecoration(line: "underline", color: "coral"),
            IRDecoration(line: "overline", color: "skyblue"),
            IRDecoration(line: "line-through", color: "green"),
        ]))
        XCTAssertEqual(lines.count, 3)
        // Wave 22: full-parser delegation — coral = #FF7F50, twin-parallel.
        XCTAssertEqual(lines[0].color!.r, 255.0/255.0, accuracy: 1e-4)
        XCTAssertEqual(lines[0].color!.g, 127.0/255.0, accuracy: 1e-4)
        XCTAssertEqual(lines[0].color!.b, 80.0/255.0, accuracy: 1e-4)
        // skyblue = #87CEEB.
        XCTAssertEqual(lines[1].color!.r, 135.0/255.0, accuracy: 1e-4)
        XCTAssertEqual(lines[1].color!.g, 206.0/255.0, accuracy: 1e-4)
        XCTAssertEqual(lines[1].color!.b, 235.0/255.0, accuracy: 1e-4)
        XCTAssertEqual(lines[2].color, DecorationColorOps.Rgba(r: 0, g: 128 / 255.0, b: 0))
    }

    /// P4 — the KNOWN twin asymmetry from the iOS side: rgb() DOES resolve
    /// here while the Compose twin drops it to currentColor.
    func testFunctionalColourTokenResolvesOnIOSOnly() throws {
        let lines = try XCTUnwrap(DecorationWire.decorationLines(
            from: [IRDecoration(line: "underline", color: "rgb(0, 0, 255)")]))
        XCTAssertEqual(lines[0].color, DecorationColorOps.Rgba(r: 0, g: 0, b: 1),
                       "iOS parses functional colours; Compose does not — pinned asymmetry")
        // `crimson` is a SECOND asymmetry the lane's banners do not name:
        // Compose's table has it, this one does not.
        let crimson = try XCTUnwrap(DecorationWire.decorationLines(
            from: [IRDecoration(line: "underline", color: "crimson")]))
        // Wave 22: crimson = #DC143C now resolves on BOTH natives (full table).
        XCTAssertEqual(crimson[0].color!.r, 220.0/255.0, accuracy: 1e-4)
        XCTAssertEqual(crimson[0].color!.g, 20.0/255.0, accuracy: 1e-4)
        XCTAssertEqual(crimson[0].color!.b, 60.0/255.0, accuracy: 1e-4)
    }

    /// P5 — decoder strictness: the shapes the schema rejects must throw,
    /// and the ONE spec-05 tolerance case must decode.
    func testDecoderRejectsEveryMalformedDecorationShape() throws {
        let head = """
        {"irVersion":2,"minReaderVersion":2,"components":[\
        {"id":"a","name":"A","properties":[],"text":"x","meta":{"decorations":
        """
        let bad = [
            "[]",
            "\"underline\"",
            "[[\"underline\"]]",
            "[{\"color\":\"blue\"}]",
            "[{\"line\":\"underline\",\"style\":\"wavy\"}]",
            "[{\"line\":\"underline\",\"color\":123}]",
            "[{\"line\":7}]",
        ]
        for b in bad {
            XCTAssertThrowsError(try decodeDoc(head + b + " } } ] }"),
                                 "meta.decorations \(b) must be a hard decode error")
        }
        let ok = try decodeDoc(head + "[{\"line\":\"spelling-error\"}] } } ] }")
        XCTAssertEqual(ok.components[0].meta?.decorations?.first?.line, "spelling-error")
    }
}
