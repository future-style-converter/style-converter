//
// Wave-20 W3 skeptic pins (adversarial pass) — edge cases the lane
// suites left unpinned: layoutEnd row-mirroring with UNEQUAL row
// extents, wrong-side clear markers between mixed-side runs, the rtl
// mapping of the LOGICAL clear keywords, the inline-flow exact-fit
// wrap boundary, the zero-width-atom cursor quirk, and kebab-case wire
// keyword folding. Same inputs executed verbatim on Compose in
// SkepticW20W3ProbeTest.kt; values must match exactly (byte-parallel
// twin-suite discipline, P10-P18).
//

import XCTest
@testable import StyleConverterRuntime

final class SkepticW20W3Probes: XCTestCase {

    private func kw(_ type: String, _ keyword: String) -> IRProperty {
        IRProperty(type: type, data: .string(keyword))
    }

    // Probe A — layoutEnd with UNEQUAL row extents.
    func testProbeA_layoutEnd_unequal_rows() {
        let p = FloatRowPacking.layoutEnd(
            widths: [30.0, 30.0, 50.0],
            heights: [10.0, 20.0, 5.0],
            availableWidth: 70.0,
            strutPx: 0.0
        )
        XCTAssertEqual(p.x, [30.0, 0.0, 10.0])
        XCTAssertEqual(p.y, [0.0, 0.0, 20.0])
        XCTAssertEqual(p.width, 60.0)
        XCTAssertEqual(p.height, 25.0)
    }

    // Probe B — mixed sides with WRONG-side clear markers between.
    func testProbeB_mixed_sides_wrong_side_clears() {
        let F = FloatRowPacking.ChildFacts(floatsLeft: true, clearBreaksLeft: false)
        let R = FloatRowPacking.ChildFacts(floatsLeft: false, clearBreaksLeft: false, floatsRight: true)
        let BR = FloatRowPacking.ChildFacts(floatsLeft: false, clearBreaksLeft: true)
        let BRR = FloatRowPacking.ChildFacts(floatsLeft: false, clearBreaksLeft: false, clearBreaksRight: true)
        let segs = FloatRowPacking.segment([F, F, BRR, R, R, BR, F])
        XCTAssertEqual(segs.count, 5)
        XCTAssertTrue(segs[0].isRun); XCTAssertFalse(segs[0].rightRun); XCTAssertFalse(segs[0].strutted)
        XCTAssertFalse(segs[1].isRun)
        XCTAssertTrue(segs[2].isRun); XCTAssertTrue(segs[2].rightRun); XCTAssertFalse(segs[2].strutted)
        XCTAssertFalse(segs[3].isRun)
        XCTAssertFalse(segs[4].isRun)
    }

    // Probe C — rtl CLEAR mapping.
    func testProbeC_rtl_clear_logical_mapping() {
        let cs = FloatRowPacking.facts(from: [kw("Clear", "INLINE_START")], hasChildren: false, rtl: true)
        XCTAssertTrue(cs.clearBreaksRight); XCTAssertFalse(cs.clearBreaksLeft)
        let ce = FloatRowPacking.facts(from: [kw("Clear", "INLINE_END")], hasChildren: false, rtl: true)
        XCTAssertTrue(ce.clearBreaksLeft); XCTAssertFalse(ce.clearBreaksRight)
    }

    // Probe D — inline flow exact-fit boundary (strict >).
    func testProbeD_inline_exact_fit() {
        let p = InlineAtomFlow.layout(
            widths: [100.0, 100.0], heights: [10.0, 10.0],
            descents: [0.0, 0.0], marginStarts: [0.0, 0.0], marginEnds: [0.0, 0.0],
            availableWidth: 204.0, gapPx: 4.0
        )
        XCTAssertEqual(p.x, [0.0, 104.0])
        XCTAssertEqual(p.y, [0.0, 0.0])
        XCTAssertEqual(p.width, 204.0)
        let q = InlineAtomFlow.layout(
            widths: [100.0, 100.0], heights: [10.0, 10.0],
            descents: [0.0, 0.0], marginStarts: [0.0, 0.0], marginEnds: [0.0, 0.0],
            availableWidth: 203.0, gapPx: 4.0
        )
        XCTAssertEqual(q.x, [0.0, 0.0])
        XCTAssertEqual(q.y, [0.0, 10.0])
    }

    // Probe E — zero-width leading atom quirk parity.
    func testProbeE_zero_width_atom() {
        let p = InlineAtomFlow.layout(
            widths: [0.0, 50.0], heights: [10.0, 10.0],
            descents: [0.0, 0.0], marginStarts: [0.0, 0.0], marginEnds: [0.0, 0.0],
            availableWidth: 500.0, gapPx: 4.0
        )
        XCTAssertEqual(p.x, [0.0, 0.0])
    }

    // Probe G — kebab-case wire keywords fold identically.
    func testProbeG_kebab_wire() {
        let f = FloatRowPacking.facts(from: [kw("Float", "inline-end")], hasChildren: false)
        XCTAssertTrue(f.floatsRight)
        let c = FloatRowPacking.facts(from: [kw("Clear", "inline-end")], hasChildren: false)
        XCTAssertTrue(c.clearBreaksRight)
    }
}
