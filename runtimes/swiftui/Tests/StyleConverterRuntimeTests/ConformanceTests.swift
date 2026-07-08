//
//  ConformanceTests.swift
//  StyleConverterRuntimeTests
//
//  SwiftUI-runtime side of the IR v1 wire contract
//  (schema/ir-v1.schema.json + schema/spec/*). Decodes EVERY golden
//  fixture in schema/conformance/fixtures/ through the runtime's real
//  IR-loading path — JSONDecoder into IRDocument/IRValue, exactly what
//  the harness's ContentView.loadDocument does — and asserts sentinel
//  extractions per shape family.
//
//  NOTE the documented caveat (schema/spec/04-metadata-fields.md): the
//  Swift model has no `_role`/`_pseudo` CodingKeys, so a keyed container
//  silently drops them. The sentinel is "decode survives their
//  presence", not "they round-trip".
//

import Foundation
import XCTest
// @testable so the decode path under test is the module-internal one.
@testable import StyleConverterRuntime

final class ConformanceTests: XCTestCase {

    // MARK: - fixture plumbing

    // Resolve repo-root/schema/conformance/fixtures from this source file's
    // compile-time path: …/runtimes/swiftui/Tests/StyleConverterRuntimeTests/
    // ConformanceTests.swift → four directory hops up is the repo root.
    private static var fixturesDir: URL {
        URL(fileURLWithPath: #filePath)               // …/ConformanceTests.swift
            .deletingLastPathComponent()              // StyleConverterRuntimeTests/
            .deletingLastPathComponent()              // Tests/
            .deletingLastPathComponent()              // swiftui/
            .deletingLastPathComponent()              // runtimes/
            .deletingLastPathComponent()              // repo root
            .appendingPathComponent("schema/conformance/fixtures", isDirectory: true)
    }

    // Real load path: raw bytes → JSONDecoder → IRDocument (mirrors the
    // harness's ContentView.loadDocument).
    private func load(_ name: String) throws -> IRDocument {
        let data = try Data(contentsOf: Self.fixturesDir.appendingPathComponent(name))
        return try JSONDecoder().decode(IRDocument.self, from: data)
    }

    // Depth-first walk — children are ARRAYS on the wire (spec 03).
    private func walk(_ components: [IRComponent], _ visit: (IRComponent) -> Void) {
        for c in components {
            visit(c)
            if let kids = c.children { walk(kids, visit) }
        }
    }

    // Find a component by unique name anywhere in the tree.
    private func byName(_ doc: IRDocument, _ name: String) throws -> IRComponent {
        var found: IRComponent?
        walk(doc.components) { if $0.name == name { found = $0 } }
        return try XCTUnwrap(found, "component \(name) not in fixture")
    }

    // Numeric IRValue helper: kotlinx emits 16.0-style decimals which
    // Foundation may surface as .int or .double — normalize both.
    private func asDouble(_ v: IRValue?) -> Double? {
        switch v {
        case .int(let i): return Double(i)
        case .double(let d): return d
        default: return nil
        }
    }

    // Object-member accessor for IRValue payloads.
    private func member(_ v: IRValue, _ key: String) -> IRValue? {
        if case .object(let dict) = v { return dict[key] }
        return nil
    }

    // MARK: - umbrella: every golden decodes through the real path

    func testEveryGoldenFixtureDecodes() throws {
        let files = try FileManager.default
            .contentsOfDirectory(at: Self.fixturesDir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
        XCTAssertGreaterThanOrEqual(files.count, 12, "expected the full golden set")
        for file in files {
            // Decode MUST NOT throw — including unknown property types
            // (spec 05 tolerance rule 1) and unknown metadata keys
            // (_role/_pseudo, the spec-04 model-gap caveat).
            let doc = try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: file))
            walk(doc.components) { c in
                XCTAssertFalse(c.id.isEmpty, "\(file.lastPathComponent): empty id")
            }
        }
    }

    // MARK: - per-family sentinels

