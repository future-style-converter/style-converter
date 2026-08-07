import XCTest
@testable import StyleConverterRuntime

// Wave 33 (lane C, C2) — XCTest pins for the declared-`display:inline-block`
// atom predicate (InlineBlockAtom.spec) and for the §9.4.2 row it feeds
// through the wave-20 packer. Every assertion here mirrors Compose's
// InlineBlockAtomTest VERBATIM so the two natives cannot drift.
//
// Measured defect (see InlineBlockAtom's header): declared inline-block
// siblings that belong on one line box were block-STACKED by both natives'
// block child loop. On device the gate admits exactly ONE corpus site —
// filter-effects/backdrop-filter-boundary — and CSS2/abspos/
// static-inside-inline-block stays UNFIXED because its three boxes are
// document ROOTS, stacked by the harness, not by this loop.
final class InlineBlockAtomTests: XCTestCase {

    // MARK: - B1, the admission ticket

    func testB1ADeclaredInlineBlockWithADefiniteSizeIsAnAtom() throws {
        let spec = try XCTUnwrap(InlineBlockAtom.spec(
            properties: box("INLINE_BLOCK", 100, 100), hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        XCTAssertEqual(spec.fixedWpx, 100)
        XCTAssertEqual(spec.fixedHpx, 100)
    }

    func testB1BlockInlineFlexGridAndTableDisplaysAreNotAtoms() throws {
        for kw in ["BLOCK", "INLINE", "FLEX", "GRID", "TABLE", "LIST_ITEM", "NONE"] {
            XCTAssertNil(InlineBlockAtom.spec(
                properties: try box(kw, 100, 100), hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false), kw)
        }
    }

    func testB1AnUndeclaredDisplayIsTheWave20WidgetLaneNotThisOne() throws {
        // A bare <input> is inline-block by UA default; UAWidgetIntrinsics
        // owns its geometry, and racing that table with a wire size the
        // component does not carry is exactly what B1 prevents.
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try props(len("Width", 100), len("Height", 100)),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
    }

    // MARK: - B2, definite border box

    func testB2AnAutoOrPercentageOrMissingAxisRefuses() throws {
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try props(kw("Display", "INLINE_BLOCK")),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false), "no size")
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try props(kw("Display", "INLINE_BLOCK"),
                                  #"{"type":"Width","data":{"type":"percentage","value":50}}"#,
                                  len("Height", 100)),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false), "percent width")
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try props(kw("Display", "INLINE_BLOCK"), len("Width", 100),
                                  kw("Height", "auto")),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false), "auto height")
    }

    func testB2TheLogicalInlineSizeAndBlockSizeSpellingsResolveToo() throws {
        let spec = try XCTUnwrap(InlineBlockAtom.spec(
            properties: try props(kw("Display", "INLINE_BLOCK"),
                                  len("InlineSize", 60), len("BlockSize", 30)),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        XCTAssertEqual(spec.fixedWpx, 60)
        XCTAssertEqual(spec.fixedHpx, 30)
    }

    // MARK: - B3/B4, flow membership

    func testB3AnAbsolutelyPositionedOrFixedBoxIsNotOnTheLine() throws {
        for p in ["ABSOLUTE", "FIXED"] {
            XCTAssertNil(InlineBlockAtom.spec(
                properties: try box("INLINE_BLOCK", 100, 100, extra: kw("Position", p)),
                hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false), p)
        }
    }

    func testB3RelativeAndStickyStayInFlowAndRemainAtoms() throws {
        for p in ["RELATIVE", "STICKY", "STATIC"] {
            XCTAssertNotNil(InlineBlockAtom.spec(
                properties: try box("INLINE_BLOCK", 100, 100, extra: kw("Position", p)),
                hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false), p)
        }
    }

    func testB4AFloatBelongsToTheWave19Packer() throws {
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try box("INLINE_BLOCK", 100, 100, extra: kw("Float", "LEFT")),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        // An explicit `float: none` is the initial value — still an atom.
        XCTAssertNotNil(InlineBlockAtom.spec(
            properties: try box("INLINE_BLOCK", 100, 100, extra: kw("Float", "NONE")),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
    }

    // MARK: - B5, bands must be zero

    func testB5ANonZeroMarginPaddingOrBorderRefuses() throws {
        for t in ["MarginLeft", "PaddingTop", "BorderRightWidth", "MarginInlineStart"] {
            XCTAssertNil(InlineBlockAtom.spec(
                properties: try box("INLINE_BLOCK", 100, 100, extra: len(t, 4)),
                hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false), t)
        }
    }

    func testB5ExplicitZeroBandsAreFineTheExtractedCorpusShape() throws {
        // backdrop-filter-clip-rect-2's boxes carry an explicit `padding: 0`
        // from the UA reset the extractor bakes in.
        XCTAssertNotNil(InlineBlockAtom.spec(
            properties: try props(kw("Display", "INLINE_BLOCK"), len("Width", 100),
                                  len("Height", 100), len("PaddingTop", 0),
                                  len("PaddingLeft", 0), len("MarginTop", 0)),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
    }

    // MARK: - B6, the baseline

    func testB6OwnTextOrRunsMoveTheBaselineSoTheBoxRefuses() throws {
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try box("INLINE_BLOCK", 100, 100), hasOwnText: true, hasOwnRuns: false, containerDeclaresLineHeight: false))
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try box("INLINE_BLOCK", 100, 100), hasOwnText: false, hasOwnRuns: true, containerDeclaresLineHeight: false))
    }

    func testB6AChildlessOrAbsposChildBoxBaselinesAtItsBottomEdge() throws {
        // §10.8.1: no in-flow line boxes ⇒ baseline == bottom margin edge ⇒
        // descent 0, which is what leaves the ref's 4px strut descent
        // visible below a row of tall inline-blocks.
        let spec = try XCTUnwrap(InlineBlockAtom.spec(
            properties: box("INLINE_BLOCK", 100, 100), hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        XCTAssertEqual(spec.descentPx, 0)
    }

    // MARK: - B7, the container's line box

    func testB7AContainerThatDeclaresItsOwnLineHeightRefuses() throws {
        // MEASURED on device: absolute-tables-013/-014/-015 put two 100x50
        // spans in a `line-height: 0` <td>, where the browser's line boxes
        // are 50 tall. Feeding them the harness's 16/4 strut moved the
        // second span 4px down and cost 0.0115/0.0163/0.0165 SSIM against
        // the frozen wave32-final Android baseline; refusing restores all
        // three byte-for-byte.
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try box("INLINE_BLOCK", 100, 50),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: true))
    }

    // MARK: - The two corpus geometries

    func testThreeSameSizeBoxesPackOnOneLineInsideTheComposedWidth() throws {
        // The static-inside-inline-block geometry: three 100×100
        // inline-blocks, one collapsed space between each, in the 358px
        // composed content width. This pins the PACKER's answer; that test
        // itself is root-level and never reaches this lane (see the file
        // header's measured-outcome section).
        let spec = try XCTUnwrap(InlineBlockAtom.spec(
            properties: box("INLINE_BLOCK", 100, 100), hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        let plan = InlineAtomFlow.layout(
            widths: Array(repeating: spec.fixedWpx!, count: 3),
            heights: Array(repeating: spec.fixedHpx!, count: 3),
            descents: Array(repeating: spec.descentPx, count: 3),
            marginStarts: Array(repeating: 0, count: 3),
            marginEnds: Array(repeating: 0, count: 3),
            availableWidth: 358,
            gapPx: UAWidgetIntrinsics.atomGapPx,
            strutAscentPx: UAWidgetIntrinsics.strutAscentPx,
            strutDescentPx: UAWidgetIntrinsics.strutDescentPx
        )
        // One row: 3×100 + 2×4.5 = 309 ≤ 358.
        XCTAssertEqual(plan.x, [0, 104.5, 209])
        XCTAssertEqual(plan.y, [0, 0, 0])
        XCTAssertEqual(plan.width, 309)
        // Row height = ascent 100 (the boxes) + descent 4 (the §10.8.1
        // strut) — the browser's classic below-inline-block gap.
        XCTAssertEqual(plan.height, 104)
    }

    func testTheAbsoluteTables013PairWrapsInsideIts100pxCell() throws {
        // Two 100×50 spans in a 100px-wide <td>: 100 + 4.5 + 100 > 100, so
        // the second wraps — the same visual stacking the frozen baseline
        // already had, one strut descent lower per line box.
        let spec = try XCTUnwrap(InlineBlockAtom.spec(
            properties: box("INLINE_BLOCK", 100, 50), hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        let plan = InlineAtomFlow.layout(
            widths: Array(repeating: spec.fixedWpx!, count: 2),
            heights: Array(repeating: spec.fixedHpx!, count: 2),
            descents: Array(repeating: spec.descentPx, count: 2),
            marginStarts: Array(repeating: 0, count: 2),
            marginEnds: Array(repeating: 0, count: 2),
            availableWidth: 100,
            gapPx: UAWidgetIntrinsics.atomGapPx,
            strutAscentPx: UAWidgetIntrinsics.strutAscentPx,
            strutDescentPx: UAWidgetIntrinsics.strutDescentPx
        )
        XCTAssertEqual(plan.x, [0, 0])
        // Line box 1 = ascent 50 + strut descent 4 ⇒ row 2 top at 54.
        XCTAssertEqual(plan.y, [0, 54])
        XCTAssertEqual(plan.height, 108)
    }

    // MARK: - Helpers

    /// One `{"type":…,"data":"<KEYWORD>"}` wire entry.
    private func kw(_ type: String, _ value: String) -> String {
        #"{"type":"\#(type)","data":"\#(value)"}"#
    }

    /// One `{"type":…,"data":{"type":"length","px":N}}` wire entry.
    private func len(_ type: String, _ px: Double) -> String {
        #"{"type":"\#(type)","data":{"type":"length","px":\#(px)}}"#
    }

    /// A declared-display box with an exact px width/height, plus optional
    /// extra declarations.
    private func box(_ display: String, _ w: Double, _ h: Double,
                     extra: String? = nil) throws -> [IRProperty] {
        var entries = [kw("Display", display), len("Width", w), len("Height", h)]
        if let extra { entries.append(extra) }
        return try props(entries)
    }

    /// Build a property list by decoding a real IRComponent, so every pin
    /// runs against the SAME decode path the renderer uses (no hand-built
    /// IRValue trees that could drift from the wire).
    private func props(_ entries: String...) throws -> [IRProperty] { try props(entries) }

    private func props(_ entries: [String]) throws -> [IRProperty] {
        let json = #"{"id":"c","name":"c","properties":[\#(entries.joined(separator: ","))]}"#
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
    }
}
