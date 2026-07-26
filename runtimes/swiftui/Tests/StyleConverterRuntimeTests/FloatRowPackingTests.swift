//
//  FloatRowPackingTests.swift
//  Wave-19 lane FLOAT — XCTest pins for the pure float-row-packing
//  twins (FloatRowPacking.swift ↔ Compose FloatRowPacking.kt). Every
//  pinned value here mirrors FloatRowPackingTest.kt VERBATIM so a
//  skeptic probe can diff the two suites; the shared table is the lane
//  pin table (P1-P6). Corpus geometry comes from the two failing WPT
//  fixtures:
//   • css-flexbox flex-abspos-staticpos-justify-self-001 — 14 left
//     floats (margin box 27×19) split 3/2/5/4 by `<br clear:both>`
//     siblings; the ref rows advance 20px (br line-box strut).
//   • css-grid descendant-static-position-001 — a green+grey 20×40 pair
//     inside a shrink-to-fit abspos box (un-strutted, unbounded).
//

import XCTest
@testable import StyleConverterRuntime

final class FloatRowPackingTests: XCTestCase {

    // IR keyword wire shape — bare string, exactly what the live
    // per-test IR carries ({"type":"Float","data":"LEFT"}).
    private func kw(_ type: String, _ keyword: String) -> IRProperty {
        IRProperty(type: type, data: .string(keyword))
    }

    // ── P1/P2 — facts derivation ──

    func testLeftAndInlineStartFloatsPackRightFloatsDoNot() {
        // P1: the LTR-normalized engine folds inline-start onto left.
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Float", "LEFT")], hasChildren: false).floatsLeft)
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Float", "INLINE_START")], hasChildren: false).floatsLeft)
        // Right-family floats keep the wave-5 end-alignment path (F4).
        XCTAssertFalse(FloatRowPacking.facts(from: [kw("Float", "RIGHT")], hasChildren: false).floatsLeft)
        XCTAssertFalse(FloatRowPacking.facts(from: [kw("Float", "INLINE_END")], hasChildren: false).floatsLeft)
        // No Float wire at all → plain in-flow content.
        XCTAssertFalse(FloatRowPacking.facts(from: [], hasChildren: false).floatsLeft)
    }

    func testChildlessClearBothIsABreakRightClearAndContentClearsAreNot() {
        // P2: the `<br clear:both>` wire shape — childless, Clear only.
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Clear", "BOTH")], hasChildren: false).clearBreaksLeft)
        // clear:left / inline-start also clear a LEFT run (§9.5.2).
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Clear", "LEFT")], hasChildren: false).clearBreaksLeft)
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Clear", "INLINE_START")], hasChildren: false).clearBreaksLeft)
        // clear:right does NOT clear a left run (§9.5.2 same-side rule).
        XCTAssertFalse(FloatRowPacking.facts(from: [kw("Clear", "RIGHT")], hasChildren: false).clearBreaksLeft)
        // A clear on a CONTENT box is real layout, not a break marker.
        XCTAssertFalse(FloatRowPacking.facts(from: [kw("Clear", "BOTH")], hasChildren: true).clearBreaksLeft)
        // A floating box never doubles as a break marker.
        XCTAssertFalse(
            FloatRowPacking.facts(from: [kw("Float", "LEFT"), kw("Clear", "BOTH")], hasChildren: false)
                .clearBreaksLeft
        )
    }

    // Shorthand fact constructors for the segmentation pins.
    private let F = FloatRowPacking.ChildFacts(floatsLeft: true, clearBreaksLeft: false)
    private let BR = FloatRowPacking.ChildFacts(floatsLeft: false, clearBreaksLeft: true)
    private let X = FloatRowPacking.ChildFacts(floatsLeft: false, clearBreaksLeft: false)

    // ── P1/P2 — segmentation ──

