//
//  PseudoBoxBridgeTests.swift
//  Unit pins for the wave-49 lane-A2 generated-BOX seam
//  (StyleEngine/content/PseudoBoxBridge.swift + the PseudoTextFold splice).
//
//  Every `pseudos` payload below is VERBATIM from the wave48-final gate's
//  consumed IR (tools/titan/runs/wave48-final/sections/*/per-test-ir/*.json)
//  — the documents whose green ::before box iOS painted as a bare red host
//  (report/images/iOS/wpt__css-pseudo__before-as-flex-container.png,
//  ios-ref wptPass false / colorFailed true). A replay of this bridge's
//  rules over all 30 frozen sections claims exactly ONE bucket and names
//  exactly one refusal; both ends are pinned here.
//

import XCTest
@testable import StyleConverterRuntime

final class PseudoBoxBridgeTests: XCTestCase {

    /// Fresh log-dedupe state per test so the logOnce pins are independent
    /// of execution order.
    override func setUp() {
        super.setUp()
        PropertyTracker._resetForTests()
    }

    /// Decode one component from wire JSON — the same tolerant decoder the
    /// runtime uses, so payload shapes stay honest.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// The typed datum for one property type on a component, for assertions.
    private func datum(_ c: IRComponent, _ type: String) -> IRValue? {
        c.properties.first { $0.type == type }?.data
    }

    /// css-pseudo/before-as-flex-container component __0-020, VERBATIM.
    private let beforeAsFlexContainer = """
    {"id":"wpt__css-pseudo__before-as-flex-container__0-020",
     "name":"wpt__css-pseudo__before-as-flex-container__0",
     "properties":[{"type":"Width","data":{"type":"length","px":200}},
                   {"type":"Height","data":{"type":"length","px":100}},
                   {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],
     "pseudos":{"before":{"properties":{"content":"\\"A B\\"","display":"flex",
        "justify-content":"space-between","width":"200px","height":"100px",
        "background":"green"},"_text":"A B","_lossy":true,
        "_lossyReasons":["generated-content-baked"]}},
     "meta":{"lang":"en"}}
    """

    // MARK: - The claimed population

    func testBeforeAsFlexContainerGeneratesATyped200x100GreenFlexBox() throws {
        let host = try component(beforeAsFlexContainer)
        // Pre-condition: the wire div is a childless leaf — which is why the
        // pre-fix iOS capture painted its red background bare.
        XCTAssertNil(host.children)
        let folded = PseudoTextFold.resolve(host)
        // CSS 2.1 §12.1: ::before is generated INSIDE the element, first.
        XCTAssertEqual(folded.children?.count, 1)
        let box = try XCTUnwrap(folded.children?.first)
        XCTAssertEqual(box.id, "wpt__css-pseudo__before-as-flex-container__0-020::before")
        // css-display-3 §2: `flex` is a BLOCK-LEVEL outer display, wired in
        // the corpus's SCREAMING_SNAKE spelling.
        XCTAssertEqual(datum(box, "Display")?.stringValue, "FLEX")
        // css-sizing-3 §3.1.1 used sizes, in the `{type:length, px:N}` shape.
        XCTAssertEqual(ValueExtractors.extractPx(try XCTUnwrap(datum(box, "Width"))).map(Double.init), 200)
        XCTAssertEqual(ValueExtractors.extractPx(try XCTUnwrap(datum(box, "Height"))).map(Double.init), 100)
        // css-color-4 `green` is #008000 — the sRGB block the extractor reads.
        let srgb = try XCTUnwrap(datum(box, "BackgroundColor")?["srgb"])
        XCTAssertEqual(srgb["r"]?.doubleValue ?? -1, 0, accuracy: 1e-6)
        XCTAssertEqual(srgb["g"]?.doubleValue ?? -1, 128.0 / 255.0, accuracy: 1e-6)
        XCTAssertEqual(srgb["b"]?.doubleValue ?? -1, 0, accuracy: 1e-6)
        // css-align-3 §5.1 keyword, same wire spelling.
        XCTAssertEqual(datum(box, "JustifyContent")?.stringValue, "SPACE_BETWEEN")
        // The producer's BAKED resolution is the box's own text run.
        XCTAssertEqual(box.text, "A B")
        // The host keeps its own properties and gains no text of its own —
        // the box path never folds glyphs into the host's run.
        XCTAssertEqual(folded.properties.count, host.properties.count)
        XCTAssertNil(folded.text)
    }

    // MARK: - The refused / not-owned populations

    func testAnchorFieldsetRefusesBecauseAPositionedBoxIsNotModeled() throws {
        // css-anchor-position/anchor-fieldset-001 component __0__0-524,
        // verbatim: a `position: absolute` generated box needs a
        // containing-block resolution this bridge does not have.
        let bucket = try JSONDecoder().decode(IRValue.self, from: Data("""
        {"properties":{"content":"\\"\\"","display":"block","width":"10px","height":"10px",
          "background":"purple","position":"absolute","position-anchor":"auto",
          "position-area":"bottom right"}}
        """.utf8))
        XCTAssertNil(PseudoBoxBridge.claim(bucket: bucket, role: "before", hostRole: nil))
    }

