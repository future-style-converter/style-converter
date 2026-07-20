//
//  OverflowAxisClipTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 18 RC4 — axis-selective overflow clipping pins. Wire shapes are
//  copied from the LIVE per-test IR at tools/titan/runs/wave18-gate/
//  sections/css-overflow/per-test-ir/wpt__css-overflow__clip-003.json:
//  OverflowX/OverflowY carry a bare UPPERCASE keyword string ("CLIP",
//  "AUTO", …). Rules under test: css-overflow-3 §3 (every non-visible
//  used value clips its axis) and §3.1 (used-value coercion; visible+clip
//  is the only single-axis pair). The pure functions pinned here are
//  signature-parallel with the Compose twins (scrolling/OverflowConfig.kt
//  companion + scrolling/AxisClip.kt) for cross-native probing.
//

import Foundation
import XCTest
// @testable: OverflowClipRules / AxisClipRect are internal to the module.
@testable import StyleConverterRuntime

final class OverflowAxisClipTests: XCTestCase {

    // Live-wire helper: build IRProperty rows exactly as decoded.
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // Derive the per-axis clip flags the applier computes, from a config.
    private func clipFlags(_ cfg: VisibilityConfig?) -> (x: Bool, y: Bool) {
        // nil axis = property undeclared → CSS initial `visible`.
        let ox = cfg?.overflowX ?? OverflowKind.visible
        let oy = cfg?.overflowY ?? OverflowKind.visible
        // Same call sequence as VisibilityApplier.body.
        return (OverflowClipRules.axisClips(OverflowClipRules.usedOverflow(ox, other: oy)),
                OverflowClipRules.axisClips(OverflowClipRules.usedOverflow(oy, other: ox)))
    }

    func testClip003WireSingleAxisClipsXOnly() {
        // clip-003 component clip-003__1__0 declares ONLY OverflowX: "CLIP".
        // §3.1: visible pairs freely with clip, so Y stays visible — the
        // translucent blue child must spill vertically, not horizontally.
        let cfg = VisibilityExtractor.extract(from: props([
            ("OverflowX", .string("CLIP")),
        ]))
        let f = clipFlags(cfg)
        XCTAssertTrue(f.x, "X must clip")
        XCTAssertFalse(f.y, "Y must stay visible (spill)")
    }

    func testClip003WireBothAxesClipBoth() {
        // Component clip-003__0__0 declares OverflowX+OverflowY: "CLIP" —
        // the both-axis case routes to the stock .clipped() path.
        let cfg = VisibilityExtractor.extract(from: props([
            ("OverflowX", .string("CLIP")),
            ("OverflowY", .string("CLIP")),
        ]))
        let f = clipFlags(cfg)
        XCTAssertTrue(f.x)
        XCTAssertTrue(f.y)
    }

    func testAutoWrapperIsAClippingScrollContainer() {
        // The 50x50 wrapper declares OverflowX+OverflowY: "AUTO". §3: a
        // scroll container ALWAYS clips, even before any scrolling.
        let cfg = VisibilityExtractor.extract(from: props([
            ("OverflowX", .string("AUTO")),
            ("OverflowY", .string("AUTO")),
        ]))
        let f = clipFlags(cfg)
        XCTAssertTrue(f.x)
        XCTAssertTrue(f.y)
    }

    func testVisibleCoercesToAutoBesideHidden() {
        // §3.1: overflow-x: hidden with overflow-y unset (visible) makes
        // the visible axis compute to auto — BOTH axes clip, unlike the
        // visible+clip pair above.
        let cfg = VisibilityExtractor.extract(from: props([
            ("OverflowX", .string("HIDDEN")),
        ]))
        let f = clipFlags(cfg)
        XCTAssertTrue(f.x)
        XCTAssertTrue(f.y)
    }

