//
//  AbsposStaticAlignmentTests.swift
//  Lane FLEX-SAFE — XCTest pins for the abspos static-position
//  safe-alignment fallback (css-flexbox-1 §4.1 sole-item hypothetical +
//  css-align-3 §4.4 overflow keywords).
//
//  Shared-semantics contract: the Compose runtime's
//  AbsposStaticAlignmentTest.kt pins the SAME (childPx, containerPx,
//  spec) → offset table — identical inputs, identical expected Doubles
//  — so the two natives cannot drift.
//
//  Fixture numbers: the flex-abspos-staticpos-align-self-safe-001/002
//  WPT containers are 50px border-box with a 3px border on the natives
//  → 44px padding box; the abspos child is 65px (frame extent). The
//  non-overflow rows use a 25px child (the safe-003 fixture's size).
//

import XCTest
// @testable: AbsposStaticAlignment + the IR model inits are internal.
@testable import StyleConverterRuntime

final class AbsposStaticAlignmentTests: XCTestCase {

    // MARK: - wire builders (shapes pinned against the LIVE converter)

    /// Typed wire: `align-self: center` → {"type":"AlignSelf","data":"CENTER"}.
    private func typed(_ keyword: String) -> [IRProperty] {
        [IRProperty(type: "AlignSelf", data: .string(keyword))]
    }

    /// Generic wire: `align-self: safe center` → the unmapped escape hatch.
    private func generic(_ rawValue: String) -> [IRProperty] {
        [IRProperty(type: "Generic", data: .object([
            "propertyName": .string("align-self"),
            "rawValue": .string(rawValue),
            "_unmapped": .bool(true)
        ]))]
    }

    /// Inset property with the typed-length wire (only the TYPE matters).
    private func inset(_ type: String) -> [IRProperty] {
        [IRProperty(type: type,
                    data: .object(["type": .string("length"), "px": .double(10)]))]
    }

    private let center = AbsposStaticAlignment.Spec(base: .center, safe: false)
    private let safeCenter = AbsposStaticAlignment.Spec(base: .center, safe: true)
    private let end = AbsposStaticAlignment.Spec(base: .end, safe: false)
    private let safeEnd = AbsposStaticAlignment.Spec(base: .end, safe: true)

    // MARK: - 1. wire resolution (both channels)

    func testTypedCenterResolvesToUnsafeCenter() {
        // Plain keyword: default overflow behaviour keeps center even
        // when overflowing (the WPT refs paint symmetric spill).
        XCTAssertEqual(AbsposStaticAlignment.resolveCross(from: typed("CENTER")), center)
    }

    func testGenericSafeCenterResolvesWithSafeFlag() {
        // The exact live-converter wire of the safe-001/002 fixtures.
        XCTAssertEqual(AbsposStaticAlignment.resolveCross(from: generic("safe center")),
                       safeCenter)
    }

    func testGenericUnsafeCenterResolvesWithoutSafeFlag() {
        // css-align-3 §4.4: unsafe == honour the alignment regardless.
        XCTAssertEqual(AbsposStaticAlignment.resolveCross(from: generic("unsafe center")),
                       center)
    }

    func testGenericSafeEndResolves() {
        // The safe-003 fixture value…
        XCTAssertEqual(AbsposStaticAlignment.resolveCross(from: generic("safe end")), safeEnd)
        // …and the flex-end spelling folds to the same END base.
        XCTAssertEqual(AbsposStaticAlignment.resolveCross(from: generic("safe flex-end")),
                       safeEnd)
    }

    func testNoClaimForAbsentAutoStretchOrUnrelatedGenerics() {
        // Absent → the caller keeps the legacy start-anchored behaviour.
        XCTAssertNil(AbsposStaticAlignment.resolveCross(from: []))
        // auto/stretch have no static-position claim (css-flexbox-1
        // §4.1 treats them as flex-start for abspos children).
        XCTAssertNil(AbsposStaticAlignment.resolveCross(from: typed("STRETCH")))
        XCTAssertNil(AbsposStaticAlignment.resolveCross(from: typed("AUTO")))
        // A Generic for a DIFFERENT property never resolves.
        XCTAssertNil(AbsposStaticAlignment.resolveCross(from: [
            IRProperty(type: "Generic", data: .object([
                "propertyName": .string("justify-self"),
                "rawValue": .string("safe center"),
                "_unmapped": .bool(true)
            ]))
        ]))
    }

    // MARK: - 2. the fallback math — SHARED pins with the Compose twin

    func testOverflowPlainCenterKeepsCenterNegativeOffset() {
        // 65px child in a 44px container: free = −21 → center = −10.5
        // (the child spills 10.5px past BOTH edges — unsafe semantics).
        XCTAssertEqual(AbsposStaticAlignment.crossOffset(childPx: 65, containerPx: 44,
                                                         spec: center),
                       -10.5, accuracy: 1e-9)
    }