    func testContainBodyRootBucketStaysWithRootPseudoBox() throws {
        // css-contain/contain-body-dir-001 component __0-011, verbatim: the
        // SAME block/width/height/background shape, but on the body-root —
        // claiming it here would double-render RootPseudoBoxView's square.
        let bucket = try JSONDecoder().decode(IRValue.self, from: Data("""
        {"properties":{"content":"\\"\\"","width":"100px","height":"100px",
          "background":"orange","display":"block"}}
        """.utf8))
        XCTAssertNil(PseudoBoxBridge.claim(bucket: bucket, role: "before", hostRole: "body-root"))
        // …and the very same bucket on an ORDINARY element IS a box — the
        // role is the only thing separating the two populations.
        XCTAssertNotNil(PseudoBoxBridge.claim(bucket: bucket, role: "before", hostRole: nil))
    }

    func testDisplayNoneGeneratesNoBox() throws {
        // css-pseudo/before-dynamic-display-none component __1-022, verbatim.
        // css-display-3 §2.5: `none` generates NO boxes, so the FAIL string
        // and its red background must never reach this path.
        let bucket = try JSONDecoder().decode(IRValue.self, from: Data("""
        {"properties":{"content":"\\"FAIL\\"","position":"absolute","width":"100px",
          "height":"100px","background-color":"red","display":"none"},
         "_text":"FAIL","_lossy":true,"_lossyReasons":["generated-content-baked"]}
        """.utf8))
        XCTAssertNil(PseudoBoxBridge.claim(bucket: bucket, role: "before", hostRole: nil))
    }

    func testInlineBlockStaysOnTheInlineTextPath() throws {
        // css-cascade/scope-pseudo-element component __0__0-120, verbatim:
        // `inline-block` is an INLINE-level outer display (css-display-3
        // §2.1), so the host's inline flow owns it — this path must decline.
        let bucket = try JSONDecoder().decode(IRValue.self, from: Data("""
        {"properties":{"content":"\\"B\\"","width":"20px","height":"20px",
          "display":"inline-block","background-color":"tomato"},
         "_text":"B","_lossy":true,"_lossyReasons":["generated-content-baked"]}
        """.utf8))
        XCTAssertNil(PseudoBoxBridge.claim(bucket: bucket, role: "before", hostRole: nil))
    }

    func testBakedCounterRunIsNeverABox() throws {
        // The dominant corpus shape (348 `content` declarations, only 33
        // `display`): a baked counter run, inline by css-display-3 §2's
        // initial value, and still folded as TEXT by PseudoTextFold.
        let c = try component("""
        {"id":"c","name":"c","properties":[],"text":"a",
         "pseudos":{"before":{"properties":{"content":"\\"1\\" \\" \\""},"_text":"1 "}}}
        """)
        XCTAssertNil(PseudoBoxBridge.claim(
            bucket: c.pseudos?["before"], role: "before", hostRole: nil))
        // The wave-42 text fold is untouched by this lane.
        XCTAssertEqual(PseudoTextFold.resolve(c).text, "1 a")
        XCTAssertNil(PseudoTextFold.resolve(c).children)
    }

    func testABlockBoxWithNeitherBackgroundNorGlyphsPaintsNothing() throws {
        // GATE 3 — mirrors RootPseudoSpec.paints: emitting this box would
        // add an invisible 10x10 layout node that displaces real content.
        let bucket = try JSONDecoder().decode(IRValue.self, from: Data("""
        {"properties":{"content":"\\"\\"","display":"block","width":"10px","height":"10px"}}
        """.utf8))
        XCTAssertNil(PseudoBoxBridge.claim(bucket: bucket, role: "before", hostRole: nil))
    }

    func testARelativeLengthRefusesRatherThanGuessing() throws {
        // CLAUDE.md's normalization rule: em/%/calc()/var() are
        // runtime-dependent and cannot be pre-computed at this seam.
        let bucket = try JSONDecoder().decode(IRValue.self, from: Data("""
        {"properties":{"content":"\\"\\"","display":"block","width":"50%","height":"2em",
          "background":"green"}}
        """.utf8))
        XCTAssertNil(PseudoBoxBridge.claim(bucket: bucket, role: "before", hostRole: nil))
    }

    func testAnAfterBoxTrailsTheElementsOwnChildren() throws {
        // CSS 2.1 §12.1: ::after is generated as the element's LAST child —
        // and unlike the text fold, a real trailing child CAN express that
        // even when the host has composed children.
        let c = try component("""
        {"id":"h","name":"h","properties":[],
         "children":[{"id":"kid","name":"kid","properties":[]}],
         "pseudos":{"after":{"properties":{"content":"\\"\\"","display":"block",
            "width":"10px","height":"10px","background":"green"}}}}
        """)
        XCTAssertEqual(PseudoTextFold.resolve(c).children?.map(\.id), ["kid", "h::after"])
    }
}
