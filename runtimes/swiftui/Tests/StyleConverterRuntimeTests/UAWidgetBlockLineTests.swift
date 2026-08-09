//
//  UAWidgetBlockLineTests.swift
//  StyleConverterRuntimeTests — wave-38 lane N1.
//
//  Pins for the BLOCK-context UA widget line box. Kotlin twin:
//  runtimes/compose/.../layout/UAWidgetBlockLineTest.kt — identical table,
//  identical chain expectations; the two suites are what keeps the natives
//  locked to the same geometry.
//

import XCTest
@testable import StyleConverterRuntime

final class UAWidgetBlockLineTests: XCTestCase {

    // MARK: - The ref-probed table

    func testLineBoxTableMatchesTheAppearanceRevertRefSolve() {
        // Every entry is (lineHeightPx, topLeadPx) — see the source header
        // for the per-row ref band each one was solved against.
        func box(_ k: UAWidgetIntrinsics.Kind) -> [Double] {
            guard let b = UAWidgetBlockLine.lineBox(k) else { return [] }
            return [b.lineHeightPx, b.topLeadPx]
        }
        XCTAssertEqual(box(.textField), [22, 1])
        XCTAssertEqual(box(.buttonLike), [22, 1])
        XCTAssertEqual(box(.textarea), [40, 0])
        XCTAssertEqual(box(.range), [22, 0])
        XCTAssertEqual(box(.checkbox), [20, 3])
        XCTAssertEqual(box(.radio), [20, 3])
        XCTAssertEqual(box(.color), [27, 0])
        XCTAssertEqual(box(.select), [21, 2])
        XCTAssertEqual(box(.listbox), [70, 0])
        XCTAssertEqual(box(.meter), [20, 3])
        XCTAssertEqual(box(.progress), [20, 3])
    }

    func testTextAnchorHasNoBlockLineBox() {
        // A text-only <a> is not a widget replica — it already renders
        // through the text path with the block's own line metrics.
        XCTAssertNil(UAWidgetBlockLine.lineBox(.textAnchor))
    }

    // MARK: - The appearance-revert-001 chain, row by row

    /// Replays css-ui appearance-revert-001.tentative: 14 widgets, each the
    /// only inline-level box of its own <div>, no wire geometry at all. The
    /// expectations ARE the composed-WPT ref's painted band tops (content
    /// origin y16), so a table edit that breaks the ref fails here first.
    func testAppearanceRevertChainReproducesEveryRefBandTop() {
        // (tag, type, multiple, expected border-box top in the ref)
        let rows: [(String, String?, Bool, Double)] = [
            ("input", "text", false, 17),
            ("input", "search", false, 39),
            ("textarea", nil, false, 60),
            ("input", "button", false, 101),
            ("input", "submit", false, 123),
            ("input", "reset", false, 145),
            // Range: ref INK top is 168; the paint plan's thumb sits 2px
            // into the box, so the border box is 166.
            ("input", "range", false, 166),
            ("input", "checkbox", false, 191),
            ("input", "radio", false, 211),
            ("input", "color", false, 228),
            ("select", nil, false, 257),
            // <select multiple> — the listbox fork.
            ("select", nil, true, 276),
            // Meter/progress: ref INK tops 353/373 with the plan's 4px bar
            // inset → border boxes 349/369.
            ("meter", nil, false, 349),
            ("progress", nil, false, 369),
        ]
        // The body's content origin in the composed ref.
        var rowTop: Double = 16
        for (tag, typeAttr, multiple, expectedBoxTop) in rows {
            let kind = UAWidgetIntrinsics.kind(tag: tag, typeAttr: typeAttr, multiple: multiple)
            guard let lineBox = UAWidgetBlockLine.lineBox(kind),
                  let h = UAWidgetIntrinsics.spec(kind).fixedHpx else {
                return XCTFail("no block line box for \(tag)/\(typeAttr ?? "-")")
            }
            // No wire geometry on this test — the lead is the whole story.
            let lead = UAWidgetBlockLine.leadFor(tag: tag, typeAttr: typeAttr,
                                                 multiple: multiple, hasWireHeight: false,
                                                 wireMarginTopPx: 0, wireMarginBottomPx: 0)
            let top = lead?.topPx ?? 0
            let bottom = lead?.bottomPx ?? 0
            XCTAssertEqual(rowTop + top, expectedBoxTop,
                           "box top for \(tag)/\(typeAttr ?? "-")")
            // The renderer's advance must BE the line box, or the next row
            // starts in the wrong place and the drift compounds.
            XCTAssertEqual(top + h + bottom, lineBox.lineHeightPx,
                           "advance for \(tag)/\(typeAttr ?? "-")")
            rowTop += lineBox.lineHeightPx
        }
        // Last row top + its line box = the stack's bottom (progress row
        // 366 + 20): a whole-chain regression tripwire.
        XCTAssertEqual(rowTop, 386)
    }

    // MARK: - The gates

    func testWireHeightSuppressesTheLead() {
        // A post-load-extracted element already carries its used box; the
        // renderer pins it, so padding the content would squeeze the
        // replica instead of leading it (css-ui accent-color-visited is
        // byte-exact today and must stay so).
        XCTAssertNil(UAWidgetBlockLine.leadFor(tag: "input", typeAttr: "checkbox",
                                               multiple: false, hasWireHeight: true,
                                               wireMarginTopPx: 3, wireMarginBottomPx: 3))
    }

