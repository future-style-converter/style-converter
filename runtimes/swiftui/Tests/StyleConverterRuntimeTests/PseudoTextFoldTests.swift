//
//  PseudoTextFoldTests.swift
//  Unit pins for the wave-42 lane-W2 pseudos text seam
//  (StyleEngine/content/PseudoTextBridge.swift + PseudoTextFold.swift).
//
//  THE MEASURED SHAPE: every pseudo payload below is VERBATIM from the
//  wave-41 gate's consumed IR (tools/titan/runs/wave41-final/sections/
//  */per-test-ir/*.json) — the css-lists / css-counter-styles fail set
//  whose Chromium refs paint baked counter text ("B7A5", "2050000000",
//  "1 a 1") where the iOS captures were BLANK (e.g. ios-screenshots/
//  wpt__css-lists__counter-reset-reversed-pseudo-001.png). These tests
//  hold the fold's semantics; the pixels are a device-run concern.
//

import XCTest
@testable import StyleConverterRuntime

final class PseudoTextFoldTests: XCTestCase {

    /// Fresh log-dedupe state per test so the logOnce pins are
    /// independent of execution order.
    override func setUp() {
        super.setUp()
        PropertyTracker._resetForTests()
    }

    /// Decode one component from wire JSON — the same tolerant decoder
    /// the runtime uses, so payload shapes stay honest.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    // MARK: - The wave-41 fail set, verbatim

    /// css-lists/counter-list-item.html, component
    /// counter-list-item__1__0__0-281: before "1 " + own "a" + after " 1"
    /// must reproduce the ref's "1 a 1" glyph order (CSS 2.1 §12.1).
    func testCounterListItemFoldsBeforeAndAfterAroundOwnText() throws {
        let c = try component("""
        {"id":"counter-list-item__1__0__0-281","name":"li","properties":[],
         "text":"a",
         "pseudos":{"before":{"properties":{"content":"\\"1\\" \\" \\""},
                              "_text":"1 ","_lossy":true,
                              "_lossyReasons":["generated-content-baked"]},
                    "after":{"properties":{"content":"\\" \\" \\"1\\""},
                             "_text":" 1","_lossy":true,
                             "_lossyReasons":["generated-content-baked"]}}}
        """)
        XCTAssertEqual(PseudoTextFold.resolve(c).text, "1 a 1")
    }

    /// css-lists/counter-reset-increment-overflow-underflow.html,
    /// component …__0__0-330: a before-only bucket on a TEXTLESS leaf —
    /// the pseudo text becomes the whole content, and the baked
    /// `counter-increment` declaration is inert (already resolved).
    func testBeforeOnlyBucketBecomesTheLeafText() throws {
        let c = try component("""
        {"id":"counter-reset-increment-overflow-underflow__0__0-330",
         "name":"div","properties":[],
         "pseudos":{"before":{"properties":{"counter-increment":"over-counter 50000000",
                                            "content":"\\"2050000000\\""},
                              "_text":"2050000000","_lossy":true,
                              "_lossyReasons":["generated-content-baked"]}}}
        """)
        XCTAssertEqual(PseudoTextFold.resolve(c).text, "2050000000")
    }

    /// css-lists/counter-reset-reversed-pseudo-001.html, component
    /// …__0__0-402: before AND after on a textless leaf concatenate in
    /// document order — the ref's visible "B7A5".
    func testBeforeAndAfterConcatenateOnTextlessLeaf() throws {
        let c = try component("""
        {"id":"counter-reset-reversed-pseudo-001__0__0-402","name":"div",
         "properties":[],
         "pseudos":{"before":{"properties":{"counter-increment":"foo -1",
                                            "content":"\\"B\\" \\"7\\""},
                              "_text":"B7","_lossy":true,
                              "_lossyReasons":["generated-content-baked"]},
                    "after":{"properties":{"counter-increment":"foo -2",
                                           "content":"\\"A\\" \\"5\\""},
                             "_text":"A5","_lossy":true,
                             "_lossyReasons":["generated-content-baked"]}}}
        """)
        XCTAssertEqual(PseudoTextFold.resolve(c).text, "B7A5")
    }

    /// css-counter-styles/counter-name-case-sensitive.html, component
    /// …__1-514: the multi-string bake ("1" "-" "5" → "1-5") arrives
    /// pre-joined in `_text` — the fold must not re-derive it.
    func testMultiStringContentUsesTheBakedJoin() throws {
        let c = try component("""
        {"id":"wpt__css-counter-styles__counter-name-case-sensitive__1-514",
         "name":"div","properties":[],
         "pseudos":{"before":{"properties":{"content":"\\"1\\" \\"-\\" \\"5\\""},
                              "_text":"1-5","_lossy":true,
                              "_lossyReasons":["generated-content-baked"]}}}
        """)
        XCTAssertEqual(PseudoTextFold.resolve(c).text, "1-5")
    }

    /// css-lists/counter-reset-reversed-display-none.html, component
    /// …__0__2-358: `content: counter(foo)` with NO baked `_text` (the
    /// display:none subtree was never laid out) folds NOTHING — web
    /// renders the empty string for exactly this shape — and the gap is
    /// named once via the unbaked log.
    func testUnbakedCounterContentFoldsNothing() throws {
        let c = try component("""
        {"id":"counter-reset-reversed-display-none__0__2-358","name":"div",
         "properties":[],
         "pseudos":{"before":{"properties":{"content":"counter(foo)"}}}}
        """)
        // Identity: text stays absent, the component renders as before.
        XCTAssertNil(PseudoTextFold.resolve(c).text)
    }

