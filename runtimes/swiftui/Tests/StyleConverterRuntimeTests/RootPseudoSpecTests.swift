//
//  RootPseudoSpecTests.swift
//  Unit pins for the wave-28 lane-PG root-scope generated-box bridge
//  (RootPseudoSpec.swift / RootPseudoBox.swift's pure helpers).
//
//  THE MEASURED SHAPE: css-contain/contain-body-dir-001..004 declare
//  `html::before { content:""; width:100px; height:100px;
//  background:orange; display:block }` on a body that is `direction: rtl;
//  contain: layout`. In tools/titan/runs/wave27-final/sections/css-contain
//  the Chromium ref paints 10 000 orange px at image [16,16]-[115,115];
//  the iOS and Android captures painted ZERO orange px because this
//  runtime decoded the `pseudos` bucket and rendered nothing from it.
//  These tests hold the bridge's semantics; the pixels are a device-run
//  concern.
//

import XCTest
@testable import StyleConverterRuntime

final class RootPseudoSpecTests: XCTestCase {

    /// The live contain-body-dir-001 body-root, decoded from the wire.
    private func bodyRoot(contain: String? = "\"LAYOUT\"",
                          pseudos: String? = """
                          {"before":{"properties":{"content":"\\"\\"","width":"100px",
                          "height":"100px","background":"orange","display":"block"}}}
                          """) throws -> IRComponent {
        let containProp = contain.map { "{\"type\":\"Contain\",\"data\":[\($0)]}" } ?? ""
        let pseudoKey = pseudos.map { ",\"pseudos\":\($0)" } ?? ""
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"wpt__contain-body-dir-001__0-011","name":"wpt__contain-body-dir-001__0",
         "properties":[\(containProp)],"meta":{"role":"body-root"}\(pseudoKey)}
        """.utf8))
    }

    /// The bucket alone, for the pure bridge tests.
    private func bucket(_ json: String) throws -> IRValue {
        try JSONDecoder().decode(IRValue.self, from: Data(json.utf8))
    }

    func testBridgesTheContainFamilyBucket() throws {
        // Every declaration of the family maps to a field; nothing is lost.
        let b = try bucket("""
        {"properties":{"content":"\\"\\"","width":"100px","height":"100px",
         "background":"orange","display":"block"}}
        """)
        let spec = try XCTUnwrap(RootPseudo.spec(bucket: b, role: "before"))
        XCTAssertEqual(spec.widthPx, 100)
        XCTAssertEqual(spec.heightPx, 100)
        XCTAssertEqual(spec.backgroundCSS, "orange")
        // `content: ""` is a LITERAL empty string, not an unresolvable value.
        XCTAssertEqual(spec.text, "")
        XCTAssertTrue(spec.isBlock)
        // A background makes the box paintable even with empty content.
        XCTAssertTrue(spec.paints)
    }

    func testNonPxLengthsAreAutoNotZero() throws {
        // % / em / calc() are runtime-dependent by the IR's own rule, so the
        // axis stays auto (a silent 0 would paint an invisible box).
        let b = try bucket("""
        {"properties":{"width":"50%","height":"0","background":"orange"}}
        """)
        let spec = try XCTUnwrap(RootPseudo.spec(bucket: b, role: "before"))
        XCTAssertNil(spec.widthPx)
        // A bare `0` IS a valid length (css-values-4 §5.2).
        XCTAssertEqual(spec.heightPx, 0)
    }

    func testContentLiteralUnquotesAndRefusesFunctions() {
        // Quoted literals unquote; `none`/`normal` generate no text …
        XCTAssertEqual(RootPseudo.contentLiteral("\"x\"", role: "before"), "x")
        XCTAssertEqual(RootPseudo.contentLiteral("'x'", role: "before"), "x")
        XCTAssertEqual(RootPseudo.contentLiteral("none", role: "before"), "")
        // … and a function form has no context here, so it renders empty.
        XCTAssertEqual(RootPseudo.contentLiteral("counter(c)", role: "before"), "")
    }

    func testBlockDisplayAcceptsTheTwoValueSyntax() {
        // css-display-3 §2 two-value grammar and the block-level aliases.
        XCTAssertTrue(RootPseudo.isBlockDisplay("block"))
        XCTAssertTrue(RootPseudo.isBlockDisplay("block flow"))
        XCTAssertTrue(RootPseudo.isBlockDisplay("flow-root"))
        XCTAssertFalse(RootPseudo.isBlockDisplay("inline"))
    }

    func testEmptyOrNonPaintingBucketsRenderNothing() throws {
        // No bucket, no declarations, and a declarations-only bucket with
        // neither background nor content all resolve to "draw nothing".
        XCTAssertNil(RootPseudo.spec(bucket: nil, role: "before"))
        XCTAssertNil(RootPseudo.spec(bucket: try bucket("{\"properties\":{}}"), role: "before"))
        let sizeOnly = try XCTUnwrap(RootPseudo.spec(
            bucket: try bucket("{\"properties\":{\"width\":\"100px\",\"display\":\"block\"}}"),
            role: "before"))
        XCTAssertFalse(sizeOnly.paints)
    }

    func testOnlyABodyRootHostsARootScopeBox() throws {
        // The role gate is what keeps ordinary elements out of this path and
        // leaves `pseudos.marker` to the list path.
        XCTAssertNotNil(RootPseudo.specFor(component: try bodyRoot(), role: "before"))
        // Same bucket, no body-root role ⇒ no root-scope box.
        let ordinary = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"d","name":"d","properties":[],"meta":{"sourceTag":"div"},
         "pseudos":{"before":{"properties":{"background":"orange","display":"block"}}}}
        """.utf8))
        XCTAssertNil(RootPseudo.specFor(component: ordinary, role: "before"))
        // A body-root with no bucket, and a role nobody declared.
        XCTAssertNil(RootPseudo.specFor(component: try bodyRoot(pseudos: nil), role: "before"))
        XCTAssertNil(RootPseudo.specFor(component: try bodyRoot(), role: "after"))
    }

    func testTheContainmentGateMatchesTheCanvasGate() {
        // Same token rule as the background gate — delegation, not a copy.
        for kind in ["LAYOUT", "PAINT", "CONTENT", "STRICT"] {
            XCTAssertTrue(RootPseudo.containmentBlocksDirectionPropagation([kind]))
        }
        XCTAssertFalse(RootPseudo.containmentBlocksDirectionPropagation(["NONE"]))
        XCTAssertFalse(RootPseudo.containmentBlocksDirectionPropagation([]))
        XCTAssertFalse(RootPseudo.containmentBlocksDirectionPropagation(nil))
    }

    func testContainKeywordsReadBothWireShapes() throws {
        // Array wire (normal emission) and the defensive primitive form.
        XCTAssertEqual(RootPseudo.containKeywords(of: try bodyRoot()), ["LAYOUT"])
        let bare = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"b","name":"b","properties":[{"type":"Contain","data":"layout paint"}],
         "meta":{"role":"body-root"}}
        """.utf8))
        XCTAssertEqual(RootPseudo.containKeywords(of: bare), ["layout", "paint"])
        // No leaf at all ⇒ no containment ⇒ the body still propagates.
        let none = try bodyRoot(contain: nil)
        XCTAssertNil(RootPseudo.containKeywords(of: none))
        XCTAssertFalse(RootPseudo.containmentBlocksDirectionPropagation(
            RootPseudo.containKeywords(of: none)))
    }
}
