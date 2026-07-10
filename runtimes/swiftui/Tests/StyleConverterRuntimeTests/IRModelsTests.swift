//
//  IRModelsTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the IRComponent decode contract across BOTH wire generations:
//    - v1 spellings (`_text`/`_tag`/`children`) keep decoding during the
//      deprecation window, translated into their v2 homes (text /
//      meta.sourceTag / children in-memory);
//    - v2 spellings (`text`/`meta`/`slot`/`pseudos`) decode natively.
//
//  Historical context (check bodies preserved from the original
//  self-test): Bug 1 mixed-content + Bug 2 element-tag rendering —
//   - testing/titan/investigations/swarm-002/css-text-decor__text-decoration-decorating-box-thickness-001.json
//   - testing/titan/investigations/swarm-002/css-counter-styles__css3-counter-styles-101.json
//

import Foundation
import XCTest
// @testable: exercises the module-internal decode path directly.
@testable import StyleConverterRuntime

final class IRModelsTests: XCTestCase {

    // One test per section of the original self-test.
    func testLegacyDecodeChecks() { for failure in Self.runLegacyDecodeChecks() { XCTFail(failure) } }
    func testTextChecks()         { for failure in Self.runTextChecks()         { XCTFail(failure) } }
    func testTagChecks()          { for failure in Self.runTagChecks()          { XCTFail(failure) } }
    func testMixedContentChecks() { for failure in Self.runMixedContentChecks() { XCTFail(failure) } }
    // v2 spellings on the standalone component decoder.
    func testV2FieldChecks()      { for failure in Self.runV2FieldChecks()      { XCTFail(failure) } }

    // MARK: - helpers

    private static func decode(_ json: String) -> IRComponent? {
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(IRComponent.self, from: data)
    }

    // MARK: - 327-pair backward compat

    private static func runLegacyDecodeChecks() -> [String] {
        var f: [String] = []
        // visual-test.json shape: no text, no tag hints. All metadata
        // must default to nil so existing fixtures decode unchanged and
        // the 327-pair baseline doesn't shift.
        let src = """
        { "id": "legacy", "name": "Card", "properties": [] }
        """
        guard let c = decode(src) else { f.append("legacy decode threw"); return f }
        if c.text != nil { f.append("legacy fixture got non-nil text") }
        if c.meta != nil { f.append("legacy fixture got non-nil meta") }
        if c.slot != nil { f.append("legacy fixture got non-nil slot") }
        if c.id != "legacy" { f.append("legacy id roundtrip broken") }
        return f
    }

    // MARK: - Bug 1: v1 `_text` decode (window translation → text)

    private static func runTextChecks() -> [String] {
        var f: [String] = []
        // FIX-A leaf-text fixture (swarm-001 css-color__color-001).
        let src = """
        {
          "id": "t1", "name": "T", "properties": [],
          "_text": "Test passes if this text is green"
        }
        """
        guard let c = decode(src) else { f.append("text decode threw"); return f }
        if c.text != "Test passes if this text is green" {
            f.append("_text not translated to text: got \(c.text ?? "<nil>")")
        }
        if c.meta?.sourceTag != nil { f.append("sourceTag should be nil when omitted") }
        return f
    }

    // MARK: - Bug 2: v1 `_tag` decode (window translation → meta.sourceTag)

    private static func runTagChecks() -> [String] {
        var f: [String] = []
        // swarm-002 css-counter-styles fixture: <ol>/<li> hierarchy.
        let src = """
        {
          "id": "ol-1", "name": "List", "_tag": "ol",
          "children": [
            { "id": "li-1", "name": "Item", "_tag": "li", "_text": "first" }
          ]
        }
        """
        guard let c = decode(src) else { f.append("tag decode threw"); return f }
        if c.meta?.sourceTag != "ol" { f.append("parent _tag missing: got \(c.meta?.sourceTag ?? "<nil>")") }
        guard let kids = c.children, kids.count == 1 else {
            f.append("children not decoded")
            return f
        }
        if kids[0].meta?.sourceTag != "li" { f.append("child _tag missing") }
        if kids[0].text != "first" { f.append("child _text missing") }
        return f
    }

    // MARK: - Bug 1+2 mixed-content

    private static func runMixedContentChecks() -> [String] {
        var f: [String] = []
        // swarm-002 css-text-decor: parent <div>abc <span>x</span> def</div>.
        // Both the parent's text and the children must survive decode.
        let src = """
        {
          "id": "mix", "name": "Mix",
          "_text": "abc def",
          "children": [
            { "id": "c", "name": "Inner", "_text": "x" }
          ]
        }
        """
        guard let c = decode(src) else { f.append("mixed decode threw"); return f }
        if c.text != "abc def" { f.append("mixed parent _text dropped") }
        guard let kids = c.children, kids.count == 1 else {
            f.append("mixed children not decoded")
            return f
        }
        if kids[0].text != "x" { f.append("mixed child _text dropped") }
        return f
    }

    // MARK: - v2 spellings (text / meta / slot / pseudos)

    private static func runV2FieldChecks() -> [String] {
        var f: [String] = []
        // A representative v2 flat entry: renamed fields + slot ref
        // (name omitted → the documented default "content").
        let src = """
        {
          "id": "v2-1", "name": "Child", "properties": [],
          "slot": { "parent": "root-001" },
          "text": "",
          "pseudos": { "before": { "text": "*" } },
          "meta": { "sourceTag": "li", "role": "body-root" }
        }
        """
        guard let c = decode(src) else { f.append("v2 component decode threw"); return f }
        // Empty string text is a legal value distinct from nil (spec 01).
        if c.text != "" { f.append("v2 empty-string text not preserved") }
        if c.slot?.parent != "root-001" { f.append("v2 slot.parent dropped") }
        // Default-name reconstruction (schema slot.name default).
        if c.slot?.name != "content" { f.append("v2 slot default name not reconstructed") }
        if c.meta?.sourceTag != "li" { f.append("v2 meta.sourceTag dropped") }
        if c.meta?.role != "body-root" { f.append("v2 meta.role dropped") }
        // pseudos survives as an opaque object (was silently dropped
        // pre-v2 — the closed lossy hop of spec 04).
        if c.pseudos?["before"]?["text"]?.stringValue != "*" {
            f.append("v2 pseudos payload dropped")
        }
        // Explicit slot name must round-trip too (multi-slot reservation).
        let named = decode("""
        { "id": "v2-2", "name": "N", "properties": [],
          "slot": { "parent": "p", "name": "header" } }
        """)
        if named?.slot?.name != "header" { f.append("v2 explicit slot name dropped") }
        return f
    }
}
