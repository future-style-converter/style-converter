//
//  VerticalMulticolBakeGateTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 52 (lane L3 · failure-ink, fix F1 — the fourth site): the vertical
//  multicol plan builder's post-load BAKE GATE
//  (ColumnsApplier.verticalFragmentPlan, VerticalMulticolPlan.swift) reads
//  the shared BakedLayoutSignature predicate — Width ∧ Height ∧ BoxSizing ∧
//  (PaddingTop ∨ BorderTopStyle) — like its Compose twin
//  ChildSpec.bakedPhysicalSize, instead of the wave-47 "Width ∧ Height"
//  heuristic that an AUTHORED `width`+`height` rule also satisfied.
//
//  The geometry is the real carrier's: anchor-position-multicol-017's
//  container (wave51-fix per-test IR `…-017__1-743`: vertical-rl,
//  column-count 5, column-gap 0, a baked 100×100 content box) and its sole
//  flow child `anchor-position-multicol-017__1__0-744`, whose 30 longhands
//  are copied VERBATIM below. Census (results/wave52-failure-ink/census.mjs,
//  f1swiftUsed — the call site's own gates: used writing mode, flow-sibling
//  count, every non-spanner child): that child is the ONLY old-guarded child
//  in the 1435-doc corpus, and the new predicate still guards it — zero
//  corpus movement; the authored rows below are the behaviour change.
//
//  Mutations EXECUTED against these pins (PLAN §0; each restored, sha-checked;
//  log: results/wave52-failure-ink/_swift-hunk1-mutations.log):
//   - H1, gate reverted to the wave-47 heuristic (the HEAD bytes of
//     VerticalMulticolPlan.swift): both authored rows FAIL (nil plan); the
//     -017 row, VerticalFragmentGeometryTests and Wave12BoxSizingBasisTests
//     stay green (both rules read their payloads as baked);
//   - H2, gate disabled (`if false`): the verbatim -017 row FAILS (a
//     5-fragment plan), and so do VerticalFragmentGeometryTests'
//     testVerticalPlanDeclinesBakedLayout and Wave12BoxSizingBasisTests'
//     testInheritedVerticalModeRendersUnfragmented.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class VerticalMulticolBakeGateTests: XCTestCase {

    /// Wire-shaped property decode (the VerticalFragmentGeometryTests helper).
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// The -017 container's columns: `column-count: 5` (gap 0 is passed
    /// separately as `gapPx`, as the renderer's multicolFillGap does).
    private func fiveColumns() -> ColumnsConfig {
        var cols = ColumnsConfig()
        cols.count = 5
        return cols
    }

    /// The plan builder over the -017 container geometry: content box
    /// 100×100 (W = 100 block extent, U = 100 inline extent), gap 0, rl.
    private func plan017(_ child: [IRProperty]) -> ColumnsApplier.FragmentPlan? {
        ColumnsApplier.verticalFragmentPlan(
            columns: fiveColumns(), siblingCount: 1,
            contentWidthPx: 100, contentHeightPx: 100, gapPx: 0,
            childProperties: child, ctx: SpacingContext(), blockRtl: true)
    }

    /// VERBATIM wave51-fix IR of `anchor-position-multicol-017__1__0-744`.
    private func verbatim017Child() throws -> [IRProperty] {
        try props(#"""
             [{"type":"Position","data":"RELATIVE"},
              {"type":"Top","data":{"px":0}},
              {"type":"Left","data":{"px":0}},
              {"type":"Width","data":{"type":"length","px":450}},
              {"type":"Height","data":{"type":"length","px":20}},
              {"type":"BoxSizing","data":"CONTENT_BOX"},
              {"type":"MarginTop","data":{"px":0}},
              {"type":"MarginRight","data":{"px":0}},
              {"type":"MarginBottom","data":{"px":0}},
              {"type":"MarginLeft","data":{"px":0}},
              {"type":"PaddingTop","data":{"px":0}},
              {"type":"PaddingRight","data":{"px":0}},
              {"type":"PaddingBottom","data":{"px":0}},
              {"type":"PaddingLeft","data":{"px":0}},
              {"type":"BorderTopWidth","data":{"px":0}},
              {"type":"BorderRightWidth","data":{"px":0}},
              {"type":"BorderBottomWidth","data":{"px":0}},
              {"type":"BorderLeftWidth","data":{"px":0}},
              {"type":"BorderTopStyle","data":"NONE"},
              {"type":"BorderRightStyle","data":"NONE"},
              {"type":"BorderBottomStyle","data":"NONE"},
              {"type":"BorderLeftStyle","data":"NONE"},
              {"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
              {"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
              {"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
              {"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
              {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},
              {"type":"Display","data":"BLOCK"},
              {"type":"OverflowX","data":"VISIBLE"},
              {"type":"OverflowY","data":"VISIBLE"}]
            """#)
    }

    /// The baked carrier keeps the frozen path: its wire already holds the
    /// browser's fragmented layout (ios P 0.9511 / android P 0.9823 today).
    func testVerbatim017SoleChildKeepsTheFrozenPath() throws {
        let child = try verbatim017Child()
        // Precondition: the payload IS the extractor's signature.
        XCTAssertTrue(BakedLayoutSignature.bakedPhysicalBox(child),
            "the verbatim -017 child carries the post-load signature")
        XCTAssertNil(plan017(child),
            "baked used layout: fragmenting again would double-apply")
    }

    /// The SAME geometry with only the author's `width:450px; height:20px`
    /// (no BoxSizing, no band) is not baked: the plan builds, C = the
    /// physical width 450 (the block extent under vertical-rl), F = 5
    /// bands of W = 100 walking leftward (tx = W − C + i·W, the V-table).
    func testAuthoredWidthHeightChildEngagesThePlan() throws {
        let authored = try props(#"""
            [{"type":"Width","data":{"type":"length","px":450}},
             {"type":"Height","data":{"type":"length","px":20}}]
            """#)
        let plan = try XCTUnwrap(plan017(authored),
            "authored Width + Height is not the bake signature")
        XCTAssertTrue(plan.vertical, "the plan must mark itself vertical")
        XCTAssertEqual(plan.fragments.map { $0.translate.width },
                       [-350, -250, -150, -50, 50],
                       "C = 450 sliced into five W = 100 bands, rl")
        XCTAssertEqual(plan.columnWidthPx, 100, "slot width = W")
        XCTAssertEqual(plan.columnBlockSizePx, 20, accuracy: 0.001,
                       "slot height = colH = 100 / 5")
    }

    /// An author's `box-sizing` alone does not re-trip the gate: the
    /// decoration-band conjunct (PaddingTop ∨ BorderTopStyle) separates it
    /// from the extractor, which always writes the band.
    func testAuthoredBoxSizingWithoutBandStillEngages() throws {
        let authored = try props(#"""
            [{"type":"Width","data":{"type":"length","px":450}},
             {"type":"Height","data":{"type":"length","px":20}},
             {"type":"BoxSizing","data":"BORDER_BOX"}]
            """#)
        XCTAssertFalse(BakedLayoutSignature.bakedPhysicalBox(authored),
            "BoxSizing without a band is an author rule")
        let plan = try XCTUnwrap(plan017(authored),
            "BoxSizing alone keeps the child authored: the plan builds")
        XCTAssertEqual(plan.fragments.count, 5,
            "F = ceil(450 / 100) = 5 ≤ N = 5")
    }
}