    func testJustifySelf001SiblingPatternSegmentsInto3254StruttedRuns() {
        // The live wire order: 14 floats + 4 br markers (18 siblings).
        let segs = FloatRowPacking.segment(
            [F, F, F, BR, F, F, BR, F, F, F, F, F, BR, F, F, F, F, BR]
        )
        // 4 runs + 4 br singles = 8 segments, sibling order preserved.
        XCTAssertEqual(8, segs.count)
        // Run 1: indices 0-2, terminated by the br at 3 → strutted.
        XCTAssertEqual([0, 1, 2], segs[0].indices)
        XCTAssertTrue(segs[0].isRun); XCTAssertTrue(segs[0].strutted)
        // The br itself is a single (renders ~0-size in WPT mode).
        XCTAssertEqual([3], segs[1].indices); XCTAssertFalse(segs[1].isRun)
        // Runs 2-4 mirror the 2/5/4 ref rows, all strutted.
        XCTAssertEqual([4, 5], segs[2].indices); XCTAssertTrue(segs[2].strutted)
        XCTAssertEqual([7, 8, 9, 10, 11], segs[4].indices); XCTAssertTrue(segs[4].strutted)
        XCTAssertEqual([13, 14, 15, 16], segs[6].indices); XCTAssertTrue(segs[6].strutted)
    }

    func testGridFloatPairIsOneUnstruttedRun() {
        // descendant-static-position-001: green+grey, no br after.
        let segs = FloatRowPacking.segment([F, F])
        XCTAssertEqual(1, segs.count)
        XCTAssertTrue(segs[0].isRun)
        // No trailing clear sibling → the run reports its bare height.
        XCTAssertFalse(segs[0].strutted)
        XCTAssertEqual([0, 1], segs[0].indices)
    }

    func testLoneFloatsAndNonFloatsStaySingles() {
        // A lone float keeps today's block path (F4 conservatism).
        let segs = FloatRowPacking.segment([F, X, F])
        XCTAssertEqual(3, segs.count)
        XCTAssertFalse(segs.contains(where: { $0.isRun }))
    }

    // ── P3/P5/P6 — packing geometry ──

    func testCorpusBigRowPacksSideBySideWithTheBrStrut() {
        // Three 27×19 margin boxes (16w+4pad+2border+5margin / 10h+2+2+5),
        // unbounded width, strutted by the trailing br (pin P6).
        let plan = FloatRowPacking.layout(
            widths: [27.0, 27.0, 27.0],
            heights: [19.0, 19.0, 19.0],
            availableWidth: .infinity,
            strutPx: FloatRowPacking.strutPx
        )
        // §9.5.1 rule 2 — each float packs against the previous margin edge.
        XCTAssertEqual([0.0, 27.0, 54.0], plan.x)
        // Single row — every float sits at the row top (rule 6).
        XCTAssertEqual([0.0, 0.0, 0.0], plan.y)
        // Run extent = Σ margin-box widths.
        XCTAssertEqual(81.0, plan.width)
        // P6: 19px of float < the 20px br line box → the strut wins,
        // reproducing the ref's 20px row pitch.
        XCTAssertEqual(20.0, plan.height)
    }

    func testGridPairReportsBareHeightWithoutStrut() {
        // Green+grey 20×40 inside the shrink-to-fit abspos (strut 0).
        let plan = FloatRowPacking.layout(
            widths: [20.0, 20.0],
            heights: [40.0, 40.0],
            availableWidth: .infinity,
            strutPx: 0.0
        )
        // Side-by-side pair: grey's left edge = green's right edge.
        XCTAssertEqual([0.0, 20.0], plan.x)
        // The run is exactly the 40×40 the ref paints (red fully covered).
        XCTAssertEqual(40.0, plan.width)
        XCTAssertEqual(40.0, plan.height)
    }

