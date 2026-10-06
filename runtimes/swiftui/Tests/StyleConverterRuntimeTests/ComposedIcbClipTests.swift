//
//  ComposedIcbClipTests.swift
//  StyleConverterRuntimeTests
//
//  wave-52 lane L2 (Fix A) — pins for the composed canvas's INLINE-AXIS CLIP
//  BAND (WPTCanvas.icbClipBand + WPTCanvas.IcbClipBand), the geometry the
//  iOS harness's ComposedCaptureCanvas applies with `.clipShape` to the group
//  holding the in-flow VStack AND both FixedHoistOverlay attach points.
//
//  THE DEFECT. The browser-ref is rendered in a 358-px viewport and padded in
//  image space, so frame columns 0..15 / 374..389 never carry ink; the iOS
//  composed canvas had no clip on that box, so a `width: 400px` container
//  (css-gaps/flex/flex-gap-decorations-040, wave51-fix ios P 0.9934) painted
//  to x=389 — its whole 1664-px mismatch. 208 overrun-right + 84 overrun-left
//  scored cells on wave51-fix (frame-ink-census.json).
//
//  Every number is read out of tools/titan/capture-browser-ref.mjs:
//    CANVAS_WIDTH 390 · CANVAS_PAD_PX 16 · REF_RENDER_WIDTH 358 → band [16, 374).
//  The Compose twin (composedIcbClipBandPx, ComposedIcbClipTest) asserts the
//  IDENTICAL numbers, so the two natives cannot drift.
//
//  EXECUTED MUTATIONS (applied to WPTCaptureMode.swift, this class run alone
//  under the Catalyst destination, source restored byte-exact — sha256
//  checked; record in tools/titan/results/wave52-composed-canvas/_note.md):
//    M1 `maxX: canvasExtent` (frame not subtracted)   → testBandIs16To374 red.
//    M2 `x: rect.minX` in the Shape (inset 0 dropped) → testShapeClipsOnlyTheInlineAxis red.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ComposedIcbClipTests: XCTestCase {

    // MARK: - The pure band

    func testBandIs16To374OnTheDefaultCanvas() {
        // THE contract: the 390 canvas with the 16 frame clips content to
        // [16, 374) — image x=374 is the first frame column, exactly where
        // the ref's raster stops having ink (its right ink edge is 373).
        let band = WPTCanvas.icbClipBand(canvasExtent: 390)
        XCTAssertEqual(band.minX, 16)
        XCTAssertEqual(band.maxX, 374)
    }

    func testBandFollowsACaptureWidthOverride() {
        // A 250-wide canvas keeps the same 16 frame per side → [16, 234): the
        // crop tracks the live canvas width, never a hard-coded 390.
        let band = WPTCanvas.icbClipBand(canvasExtent: 250)
        XCTAssertEqual(band.minX, 16)
        XCTAssertEqual(band.maxX, 234)
    }

    func testBandIsTheFrameNotTheResolvedPad() {
        // An author `body { padding: 40px }` makes resolvedPadding 56, but
        // CSS 2.1 §9.1.1's viewport — the ref's crop — does not move: the
        // harness passes the FRAME (16) and the band stays 16/374. Passing
        // the resolved pad would give 56/334, which the ref never shows.
        let padded = WPTCanvas.icbClipBand(canvasExtent: 390, frame: 16 + 40)
        XCTAssertEqual(padded.minX, 56)
        XCTAssertEqual(padded.maxX, 334)
        XCTAssertNotEqual(padded.maxX, WPTCanvas.icbClipBand(canvasExtent: 390).maxX)
    }

    func testBandNeverInvertsOnADegenerateCanvas() {
        // Narrower than two frames → an EMPTY band at the frame (minX ==
        // maxX), never a negative-width rect.
        let band = WPTCanvas.icbClipBand(canvasExtent: 20)
        XCTAssertEqual(band.minX, 16)
        XCTAssertEqual(band.maxX, 16)
    }

    // MARK: - The Shape

    func testShapeClipsOnlyTheInlineAxis() {
        // path(in: the 390x600 canvas): x ∈ [16, 374) (width 358 = the ICB),
        // y open far past both edges — the block axis stays visible because
        // the ref's second viewport is max(scrollHeight, 568) and never
        // crops the bottom. Mutation M2 (inset 0) lands minX at 0.
        let rect = WPTCanvas.IcbClipBand(frame: WPTCanvas.canvasFramePx)
            .path(in: CGRect(x: 0, y: 0, width: 390, height: 600)).boundingRect
        XCTAssertEqual(rect.minX, 16)
        XCTAssertEqual(rect.width, 358)
        XCTAssertLessThan(rect.minY, -100_000)
        XCTAssertGreaterThan(rect.maxY, 100_600)
    }

    func testShapeHonoursTheFrameArgument() {
        // The Shape reads its `frame` — the harness must pass
        // ComposedCaptureCanvas.padding (= canvasFramePx). A different frame
        // moves the band, so the harness source pin
        // (apps/ios-harness ComposedCanvasIcbClipTests) checks the argument.
        let rect = WPTCanvas.IcbClipBand(frame: 56)
            .path(in: CGRect(x: 0, y: 0, width: 390, height: 600)).boundingRect
        XCTAssertEqual(rect.minX, 56)
        XCTAssertEqual(rect.maxX, 334)
    }

    func testVerticalSlackIsLargeAndFinite() {
        // Dwarfs the tallest wave51-fix capture (4232 px) yet finite so the
        // mask rect's arithmetic cannot overflow; same number as Compose.
        XCTAssertGreaterThan(WPTCanvas.icbClipVerticalSlack, 100_000)
        XCTAssertTrue(WPTCanvas.icbClipVerticalSlack.isFinite)
        XCTAssertEqual(WPTCanvas.icbClipVerticalSlack, 1_000_000)
    }
}
