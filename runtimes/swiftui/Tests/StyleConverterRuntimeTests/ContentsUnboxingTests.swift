//
//  ContentsUnboxingTests.swift
//  Wave 18 (RC6, lane 1) — `display: contents` unboxing pins
//  (css-display-3 §2.5/§2.7), plus the ported `all:<global>` reset drop.
//
//  Twin of Compose's ContentsUnboxingTest.kt — the gate and splice rules
//  are the shared cross-native contract. Property shapes are copied from
//  the LIVE wave-18 css-display IRs
//  (tools/titan/runs/wave18-gate/sections/css-display/per-test-ir), so
//  wire drift fails here before it fails on a device.
//

import XCTest
@testable import StyleConverterRuntime

final class ContentsUnboxingTests: XCTestCase {

    /// Decode wire-shaped JSON — the same decode path production IR takes.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    // MARK: - The gate (pin C1/C3)

    func testOnlyBaseDisplayContentsUnboxes() throws {
        let contents = try component(
            #"{"id":"c","name":"c","properties":[{"type":"Display","data":"CONTENTS"}]}"#)
        XCTAssertTrue(ContentsUnboxing.isUnboxable(contents))
        let block = try component(
            #"{"id":"b","name":"b","properties":[{"type":"Display","data":"BLOCK"}]}"#)
        XCTAssertFalse(ContentsUnboxing.isUnboxable(block))
    }

    /// css-display-3 §2.7: absolute positioning and float blockify
    /// `display: contents` — the wrapper box must stay.
    func testPositionedOrFloatedContentsKeepsItsBox() throws {
        let abs = try component(
            #"{"id":"a","name":"a","properties":[{"type":"Display","data":"CONTENTS"},{"type":"Position","data":"ABSOLUTE"}]}"#)
        XCTAssertFalse(ContentsUnboxing.isUnboxable(abs))
        let floated = try component(
            #"{"id":"f","name":"f","properties":[{"type":"Display","data":"CONTENTS"},{"type":"Float","data":"LEFT"}]}"#)
        XCTAssertFalse(ContentsUnboxing.isUnboxable(floated))
        // ANY non-static position keeps the box (wider than §2.7 on
        // purpose — see the gate's comment; twin of the Compose pin).
        let rel = try component(
            #"{"id":"r","name":"r","properties":[{"type":"Display","data":"CONTENTS"},{"type":"Position","data":"RELATIVE"}]}"#)
        XCTAssertFalse(ContentsUnboxing.isUnboxable(rel))
    }

    /// Pseudo-bearing contents keeps the legacy wrapper — pins the four
    /// currently-green display-contents-before-after tests untouched.
    func testPseudoBearingContentsIsKeptVerbatim() throws {
        let withPseudo = try component(
            #"{"id":"p","name":"p","properties":[{"type":"Display","data":"CONTENTS"}],"pseudos":{"before":{}}}"#)
        XCTAssertFalse(ContentsUnboxing.isUnboxable(withPseudo))
        // resolve() keeps the component's declarations byte-identical.
        let resolved = ContentsUnboxing.resolve(withPseudo)
        XCTAssertEqual(resolved.properties.map(\.type), withPseudo.properties.map(\.type))
    }

    // MARK: - The splice (pin C1/C2) — live display-contents-alignment-002

    /// The grid wrapper splice: the grandchild becomes the DIRECT grid
    /// item, so the container's justify-items reaches it; the wrapper's
    /// own non-inherited JustifyItems does not leak.
    func testGridWrapperSpliceMakesTheGrandchildTheItem() throws {
        let grid = try component(#"""
        {"id":"grid","name":"grid","properties":[
           {"type":"Display","data":"GRID"},{"type":"JustifyItems","data":"CENTER"}],
         "children":[
           {"id":"wrapper","name":"w","properties":[
              {"type":"Display","data":"CONTENTS"},{"type":"JustifyItems","data":"START"}],
            "children":[
              {"id":"square","name":"s","properties":[
                 {"type":"Width","data":{"type":"length","px":100}},
                 {"type":"Height","data":{"type":"length","px":100}},
                 {"type":"JustifySelf","data":{"type":"auto"}}]}]}]}
        """#)
        let resolved = ContentsUnboxing.resolve(grid)
        XCTAssertEqual(resolved.children?.map(\.id), ["square"])
        let item = resolved.children![0]
        // css-align-3 §7.1: justify-items does not inherit — dropped.
        XCTAssertFalse(item.properties.contains { $0.type == "JustifyItems" })
        // The item's own declarations survive verbatim.
        XCTAssertTrue(item.properties.contains { $0.type == "JustifySelf" })
    }

    /// Non-inherited box declarations drop with the box — the -block-002
    /// margin and the -button/-details border set.
    func testBoxDeclarationsDropWithTheBox() throws {
        let parent = try component(#"""
        {"id":"parent","name":"p","properties":[],
         "children":[
           {"id":"wrapper","name":"w","properties":[
              {"type":"Display","data":"CONTENTS"},
              {"type":"MarginTop","data":{"px":2124}},
              {"type":"BorderTopWidth","data":{"px":10}}],
            "children":[{"id":"pass","name":"pass","properties":[],"text":"PASS"}]}]}
        """#)
        let spliced = ContentsUnboxing.resolve(parent).children!
        XCTAssertEqual(spliced.map(\.id), ["pass"])
        XCTAssertFalse(spliced[0].properties.contains {
            $0.type.hasPrefix("Margin") || $0.type.hasPrefix("Border")
        })
    }

    /// css-display-3 §2.5 removes the BOX, not the element: inheritable
    /// declarations still flow into the spliced grandchild — under its
    /// own (the child's FontSize wins over the wrapper's).
    func testInheritableDeclarationsFlowThroughTheRemovedBox() throws {
        let parent = try component(#"""
        {"id":"parent","name":"p","properties":[],
         "children":[
           {"id":"wrapper","name":"w","properties":[
              {"type":"Display","data":"CONTENTS"},
              {"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},
              {"type":"FontSize","data":{"px":12}}],
            "children":[
              {"id":"leaf","name":"l","properties":[{"type":"FontSize","data":{"px":20}}]}]}]}
        """#)
        let item = ContentsUnboxing.resolve(parent).children![0]
        XCTAssertTrue(item.properties.contains { $0.type == "Color" })
        // Exactly ONE FontSize survives — the child's own 20px (the
        // wrapper's 12px was blocked by the declared-type gate).
        XCTAssertEqual(item.properties.filter { $0.type == "FontSize" }.count, 1)
    }

    /// The -details chain: contents inside contents flattens recursively;
    /// text runs survive as carriers in document order; borders vanish.
    func testDetailsChainFlattensWithTextCarriers() throws {
        let details = try component(#"""
        {"id":"details","name":"d","properties":[
           {"type":"All","data":"INITIAL"},
           {"type":"BorderTopWidth","data":{"px":10}},
           {"type":"Display","data":"CONTENTS"}],
         "text":"S",
         "children":[
           {"id":"summary","name":"s","properties":[
              {"type":"All","data":"INITIAL"},
              {"type":"BorderTopWidth","data":{"px":10}},
              {"type":"Display","data":"CONTENTS"}],
            "text":"A"}]}
        """#)
        // details is a ROOT → self-strip + recursive child splice.
        let resolved = ContentsUnboxing.resolve(details)
        // Self-strip: border, Display AND `All` gone (so the all-reset
        // never fires on the pass-through), text kept.
        XCTAssertEqual(resolved.text, "S")
        XCTAssertFalse(resolved.properties.contains {
            $0.type == "All" || $0.type.hasPrefix("Border") || $0.type == "Display"
        })
        // The summary child became a bare text carrier for "A".
        XCTAssertEqual(resolved.children?.count, 1)
        let carrier = resolved.children![0]
        XCTAssertEqual(carrier.text, "A")
        XCTAssertNil(carrier.children)
        XCTAssertFalse(carrier.properties.contains { $0.type.hasPrefix("Border") })
    }

    /// An empty splice normalizes children to nil — the decode contract
    /// (children is nil, never [], when empty).
    func testEmptySpliceNormalizesChildrenToNil() throws {
        let parent = try component(#"""
        {"id":"parent","name":"p","properties":[],
         "children":[{"id":"w","name":"w","properties":[{"type":"Display","data":"CONTENTS"}]}]}
        """#)
        XCTAssertNil(ContentsUnboxing.resolve(parent).children)
    }

    // MARK: - The ported all-reset (RC6 companion — Compose parity)

    /// `all: <global>` present → EVERY other declaration drops (the
    /// Compose allReset semantics, css-cascade-4 §3.2 observable
    /// behaviour the audit fixtures pinned).
    func testAllResetDropsEveryOtherDeclaration() throws {
        let comp = try component(#"""
        {"id":"a","name":"a","properties":[
           {"type":"All","data":"INITIAL"},
           {"type":"Width","data":{"type":"length","px":160}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":1}}}]}
        """#)
        XCTAssertTrue(GlobalExtractor.applyingAllReset(to: comp.properties).isEmpty)
    }

    /// All-free lists flow through verbatim — the whole existing corpus
    /// takes the identity fast path.
    func testAllFreeListIsUntouched() throws {
        let comp = try component(
            #"{"id":"b","name":"b","properties":[{"type":"Width","data":{"type":"length","px":10}}]}"#)
        XCTAssertEqual(GlobalExtractor.applyingAllReset(to: comp.properties).map(\.type),
                       ["Width"])
    }
}