    func testFiniteWidthWrapsAtExhaustionPerRule7() {
        // Five 27-wide floats against a 100px containing block: three fit
        // (54+27=81 ≤ 100), the fourth would end at 108 > 100 → new row.
        let plan = FloatRowPacking.layout(
            widths: [Double](repeating: 27.0, count: 5),
            heights: [Double](repeating: 19.0, count: 5),
            availableWidth: 100.0,
            strutPx: 0.0
        )
        XCTAssertEqual([0.0, 27.0, 54.0, 0.0, 27.0], plan.x)
        // Row 2 starts below row 1's tallest float (P5).
        XCTAssertEqual([0.0, 0.0, 0.0, 19.0, 19.0], plan.y)
        XCTAssertEqual(81.0, plan.width)
        XCTAssertEqual(38.0, plan.height)
    }

    func testARowsFirstFloatMayOverflowRule7LooseCase() {
        // A 150-wide float in a 100-wide block stays in row 1 (§9.5.1
        // rule 7: only a float with another float to its LEFT wraps).
        let plan = FloatRowPacking.layout(
            widths: [150.0, 30.0],
            heights: [10.0, 8.0],
            availableWidth: 100.0,
            strutPx: 0.0
        )
        // The second float cannot fit beside it → row 2 at x=0.
        XCTAssertEqual([0.0, 0.0], plan.x)
        XCTAssertEqual([0.0, 10.0], plan.y)
        // Extent = the widest row (the overflowing first float).
        XCTAssertEqual(150.0, plan.width)
        XCTAssertEqual(18.0, plan.height)
    }

    // ── Wave-20 W3 — right/inline-end float runs (P10-P14) ──
    // Corpus geometry: css-grid descendant-static-position-002/004 —
    // a green+grey Float:RIGHT 20×40 pair inside a shrink-to-fit abspos
    // box under a direction:rtl grid; ref paints grey LEFT of green
    // (grey x40-59, green x60-79). Every pinned value mirrors
    // FloatRowPackingTest.kt VERBATIM.

    func testRightAndInlineEndFloatsDeriveFloatsRightUnderLtr() {
        // P10: the physical keyword and the ltr logical alias.
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Float", "RIGHT")], hasChildren: false).floatsRight)
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Float", "INLINE_END")], hasChildren: false).floatsRight)
        // Left-family floats never derive the right fact.
        XCTAssertFalse(FloatRowPacking.facts(from: [kw("Float", "LEFT")], hasChildren: false).floatsRight)
        XCTAssertFalse(FloatRowPacking.facts(from: [kw("Float", "INLINE_START")], hasChildren: false).floatsRight)
        // No Float wire at all → plain in-flow content.
        XCTAssertFalse(FloatRowPacking.facts(from: [], hasChildren: false).floatsRight)
    }

    func testRtlSwapsTheLogicalFloatMembersPerCssLogical() {
        // css-logical-1 §2.1: rtl maps inline-start→right, inline-end→left.
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Float", "INLINE_START")], hasChildren: false, rtl: true).floatsRight)
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Float", "INLINE_END")], hasChildren: false, rtl: true).floatsLeft)
        // The physical keywords are direction-independent.
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Float", "RIGHT")], hasChildren: false, rtl: true).floatsRight)
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Float", "LEFT")], hasChildren: false, rtl: true).floatsLeft)
    }

    func testChildlessRightClearsBreakRightRunsOnly() {
        // P14: BOTH clears either side; RIGHT/INLINE_END clear right (ltr).
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Clear", "BOTH")], hasChildren: false).clearBreaksRight)
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Clear", "RIGHT")], hasChildren: false).clearBreaksRight)
        XCTAssertTrue(FloatRowPacking.facts(from: [kw("Clear", "INLINE_END")], hasChildren: false).clearBreaksRight)
        // §9.5.2 same-side rule: clear:left never breaks a RIGHT run.
        XCTAssertFalse(FloatRowPacking.facts(from: [kw("Clear", "LEFT")], hasChildren: false).clearBreaksRight)
        // Content boxes and floating boxes are never break markers.
        XCTAssertFalse(FloatRowPacking.facts(from: [kw("Clear", "BOTH")], hasChildren: true).clearBreaksRight)
        XCTAssertFalse(
            FloatRowPacking.facts(from: [kw("Float", "RIGHT"), kw("Clear", "BOTH")], hasChildren: false)
                .clearBreaksRight
        )
    }