    func testLengthsAllForms() throws {
        let doc = try load("lengths-all-forms.json")
        // {"px":16.0} → 16 through the generic IRValue payload.
        let px = try byName(doc, "Lengths_PxOnly")
        let padTop = try XCTUnwrap(px.properties.first { $0.type == "PaddingTop" })
        XCTAssertEqual(asDouble(member(padTop.data, "px")), 16.0)
        // Dual storage: px present AND original {v:12,u:PT} preserved.
        let dual = try byName(doc, "Lengths_DualStorage")
        let padRight = try XCTUnwrap(dual.properties.first { $0.type == "PaddingRight" })
        XCTAssertEqual(asDouble(member(padRight.data, "px")), 16.0)
        if case .string(let unit)? = member(try XCTUnwrap(member(padRight.data, "original")), "u") {
            XCTAssertEqual(unit, "PT")
        } else { XCTFail("original.u missing") }
        // Relative EM: px MUST be absent (runtime-dependent — spec 02).
        let rel = try byName(doc, "Lengths_RelativeOriginalOnly")
        let padBottom = try XCTUnwrap(rel.properties.first { $0.type == "PaddingBottom" })
        XCTAssertNil(member(padBottom.data, "px"))
        // Escapes: raw number percent, raw string keyword.
        let esc = try byName(doc, "Lengths_PercentAndKeyword")
        XCTAssertEqual(asDouble(esc.properties.first { $0.type == "PaddingLeft" }?.data), 10.0)
        if case .string(let kw)? = esc.properties.first(where: { $0.type == "MarginLeft" })?.data {
            XCTAssertEqual(kw, "auto")
        } else { XCTFail("MarginLeft escape must be the raw string 'auto'") }
    }

    func testColors() throws {
        let doc = try load("colors.json")
        let bg = try XCTUnwrap(try byName(doc, "Colors_StaticForms").properties.first { $0.type == "BackgroundColor" })
        let srgb = try XCTUnwrap(member(bg.data, "srgb"))
        XCTAssertEqual(try XCTUnwrap(asDouble(member(srgb, "r"))), 0.20392156862745098, accuracy: 1e-12)
        // Dynamic currentColor: srgb absent by contract (spec 02).
        let accent = try XCTUnwrap(try byName(doc, "Colors_SpecialKeywords").properties.first { $0.type == "AccentColor" })
        XCTAssertNil(member(accent.data, "srgb"))
    }

    func testShapeRadiusAsymmetry() throws {
        let doc = try load("shape-radius.json")
        // Keyword branch: raw string under "r".
        let kw = try byName(doc, "Shape_Circle_Keyword").properties[0]
        if case .string(let r)? = member(kw.data, "r") {
            XCTAssertEqual(r, "farthest-side")   // survives verbatim
        } else { XCTFail("keyword radius must be a string") }
        // Length branch: deep-flattened — px inlined, r key gone (spec 02).
        let len = try byName(doc, "Shape_Circle_Length").properties[0]
        XCTAssertEqual(asDouble(member(len.data, "px")), 40.0)
        XCTAssertNil(member(len.data, "r"))
    }

    func testTextAndRole() throws {
        let doc = try load("text-and-role.json")
        XCTAssertEqual(try byName(doc, "TextRole_BodyRoot")._text, "Test passes if this text is green")
        XCTAssertEqual(try byName(doc, "TextRole_EmptyStringText")._text, "")  // "" meaningful, not nil
        XCTAssertNil(try byName(doc, "TextRole_NoMetadata")._text)
        // _role: no CodingKey in the Swift model — decode surviving IS the
        // documented current behavior (spec 04 matrix).
    }

    func testChildrenNesting() throws {
        let doc = try load("children-nesting.json")
        let parent = try byName(doc, "Parent")
        let child = try XCTUnwrap(parent.children?.first)
        XCTAssertEqual(child.name, "child__0")                 // map key became name (spec 03)
        XCTAssertEqual(child.children?.first?._text, "leaf text")
        XCTAssertNil(try byName(doc, "LeafSibling").children)  // omit-when-empty, never []
    }

