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

    // MARK: - B5, margins zero, bands box-sized

    func testB5ANonZeroMarginRefusesOnAnySpelling() throws {
        for t in ["MarginLeft", "MarginTop", "MarginInlineStart", "MarginBlockEnd"] {
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

    func testB5Wave33AllZeroBandSitesAreByteIdenticalUnderH2() throws {
        // The wave-33 gate forced every band to zero. Both wave-34 branches
        // ADD 0 on such a box, so every site wave 33 admitted keeps its
        // exact AtomSpec — which is what makes backdrop-filter-boundary and
        // appearance-auto-non-html-namespace-001 unmoved.
        for bs in [nil, "CONTENT_BOX", "BORDER_BOX"] as [String?] {
            var entries = [kw("Display", "INLINE_BLOCK"), len("Width", 160), len("Height", 90),
                           len("PaddingLeft", 0), len("BorderTopWidth", 0)]
            if let bs { entries.append(kw("BoxSizing", bs)) }
            let spec = try XCTUnwrap(InlineBlockAtom.spec(
                properties: props(entries), hasOwnText: false, hasOwnRuns: false,
                containerDeclaresLineHeight: false), bs ?? "unset")
            XCTAssertEqual(spec.fixedWpx, 160)
            XCTAssertEqual(spec.fixedHpx, 90)
        }
    }

    // MARK: - H2 (wave 34), the box-sizing-aware band read

    func testH2BorderBoxKeepsTheWirePxAsTheOuterBorderBox() throws {
        // backdrop-filter-clip-rect-2's exact shape: `box-sizing:
        // border-box; width/height: 100px; border: 10px`. css-sizing-3 §3
        // puts the bands INSIDE the declared size, so the atom is 100×100
        // and the three boxes pack 100 + 4.5 + 100 + 4.5 + 100 = 309.
        let spec = try XCTUnwrap(InlineBlockAtom.spec(
            properties: props(kw("Display", "INLINE_BLOCK"), len("Width", 100),
                              len("Height", 100), kw("BoxSizing", "BORDER_BOX"),
                              len("BorderTopWidth", 10), len("BorderRightWidth", 10),
                              len("BorderBottomWidth", 10), len("BorderLeftWidth", 10)),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        XCTAssertEqual(spec.fixedWpx, 100)
        XCTAssertEqual(spec.fixedHpx, 100)
    }

    func testH2BorderBoxToleratesABandShapeThisRuleCannotRead() throws {
        // Under border-box the outer box is the declared px WHATEVER the
        // band is, so a % padding does not need to be resolvable.
        XCTAssertNotNil(InlineBlockAtom.spec(
            properties: try props(kw("Display", "INLINE_BLOCK"), len("Width", 100),
                                  len("Height", 100), kw("BoxSizing", "BORDER_BOX"),
                                  #"{"type":"PaddingLeft","data":{"type":"percentage","value":25}}"#),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
    }

    func testH2ContentBoxAddsThePaddingAndBorderBands() throws {
        // css-sizing-3 §3: the declared size is the CONTENT box, so the
        // atom's outer border box is 100 + 4 + 6 wide and 100 + 2 + 8 tall.
        let spec = try XCTUnwrap(InlineBlockAtom.spec(
            properties: props(kw("Display", "INLINE_BLOCK"), len("Width", 100),
                              len("Height", 100), kw("BoxSizing", "CONTENT_BOX"),
                              len("PaddingLeft", 4), len("PaddingTop", 2),
                              len("BorderRightWidth", 6), kw("BorderRightStyle", "SOLID"),
                              len("BorderBottomWidth", 8), kw("BorderBottomStyle", "SOLID")),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        XCTAssertEqual(spec.fixedWpx, 110)
        XCTAssertEqual(spec.fixedHpx, 110)
    }

    func testH2AnUnsetBoxSizingIsContentBoxUnderTheWPTGate() throws {
        // This predicate is composed-WPT-only (P19), where
        // SizeApplier.effectiveBoxSizing(nil, true) == .contentBox.
        let spec = try XCTUnwrap(InlineBlockAtom.spec(
            properties: box("INLINE_BLOCK", 100, 50, extra: len("PaddingRight", 7)),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false))
        XCTAssertEqual(spec.fixedWpx, 107)
        XCTAssertEqual(spec.fixedHpx, 50)
    }

    func testH2ANoneOrHiddenBorderSideBandsAtZero() throws {
        // CSS 2.1 §8.5.3 — used width 0, matching BorderSideConfig.hasBorder
        // on both natives. clip-rect-2's `border-style: none` box is this.
        for style in ["NONE", "HIDDEN"] {
            let spec = try XCTUnwrap(InlineBlockAtom.spec(
                properties: props(kw("Display", "INLINE_BLOCK"), len("Width", 100),
                                  len("Height", 100), len("BorderLeftWidth", 10),
                                  kw("BorderLeftStyle", style)),
                hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false), style)
            XCTAssertEqual(spec.fixedWpx, 100, style)
        }
    }

    func testH2ContentBoxRefusesTheThreeUnknowableBandShapes() throws {
        // 1. a non-exact band value (the % basis is a measure question).
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try props(kw("Display", "INLINE_BLOCK"), len("Width", 100),
                                  len("Height", 100),
                                  #"{"type":"PaddingLeft","data":{"type":"percentage","value":25}}"#),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false),
            "percent padding")
        // 2. a non-zero LOGICAL band (physical mapping is writing-mode aware).
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try box("INLINE_BLOCK", 100, 100, extra: len("PaddingInlineStart", 5)),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false),
            "logical padding")
        // 3. a visible border-style with NO width — the `medium` initial the
        //    two natives resolve differently (Compose 0 / iOS 3).
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try box("INLINE_BLOCK", 100, 100, extra: kw("BorderTopStyle", "SOLID")),
            hasOwnText: false, hasOwnRuns: false, containerDeclaresLineHeight: false),
            "styled width-less border")
    }

    func testH2AnUnreadableBoxSizingKeywordRefusesRatherThanGuesses() throws {
        XCTAssertNil(InlineBlockAtom.spec(
            properties: try box("INLINE_BLOCK", 100, 100, extra: kw("BoxSizing", "WHAT_BOX")),
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

    // MARK: - H1 (wave 34), the composed-canvas ROOT flow facade

    func testH1RootBoxIsSpecProjectedToScalars() throws {
        let b = InlineBlockAtom.rootBox(properties: try box("INLINE_BLOCK", 100, 100),
                                        hasOwnText: false, hasOwnRuns: false,
                                        containerDeclaresLineHeight: false)
        XCTAssertEqual(b, InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100))
        // A non-atom root keeps the harness's frozen block stack.
        XCTAssertNil(InlineBlockAtom.rootBox(properties: try box("BLOCK", 100, 100),
                                             hasOwnText: false, hasOwnRuns: false,
                                             containerDeclaresLineHeight: false))
    }

    func testH1TheTargetDocumentSegmentsIntoPThenA3BoxRun() throws {
        // CSS2/abspos/static-inside-inline-block: root 0 is the prose <p>
        // (no Display, so not an atom), roots 1–3 the inline-blocks.
        let boxes: [InlineBlockAtom.RootBox?] = [
            nil,
            InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100),
            InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100),
            InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100),
        ]
        // The harness's leading gaps are [16 above <p>, 16 above root 1, 0, 0].
        let segs = try XCTUnwrap(InlineBlockAtom.rootSegments(
            boxes: boxes, blockGapsAbovePx: [16, 16, 0, 0]))
        XCTAssertEqual(segs, [
            InlineBlockAtom.RootSegment(indices: [0], isRun: false),
            InlineBlockAtom.RootSegment(indices: [1, 2, 3], isRun: true),
        ])
    }

    func testH1ARunFreeDocumentGetsNoPlanSoTheHarnessStaysFrozen() {
        // Every composed section but two is this case — the nil return is
        // what keeps their captures byte-identical.
        XCTAssertNil(InlineBlockAtom.rootSegments(boxes: [nil, nil, nil],
                                                  blockGapsAbovePx: [0, 0, 0]))
        // A LONE atom is not a run either (the ≥2 rule the segmenter pins).
        XCTAssertNil(InlineBlockAtom.rootSegments(
            boxes: [nil, InlineBlockAtom.RootBox(widthPx: 10, heightPx: 10), nil],
            blockGapsAbovePx: [0, 0, 0]))
    }

    func testH3ARunWithARealBlockGapInsideItRefusesAndStacks() {
        // Dropping that gap would silently lose layout, so the whole run
        // degrades to singles and the harness stacks it exactly as before.
        let boxes: [InlineBlockAtom.RootBox?] = [
            InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100),
            InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100),
        ]
        XCTAssertNil(InlineBlockAtom.rootSegments(boxes: boxes, blockGapsAbovePx: [0, 16]))
        // The gap ABOVE the first member is the caller's and never gates.
        XCTAssertNotNil(InlineBlockAtom.rootSegments(boxes: boxes, blockGapsAbovePx: [16, 0]))
    }

    func testH1TheTargetRunPacksToTheRefs121x88GreenBox() {
        // Three 100×100 roots in the composed canvas's 358px content width.
        let plan = InlineBlockAtom.rootRowPlan(
            widthsPx: Array(repeating: 100, count: 3),
            heightsPx: Array(repeating: 100, count: 3),
            availableWidthPx: 358,
            gapPx: InlineBlockAtom.rootAtomGapPx)
        // One row: 3×100 + 2×4.5 = 309 ≤ 358. The MIDDLE box (the red one
        // holding the green abspos child) starts at run-relative 104.5, so
        // the harness's 16px frame puts it at image x = 120.5 → the 121
        // column the browser-ref rasterises.
        XCTAssertEqual(plan.xPx, [0, 104.5, 209])
        XCTAssertEqual(plan.yPx, [0, 0, 0])
        XCTAssertEqual(plan.widthPx, 309)
        // Row height = ascent 100 (the boxes) + descent 4 (the §10.8.1
        // strut). 16 pad + 16 gap + a 40px two-line <p> + 16 gap = y 88.
        XCTAssertEqual(plan.heightPx, 104)
    }

    func testH1AnchorCenterOverflow005sFourRootsWrap3Plus1() {
        // 4×100 + 3×4.5 = 413.5 > 358, so the 4th box starts row 2 at the
        // first row's full height (100 + the 4px strut descent).
        let plan = InlineBlockAtom.rootRowPlan(
            widthsPx: Array(repeating: 100, count: 4),
            heightsPx: Array(repeating: 100, count: 4),
            availableWidthPx: 358,
            gapPx: InlineBlockAtom.rootAtomGapPx)
        XCTAssertEqual(plan.xPx, [0, 104.5, 209, 0])
        XCTAssertEqual(plan.yPx, [0, 0, 0, 104])
        XCTAssertEqual(plan.heightPx, 208)
    }

    func testH1TheRootRowPlanIsTheNestedRowPlan() {
        // The facade must be a pure re-expression of InlineAtomFlow.layout
        // with the inline-block family's fixed answers, never a second
        // packer — this pins the two against each other.
        let direct = InlineAtomFlow.layout(
            widths: Array(repeating: 100, count: 3),
            heights: Array(repeating: 100, count: 3),
            descents: Array(repeating: 0, count: 3),
            marginStarts: Array(repeating: 0, count: 3),
            marginEnds: Array(repeating: 0, count: 3),
            availableWidth: 358,
            gapPx: UAWidgetIntrinsics.atomGapPx,
            strutAscentPx: UAWidgetIntrinsics.strutAscentPx,
            strutDescentPx: UAWidgetIntrinsics.strutDescentPx)
        let viaFacade = InlineBlockAtom.rootRowPlan(
            widthsPx: Array(repeating: 100, count: 3),
            heightsPx: Array(repeating: 100, count: 3),
            availableWidthPx: 358,
            gapPx: InlineBlockAtom.rootAtomGapPx)
        XCTAssertEqual(direct.x, viaFacade.xPx)
        XCTAssertEqual(direct.y, viaFacade.yPx)
        XCTAssertEqual(direct.width, viaFacade.widthPx)
        XCTAssertEqual(direct.height, viaFacade.heightPx)
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