    // Right-float / right-clear fact shorthands for the segmentation pins.
    private var R: FloatRowPacking.ChildFacts {
        FloatRowPacking.ChildFacts(floatsLeft: false, clearBreaksLeft: false, floatsRight: true)
    }
    private var BRR: FloatRowPacking.ChildFacts {
        FloatRowPacking.ChildFacts(floatsLeft: false, clearBreaksLeft: false, clearBreaksRight: true)
    }

    func testRightPairSegmentsIntoOneRightRun() {
        // P11: descendant-static-position-002/004's green+grey pair.
        let segs = FloatRowPacking.segment([R, R])
        XCTAssertEqual(1, segs.count)
        XCTAssertTrue(segs[0].isRun)
        XCTAssertTrue(segs[0].rightRun)
        // No trailing clear sibling → un-strutted.
        XCTAssertFalse(segs[0].strutted)
        XCTAssertEqual([0, 1], segs[0].indices)
    }

    func testOppositeSidesNeverMergeAndLoneRightsStaySingle() {
        // A side flip splits the streak into two independent runs (P11).
        let segs = FloatRowPacking.segment([F, F, R, R])
        XCTAssertEqual(2, segs.count)
        XCTAssertTrue(segs[0].isRun); XCTAssertFalse(segs[0].rightRun)
        XCTAssertTrue(segs[1].isRun); XCTAssertTrue(segs[1].rightRun)
        // Lone right floats keep the frozen wave-5 end-alignment path.
        XCTAssertFalse(FloatRowPacking.segment([R, X, R]).contains(where: { $0.isRun }))
    }

    func testRightRunStrutsOnlyOnASameSideClearMarker() {
        // P14: a right-clear br struts the right run…
        let strutted = FloatRowPacking.segment([R, R, BRR])
        XCTAssertTrue(strutted[0].isRun); XCTAssertTrue(strutted[0].strutted)
        // …while a LEFT-only clear marker does not (§9.5.2 same-side).
        let unstrutted = FloatRowPacking.segment([R, R, BR])
        XCTAssertTrue(unstrutted[0].isRun); XCTAssertFalse(unstrutted[0].strutted)
    }

    func testLayoutEndMirrorsTheGridPairRightToLeft() {
        // P12: the 002/004 pin — first float (green) flush right at the
        // run's right edge, grey packs to its LEFT: x = [20, 0], the
        // exact grey|green order the ref paints (grey x40-59, green
        // x60-79 once the abspos static position lands at x40).
        let plan = FloatRowPacking.layoutEnd(
            widths: [20.0, 20.0],
            heights: [40.0, 40.0],
            availableWidth: .infinity,
            strutPx: 0.0
        )
        XCTAssertEqual([20.0, 0.0], plan.x)
        // Extent + height identical to the left plan (shared rows).
        XCTAssertEqual(40.0, plan.width)
        XCTAssertEqual(40.0, plan.height)
    }

    func testLayoutEndKeepsTheLeftPlansRowsAndMirrorsPerExtent() {
        // P12: five 27-wide floats over a 100px block — same 3+2 row
        // split as the left pin, each x reflected against the 81 extent.
        let plan = FloatRowPacking.layoutEnd(
            widths: [Double](repeating: 27.0, count: 5),
            heights: [Double](repeating: 19.0, count: 5),
            availableWidth: 100.0,
            strutPx: 0.0
        )
        // Mirror of [0,27,54,0,27] against width 81 minus each 27.
        XCTAssertEqual([54.0, 27.0, 0.0, 54.0, 27.0], plan.x)
        XCTAssertEqual([0.0, 0.0, 0.0, 19.0, 19.0], plan.y)
        XCTAssertEqual(81.0, plan.width)
        XCTAssertEqual(38.0, plan.height)
    }

    // ── Wave-20 fix 6 — the RTL double-mirror regression (002 live IR) ──

