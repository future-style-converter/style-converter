//
//  DecorationSkipSpacesTests.swift
//  StyleConverterRuntimeTests — applier campaign wave 47, lane Z7.
//
//  Pins for css-text-decor-4 §2.6 `text-decoration-skip-spaces` (initial
//  `start end`): the DecorationSkipSpaces splitter and its wiring into
//  DecorationMetrics.segments. Twin of the Compose suite's
//  DecorationSkipSpacesTest (change one, change both).
//
//  The character inventory below is LIFTED from WPT
//  text-decoration-skip-spaces-001.html — the exact spacers Chromium
//  refuses to underline at line edges in the frozen ref
//  (tools/wpt/refs/9b54…/css-text-decor/text-decoration-skip-spaces-001
//  .png: blue band spans only "ABCDEF").
//

import XCTest
@testable import StyleConverterRuntime

final class DecorationSkipSpacesTests: XCTestCase {

    // MARK: - the spacer set

    /// Every character class the WPT -001 source packs around "ABCDEF"
    /// must classify as a spacer — they are all Unicode Zs (plus the
    /// TAB/ZWSP carve-ins documented in the file header).
    func testSpacerSetMatchesWptInventory() {
        // U+2000…U+200A en quad … hair space, U+205F MMSP, U+3000
        // ideographic, U+1680 ogham, NBSP, plain space, TAB, ZWSP.
        let spacers: [UInt32] = [0x0020, 0x00A0, 0x1680, 0x2000, 0x2001,
                                 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
                                 0x2007, 0x2008, 0x2009, 0x200A, 0x202F,
                                 0x205F, 0x3000, 0x0009, 0x200B]
        for v in spacers {
            XCTAssertTrue(DecorationSkipSpaces.isSpacer(Unicode.Scalar(v)!),
                          "U+\(String(v, radix: 16)) must be a spacer")
        }
        // Ink-bearing characters must NOT classify — including the
        // ogham-adjacent letters the underline must still cover.
        for ch: Unicode.Scalar in ["A", "F", ".", "ß", "字"] {
            XCTAssertFalse(DecorationSkipSpaces.isSpacer(ch),
                           "\(ch) is glyph ink, not a spacer")
        }
    }

    // MARK: - the splitter

    /// Edge runs peel off; the mid-line spacer stays in the core (that
    /// is what keeps skip-spaces-002/003/004 — mid-line spacers keep
    /// their underline — passing).
    func testSplitTrimsEdgesOnly() {
        let (lead, core, trail) = DecorationSkipSpaces.split("\u{2003}\u{00A0} AB CD \u{00A0}\u{1680}")
        XCTAssertEqual(String(lead), "\u{2003}\u{00A0} ")
        XCTAssertEqual(String(core), "AB CD")
        XCTAssertEqual(String(trail), " \u{00A0}\u{1680}")
    }

    /// No edge spacers → identity split (empty lead/trail, core == line).
    func testSplitIdentityWithoutEdgeSpacers() {
        let (lead, core, trail) = DecorationSkipSpaces.split("Grumpy wizards vex 0123")
        XCTAssertTrue(lead.isEmpty)
        XCTAssertEqual(String(core), "Grumpy wizards vex 0123")
        XCTAssertTrue(trail.isEmpty)
    }

    /// A spacers-only line yields an empty core — the segments() caller
    /// then paints NOTHING on that line (Chromium parity on -001's two
    /// wrapped spacers-only lines).
    func testSplitAllSpacersYieldsEmptyCore() {
        let (lead, core, trail) = DecorationSkipSpaces.split("\u{2000}\u{2001} \u{3000}")
        XCTAssertEqual(String(lead), "\u{2000}\u{2001} \u{3000}")
        XCTAssertTrue(core.isEmpty)
        XCTAssertTrue(trail.isEmpty)
        // Degenerate: the empty line splits into three empties.
        let empty = DecorationSkipSpaces.split("")
        XCTAssertTrue(empty.core.isEmpty && empty.lead.isEmpty && empty.trail.isEmpty)
    }

    // MARK: - segments() wiring

    /// The -001 shape: [spacers-only, spacers+text+spacers, spacers-only]
    /// → exactly ONE segment, whose ink is inset by the lead advance and
    /// spans only the core, while `width` (the alignment extent) stays
    /// the full line advance.
    func testSegmentsSkipSpacerEdges() {
        // 10pt-per-character fake metric (GreedyLineBreaker test
        // pattern) — advance-additive, like the real measurer over
        // spacer boundaries.
        let measure: (String) -> CGFloat = { CGFloat($0.count) * 10 }
        let segs = DecorationMetrics.segments(
            lines: ["\u{2000}\u{2001}\u{2002}",          // spacers-only → no band
                    "\u{2003}\u{00A0}ABCDEF \u{00A0}",   // lead 2, core 6, trail 2
                    "\u{3000}\u{3000}"],                 // spacers-only → no band
            fontSizePx: 32,
            measure: measure)
        XCTAssertEqual(segs.map(\.index), [1],
                       "spacers-only lines paint no decoration")
        // Full line advance (10 chars × 10) is still the frame extent…
        XCTAssertEqual(segs[0].width, 100)
        // …but the ink is inset past the 2-char lead and spans the
        // 6-char core only.
        XCTAssertEqual(segs[0].inkLeadInset, 20)
        XCTAssertEqual(segs[0].inkWidth, 60)
    }

    /// Byte-stability pin: a line WITHOUT edge spacers must come back
    /// EXACTLY as the pre-wave-47 shape — full width, zero inset,
    /// inkWidth == width — through the no-re-measure fast path (this is
    /// what holds the committed 066/067 typography baselines and every
    /// pinned Segment construction).
    func testSegmentsIdentityWithoutEdgeSpacers() {
        let measure: (String) -> CGFloat = { CGFloat($0.count) * 10 }
        let segs = DecorationMetrics.segments(
            lines: ["abc", "", "de f"], fontSizePx: 32, measure: measure)
        // Same indices/widths the wave-5 pin (testOverlineSegmentGeometry)
        // expects: empties skipped, index preserved.
        XCTAssertEqual(segs.map(\.index), [0, 2])
        XCTAssertEqual(segs.map(\.width), [30, 40])
        XCTAssertEqual(segs.map(\.inkLeadInset), [0, 0])
        XCTAssertEqual(segs.map(\.inkWidth), [30, 40],
                       "mid-line space keeps full-width decoration")
        // The defaulted initializer equals the computed segment — the
        // Equatable identity older pinned tests rely on.
        XCTAssertEqual(segs[0],
                       DecorationMetrics.Segment(index: 0, width: 30, thickness: 3))
    }
}
