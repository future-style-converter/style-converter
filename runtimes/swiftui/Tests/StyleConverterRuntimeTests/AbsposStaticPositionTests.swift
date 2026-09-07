//
//  AbsposStaticPositionTests.swift
//  Wave 19 (lane FLEX) — XCTest pins for the FULL abspos static-position
//  resolver (AbsposStaticPosition): the css-flexbox-1 §5 axis-mapping
//  table + the physical per-axis offset math. Shared-semantics contract:
//  the Compose runtime's AbsposStaticPositionTest.kt pins the SAME wires
//  and the SAME (childPx, containerPx, spec) → offset table — identical
//  inputs, identical expected Doubles — so the two natives cannot drift.
//
//  PIN TABLE — the WPT flex-abspos-staticpos-align-self-safe fixtures,
//  per sub-container. Containers are 50×50 padding-box (WPT content-box
//  native path); child margin-box extents: 69 (safe-001: 65 + 2·2px
//  border), 79 (safe-002: 69 + 2·5px margin), 29 (safe-003: 25 + 2·2px
//  border). Offsets are the child MARGIN-box origin relative to the
//  container's padding-box origin (+x right, +y down):
//
//    test     | sub | flex-direction | writing-mode | claim        |   x |   y
//    safe-001 |  0  | row            | horizontal   | safe center  |   0 |   0
//    safe-001 |  1  | column         | horizontal   | safe center  |   0 |   0
//    safe-001 |  2  | row            | vertical-rl  | safe center  | −19 |   0
//    safe-001 |  3  | column         | vertical-rl  | safe center  | −19 |   0
//    safe-002 |  0  | row-reverse    | horizontal   | safe center  | −29 |   0
//    safe-002 |  1  | column-reverse | horizontal   | safe center  |   0 | −29
//    safe-002 |  2  | row-reverse    | vertical-rl  | safe center  | −29 | −29
//    safe-002 |  3  | column-reverse | vertical-rl  | safe center  |   0 |   0
//    safe-003 |  0  | row            | horizontal   | safe end     |   0 |  21
//    safe-003 |  1  | column         | horizontal   | safe end     |  21 |   0
//    safe-003 |  2  | row            | vertical-rl  | safe end     |   0 |   0
//    safe-003 |  3  | column         | vertical-rl  | safe end     |  21 |  21
//

import XCTest
// @testable: AbsposStaticPosition + the IR model inits are internal.
@testable import StyleConverterRuntime

final class AbsposStaticPositionTests: XCTestCase {

    // MARK: - wire builders (shapes pinned against the LIVE converter)

    /// Typed keyword wire, e.g. {"type":"FlexDirection","data":"ROW_REVERSE"}.
    private func kw(_ type: String, _ keyword: String) -> IRProperty {
        IRProperty(type: type, data: .string(keyword))
    }

    /// Container list: raw flex-direction / writing-mode (+ optional justify).
    private func container(fd: String? = nil, wm: String? = nil,
                           justify: String? = nil) -> [IRProperty] {
        var out: [IRProperty] = []
        if let fd { out.append(kw("FlexDirection", fd)) }
        if let wm { out.append(kw("WritingMode", wm)) }
        if let justify { out.append(kw("JustifyContent", justify)) }
        return out
    }

    /// Child list: the Generic align-self escape hatch (live wire shape).
    private func child(_ rawAlignSelf: String) -> [IRProperty] {
        [IRProperty(type: "Generic", data: .object([
            "propertyName": .string("align-self"),
            "rawValue": .string(rawAlignSelf),
            "_unmapped": .bool(true)
        ]))]
    }

    /// Inset property with the typed-length wire (only the TYPE matters).
    private func inset(_ type: String) -> IRProperty {
        IRProperty(type: type,
                   data: .object(["type": .string("length"), "px": .double(10)]))
    }