    func testOverflowSafeCenterFallsBackToStart() {
        // css-align-3 §4.4: safe + overflow → start (offset 0) — the
        // exact geometry the safe-001/002 web captures paint.
        XCTAssertEqual(AbsposStaticAlignment.crossOffset(childPx: 65, containerPx: 44,
                                                         spec: safeCenter),
                       0, accuracy: 1e-9)
    }

    func testOverflowEndAndSafeEnd() {
        // end → full free space (−21: end-edge alignment)…
        XCTAssertEqual(AbsposStaticAlignment.crossOffset(childPx: 65, containerPx: 44,
                                                         spec: end),
                       -21, accuracy: 1e-9)
        // …while safe end falls back to start on overflow.
        XCTAssertEqual(AbsposStaticAlignment.crossOffset(childPx: 65, containerPx: 44,
                                                         spec: safeEnd),
                       0, accuracy: 1e-9)
    }

    func testFitsSafeAndUnsafeAgree() {
        // 25px child in a 44px container: no overflow, so safe changes
        // nothing — center → 9.5, end/safe end → 19 (safe-003 numbers).
        XCTAssertEqual(AbsposStaticAlignment.crossOffset(childPx: 25, containerPx: 44,
                                                         spec: center),
                       9.5, accuracy: 1e-9)
        XCTAssertEqual(AbsposStaticAlignment.crossOffset(childPx: 25, containerPx: 44,
                                                         spec: safeCenter),
                       9.5, accuracy: 1e-9)
        XCTAssertEqual(AbsposStaticAlignment.crossOffset(childPx: 25, containerPx: 44,
                                                         spec: end),
                       19, accuracy: 1e-9)
        XCTAssertEqual(AbsposStaticAlignment.crossOffset(childPx: 25, containerPx: 44,
                                                         spec: safeEnd),
                       19, accuracy: 1e-9)
    }

    // MARK: - 3. the inset gate (css-position-3 §3.5)

    func testExplicitCrossInsetDisablesStaticPosition() {
        // Vertical axis: top/bottom + the logical block insets bind.
        XCTAssertTrue(AbsposStaticAlignment.hasCrossInset(inset("Top"), vertical: true))
        XCTAssertTrue(AbsposStaticAlignment.hasCrossInset(inset("InsetBlockEnd"), vertical: true))
        // A HORIZONTAL inset does not gate the vertical axis…
        XCTAssertFalse(AbsposStaticAlignment.hasCrossInset(inset("Left"), vertical: true))
        // …and vice versa (horizontal axis: left/right + inline insets).
        XCTAssertTrue(AbsposStaticAlignment.hasCrossInset(inset("Right"), vertical: false))
        XCTAssertFalse(AbsposStaticAlignment.hasCrossInset(inset("Bottom"), vertical: false))
    }

    // MARK: - 4. the view-level convenience (per-axis routing)

    func testStaticCrossOffsetRoutesAxesAndGates() {
        // Row flex parent, unsafe center, overflowing child: the shift
        // lands on the VERTICAL (cross) axis only — the fixture case.
        let child = generic("unsafe center")
            + [IRProperty(type: "Width",
                          data: .object(["type": .string("length"), "px": .double(65)])),
               IRProperty(type: "Height",
                          data: .object(["type": .string("length"), "px": .double(65)]))]
        let rowShift = AbsposStaticAlignment.staticCrossOffset(
            flexDirection: .row, childProperties: child, containerW: 44, containerH: 44)
        // Pinned to the shared −10.5 table row above (y axis for row).
        XCTAssertEqual(rowShift.height, -10.5, accuracy: 1e-9)
        XCTAssertEqual(rowShift.width, 0, accuracy: 1e-9)
        // Column flex parent: same claim shifts the HORIZONTAL axis.
        let colShift = AbsposStaticAlignment.staticCrossOffset(
            flexDirection: .column, childProperties: child, containerW: 44, containerH: 44)
        XCTAssertEqual(colShift.width, -10.5, accuracy: 1e-9)
        XCTAssertEqual(colShift.height, 0, accuracy: 1e-9)
        // Non-flex parent (nil direction) → block-flow anchor, no shift.
        XCTAssertEqual(AbsposStaticAlignment.staticCrossOffset(
            flexDirection: nil, childProperties: child, containerW: 44, containerH: 44),
            .zero)
        // safe center + overflow → start fallback (zero shift), row axis.
        let safeChild = generic("safe center")
            + [IRProperty(type: "Height",
                          data: .object(["type": .string("length"), "px": .double(65)]))]
        XCTAssertEqual(AbsposStaticAlignment.staticCrossOffset(
            flexDirection: .row, childProperties: safeChild, containerW: 44, containerH: 44),
            .zero)
        // An explicit cross inset stands the static position down even
        // with an align-self claim present (css-position-3 §3.5).
        let insetChild = child + inset("Top")
        XCTAssertEqual(AbsposStaticAlignment.staticCrossOffset(
            flexDirection: .row, childProperties: insetChild, containerW: 44, containerH: 44),
            .zero)
    }
}
