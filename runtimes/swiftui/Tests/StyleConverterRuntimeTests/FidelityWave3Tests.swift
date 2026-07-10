//
//  FidelityWave3Tests.swift
//  StyleConverterRuntimeTests
//
//  Pinning tests for the wave-3 iOS engine fixes:
//    • Containing-block width channel (css-sizing-3 §5.1): percent
//      widths resolve against the parent's CONTENT box, not the canvas
//      (trees/block-flow B_Stack — 100%/75%/50% of a 280px/10px-padding
//      parent must be 260/195/130, not 358/268.5/179).
//    • Out-of-flow classification (css-position-3 §2.1) driving the
//      absolute-overlay split: absolute/fixed children leave normal
//      flow and paint ABOVE in-flow siblings (B_RelativeAnchor).
//    • Perspective identity on flat content (css-transforms-2 §13.1):
//      the z = 0 plane projects to itself for every finite distance —
//      the old max(0.9, d/1000) shrink was fabricated distortion
//      (Transforms_TextBlock / Transforms_Decorated).
//    • Line-box leading split (CSS 2.1 §10.8.1): advance = line-height
//      exactly and half-leading above/below the run (IH_LineHeight /
//      Images_TextBlock / Columns_TextBlock).
//    • Typed column-count + the multicol full-width default
//      (Columns_Decorated).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FidelityWave3Tests: XCTestCase {

    // MARK: - Helpers

    /// Decode an IRComponent from inline JSON (IRComponent has no
    /// memberwise init — the decode path IS the production path).
    private func component(_ json: String) -> IRComponent {
        try! JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    // MARK: - Containing-block width channel (B_Stack)

    func testPercentWidthResolvesAgainstThreadedContainingBlock() {
        // B_Stack: parent width 280, padding 10 per side → content 260.
        // The parent publishes 260 through the channel; each child's
        // SpacingContext carries it and percent widths resolve there.
        var ctx = SpacingContext()
        ctx.containingBlockWidthPx = 260
        let pct: (Double) -> LengthValue = {
            .relative(value: $0, unit: .percent, pxFallback: nil)
        }
        // width: 100% / 75% / 50% → 260 / 195 / 130 (browser numbers).
        XCTAssertEqual(SizeApplierResolve.exact(pct(100), ctx: ctx,
                                                parent: ctx.containingBlockWidth), 260)
        XCTAssertEqual(SizeApplierResolve.exact(pct(75), ctx: ctx,
                                                parent: ctx.containingBlockWidth), 195)
        XCTAssertEqual(SizeApplierResolve.exact(pct(50), ctx: ctx,
                                                parent: ctx.containingBlockWidth), 130)
    }

    func testContainingBlockDefaultsToCanvasContentWidth() {
        // Root components (nil channel) keep the wave-2 canvas basis:
        // 390 − 2×16 CaptureCanvas padding = 358. Regression guard for
        // the committed Sizing_PercentWidth baseline (50% × 358 = 179).
        let ctx = SpacingContext()
        XCTAssertNil(ctx.containingBlockWidthPx)
        XCTAssertEqual(ctx.containingBlockWidth, 358)
        let half = LengthValue.relative(value: 50, unit: .percent, pxFallback: nil)
        XCTAssertEqual(SizeApplierResolve.exact(half, ctx: ctx,
                                                parent: ctx.containingBlockWidth), 179)
    }

    // MARK: - Out-of-flow classification (B_RelativeAnchor)

    func testAbsoluteAndFixedChildrenAreOutOfFlow() {
        // css-position-3 §2.1: absolute/fixed leave the flow; static/
        // relative/sticky children stay in it. This classification
        // drives the overlay split — the absolute child must NOT render
        // inside the flow VStack (where the in-flow sibling would paint
        // over it) and MUST render in the later ZStack layer.
        let abs = component(#"{"name":"floating","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":10}},{"type":"Left","data":{"px":20}}]}"#)
        let fix = component(#"{"name":"pinned","properties":[{"type":"Position","data":"FIXED"}]}"#)
        let rel = component(#"{"name":"nudged","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Top","data":{"px":5}}]}"#)
        let sta = component(#"{"name":"inflow","properties":[{"type":"Width","data":{"px":100}}]}"#)
        let sticky = component(#"{"name":"sticky","properties":[{"type":"Position","data":"STICKY"}]}"#)
        XCTAssertTrue(ComponentRenderer.isOutOfFlow(abs))
        XCTAssertTrue(ComponentRenderer.isOutOfFlow(fix))
        XCTAssertFalse(ComponentRenderer.isOutOfFlow(rel))
        XCTAssertFalse(ComponentRenderer.isOutOfFlow(sta))
        XCTAssertFalse(ComponentRenderer.isOutOfFlow(sticky))
    }

    // MARK: - Perspective identity on flat content

    func testPerspectiveIsIdentityForFlatContent() {
        // css-transforms-2 §13.1: the perspective matrix maps
        // (x, y, 0, 1) → (x, y, 0, 1 − 0/d) — identity at z = 0 for
        // EVERY finite distance. The wave-3 measurement caught the old
        // max(0.9, d/1000) shrink: ~10% at 500px, invisible at 1000px+
        // (C01 passed at 0.9908 precisely because k hit 1.0 there).
        XCTAssertEqual(TransformsMath.perspectiveScale(distancePx: 500,
                                                       has3DRotation: false), 1.0)
        XCTAssertEqual(TransformsMath.perspectiveScale(distancePx: 1000,
                                                       has3DRotation: false), 1.0)
        XCTAssertEqual(TransformsMath.perspectiveScale(distancePx: 1,
                                                       has3DRotation: false), 1.0)
        // 3D-rotated regime keeps the legacy baseline-pinned
        // foreshortening (046_Perspective_Rotate: 500px → 0.9).
        XCTAssertEqual(TransformsMath.perspectiveScale(distancePx: 500,
                                                       has3DRotation: true), 0.9)
        XCTAssertEqual(TransformsMath.perspectiveScale(distancePx: 1000,
                                                       has3DRotation: true), 1.0)
    }

    func testHas3DRotationClassification() {
        // rotateY(30deg) — out-of-plane → 3D (the 046 baseline case).
        var rotY = TransformsAggregate()
        rotY.functions = [.rotate(x: 0, y: 1, z: 0, deg: 30)]
        XCTAssertTrue(TransformsMath.has3DRotation(rotY))
        // Bare 2D rotate(15deg) = axis (0,0,1) → flat.
        var rotZ = TransformsAggregate()
        rotZ.functions = [.rotate(x: 0, y: 0, z: 1, deg: 15)]
        XCTAssertFalse(TransformsMath.has3DRotation(rotZ))
        // No rotation at all (the wave-3 flat fixtures) → flat.
        XCTAssertFalse(TransformsMath.has3DRotation(TransformsAggregate()))
    }

    // MARK: - Line-box leading (IH_LineHeight / Columns_TextBlock)

    func testContentHeightUsesInterMetricsFallback() {
        // Test bundle has no registered Inter → the hhea-table constant
        // path: (1984 + 494) / 2048 per em. 18px → 21.779…px, the same
        // content-area height Chromium uses for Inter on macOS.
        let h = LineBoxMetrics.contentHeight(fontSizePx: 18, design: .default)
        XCTAssertEqual(h, 18 * 2478 / 2048, accuracy: 0.01)
    }

    func testLeadingSplitsAroundContentArea() {
        // Columns_TextBlock: font-size 18, line-height 1.8 → 32.4px
        // line boxes. Content area 21.779 → leading 10.62, half 5.31.
        // Advance (content + spacing) must be EXACTLY 32.4 and the box
        // total for N lines N × 32.4 (content×N + spacing×(N−1) +
        // 2×half) — the browser's numbers.
        let (spacing, half) = LineBoxMetrics.leading(lineHeightPx: 32.4,
                                                     fontSizePx: 18,
                                                     design: .default)
        let content = LineBoxMetrics.contentHeight(fontSizePx: 18, design: .default)
        XCTAssertEqual(content + spacing, 32.4, accuracy: 0.01)      // per-line advance
        XCTAssertEqual(spacing, 2 * half, accuracy: 0.001)           // §10.8.1 half split
        // 2-line box: first/last half-leading included.
        XCTAssertEqual(2 * content + spacing + 2 * half, 64.8, accuracy: 0.02)
    }

    func testLeadingIsZeroWithoutLineHeightOrBelowNormal() {
        // No declared line-height → natural metrics untouched (keeps
        // every no-line-height fixture byte-stable).
        let none = LineBoxMetrics.leading(lineHeightPx: nil, fontSizePx: 16,
                                          design: .default)
        XCTAssertEqual(none.spacing, 0)
        XCTAssertEqual(none.halfLeading, 0)
        // line-height BELOW the content area (e.g. `line-height: 1` on
        // Inter) → clamp at natural metrics, matching the pre-wave-3
        // renderer (SwiftUI cannot overlap lines).
        let tight = LineBoxMetrics.leading(lineHeightPx: 16, fontSizePx: 16,
                                           design: .default)
        XCTAssertEqual(tight.spacing, 0)
        XCTAssertEqual(tight.halfLeading, 0)
    }

    // MARK: - Multicol typed count + full-width default (Columns_Decorated)

    func testColumnCountExtractsTypedValue() {
        // Wire shape from the converter (ColumnCountSerializer):
        // an integer count is a bare number, `auto` a string.
        let counted = ColumnsExtractor.extract(from: [
            IRProperty(type: "ColumnCount", data: .double(3.0)),
        ])
        XCTAssertEqual(counted?.count, 3)
        let auto = ColumnsExtractor.extract(from: [
            IRProperty(type: "ColumnCount", data: .string("auto")),
        ])
        XCTAssertNotNil(auto)          // touched (registered family)
        XCTAssertNil(auto?.count)      // …but no numeric count
        // Invalid (< 1) counts are rejected per css-multicol-1 §3.1.
        let zero = ColumnsExtractor.extract(from: [
            IRProperty(type: "ColumnCount", data: .double(0)),
        ])
        XCTAssertNil(zero?.count)
    }

    func testMulticolFullWidthFoldPreconditions() {
        // The ComponentRenderer fold gives an AUTO-width multicol
        // container (count ≥ 2) the containing-block width — pin the
        // style-level inputs it keys on: Columns_Decorated (no Width
        // property, column-count 3) must expose count 3 AND no width,
        // while Columns_C01 (explicit width 240) keeps its width.
        let decorated = StyleBuilder.build(from: [
            IRProperty(type: "ColumnCount", data: .double(3.0)),
            IRProperty(type: "BackgroundColor", data: .object(["hex": .string("#e67e22")])),
        ])
        XCTAssertEqual(decorated.columns?.count, 3)
        XCTAssertNil(decorated.size.width)
        let c01 = StyleBuilder.build(from: [
            IRProperty(type: "ColumnCount", data: .double(3.0)),
            IRProperty(type: "Width", data: .object(["px": .double(240)])),
        ])
        XCTAssertEqual(c01.columns?.count, 3)
        XCTAssertNotNil(c01.size.width)   // explicit width always wins
    }
}
