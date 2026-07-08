//
//  IRModelsTests.swift
//  StyleConverterRuntimeTests
//
//  XCTest port of the IRComponent `_text` / `_tag` decode self-test
//  (formerly Models/IRModelsSelfTest.swift), pinning the JSON contract
//  added for Bug 1 (mixed-content) + Bug 2 (element-tag rendering). See:
//   - testing/titan/investigations/swarm-002/css-text-decor__text-decoration-decorating-box-thickness-001.json
//   - testing/titan/investigations/swarm-002/css-counter-styles__css3-counter-styles-101.json
//
//  Check bodies unchanged — every failure-collection site survives 1:1;
//  the print-PASS/FAIL summary becomes one XCTFail per collected
//  failure name.
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

    // MARK: - helpers

    private static func decode(_ json: String) -> IRComponent? {
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(IRComponent.self, from: data)
    }

    // MARK: - 327-pair backward compat

    private static func runLegacyDecodeChecks() -> [String] {
        var f: [String] = []
        // visual-test.json shape: no _text, no _tag. Both must default
        // to nil so existing fixtures decode unchanged and the 327-pair
        // baseline doesn't shift.
        let src = """
        { "id": "legacy", "name": "Card", "properties": [] }
        """
        guard let c = decode(src) else { f.append("legacy decode threw"); return f }
        if c._text != nil { f.append("legacy fixture got non-nil _text") }
        if c._tag != nil  { f.append("legacy fixture got non-nil _tag") }
        if c.id != "legacy" { f.append("legacy id roundtrip broken") }
        return f
    }

    // MARK: - Bug 1: _text decode

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
        if c._text != "Test passes if this text is green" {
            f.append("_text not preserved: got \(c._text ?? "<nil>")")
        }
        if c._tag != nil { f.append("_tag should be nil when omitted") }
        return f
    }

    // MARK: - Bug 2: _tag decode

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
        if c._tag != "ol" { f.append("parent _tag missing: got \(c._tag ?? "<nil>")") }
        guard let kids = c.children, kids.count == 1 else {
            f.append("children not decoded")
            return f
        }
        if kids[0]._tag != "li" { f.append("child _tag missing") }
        if kids[0]._text != "first" { f.append("child _text missing") }
        return f
    }

    // MARK: - Bug 1+2 mixed-content

    private static func runMixedContentChecks() -> [String] {
        var f: [String] = []
        // swarm-002 css-text-decor: parent <div>abc <span>x</span> def</div>.
        // Both the parent's _text and the children must survive decode.
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
        if c._text != "abc def" { f.append("mixed parent _text dropped") }
        guard let kids = c.children, kids.count == 1 else {
            f.append("mixed children not decoded")
            return f
        }
        if kids[0]._text != "x" { f.append("mixed child _text dropped") }
        return f
    }
}