    func testUsedOverflowCoercionTable() {
        // Direct pins on the §3.1 pure function (cross-native parity
        // surface — Compose OverflowConfig.usedOverflow mirrors this table).
        // visible+clip family: used as specified (the single-axis pair).
        XCTAssertEqual(OverflowClipRules.usedOverflow(.visible, other: .clip), .visible)
        XCTAssertEqual(OverflowClipRules.usedOverflow(.clip, other: .visible), .clip)
        XCTAssertEqual(OverflowClipRules.usedOverflow(.visible, other: .visible), .visible)
        XCTAssertEqual(OverflowClipRules.usedOverflow(.clip, other: .clip), .clip)
        // visible beside a scroll-container partner → auto.
        XCTAssertEqual(OverflowClipRules.usedOverflow(.visible, other: .hidden), .auto)
        XCTAssertEqual(OverflowClipRules.usedOverflow(.visible, other: .scroll), .auto)
        XCTAssertEqual(OverflowClipRules.usedOverflow(.visible, other: .auto), .auto)
        // clip beside a scroll-container partner → hidden.
        XCTAssertEqual(OverflowClipRules.usedOverflow(.clip, other: .scroll), .hidden)
        XCTAssertEqual(OverflowClipRules.usedOverflow(.clip, other: .auto), .hidden)
        XCTAssertEqual(OverflowClipRules.usedOverflow(.clip, other: .hidden), .hidden)
        // Non-coerced values pass through untouched.
        XCTAssertEqual(OverflowClipRules.usedOverflow(.hidden, other: .visible), .hidden)
        XCTAssertEqual(OverflowClipRules.usedOverflow(.scroll, other: .auto), .scroll)
    }

    func testAxisClipsSet() {
        // §3: every used value except visible clips its axis.
        XCTAssertFalse(OverflowClipRules.axisClips(.visible))
        XCTAssertTrue(OverflowClipRules.axisClips(.hidden))
        XCTAssertTrue(OverflowClipRules.axisClips(.clip))
        XCTAssertTrue(OverflowClipRules.axisClips(.scroll))
        XCTAssertTrue(OverflowClipRules.axisClips(.auto))
    }

    func testClipBoundsExtendsOnlyTheUnclippedAxis() {
        // Geometry pin on a 50x50 box (clip-003's black box size):
        // clipX-only binds X to [0,50] and extends Y by the pinned finite
        // extent (±1e6 — greatestFiniteMagnitude degrades in CG path math).
        let e = AxisClipRect.unclippedExtent
        let b = AxisClipRect.clipBounds(clipX: true, clipY: false, width: 50, height: 50)
        XCTAssertEqual(b.minX, 0)
        XCTAssertEqual(b.maxX, 50)
        XCTAssertEqual(b.minY, -e)
        XCTAssertEqual(b.maxY, 50 + e)
        // Mirror: clipY-only binds Y and extends X.
        let b2 = AxisClipRect.clipBounds(clipX: false, clipY: true, width: 50, height: 50)
        XCTAssertEqual(b2.minX, -e)
        XCTAssertEqual(b2.maxX, 50 + e)
        XCTAssertEqual(b2.minY, 0)
        XCTAssertEqual(b2.maxY, 50)
    }

    func testAxisClipRectPathTracksTheProposedFrame() {
        // Shape conformance pin: the path must offset by the frame origin
        // SwiftUI proposes, so the clip follows the box's placement.
        let shape = AxisClipRect(clipX: true, clipY: false)
        let r = shape.path(in: CGRect(x: 10, y: 20, width: 50, height: 50)).boundingRect
        // X is bound to the frame's horizontal extent…
        XCTAssertEqual(r.minX, 10)
        XCTAssertEqual(r.maxX, 60)
        // …while Y extends past it by the finite extent on both sides.
        XCTAssertEqual(r.minY, 20 - AxisClipRect.unclippedExtent)
        XCTAssertEqual(r.maxY, 70 + AxisClipRect.unclippedExtent)
    }
}