    /// The full static offset for one axis-claim pair — the shared math.
    private func off(_ childPx: Double, _ containerPx: Double,
                     _ spec: AbsposStaticPosition.AxisSpec?) -> Double {
        spec.map { AbsposStaticPosition.axisOffset(childPx: childPx,
                                                   containerPx: containerPx,
                                                   spec: $0) } ?? 0
    }

    // MARK: - 1. the axis-mapping table (css-flexbox-1 §5)

    func testAxisMapHorizontalTbRowFamily() {
        // row: main = inline = +x; cross = block = +y.
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "ROW", writingMode: nil, direction: nil),
            .init(mainIsHorizontal: true, mainReversed: false, crossReversed: false))
        // Absent wire → the CSS initial row (css-flexbox-1 §5.1).
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: nil, writingMode: nil, direction: nil),
            .init(mainIsHorizontal: true, mainReversed: false, crossReversed: false))
        // row-reverse flips ONLY the main axis.
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "ROW_REVERSE", writingMode: nil, direction: nil),
            .init(mainIsHorizontal: true, mainReversed: true, crossReversed: false))
    }

    func testAxisMapHorizontalTbColumnFamily() {
        // column: main = block = +y; cross = inline = +x.
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "COLUMN", writingMode: nil, direction: nil),
            .init(mainIsHorizontal: false, mainReversed: false, crossReversed: false))
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "COLUMN_REVERSE", writingMode: nil, direction: nil),
            .init(mainIsHorizontal: false, mainReversed: true, crossReversed: false))
    }

    func testAxisMapVerticalRlSwapsAxesAndReversesBlock() {
        // vertical-rl row: main = inline = +y; cross = block = −x
        // (css-writing-modes-4 §2.4).
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "ROW", writingMode: "VERTICAL_RL", direction: nil),
            .init(mainIsHorizontal: false, mainReversed: false, crossReversed: true))
        // vertical-rl column: main = block = −x; cross = inline = +y.
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "COLUMN", writingMode: "VERTICAL_RL", direction: nil),
            .init(mainIsHorizontal: true, mainReversed: true, crossReversed: false))
        // vertical-rl row-reverse: main = inline reversed = −y.
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "ROW_REVERSE", writingMode: "VERTICAL_RL", direction: nil),
            .init(mainIsHorizontal: false, mainReversed: true, crossReversed: true))
        // vertical-rl column-reverse: block-rl × reverse cancel → +x.
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "COLUMN_REVERSE", writingMode: "VERTICAL_RL", direction: nil),
            .init(mainIsHorizontal: true, mainReversed: false, crossReversed: false))
        // vertical-lr: block runs +x — no cross reversal for row.
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "ROW", writingMode: "VERTICAL_LR", direction: nil),
            .init(mainIsHorizontal: false, mainReversed: false, crossReversed: false))
        // RTL flips the INLINE axis (row main).
        XCTAssertEqual(
            AbsposStaticPosition.axisMap(flexDirection: "ROW", writingMode: "HORIZONTAL_TB", direction: "RTL"),
            .init(mainIsHorizontal: true, mainReversed: true, crossReversed: false))
    }

    // MARK: - 2. the physical offset math (shared table with Compose)

    func testAxisOffsetReversedMirrorsInsideFreeSpace() {
        let safeCenterRev = AbsposStaticPosition.AxisSpec(base: .center, safe: true, reversed: true)
        // safe-001 C2 cross: 69px child in 50px, safe center overflows →
        // logical start → PHYSICAL END (right-anchored): −19.
        XCTAssertEqual(AbsposStaticPosition.axisOffset(childPx: 69, containerPx: 50, spec: safeCenterRev),
                       -19, accuracy: 1e-9)
        // Fits + end on a reversed axis = physical start: 0.
        let endRev = AbsposStaticPosition.AxisSpec(base: .end, safe: false, reversed: true)
        XCTAssertEqual(AbsposStaticPosition.axisOffset(childPx: 29, containerPx: 50, spec: endRev),
                       0, accuracy: 1e-9)
        // Fits + start on a reversed axis = physical end: free = 21.
        let startRev = AbsposStaticPosition.AxisSpec(base: .start, safe: false, reversed: true)
        XCTAssertEqual(AbsposStaticPosition.axisOffset(childPx: 29, containerPx: 50, spec: startRev),
                       21, accuracy: 1e-9)
        // Center is symmetric — reversal changes nothing (−9.5 both ways).
        let centerRev = AbsposStaticPosition.AxisSpec(base: .center, safe: false, reversed: true)
        let centerFwd = AbsposStaticPosition.AxisSpec(base: .center, safe: false, reversed: false)
        XCTAssertEqual(AbsposStaticPosition.axisOffset(childPx: 69, containerPx: 50, spec: centerRev),
                       AbsposStaticPosition.axisOffset(childPx: 69, containerPx: 50, spec: centerFwd),
                       accuracy: 1e-9)
    }

    // MARK: - 3. the full fixture pin table (resolveStatic → axisOffset)

    /// Resolve + offset both axes for one sub-container configuration.
    private func place(fd: String?, wm: String?, _ rawAlignSelf: String,
                       childW: Double, childH: Double,
                       containerPx: Double = 50) -> (x: Double, y: Double) {
        let pos = AbsposStaticPosition.resolveStatic(
            containerProperties: container(fd: fd, wm: wm),
            childProperties: child(rawAlignSelf))
        return (off(childW, containerPx, pos.x), off(childH, containerPx, pos.y))
    }

    func testSafe001FourSubContainers() {
        // C0/C1: overflow safe center → start fallback on both axes.
        var p = place(fd: "ROW", wm: nil, "safe center", childW: 69, childH: 69)
        XCTAssertEqual(p.x, 0, accuracy: 1e-9); XCTAssertEqual(p.y, 0, accuracy: 1e-9)
        p = place(fd: "COLUMN", wm: nil, "safe center", childW: 69, childH: 69)
        XCTAssertEqual(p.x, 0, accuracy: 1e-9); XCTAssertEqual(p.y, 0, accuracy: 1e-9)
        // C2 row vertical-rl: cross = block −x → RIGHT-anchored spill.
        p = place(fd: "ROW", wm: "VERTICAL_RL", "safe center", childW: 69, childH: 69)
        XCTAssertEqual(p.x, -19, accuracy: 1e-9); XCTAssertEqual(p.y, 0, accuracy: 1e-9)
        // C3 column vertical-rl: main = block −x → right-anchored too.
        p = place(fd: "COLUMN", wm: "VERTICAL_RL", "safe center", childW: 69, childH: 69)
        XCTAssertEqual(p.x, -19, accuracy: 1e-9); XCTAssertEqual(p.y, 0, accuracy: 1e-9)
    }

    func testSafe002ReverseDirectionsWithMargins() {
        // Margin-box extent 79 — the static position places the MARGIN
        // box (css-flexbox-1 §4.1 hypothetical item).
        var p = place(fd: "ROW_REVERSE", wm: nil, "safe center", childW: 79, childH: 79)
        XCTAssertEqual(p.x, -29, accuracy: 1e-9); XCTAssertEqual(p.y, 0, accuracy: 1e-9)
        p = place(fd: "COLUMN_REVERSE", wm: nil, "safe center", childW: 79, childH: 79)
        XCTAssertEqual(p.x, 0, accuracy: 1e-9); XCTAssertEqual(p.y, -29, accuracy: 1e-9)
        p = place(fd: "ROW_REVERSE", wm: "VERTICAL_RL", "safe center", childW: 79, childH: 79)
        XCTAssertEqual(p.x, -29, accuracy: 1e-9); XCTAssertEqual(p.y, -29, accuracy: 1e-9)
        // C3: the two x-flips cancel → top-left margin-box anchor.
        p = place(fd: "COLUMN_REVERSE", wm: "VERTICAL_RL", "safe center", childW: 79, childH: 79)
        XCTAssertEqual(p.x, 0, accuracy: 1e-9); XCTAssertEqual(p.y, 0, accuracy: 1e-9)
    }

    func testSafe003SafeEndFitsSoEndIsHonored() {
        // Frame extent 29 fits the 50px container — END is honored.
        var p = place(fd: "ROW", wm: nil, "safe end", childW: 29, childH: 29)
        XCTAssertEqual(p.x, 0, accuracy: 1e-9); XCTAssertEqual(p.y, 21, accuracy: 1e-9)
        p = place(fd: "COLUMN", wm: nil, "safe end", childW: 29, childH: 29)
        XCTAssertEqual(p.x, 21, accuracy: 1e-9); XCTAssertEqual(p.y, 0, accuracy: 1e-9)
        // C2 row vertical-rl: cross −x, end → physical LEFT.
        p = place(fd: "ROW", wm: "VERTICAL_RL", "safe end", childW: 29, childH: 29)
        XCTAssertEqual(p.x, 0, accuracy: 1e-9); XCTAssertEqual(p.y, 0, accuracy: 1e-9)
        // C3 column vertical-rl: main −x start → right; cross end → bottom.
        p = place(fd: "COLUMN", wm: "VERTICAL_RL", "safe end", childW: 29, childH: 29)
        XCTAssertEqual(p.x, 21, accuracy: 1e-9); XCTAssertEqual(p.y, 21, accuracy: 1e-9)
    }

    // MARK: - 4. justify-content as the sole-item main claim

    func testJustifyContentSoleItemFolds() {
        // Declared center claims the main axis (css-position-3 §3.5.3)
        // and marks the TYPED-declared flag (Compose arrangement gate).
        let pos = AbsposStaticPosition.resolveStatic(
            containerProperties: container(fd: "ROW", justify: "CENTER"),
            childProperties: child("safe end"))
        XCTAssertEqual(pos.x, .init(base: .center, safe: false, reversed: false))
        XCTAssertTrue(pos.justifyTyped)
        // Distribution keywords fold per css-align-3 §5.1 (sole item).
        XCTAssertEqual(AbsposStaticPosition.justifySpec(container(justify: "SPACE_BETWEEN")),
                       AbsposStaticAlignment.Spec(base: .start, safe: false))
        XCTAssertEqual(AbsposStaticPosition.justifySpec(container(justify: "SPACE_AROUND")),
                       AbsposStaticAlignment.Spec(base: .center, safe: false))
        // Undeclared → nil (the resolver synthesizes default start).
        XCTAssertNil(AbsposStaticPosition.justifySpec(container(fd: "ROW")))
        XCTAssertFalse(AbsposStaticPosition.resolveStatic(
            containerProperties: container(fd: "ROW"),
            childProperties: child("safe end")).justifyTyped)
        // Undeclared justify still yields the default START main claim —
        // reversal carried on the spec (row-reverse right anchor).
        let rev = AbsposStaticPosition.resolveStatic(
            containerProperties: container(fd: "ROW_REVERSE"), childProperties: [])
        XCTAssertEqual(rev.x, .init(base: .start, safe: false, reversed: true))
    }

    // MARK: - 5. the §3.5 inset gate is PHYSICAL per axis

    func testExplicitInsetsNullTheClaimOnTheirPhysicalAxisOnly() {
        // dynamic-align-self-001's exact shape: typed align-self END +
        // Top/Left insets → BOTH axes stand down (PositionApplier owns).
        let insetChild: [IRProperty] = [
            IRProperty(type: "AlignSelf", data: .string("END")),
            inset("Top"), inset("Left")
        ]
        let pos = AbsposStaticPosition.resolveStatic(
            containerProperties: container(fd: "ROW"), childProperties: insetChild)
        XCTAssertNil(pos.x)
        XCTAssertNil(pos.y)
        // A vertical-only inset keeps the horizontal claim: row main (x)
        // survives, the cross (y) claim stands down (css-position-3 §3.5).
        let vOnly: [IRProperty] = [
            IRProperty(type: "AlignSelf", data: .string("END")),
            inset("Top")
        ]
        let pos2 = AbsposStaticPosition.resolveStatic(
            containerProperties: container(fd: "ROW"), childProperties: vOnly)
        XCTAssertEqual(pos2.x, .init(base: .start, safe: false, reversed: false))
        XCTAssertNil(pos2.y)
    }

    func testAutoInsetsKeepTheStaticPosition() {
        // Live wire (pinned 2026-07-26): `top: auto` ships as
        // {"type":"Top","data":"auto"} — §3.5 only replaces the static
        // position for NON-auto values, so both claims must stand.
        // Twin of the Kotlin auto-inset pin.
        let autoChild: [IRProperty] = [
            IRProperty(type: "AlignSelf", data: .string("END")),
            IRProperty(type: "Top", data: .string("auto")),
            IRProperty(type: "Left", data: .string("auto"))
        ]
        let pos = AbsposStaticPosition.resolveStatic(
            containerProperties: container(fd: "ROW"), childProperties: autoChild)
        XCTAssertEqual(pos.x, .init(base: .start, safe: false, reversed: false))
        XCTAssertEqual(pos.y, .init(base: .end, safe: false, reversed: false))
        // Mixed: auto Top + px Left → x owned by the real inset, the
        // vertical (cross) claim stands.
        let mixed: [IRProperty] = [
            IRProperty(type: "AlignSelf", data: .string("END")),
            IRProperty(type: "Top", data: .string("auto")),
            inset("Left")
        ]
        let pos2 = AbsposStaticPosition.resolveStatic(
            containerProperties: container(fd: "ROW"), childProperties: mixed)
        XCTAssertNil(pos2.x)
        XCTAssertEqual(pos2.y, .init(base: .end, safe: false, reversed: false))
    }

    func testChildFrameExtentsBorderlessChildNoBandDoubleCount() {
        // Skeptic pin: a BORDERLESS declared-25px child must report the
        // bare 25 in WPT mode — the RC-A6 band join only fires when
        // borders/padding actually inflate the painted frame.
        let plain: [IRProperty] = [
            IRProperty(type: "Width", data: .object(["type": .string("length"), "px": .double(25)])),
            IRProperty(type: "Height", data: .object(["type": .string("length"), "px": .double(25)]))
        ]
        let e = AbsposStaticPosition.childFrameExtents(
            childProperties: plain, wptCaptureMode: true)
        XCTAssertEqual(e.w!, 25, accuracy: 1e-9)
        XCTAssertEqual(e.h!, 25, accuracy: 1e-9)
    }

    // MARK: - 6. RC-A6 — painted-frame extents (declared + bands + margins)

    /// A 25×25 child with 2px dotted borders — the safe-003 child shape.
    private func borderedChild(margin: Double? = nil) -> [IRProperty] {
        var props: [IRProperty] = [
            IRProperty(type: "Width", data: .object(["type": .string("length"), "px": .double(25)])),
            IRProperty(type: "Height", data: .object(["type": .string("length"), "px": .double(25)])),
            IRProperty(type: "BorderTopWidth", data: .object(["px": .double(2)])),
            IRProperty(type: "BorderRightWidth", data: .object(["px": .double(2)])),
            IRProperty(type: "BorderBottomWidth", data: .object(["px": .double(2)])),
            IRProperty(type: "BorderLeftWidth", data: .object(["px": .double(2)])),
            IRProperty(type: "BorderTopStyle", data: .string("DOTTED")),
            IRProperty(type: "BorderRightStyle", data: .string("DOTTED")),
            IRProperty(type: "BorderBottomStyle", data: .string("DOTTED")),
            IRProperty(type: "BorderLeftStyle", data: .string("DOTTED"))
        ]
        if let m = margin {
            // Physical margin longhands, the live {"px":N} wire.
            for side in ["MarginTop", "MarginRight", "MarginBottom", "MarginLeft"] {
                props.append(IRProperty(type: side, data: .object(["px": .double(m)])))
            }
        }
        return props
    }

    func testChildFrameExtentsAddBandsInWptModeOnly() {
        // WPT capture: content-box default → frame = 25 + 2·2 = 29 (the
        // extent SizeApplier actually paints — RC-A6's missing band).
        let wpt = AbsposStaticPosition.childFrameExtents(
            childProperties: borderedChild(), wptCaptureMode: true)
        XCTAssertEqual(wpt.w!, 29, accuracy: 1e-9)
        XCTAssertEqual(wpt.h!, 29, accuracy: 1e-9)
        // Dark stage: border-box status quo → declared IS the frame.
        let dark = AbsposStaticPosition.childFrameExtents(
            childProperties: borderedChild(), wptCaptureMode: false)
        XCTAssertEqual(dark.w!, 25, accuracy: 1e-9)
        XCTAssertEqual(dark.h!, 25, accuracy: 1e-9)
        // Margins always join (the static position places the margin box).
        let margined = AbsposStaticPosition.childFrameExtents(
            childProperties: borderedChild(margin: 5), wptCaptureMode: true)
        XCTAssertEqual(margined.w!, 39, accuracy: 1e-9)
        XCTAssertEqual(margined.h!, 39, accuracy: 1e-9)
    }

    func testStaticOffsetBorderedChildEndAlignsPaintedFrame() {
        // RC-A6 pin: safe-003 C0 on the overlay path — a 2px-bordered
        // 25px child, `safe end`, 50px padding box: the END offset must
        // use the PAINTED 29px frame → y = 21 (the wave-18 declared-25
        // read produced 25 — the +4px drift, iOS y64 vs ref y60).
        let props = borderedChild() + child("safe end")
        let shift = AbsposStaticPosition.staticOffset(
            containerProperties: container(fd: "ROW"),
            childProperties: props,
            containerW: 50, containerH: 50,
            wptCaptureMode: true)
        XCTAssertEqual(shift.height, 21, accuracy: 1e-9)
        XCTAssertEqual(shift.width, 0, accuracy: 1e-9)
        // Margin twin (safe-002 C0): row-reverse right-anchors the 79px
        // margin box → x = −29; the 5px margin keeps ink at −24.
        let marginProps: [IRProperty] = [
            IRProperty(type: "Width", data: .object(["type": .string("length"), "px": .double(65)])),
            IRProperty(type: "Height", data: .object(["type": .string("length"), "px": .double(65)]))
        ] + borderedChildMargins() + child("safe center")
        let shift2 = AbsposStaticPosition.staticOffset(
            containerProperties: container(fd: "ROW_REVERSE"),
            childProperties: marginProps,
            containerW: 50, containerH: 50,
            wptCaptureMode: true)
        XCTAssertEqual(shift2.width, -29, accuracy: 1e-9)
        XCTAssertEqual(shift2.height, 0, accuracy: 1e-9)
    }

    /// safe-002's child bands: 2px dotted borders + 5px margins (the
    /// 65px declared size is added by the caller).
    private func borderedChildMargins() -> [IRProperty] {
        var props: [IRProperty] = []
        for side in ["Top", "Right", "Bottom", "Left"] {
            // 2px dotted purple border, live wire shapes.
            props.append(IRProperty(type: "Border\(side)Width", data: .object(["px": .double(2)])))
            props.append(IRProperty(type: "Border\(side)Style", data: .string("DOTTED")))
            // 5px margin on every side.
            props.append(IRProperty(type: "Margin\(side)", data: .object(["px": .double(5)])))
        }
        return props
    }
}