    // MARK: - Ownership boundaries (never double-render)

    /// The body-root's bucket belongs to RootPseudoBox (wave-28 lane PG):
    /// the contain-family box (`content:"" + width/height/background`)
    /// must NOT also fold — payload verbatim from that lane's family.
    func testBodyRootBucketIsLeftToRootPseudoBox() throws {
        let c = try component("""
        {"id":"wpt__contain-body-dir-001__0-011","name":"body",
         "properties":[],"meta":{"role":"body-root"},
         "pseudos":{"before":{"properties":{"content":"\\"\\"","width":"100px",
                    "height":"100px","background":"orange","display":"block"}}}}
        """)
        // Identity — the box path owns it; no text materialises.
        XCTAssertNil(PseudoTextFold.resolve(c).text)
    }

    /// A BOX pseudo on an ordinary element (the css-anchor-position /
    /// css-contain shape) is refused whole: width/height/background are
    /// beyond an inline text run, and a half-fold would lie.
    func testBoxDeclarationsRefuseTheFoldAndAreNamed() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],
         "pseudos":{"before":{"properties":{"content":"\\"hi\\"","width":"50px"},
                              "_text":"hi"}}}
        """)
        // Refused: the baked text does NOT fold when the bucket also asks
        // for a box this platform cannot attach to a text run.
        XCTAssertNil(PseudoTextFold.resolve(c).text)
        // And the refusal is a NAMED once-per-(role,prop) log — a second
        // sighting of the same key stays silent (dedupe pin).
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "pseudotext-unsupported-before-width", message: "dup"))
    }

    /// `pseudos.marker` belongs to the list marker machinery
    /// (meta.markerText / markerPlacement) — never folded here.
    func testMarkerBucketIsDelegatedNotFolded() throws {
        let c = try component("""
        {"id":"x","name":"li","properties":[],
         "pseudos":{"marker":{"properties":{"content":"\\"1. \\""},"_text":"1. "}}}
        """)
        // Identity: no text, the marker path keeps sole ownership.
        XCTAssertNil(PseudoTextFold.resolve(c).text)
    }

    // MARK: - Placement gates

    /// ::after must TRAIL composed children (web order: text, children,
    /// after) — a leading fold cannot express that, so only ::before
    /// folds on a component with children; the ::after refusal is named.
    func testAfterWithChildrenRefusedButBeforeStillFolds() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],"text":"mid",
         "children":[{"id":"k","name":"span","properties":[]}],
         "pseudos":{"before":{"properties":{"content":"\\"pre \\""},"_text":"pre "},
                    "after":{"properties":{"content":"\\" post\\""},"_text":" post"}}}
        """)
        // Leading fold only: "pre mid", never "pre mid post".
        XCTAssertEqual(PseudoTextFold.resolve(c).text, "pre mid")
        // The dropped ::after was named once (dedupe pin, as above).
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "pseudotext-after-children", message: "dup"))
    }

    /// `meta.runs` owns the content slot (spec 03 §4.1) and the renderer
    /// drops the `text` slot under a plan — a fold there would silently
    /// vanish, so the fold refuses instead.
    func testRunsCarrierRefusesTheFold() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],"text":"ab",
         "children":[{"id":"k","name":"span","properties":[]}],
         "meta":{"runs":[{"text":"a"},{"child":"k"},{"text":"b"}]},
         "pseudos":{"before":{"properties":{"content":"\\"1\\""},"_text":"1"}}}
        """)
        // Identity: the text is exactly the wire's own, unfolded.
        XCTAssertEqual(PseudoTextFold.resolve(c).text, "ab")
    }

    // MARK: - Wire tolerance + identity

    /// The v2 spelling (`text`) is tolerated exactly like web's
    /// PseudoNodeRenderer (`p._text ?? p.text`).
    func testV2TextSpellingIsTolerated() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],
         "pseudos":{"before":{"properties":{"content":"\\"v2\\""},"text":"v2"}}}
        """)
        XCTAssertEqual(PseudoTextFold.resolve(c).text, "v2")
    }

    /// `content: ""` bakes an empty string — a legal pseudo that paints
    /// nothing, so the fold is identity (no empty-string text invented).
    func testEmptyBakedTextFoldsNothing() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],
         "pseudos":{"before":{"properties":{"content":"\\"\\""},"_text":""}}}
        """)
        XCTAssertNil(PseudoTextFold.resolve(c).text)
    }

    /// A pseudos-free component is untouched — the identity path the
    /// whole committed baseline corpus takes through the new init seam.
    func testPseudosFreeComponentIsIdentity() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],"text":"plain"}
        """)
        let r = PseudoTextFold.resolve(c)
        // Text untouched, and no pseudo state invented.
        XCTAssertEqual(r.text, "plain")
        XCTAssertNil(r.pseudos)
    }

    /// The folded copy KEEPS `pseudos`: ContentsUnboxing's eligibility
    /// gate reads it (a pseudos-carrying component never unboxes) and
    /// spec 05 rule 4 wants the wire shape re-derivable.
    func testFoldKeepsThePseudosBucketOnTheCopy() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],
         "pseudos":{"before":{"properties":{"content":"\\"1\\""},"_text":"1"}}}
        """)
        let r = PseudoTextFold.resolve(c)
        // The fold happened…
        XCTAssertEqual(r.text, "1")
        // …and consuming was not erasing.
        XCTAssertNotNil(r.pseudos)
    }
}
