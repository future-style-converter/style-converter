//
//  SkepticUAControlFontProbeTests.swift
//  Wave-22 SKEPTIC probe for lane INK (A-RC2). The lane's headline claim
//  is that "every widget box size is identical on Android and iOS by
//  construction" because both natives size labels from the SAME pure
//  UAControlFontMetrics table. That holds for the ASCII corpus, but the
//  claim is about ALL wire labels — UAWidgetsResolve reads `label` from
//  `component._text` / the `value` / `alt` attrs, i.e. arbitrary Unicode
//  off the IR. These probes pin the string-iteration contract that makes
//  the claim true beyond ASCII. The Kotlin twin
//  (SkepticUAControlFontProbeTest.kt) asserts the IDENTICAL numbers.
//
//  The pinned unit is the UTF-16 CODE UNIT: Kotlin's `for (ch in text)`
//  walks UTF-16 units, so this side must walk `text.utf16` too. Walking
//  Swift Characters (grapheme clusters) instead makes a non-BMP or
//  combining label measure narrower on iOS than on Android.
//

import XCTest
@testable import StyleConverterRuntime

final class SkepticUAControlFontProbeTests: XCTestCase {

    private let px = UAControlFontMetrics.CONTROL_FONT_PX

    /// One fallback advance ('n'-width, 1139/2048 em) at the control size.
    private var fb: CGFloat { 1139.0 * px / 2048.0 }

    func testEmptyLabelMeasuresZeroAndStillYieldsTheChromeOnlyButtonBox() {
        // No glyphs → no advance. Guards against a fallback that fires
        // on the empty string and silently widens every unlabelled box.
        XCTAssertEqual(UAControlFontMetrics.advance("", px), 0, accuracy: 1e-6)
        // A label-driven box with no label is pure chrome: 2 × 8.
        let size = UAWidgetsGeometry.intrinsicSize(
            spec: UAWidgetsGeometry.Spec(kind: .button, label: ""),
            measure: { UAControlFontMetrics.advance($0, self.px) })
        XCTAssertEqual(size.w, 2 * UAWidgetsGeometry.BUTTON_PAD_X, accuracy: 1e-6)
        XCTAssertEqual(size.h, UAWidgetsGeometry.CONTROL_H, accuracy: 1e-6)
    }

    func testControlCharactersBelowTheASCIIBandTakeTheDocumentedFallback() {
        // "\n" (0x0A) and "\t" (0x09) index NEGATIVE into the table. The
        // lookup must clamp to the fallback, never read out of bounds
        // and never return zero width.
        XCTAssertEqual(UAControlFontMetrics.charAdvance("\n", px), fb, accuracy: 1e-6)
        XCTAssertEqual(UAControlFontMetrics.charAdvance("\t", px), fb, accuracy: 1e-6)
        // DEL (0x7F) is one past the pinned band's last entry "~".
        XCTAssertEqual(UAControlFontMetrics.charAdvance("\u{7F}", px), fb, accuracy: 1e-6)
        // "~" (0x7E) IS the last pinned entry — 1196 units, not the
        // fallback. This is the boundary the clamp must not swallow.
        XCTAssertEqual(UAControlFontMetrics.charAdvance("~", px),
                       1196.0 * px / 2048.0, accuracy: 1e-6)
    }

    func testNonASCIIBMPLabelFallsBackOncePerCodeUnit() {
        // "café" — "é" is U+00E9, one UTF-16 unit, outside the band.
        XCTAssertEqual(UAControlFontMetrics.advance("café", px),
                       UAControlFontMetrics.advance("caf", px) + fb, accuracy: 1e-6)
    }

    func testNonBMPLabelMeasuresPerUTF16Unit() {
        // U+1F600 GRINNING FACE is a surrogate PAIR: two UTF-16 units,
        // so Kotlin's loop takes the fallback TWICE. This side must
        // agree — walking Characters takes the fallback once and the
        // same wire label produces a box ~7.4px narrower on iOS.
        XCTAssertEqual(UAControlFontMetrics.advance("😀", px), 2 * fb, accuracy: 1e-6)
        // A combining sequence is likewise TWO units ("e" + U+0301).
        XCTAssertEqual(UAControlFontMetrics.advance("e\u{0301}", px),
                       1139.0 * px / 2048.0 + fb, accuracy: 1e-6)
        // CRLF is two units (Swift folds it into ONE Character).
        XCTAssertEqual(UAControlFontMetrics.advance("\r\n", px), 2 * fb, accuracy: 1e-6)
        // The monospace twin counts the same unit.
        XCTAssertEqual(UAControlFontMetrics.monoAdvance("😀", px),
                       2 * UAControlFontMetrics.MONO_ADVANCE_EM * px, accuracy: 1e-6)
    }

    func testHiddenInputsOccupyNoBoxAtAll() {
        // display:none per the UA sheet — the mount hook must get 0×0 so
        // the atom contributes nothing to the inline cursor.
        let size = UAWidgetsGeometry.intrinsicSize(
            spec: UAWidgetsGeometry.Spec(kind: .hidden),
            measure: { UAControlFontMetrics.advance($0, self.px) })
        XCTAssertEqual(size.w, 0, accuracy: 1e-6)
        XCTAssertEqual(size.h, 0, accuracy: 1e-6)
        // …and paints nothing.
        XCTAssertEqual(
            UAWidgetsGeometry.plan(spec: UAWidgetsGeometry.Spec(kind: .hidden),
                                   measure: { _ in 0 }).count, 0)
    }
}
