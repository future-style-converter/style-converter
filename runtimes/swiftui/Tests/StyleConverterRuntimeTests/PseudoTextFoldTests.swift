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

    // MARK: - Wave 43 (lane V7): styled pseudo text, verbatim payloads

    /// css-contain/contain-content-011.html ::after (wave42-final
    /// per-test-ir, VERBATIM): `_text "25"` + `font-size: 3em` on a
    /// textless host. The Chromium ref paints the counter number at 48px
    /// (3 × the 16px inherited base); wave-42 refused the bucket so iOS
    /// rendered nothing. The fold must carry the text AND append the
    /// resolved typed FontSize so the ordinary text pipeline sizes it.
    func testStyledFontSizeFoldsAndAppendsTypedProperty() throws {
        let c = try component("""
        {"id":"contain-content-011__3-004","name":"div","properties":[],
         "pseudos":{"after":{"properties":{"content":"\\"25\\"","font-size":"3em"},
                             "_text":"25","_lossy":true,
                             "_lossyReasons":["generated-content-baked"]}}}
        """)
        let r = PseudoTextFold.resolve(c)
        // The baked number becomes the leaf text…
        XCTAssertEqual(r.text, "25")
        // …and the 3em resolves against the 16px document default → 48px,
        // in the exact {px:N} shape FontSizeExtractor unwraps.
        XCTAssertEqual(r.properties.last?.type, "FontSize")
        XCTAssertEqual(r.properties.last.flatMap { ValueExtractors.extractPx($0.data) }, 48)
    }

    /// The em base is the HOST's own font-size when declared (css-values-4
    /// §5.1.1: font-size's em resolves against the INHERITED size, and the
    /// pseudo's parent is its originating element) — not a hardcoded 16.
    func testStyledFontSizeEmResolvesAgainstHostFontSize() throws {
        let c = try component("""
        {"id":"x","name":"div",
         "properties":[{"type":"FontSize","data":{"px":20}}],
         "pseudos":{"before":{"properties":{"content":"\\"x\\"","font-size":"2em"},
                              "_text":"x"}}}
        """)
        let r = PseudoTextFold.resolve(c)
        // 2em × the host's 20px = 40px, appended LAST so it wins the
        // extractors' last-wins cascade over the host's own entry.
        XCTAssertEqual(r.properties.last.flatMap { ValueExtractors.extractPx($0.data) }, 40)
    }

    /// css-display/display-contents-dynamic-before-after-001.html,
    /// component …__0-049 (VERBATIM): `_text "P"` + `color: red` +
    /// `display: contents` + `border: 1px solid red`. display:contents
    /// makes the pseudo box-less (css-display-3 §2.5) so the border is
    /// spec-dead, and the declared ink converts to a typed Color entry.
    func testStyledColorUnderDisplayContentsFoldsWithTypedInk() throws {
        let c = try component("""
        {"id":"display-contents-dynamic-before-after-001__1__0__0-049",
         "name":"div","properties":[],
         "pseudos":{"before":{"properties":{"content":"\\"P\\"","color":"red",
                                            "display":"contents",
                                            "border":"1px solid red"},
                              "_text":"P","_lossy":true,
                              "_lossyReasons":["generated-content-baked"]}}}
        """)
        let r = PseudoTextFold.resolve(c)
        // The bucket folds despite display/border (both consumed by spec)…
        XCTAssertEqual(r.text, "P")
        // …and the declared red rides the copy as the typed srgb block.
        XCTAssertEqual(r.properties.last?.type, "Color")
        XCTAssertEqual(r.properties.last.map { extractColor($0.data) },
                       .srgb(r: 1, g: 0, b: 0, a: 1))
    }

    /// css-display/display-contents-before-after-002.html (VERBATIM):
    /// UNSTYLED before "P" / after "S" around host text "AS", each with
    /// `display: contents` + `border: 100px solid red`. The wave-42 gate
    /// refused these for the very declarations css-display-3 §2.5 makes
    /// dead — the ref paints "PASS", no red, and so must the fold.
    func testDisplayContentsWithDeadBorderFoldsAroundHostText() throws {
        let c = try component("""
        {"id":"display-contents-before-after-002__1-003","name":"div",
         "properties":[],"text":"AS",
         "pseudos":{"before":{"properties":{"display":"contents",
                                            "border":"100px solid red",
                                            "content":"\\"P\\""},
                              "_text":"P","_lossy":true,
                              "_lossyReasons":["generated-content-baked"]},
                    "after":{"properties":{"display":"contents",
                                           "border":"100px solid red",
                                           "content":"\\"S\\""},
                             "_text":"S","_lossy":true,
                             "_lossyReasons":["generated-content-baked"]}}}
        """)
        let r = PseudoTextFold.resolve(c)
        // The ref's word, assembled in CSS 2.1 §12.1 order.
        XCTAssertEqual(r.text, "PASS")
        // Unstyled buckets append nothing — no typed entries invented.
        XCTAssertTrue(r.properties.isEmpty)
    }

    /// A styled bucket that is NOT the component's sole ink refuses (one
    /// uniform `text` run cannot carry two styles) — named, and the
    /// component stays identity.
    func testStyledBucketSharingTheRunIsRefusedAndNamed() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],"text":"mid",
         "pseudos":{"before":{"properties":{"content":"\\"p\\"","color":"red"},
                              "_text":"p"}}}
        """)
        let r = PseudoTextFold.resolve(c)
        // Identity: the styled fold would repaint "mid" red — refused.
        XCTAssertEqual(r.text, "mid")
        XCTAssertTrue(r.properties.isEmpty)
        // The refusal is the named uniformity gate (dedupe pin).
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "pseudotext-styled-nonuniform-before", message: "dup"))
    }

    /// A box declaration WITHOUT display:contents still refuses — the
    /// §2.5 dead-box rule only applies to a box-less pseudo, so the
    /// wave-42 gate stands for every real-box shape.
    func testBorderWithoutDisplayContentsStillRefuses() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],
         "pseudos":{"before":{"properties":{"content":"\\"hi\\"",
                                            "border":"1px solid red"},
                              "_text":"hi"}}}
        """)
        // Refused whole: a bordered inline box is beyond the text fold.
        XCTAssertNil(PseudoTextFold.resolve(c).text)
        // And named under the same key family as every other refusal.
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "pseudotext-unsupported-before-border", message: "dup"))
    }

    /// An unparseable styling value (var() needs a scope this seam does
    /// not have) refuses the bucket — folding the text in the WRONG ink
    /// would half-render, the exact lie the wave-42 gate exists to stop.
    func testUnparseableColorRefusesTheBucket() throws {
        let c = try component("""
        {"id":"x","name":"div","properties":[],
         "pseudos":{"before":{"properties":{"content":"\\"p\\"",
                                            "color":"var(--ink)"},
                              "_text":"p"}}}
        """)
        XCTAssertNil(PseudoTextFold.resolve(c).text)
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "pseudotext-unsupported-before-color", message: "dup"))
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