    /// Repo-root anchored path to the REAL wave20-final per-test IR — the
    /// same live-wire pattern ConformanceTests uses (#filePath hops).
    private static var descendant002IR: URL {
        URL(fileURLWithPath: #filePath)               // …/FloatRowPackingTests.swift
            .deletingLastPathComponent()              // StyleConverterRuntimeTests/
            .deletingLastPathComponent()              // Tests/
            .deletingLastPathComponent()              // swiftui/
            .deletingLastPathComponent()              // runtimes/
            .deletingLastPathComponent()              // repo root
            .appendingPathComponent("tools/titan/runs/wave20-final/sections/css-grid/per-test-ir/"
                + "wpt__css-grid__descendant-static-position-002.json")
    }

    func testDescendantStaticPosition002RightRunKeepsFirstFloatRightmost() throws {
        // Live-wire pin (skip-guarded like the web suites: hermetic
        // checkouts without the run directory stay green).
        let url = Self.descendant002IR
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("wave20-final run directory absent")
        }
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: url))
        // The v2 decode slot-COMPOSES the flat wire (doc.components are
        // the composed roots), so the float pair lives nested under the
        // abspos red box — walk the tree like ConformanceTests does.
        var found: [String: IRComponent] = [:]
        func walk(_ cs: [IRComponent]) {
            for c in cs {
                found[c.id] = c
                if let kids = c.children { walk(kids) }
            }
        }
        walk(doc.components)
        // The abspos red box's two float children (green first, gray
        // second — source order).
        let green = try XCTUnwrap(found["descendant-static-position-002__0__0__0__0-031"])
        let gray = try XCTUnwrap(found["descendant-static-position-002__0__0__0__1-032"])
        // P10 facts off the REAL wire: both are Float:RIGHT (physical —
        // the grid root's Direction:RTL does not relabel a physical side).
        let facts = [green, gray].map {
            FloatRowPacking.facts(from: $0.properties, hasChildren: false)
        }
        XCTAssertTrue(facts.allSatisfy { $0.floatsRight && !$0.floatsLeft })
        // One right run over the pair — the FloatBlockLayout route.
        let segs = FloatRowPacking.segment(facts)
        XCTAssertEqual(1, segs.count)
        XCTAssertTrue(segs[0].isRun)
        XCTAssertTrue(segs[0].rightRun)
        // §9.5.1 rule 1 mirrored: green (FIRST right float) claims the
        // RIGHTMOST slot — run-relative x [20, 0] over the 40px extent.
        let plan = FloatRowPacking.layoutEnd(
            widths: [20.0, 20.0], heights: [40.0, 40.0],
            availableWidth: .infinity, strutPx: 0.0)
        XCTAssertEqual([20.0, 0.0], plan.x)
        // fix 6 — the ambient-RTL counter-mirror: the container inherits
        // Direction:RTL (the grid root sets \.layoutDirection), so
        // SwiftUI will flip every place() x by x' = W − x − w. placedX
        // pre-applies the involution so the ENGINE's flip restores the
        // physical origin — green stays at 20 (rightmost), gray at 0.
        // With the abspos box's static position at page x40, that is the
        // ref's gray x40-59 / green x60-79 band, mirrored right.
        for (x, w) in [(20.0, 20.0), (0.0, 20.0)] {
            if #available(iOS 16.0, macOS 13.0, *) {
                let pre = FloatBlockLayout.placedX(
                    intendedX: CGFloat(x), memberWidth: CGFloat(w),
                    boundsWidth: 40, rtl: true)
                // The engine's own mirror (W − x − w) lands on the
                // intended physical origin — double flip neutralized.
                XCTAssertEqual(CGFloat(x), 40 - pre - CGFloat(w), accuracy: 1e-9)
                // And LTR passes through verbatim (regression guard for
                // every existing left-run placement).
                XCTAssertEqual(CGFloat(x), FloatBlockLayout.placedX(
                    intendedX: CGFloat(x), memberWidth: CGFloat(w),
                    boundsWidth: 40, rtl: false))
            }
        }
    }
}