    func testVerticalWritingModeSuppressesTheLead() {
        // The whole table is a horizontal-tb §10.8 solve; under a vertical
        // writing mode the block axis is x, so a top/bottom pad displaces
        // the replica instead of leading it. Device-measured on iOS
        // w38-n1: without this gate the four css-writing-modes
        // forms/{checkbox,radio}-appearance-native-vertical-* rows moved
        // 0.7795→0.7665 and 0.7796→0.7630 against the ref.
        XCTAssertNil(UAWidgetBlockLine.leadFor(tag: "input", typeAttr: "checkbox",
                                               multiple: false, hasWireHeight: false,
                                               wireMarginTopPx: 0, wireMarginBottomPx: 0,
                                               horizontalWritingMode: false))
        // …and the horizontal default is untouched: the SAME call with the
        // gate open still returns the ref-probed 3px lead.
        let horizontal = UAWidgetBlockLine.leadFor(tag: "input", typeAttr: "checkbox",
                                                   multiple: false, hasWireHeight: false,
                                                   wireMarginTopPx: 0, wireMarginBottomPx: 0,
                                                   horizontalWritingMode: true)
        XCTAssertEqual(horizontal?.topPx, 3)
    }

    func testTheAxisGateDefaultsOpenSoEveryFrozenCallSiteIsUnchanged() {
        // The parameter is defaulted precisely so the wave-38 chain test
        // above (and any caller that cannot read a writing mode) keeps the
        // horizontal solve. This pins that default rather than trusting it.
        XCTAssertEqual(
            UAWidgetBlockLine.leadFor(tag: "input", typeAttr: "text", multiple: false,
                                      hasWireHeight: false, wireMarginTopPx: 0,
                                      wireMarginBottomPx: 0, horizontalWritingMode: true),
            UAWidgetBlockLine.leadFor(tag: "input", typeAttr: "text", multiple: false,
                                      hasWireHeight: false, wireMarginTopPx: 0,
                                      wireMarginBottomPx: 0))
    }

    func testWireMarginTopIsSubtractedNotStacked() {
        // Blink's UA `margin: 3px` on a checkbox rides the wire whenever
        // computed styles were extracted. The table's 3px topLead is the
        // SAME margin, so the lead must fall to zero rather than double it.
        let lead = UAWidgetBlockLine.leadFor(tag: "input", typeAttr: "checkbox",
                                             multiple: false, hasWireHeight: false,
                                             wireMarginTopPx: 3, wireMarginBottomPx: 3)
        XCTAssertEqual(lead?.topPx, 0)
        // 20 line box − 3 wire top − 0 lead − 13 box − 3 wire bottom = 1.
        XCTAssertEqual(lead?.bottomPx, 1)
    }

    func testAWireMarginLargerThanTheLeadClampsAtZero() {
        // The element's own ascent then already exceeds the strut, so no
        // extra space is owed — and a negative pad would be nonsense.
        // 40 already overruns the 20px line box → both pads clamp to 0 →
        // no lead at all (the frozen composition).
        XCTAssertNil(UAWidgetBlockLine.leadFor(tag: "progress", typeAttr: nil,
                                               multiple: false, hasWireHeight: false,
                                               wireMarginTopPx: 40, wireMarginBottomPx: 0))
    }

    func testNonWidgetInputTypesAreVetoedRatherThanFoldedToTextField() {
        // UAWidgetIntrinsics.kind maps these to .textField (the HTML
        // invalid-type default), whose 21px control box does not describe
        // them at all — appearance-auto-input-non-widget-001's assertion.
        for t in ["hidden", "image", "file"] {
            XCTAssertNil(UAWidgetBlockLine.leadFor(tag: "input", typeAttr: t,
                                                   multiple: false, hasWireHeight: false,
                                                   wireMarginTopPx: 0, wireMarginBottomPx: 0), t)
        }
    }

    func testAnchorsAndUnknownTagsGetNoLead() {
        // <a> resolves to .textAnchor (no line box entry); an unknown tag
        // folds to the same harmless default rather than a guessed box.
        XCTAssertNil(UAWidgetBlockLine.leadFor(tag: "a", typeAttr: nil, multiple: false,
                                               hasWireHeight: false,
                                               wireMarginTopPx: 0, wireMarginBottomPx: 0))
        XCTAssertNil(UAWidgetBlockLine.leadFor(tag: "div", typeAttr: nil, multiple: false,
                                               hasWireHeight: false,
                                               wireMarginTopPx: 0, wireMarginBottomPx: 0))
    }

    func testExactFitKindsReportNoLead() {
        // .color and .listbox are the two kinds whose line box EQUALS their
        // border box — a 0/0 lead, reported as nil so the renderer keeps the
        // frozen, unpadded composition on those rows.
        XCTAssertNil(UAWidgetBlockLine.leadFor(tag: "input", typeAttr: "color",
                                               multiple: false, hasWireHeight: false,
                                               wireMarginTopPx: 0, wireMarginBottomPx: 0))
        XCTAssertNil(UAWidgetBlockLine.leadFor(tag: "select", typeAttr: nil,
                                               multiple: true, hasWireHeight: false,
                                               wireMarginTopPx: 0, wireMarginBottomPx: 0))
    }
}
