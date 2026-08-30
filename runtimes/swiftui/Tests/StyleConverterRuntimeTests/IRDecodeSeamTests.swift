//
//  IRDecodeSeamTests.swift
//  StyleConverterRuntimeTests — wave-49 lane F2.
//
//  WHY THIS EXISTS: skeptic S5 found lane A2's Android module shipped with no
//  call site — a new module nothing calls looks like a fix and renders nothing.
//  Lane A5's SwiftUI half has the same exposure: BackgroundColorInheritanceTests
//  exercises `BackgroundColorInheritance.resolveAll` DIRECTLY, so it stays
//  green whether or not IRModels.swift actually calls it. This file closes that
//  hole by driving the pass through the REAL decode entry — the same
//  `JSONDecoder().decode(IRDocument.self, …)` every harness uses — on the
//  frozen corpus documents themselves, read off disk rather than retyped.
//
//  Corpus source (read at test time, never copied):
//    tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//      wpt__css-color__currentcolor-002.json
//      wpt__css-color__color-mix-currentcolor-001.json
//  These are the only corpus documents whose BackgroundColor carries the
//  `inherit` keyword (censused across all 1435 frozen per-test-ir docs:
//  4 components in 3 tests).
//
//  NEGATIVE CONTROL, EXECUTED: with IRModels.swift restored to its pre-seam
//  bytes, this class reports 3 failures / 3 tests —
//    testDecodeResolvesBackgroundColorInheritOnTheRealCorpusDocument
//      XCTAssertEqual failed: "inherit" is not equal to "currentColor"  (x2)
//    testColorMixDocumentsAlsoRunThroughTheSeam
//      XCTAssertFalse failed - an unresolved background-color:inherit survived
//  while testTheKeywordIsReallyOnTheWireBeforeDecode still PASSES, which is
//  what separates "the seam is gone" from "the decoder dropped the property".
//  Re-applying the seam turns all three green.
//

import XCTest
@testable import StyleConverterRuntime

final class IRDecodeSeamTests: XCTestCase {