    func testSelectorsMedia() throws {
        let doc = try load("selectors-media.json")
        let c = try byName(doc, "SelMedia_Both")
        XCTAssertEqual(c.selectors?.first?.condition, "hover") // no leading colon (spec 01)
        XCTAssertEqual(c.selectors?.first?.properties.first?.type, "BackgroundColor")
        XCTAssertEqual(c.media?.first?.query, "(min-width: 768px)")
        XCTAssertNil(try byName(doc, "SelMedia_OmittedWhenEmpty").selectors)
    }

    func testUnknownPropertyTolerance() throws {
        let doc = try load("unknown-property-tolerance.json")
        let mixed = try byName(doc, "Unknown_MixedWithKnown")
        // Unknown types decode into the generic envelope and are skipped by
        // dispatch — never a decode failure (spec 05 rule 1).
        XCTAssertEqual(mixed.properties[0].type, "FrobnicateCorner")
        XCTAssertEqual(mixed.properties[1].type, "Width")
        XCTAssertEqual(asDouble(member(mixed.properties[1].data, "px")), 100.0)
        // Generic keeps its _unmapped marker.
        let generic = try byName(doc, "Unknown_GenericEnvelope").properties[0]
        XCTAssertEqual(generic.type, "Generic")
        if case .bool(let flag)? = member(generic.data, "_unmapped") {
            XCTAssertTrue(flag)
        } else { XCTFail("_unmapped marker missing") }
    }

    func testPropertyEnvelopeEdges() throws {
        let doc = try load("property-envelope-edge.json")
        let prim = try byName(doc, "Envelope_PrimitiveData")
        if case .string(let s)? = prim.properties.first(where: { $0.type == "MarginLeft" })?.data {
            XCTAssertEqual(s, "auto")
        } else { XCTFail("MarginLeft data must be a raw string") }
        XCTAssertEqual(asDouble(prim.properties.first { $0.type == "FontWeight" }?.data), 700.0)
        // Empty-object data decodes to an empty object, not a crash.
        if case .object(let o)? = try byName(doc, "Envelope_EmptyObjectData").properties.first?.data {
            XCTAssertTrue(o.isEmpty)
        } else { XCTFail("empty-object data must decode as .object([:])") }
        // Array data: TransitionDelay [{ms:150}].
        let delays = try XCTUnwrap(try byName(doc, "Envelope_ArrayData").properties.first { $0.type == "TransitionDelay" })
        if case .array(let items) = delays.data {
            XCTAssertEqual(asDouble(member(items[0], "ms")), 150.0)
        } else { XCTFail("TransitionDelay data must be an array") }
        XCTAssertTrue(try byName(doc, "Envelope_EmptyPropertiesList").properties.isEmpty)
    }

    func testTransformList() throws {
        let doc = try load("transform-list.json")
        let transform = try byName(doc, "Transform_FunctionList").properties[0]
        guard case .array(let fns)? = member(transform.data, "list") else {
            return XCTFail("transform data.list must be an array")
        }
        if case .string(let fn)? = member(fns[1], "fn") { XCTAssertEqual(fn, "rotate") }
        else { XCTFail("fn missing") }
        XCTAssertEqual(asDouble(member(try XCTUnwrap(member(fns[1], "a")), "deg")), 45.0)
        // Rotate dual storage: 0.5turn normalized to 180deg.
        let rotate = try byName(doc, "Transform_RotateAngleDual").properties[0]
        XCTAssertEqual(asDouble(member(rotate.data, "deg")), 180.0)
    }

    func testFontWeightKeywords() throws {
        let doc = try load("font-weight-keywords.json")
        let bold = try byName(doc, "FontWeight_BoldKeyword").properties[0]
        XCTAssertEqual(asDouble(member(bold.data, "weight")), 700.0)
        if case .string(let original)? = member(bold.data, "original") {
            XCTAssertEqual(original, "bold")
        } else { XCTFail("keyword dual storage must keep the original") }
        // Numeric primitive form: bare 700.
        XCTAssertEqual(asDouble(try byName(doc, "FontWeight_NumericPrimitive").properties[0].data), 700.0)
    }
}
