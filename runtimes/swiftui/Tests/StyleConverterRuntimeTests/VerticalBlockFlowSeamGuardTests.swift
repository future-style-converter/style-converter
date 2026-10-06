//
//  VerticalBlockFlowSeamGuardTests.swift
//  Wave 52 (lane L3 · failure-ink, pin P1 — the Swift twin).
//
//  Pins the POST-LOAD-EXTRACTION SIGNATURE (BakedLayoutSignature
//  .bakedPhysicalBox), the guard `ComponentRenderer.verticalBlockFlowZ2()`
//  consults before declining a vertical-writing-mode container. The same
//  rows run on Compose (VerticalBlockFlowSeamGuardTest.kt) — one rule table
//  on both natives.
//
//  Every payload is VERBATIM wave51-fix per-test IR:
//    tools/titan/runs/wave51-fix/sections/css-writing-modes/per-test-ir/
//      wpt__css-writing-modes__flexbox_align-items-stretch-writing-modes.json
//      (`…__1__1__1__0-777`: an AUTHORED `.square { width:50px; height:50px }`
//      — the child that tripped the wave-47 two-property heuristic and froze
//      the VStack: squares stacked vertically, item 50 wide, red probe showing
//      through its right 100px — ios f 0.999, colorFailed)
//    tools/titan/runs/wave51-fix/sections/css-writing-modes/per-test-ir/
//      wpt__css-writing-modes__abs-pos-border-offset-003.json (`…__0__0__0-321`)
//    tools/titan/runs/wave51-fix/sections/css-anchor-position/per-test-ir/
//      wpt__css-anchor-position__anchor-position-multicol-013.json
//      (`anchor-position-multicol-013__1__0-707`: a post-load-extracted box,
//      29 longhands — the family the guard exists to protect, 8 carriers)
//
//  MUTATION RECORD (executed 2026-09-25, lane L3): BakedLayoutSignature
//  .bakedPhysicalBox reduced to the wave-47 heuristic (Width && Height) →
//  the authored rows and the BoxSizing-alone row FAIL (expected false, was
//  true); restored byte-exact (sha256 verified), all rows green again.
//

import XCTest
@testable import StyleConverterRuntime

final class VerticalBlockFlowSeamGuardTests: XCTestCase {