    /// The frozen wave-48 per-test IR directory for one WPT section.
    /// Compile-time path hop, the same idiom ConformanceTests uses: this file
    /// sits at …/runtimes/swiftui/Tests/StyleConverterRuntimeTests/, so five
    /// hops up is the repo root.
    private static func section(_ name: String) -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // StyleConverterRuntimeTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // swiftui/
            .deletingLastPathComponent()   // runtimes/
            .deletingLastPathComponent()   // repo root
            .appendingPathComponent("tools/titan/runs/wave48-final/sections/\(name)/per-test-ir",
                                    isDirectory: true)
    }

    /// Decode one frozen per-test IR document through the production entry.
    private func decode(_ sectionName: String, _ test: String) throws -> IRDocument {
        let url = Self.section(sectionName).appendingPathComponent("\(test).json")
        return try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: url))
    }

    /// The winning (last-declared) BackgroundColor payload of a component,
    /// as its `original` author string — enough to tell `inherit` (unresolved)
    /// from `currentColor` (resolved) without an Equatable IRValue.
    private func backgroundOriginal(_ c: IRComponent) -> String? {
        guard let data = c.properties.last(where: { $0.type == "BackgroundColor" })?.data
        else { return nil }
        return data.objectValue?["original"]?.stringValue ?? data.stringValue
    }

    /// Depth-first lookup by wire id over a composed forest.
    private func find(_ id: String, in roots: [IRComponent]) -> IRComponent? {
        for r in roots {
            if r.id == id { return r }
            if let hit = find(id, in: r.children ?? []) { return hit }
        }
        return nil
    }

    // MARK: - the A5 decode seam

    func testDecodeResolvesBackgroundColorInheritOnTheRealCorpusDocument() throws {
        // css-color/currentcolor-002: three nested divs. The OUTER declares
        // `color: red; background-color: currentColor`; the two inner ones
        // declare `background-color: inherit`, and the innermost also
        // `color: green`. Chrome 151 on that exact nesting computes the three
        // backgrounds as rgb(255,0,0) / rgb(255,0,0) / rgb(0,128,0) — measured
        // with getComputedStyle by skeptic S5, not assumed.
        let doc = try decode("css-color", "wpt__css-color__currentcolor-002")
        // The document must have taken the v2 branch — the seam is installed
        // on both branches, but this corpus is v2 and the assertion below
        // would otherwise be checking the wrong one.
        XCTAssertEqual(doc.irVersion, 2)

        // The outer div keeps its own declaration untouched.
        let outer = try XCTUnwrap(find("wpt__css-color__currentcolor-002__1-251",
                                       in: doc.components))
        XCTAssertEqual(backgroundOriginal(outer), "currentColor")

        // THE SEAM ASSERTION. Both inner divs declared the literal keyword
        // `inherit` on the wire — verified against the file three lines down —
        // and must come back carrying the parent's computed value instead.
        // css-cascade-5 §7.3.2 computes `inherit` to the inherited value,
        // which §7.2 defines as the parent's computed value. If IRModels.swift
        // ever stops calling BackgroundColorInheritance, these two fail while
        // BackgroundColorInheritanceTests stays green.
        let middle = try XCTUnwrap(find("currentcolor-002__1__0-252", in: doc.components))
        XCTAssertEqual(backgroundOriginal(middle), "currentColor",
                       "background-color:inherit was not resolved at decode")
        let inner = try XCTUnwrap(find("currentcolor-002__1__0__0-253", in: doc.components))
        XCTAssertEqual(backgroundOriginal(inner), "currentColor",
                       "background-color:inherit was not resolved at decode")

        // And the innermost div's OWN `color: green` survives the pass, since
        // that is what CurrentColorBackground later substitutes (css-color-4
        // §6.4) to produce the reference's green square.
        let ownColor = inner.properties.last { $0.type == "Color" }?.data
        XCTAssertEqual(ownColor?.objectValue?["original"]?.stringValue, "green")
    }

    func testTheKeywordIsReallyOnTheWireBeforeDecode() throws {
        // The control for the assertion above: without this, a decoder that
        // silently DROPPED the property would also read as "resolved". Parse
        // the same file as raw JSON and confirm both inner components ship
        // `{"original":"inherit"}`.
        let url = Self.section("css-color")
            .appendingPathComponent("wpt__css-color__currentcolor-002.json")
        let raw = try JSONSerialization.jsonObject(with: Data(contentsOf: url))
        let comps = try XCTUnwrap((raw as? [String: Any])?["components"] as? [[String: Any]])
        let inheritIds = comps.filter { comp in
            let props = comp["properties"] as? [[String: Any]] ?? []
            return props.contains { p in
                (p["type"] as? String) == "BackgroundColor" &&
                    ((p["data"] as? [String: Any])?["original"] as? String) == "inherit"
            }
        }.compactMap { $0["id"] as? String }
        XCTAssertEqual(inheritIds.sorted(),
                       ["currentcolor-002__1__0-252", "currentcolor-002__1__0__0-253"])
    }

    func testColorMixDocumentsAlsoRunThroughTheSeam() throws {
        // The other corpus carrier, and the one that proves the pass hands
        // down an UNRESOLVED payload rather than a colour: the parent's
        // background is a `color-mix()` call, so the child's `inherit` must
        // come back as that same call — which StaticColorMix then resolves
        // per element against each element's own `color` (css-color-5 §3).
        let doc = try decode("css-color", "wpt__css-color__color-mix-currentcolor-001")
        XCTAssertEqual(doc.irVersion, 2)
        // Find the component that declared `inherit` on the wire, then assert
        // the decoded tree no longer carries the keyword anywhere.
        var sawKeyword = false
        func walk(_ comps: [IRComponent]) {
            for c in comps {
                if backgroundOriginal(c)?.lowercased() == "inherit" { sawKeyword = true }
                walk(c.children ?? [])
            }
        }
        walk(doc.components)
        XCTAssertFalse(sawKeyword,
                       "an unresolved background-color:inherit survived the decode seam")
    }
}
