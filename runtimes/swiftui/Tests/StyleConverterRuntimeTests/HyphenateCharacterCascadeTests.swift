//
//  HyphenateCharacterCascadeTests.swift
//  Wave 55 (lane L7, unit U6-swift, pin S1 — wave-54 L3 skeptic SK-U2I-g).
//
//  HyphenateCharacterExtractor (rewritten in wave 54, lane L3 U2-ios) promises
//  that the LAST of several declarations wins (css-cascade-4 §6.1: equal
//  declarations are ordered by appearance, the later one overrides), but no
//  test ever handed it two. The Compose twin is pinned by
//  HyphenateCharacterExtractorTest.ofTwoDeclarationsInOneBlockTheLastWins (C1).
//
//  Values: -004's "/-/" then -003's "•" (the reader's decoded `\2022`), each
//  in the reader's `{type: string, value}` shape.
//
//  MUTATION (executed red → restore → green, sha256 in
//  tools/titan/results/wave55-instruments/_note.md): first-wins — the loop body
//  guarded by `if cfg == nil`.
//

import XCTest
@testable import StyleConverterRuntime

final class HyphenateCharacterCascadeTests: XCTestCase {

    // One HyphenateCharacter property with the given `data` JSON (the reader's shape).
    private func prop(_ json: String) throws -> IRProperty {
        try JSONDecoder().decode(IRProperty.self, from: Data(#"{"type":"HyphenateCharacter","data":\#(json)}"#.utf8))
    }

    func testS1OfTwoDeclarationsTheLastWins() throws {
        let both = [try prop(#"{"type":"string","value":"/-/"}"#), try prop(#"{"type":"string","value":"•"}"#)]
        // The later declaration's string, verbatim.
        XCTAssertEqual(HyphenateCharacterExtractor.extract(from: both)?.value, "•")
        // And in the other order, the other one: order of appearance, not value, decides.
        XCTAssertEqual(HyphenateCharacterExtractor.extract(from: both.reversed())?.value, "/-/")
    }
}