    /// Decode a declaration list through the production wire decoder.
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode(
            IRComponent.self,
            from: Data(#"{"id":"x","name":"x","properties":\#(json)}"#.utf8)).properties
    }

    /// VERBATIM `…flexbox_align-items-stretch-writing-modes__1__1__1__0-777`.
    private let authoredSquare =
        #"[{"type":"Height","data":{"type":"length","px":50}},{"type":"Width","data":{"type":"length","px":50}}]"#

    /// VERBATIM `abs-pos-border-offset-003__0__0__0-321` (authored 10×20).
    private let authoredSibling =
        #"[{"type":"Width","data":{"type":"length","px":10}},{"type":"Height","data":{"type":"length","px":20}}]"#

    /// VERBATIM `anchor-position-multicol-013__1__0-707` — all 29 longhands.
    private let bakedBox = #"""
    [{"type":"BlockSize","data":{"px":110}},{"type":"Position","data":"STATIC"},
     {"type":"Width","data":{"type":"length","px":110}},{"type":"Height","data":{"type":"length","px":20}},
     {"type":"BoxSizing","data":"CONTENT_BOX"},
     {"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},
     {"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},
     {"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},
     {"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},
     {"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},
     {"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},
     {"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},
     {"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},
     {"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
     {"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
     {"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
     {"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
     {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":{"r":0,"g":128,"b":0}}},
     {"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}]
    """#

    // MARK: - P1: the predicate

    /// The wave-47 heuristic said true here and froze the VStack (ios f 0.999).
    func testAuthoredWidthHeightAloneIsNotBaked_the012Square() throws {
        XCTAssertFalse(BakedLayoutSignature.bakedPhysicalBox(try props(authoredSquare)),
                       "an authored width+height rule is not the post-load extractor's signature")
    }

    /// The census's other authored block-container carrier — unguarded now.
    func testAuthoredSiblingOfAbsPosBorderOffset003IsNotBaked() throws {
        XCTAssertFalse(BakedLayoutSignature.bakedPhysicalBox(try props(authoredSibling)))
    }

    /// The anchor-position-multicol family must STAY guarded (7 of its 8
    /// baked carrier docs pass today on the frozen VStack render, 007 is f —
    /// census F1).
    func testPostLoadExtracted013BoxIsBaked() throws {
        XCTAssertTrue(BakedLayoutSignature.bakedPhysicalBox(try props(bakedBox)))
    }

    /// Neither the basis alone nor a band alone fakes the signature; basis
    /// plus either band is the extractor's shape; a missing size never is.
    func testBoxSizingAloneOrABandAloneDoesNotFakeTheSignature() throws {
        let square = try props(authoredSquare)
        let boxSizing = try props(#"[{"type":"BoxSizing","data":"BORDER_BOX"}]"#)
        let padding = try props(#"[{"type":"PaddingTop","data":{"px":4}}]"#)
        let borderStyle = try props(#"[{"type":"BorderTopStyle","data":"NONE"}]"#)
        XCTAssertFalse(BakedLayoutSignature.bakedPhysicalBox(square + boxSizing))
        XCTAssertFalse(BakedLayoutSignature.bakedPhysicalBox(square + padding))
        XCTAssertTrue(BakedLayoutSignature.bakedPhysicalBox(square + boxSizing + padding))
        XCTAssertTrue(BakedLayoutSignature.bakedPhysicalBox(square + boxSizing + borderStyle))
        XCTAssertFalse(BakedLayoutSignature.bakedPhysicalBox(
            try props(bakedBox).filter { $0.type != "Height" }))
    }

    // MARK: - Used-mode census row (resume pass 2026-10-05, census.json f1Used)

    /// VERBATIM css-tables/height-distribution/td-different-subpixel-padding-
    /// in-same-row-vertical-rl `__0__0__{0..4}-304..308`: five authored <td>s
    /// under a <tr> that INHERITS vertical-rl (wave51-fix ios P 0.9638 ·
    /// android P 0.9609). The old guard froze the row; the new one does not
    /// (padding, no BoxSizing), so the row's protection is `tableTrackPlan`
    /// — role .row → `.inlineRow`, decided BEFORE `verticalBlockFlowZ2()`.
    /// MUTATION RECORD (executed 2026-10-05): `boxSizing` dropped from
    /// bakedPhysicalBox → assertion (1) FAILS; restored byte-exact (sha256
    /// verified). (2) is a DEPENDENCY pin on TableBoxTree/TableSeparatedTracks
    /// (not this lane's files — never mutated); its tag-less control row
    /// proves the assertion can fail.
    func testSubpixelRowIsProtectedByTheTablePathNotTheGuard() throws {
        // (1) No cell is a baked box — authored sizes + padding, no BoxSizing.
        for side in ["1", "1.2", "1.5", "1.7", "2"] {
            let cell = try props(#"[{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}},{"type":"PaddingTop","data":{"px":2}},{"type":"PaddingRight","data":{"px":\#(side)}},{"type":"PaddingBottom","data":{"px":2}},{"type":"PaddingLeft","data":{"px":\#(side)}}]"#)
            XCTAssertFalse(BakedLayoutSignature.bakedPhysicalBox(cell), "cell padding \(side)")
        }
        // (2) The undeclared <tr> takes the §17.6.1 inline-row track first.
        XCTAssertEqual(TableSeparatedTracks.arrangement(TableBoxTree.roleOf([], sourceTag: "tr")), .inlineRow)
        // Control: without its UA tag the row would fall through to the seam.
        XCTAssertEqual(TableSeparatedTracks.arrangement(TableBoxTree.roleOf([], sourceTag: nil)), .none)
    }
}
