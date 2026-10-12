//
//  NormalLineBoxTests.swift — wave 55 (lane L1, NB-ios): pins P1–P8 of NormalLineBox (wave-55 plan §2 L1,
//  brief hyphen-ios-linebox.md §4). Catalyst cannot see the defect (integral UIFont metrics), so P1–P5 are
//  arithmetic on explicit hhea metrics (as LineBoxPinTests); P6 pins face resolution; P7 / P8 are verbatim wire.
//

// XCTest: the Catalyst test host.
import XCTest
// SwiftUI: Font.Design, the generic-family input of the face probe.
import SwiftUI
// @testable: NormalLineBox and its collaborators are internal.
@testable import StyleConverterRuntime

/// Pins the Blink `line-height: normal` box and the guards that scope it.
final class NormalLineBoxTests: XCTestCase {

    /// Blink's box from hhea units (ascender, signed descender, gap) at `px`.
    private func blink(_ asc: CGFloat, _ desc: CGFloat, _ gap: CGFloat, px: CGFloat) -> CGFloat {
        // Each term scaled as CoreText does (units / 2048 × size), then the rule.
        NormalLineBox.blinkLineSpacing(ascender: asc / 2048 * px, descender: desc / 2048 * px, leading: gap / 2048 * px)
    }

    /// The seam-1 call with explicit flags on the default design at 16 px.
    private func used(_ effective: CGFloat?, _ normal: Bool, _ exact: Bool, _ wpt: Bool, _ horizontal: Bool,
                      design: Font.Design = .default, face: String? = nil) -> CGFloat? {
        // Every argument the renderer passes, in the renderer's order.
        NormalLineBox.usedLineHeight(effective: effective, declaredNormal: normal, lineCountIsExact: exact, wptCapture: wpt,
                                     horizontalWritingMode: horizontal, fontSizePx: 16, design: design, faceName: face)
    }

    /// The oracle: Blink's rounding of the face faceMetrics resolves (Inter registered or not, one face).
    private func expectedBox(_ design: Font.Design, _ face: String? = nil) -> CGFloat {
        // The same resolution the unit uses.
        let m = NormalLineBox.faceMetrics(fontSizePx: 16, design: design, faceName: face)
        // Blink's three rounded terms.
        return NormalLineBox.blinkLineSpacing(ascender: m.ascender, descender: m.descender, leading: m.leading)
    }

    /// A verbatim per-test IR `properties` array, decoded like the harness does.
    private func wire(_ json: String) throws -> [IRProperty] {
        // The same Decodable the capture pipeline reads the wire with.
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// P1 — DejaVu Sans Mono 16 (== Menlo, hhea 1901 / −483 / 0): 15 + 4 = the ref's 19-px pitch (114 = 6 × 19).
    func testP1DejaVu16IsTheRefNineteenPixelPitch() {
        // The anchor every other pin is read against.
        XCTAssertEqual(blink(1901, -483, 0, px: 16), 19)
    }

    /// P2 — each term rounds ALONE: Arial 92 (1854 / −434 / 67) is the ref's 105 (sum: 106); DejaVu 20 is 24 (sum: 23).
    func testP2EachTermRoundsAloneNeverTheSum() {
        // text-decoration-dotted-001's ref divs advance 105 + the 46-px margin.
        XCTAssertEqual(blink(1854, -434, 67, px: 92), 105)
        // all-prop-001's monospace 20 px: 18.56 + 4.72 → 19 + 5.
        XCTAssertEqual(blink(1901, -483, 0, px: 20), 24)
    }

    /// P3 — lroundf: an exact half rounds AWAY from zero, never to even.
    func testP3HalvesRoundAwayFromZero() {
        // 14.5 → 15 and |−4.5| → 5 (banker's rounding would give 14 + 4).
        XCTAssertEqual(NormalLineBox.blinkLineSpacing(ascender: 14.5, descender: -4.5, leading: 0), 20)
    }

    /// P4 — Blink's box needs all four guards; each one alone declines.
    func testP4BlinkBoxOnlyUnderAllFourGuards() {
        // All four hold → the face's Blink box.
        XCTAssertEqual(used(nil, true, true, true, true), expectedBox(.default))
        // Not the author's `normal` → natural metrics, as today.
        XCTAssertNil(used(nil, false, true, true, true))
        // A guessed line count must not pin (the wave-30 ceil + leading defect).
        XCTAssertNil(used(nil, true, false, true, true))
        // Product path and the 327-pair baselines: unchanged.
        XCTAssertNil(used(nil, true, true, false, true))
        // A vertical run (plan D22): the horizontal line count says nothing.
        XCTAssertNil(used(nil, true, true, true, false))
    }

    /// P5 — identity for every declared / calibrated run under all 16 flag sets (37.5 is no Blink box).
    func testP5EffectiveValueIsReturnedVerbatim() {
        // The four flags are the loop counter's four bits.
        for bits in 0..<16 {
            // A non-nil effective wins under every combination.
            XCTAssertEqual(used(37.5, bits & 1 != 0, bits & 2 != 0, bits & 4 != 0, bits & 8 != 0), 37.5, "flags \(bits)")
        }
    }

    /// P6 — faceMetrics and LineBoxMetrics.contentHeight resolve the SAME face per no-face design (box and split
    /// agree): contentHeight is that UIFont's lineHeight, faceMetrics its triple (Catalyst's lineHeight ≠ the sum).
    func testP6FaceResolutionMatchesLineBoxMetrics() {
        // The designs the corpus renders when no document face is declared.
        for design in [Font.Design.default, .monospaced, .serif] {
            // The face contentHeight names: registered Inter (default), else the system face; nil = Inter's table.
            let f = design == .default ? UIFont(name: "Inter", size: 16) : UIFont.systemFont(ofSize: 16)
            // faceMetrics' triple for the design.
            let m = NormalLineBox.faceMetrics(fontSizePx: 16, design: design)
            // contentHeight measured that face (its lineHeight), or Inter's table when unregistered.
            XCTAssertEqual(LineBoxMetrics.contentHeight(fontSizePx: 16, design: design),
                           f?.lineHeight ?? 16 * LineBoxMetrics.interContentRatio, "\(design)")
            // …and faceMetrics read the same face (or the same table: 15.5 / −3.859375 / 0).
            XCTAssertEqual([m.ascender, m.descender, m.leading],
                           f.map { [$0.ascender, $0.descender, $0.leading] } ?? [15.5, -3.859375, 0], "\(design)")
        }
    }

    /// P7 — the inputs seam 1 hands usedLineHeight on hyphenate-character-001's host `__1-177`, verbatim (input pin).
    func testP7VerbatimHostInputs() throws {
        // `font: 16px monospace`, width 4.5ch, hyphens manual, character "".
        let props = try wire(#"""
        [{"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"Width","data":{"type":"length","original":{"v":4.5,"u":"CH"}}},{"type":"Hyphens","data":"MANUAL"},{"type":"HyphenateCharacter","data":{"type":"string","value":""}}]
        """#)
        // The TextConfig PlaceholderLabel reads; CSS 2.1 §15.8's reset arrives.
        let text = StyleBuilder.build(from: props).text
        // Declared `normal`, on the generic monospace design (the pin adds DejaVu).
        XCTAssertTrue(text.lineHeightIsNormal); XCTAssertEqual(text.fontDesign, .monospaced)
        // Today's input under capture: `.natural` → nil, no line box at all (unpinned).
        let effective = ComponentRenderer.effectiveLineHeight(
            declared: text.lineHeight, declaredNormal: true, fontSizePx: 16, wptCaptureMode: true); XCTAssertNil(effective)
        // The run's own writing-mode flag (none declared → horizontal).
        let horizontal = WritingModeExtractor.extract(from: props)?.isVertical != true
        // Seam 1 on the pre-broken (exact) run: Blink's box of the rendered face.
        XCTAssertEqual(used(effective, text.lineHeightIsNormal, true, true, horizontal, design: text.fontDesign,
                            face: text.fontFaceName), expectedBox(.monospaced, text.fontFaceName))
    }

    /// P8 (plan D22) — hyphens-vertical-001's `__2-320`, verbatim: a vertical space-less run the whitespace
    /// proxy calls ONE exact line while TextKit draws three; only the horizontal guard declines it.
    func testP8VerticalRunIsDeclinedByTheHorizontalGuard() throws {
        // The verbatim property list (vertical-rl, width 3em, height 9ch).
        let props = try wire(#"""
        [{"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"WritingMode","data":"VERTICAL_RL"},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":1}},{"type":"BorderBottomWidth","data":{"px":1}},{"type":"BorderLeftWidth","data":{"px":1}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}},{"type":"BorderRightColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}},{"type":"MarginTop","data":{"px":10}},{"type":"MarginRight","data":{"px":10}},{"type":"MarginBottom","data":{"px":10}},{"type":"MarginLeft","data":{"px":10}},{"type":"PaddingTop","data":{"px":2}},{"type":"PaddingRight","data":{"px":2}},{"type":"PaddingBottom","data":{"px":2}},{"type":"PaddingLeft","data":{"px":2}},{"type":"Hyphens","data":"MANUAL"},{"type":"Width","data":{"type":"length","original":{"v":3,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":9,"u":"CH"}}}]
        """#)
        // Its TextConfig, and PlaceholderLabel's flag (the :3220 call site).
        let text = StyleBuilder.build(from: props).text
        // WritingModeExtractor over the run's list, as the call site reads it.
        let horizontal = WritingModeExtractor.extract(from: props)?.isVertical != true
        // The verbatim text `hyphen` U+00AD `ation` has no whitespace: one exact line.
        let single = !"hyphen\u{AD}ation".contains { $0.isWhitespace }
        // Logged for the lane note (the census asserts these, never derives them).
        print("NB-ios P8 hyphens-vertical-001 __2-320 horizontal=\(horizontal) singleLineText=\(single) "
              + "lineCountIsExact=\(single) renderedLineCount=\(LineBoxMetrics.renderedLineCount("hyphen\u{AD}ation"))")
        // The wire is vertical, and the proxy really says one exact line.
        XCTAssertFalse(horizontal); XCTAssertTrue(single)
        // With the run's own vertical flag: declined → natural metrics, unpinned.
        XCTAssertNil(used(nil, text.lineHeightIsNormal, single, true, horizontal, design: text.fontDesign, face: text.fontFaceName))
        // The same call as if horizontal: a box (one 19-px line on device).
        XCTAssertNotNil(used(nil, text.lineHeightIsNormal, single, true, true, design: text.fontDesign, face: text.fontFaceName))
    }

    /// The brief's five SAME controls: logs PlaceholderLabel's line-count inputs per declared-`normal` run, read
    /// VERBATIM from `$TEST_RUNNER_NB_IOS_PER_TEST_IR/<section>/per-test-ir/` (skipped when unset: runs are local).
    func testSameControlsLogTheirLineCountInputs() throws {
        // The run's sections directory, or a skip (CI has no runs).
        guard let root = ProcessInfo.processInfo.environment["NB_IOS_PER_TEST_IR"] else { throw XCTSkip("no run") }
        // section and `__`-joined stem of each control (wpt__<section>__<stem>.json).
        for (sec, stem) in [("css-cascade", "all-prop-001"), ("css-tables", "anonymous-table-ws-001"),
                            ("css-counter-styles", "counter-style-at-rule__disclosure-styles"),
                            ("css-text", "boundary-shaping__boundary-shaping-010"), ("css-text", "hyphens__hyphens-vertical-001")] {
            // Decoded as the harness does (the composed tree), walked depth-first with the iOS inherited-text channel.
            var stack = try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: URL(fileURLWithPath:
                "\(root)/\(sec)/per-test-ir/wpt__\(sec)__\(stem).json"))).components.map { ($0, [IRProperty]()) }, runs = 0
            // Every component of the document.
            while let (c, inherited) = stack.popLast() {
                // `all` resets FIRST (GlobalExtractor — the renderer's order), then own over inherited.
                let r = GlobalExtractor.applyingAllReset(own: c.properties, inherited: inherited)
                // InheritedText's merge of the reset lists.
                let merged = InheritedText.merge(own: r.own, inherited: r.inherited)
                // Children inherit the inheritable subset of this merged list.
                stack += (c.children ?? []).map { ($0, InheritedText.inheritable(from: merged)) }
                // Only text runs whose used LineHeight is the declared keyword.
                guard let t = c.text, !t.allSatisfy(\.isWhitespace), LineHeightNormal.isDeclaredNormal(merged) else { continue }
                // The label's TextConfig and display string.
                let x = StyleBuilder.build(from: merged).text, shown = SoftHyphenPolicy.displayString(t, mode: x.hyphensMode)
                // The horizontal flag and the whitespace proxy.
                let h = WritingModeExtractor.extract(from: merged)?.isVertical != true, single = !shown.contains { $0.isWhitespace }
                // The greedy pre-break's precondition (no dictionary: none of the five is `hyphens: auto`).
                let pre = !x.noWrap && !x.preservesSpaces && SoftHyphenPolicy.admitsPreBreak(shown, dictionary: false, horizontal: h)
                // PlaceholderLabel's lineCountIsExact with a known wrap width (no upright plan here).
                let exact = single || x.noWrap || pre || !DecorationOps.hasSoftWrapOpportunity(shown)
                // Hard lines of the string before any pre-break (the device draws one line each).
                let n = LineBoxMetrics.renderedLineCount(shown, clampLimit: x.lineClampLimit); runs += 1
                // The log line the lane note records.
                print("NB-ios SAME \(stem) \(c.id) horizontal=\(h) singleLineText=\(single) preBreak=\(pre) lineCountIsExact=\(exact) renderedLineCount=\(n)")
                // The census's reading: one exact line (a vertical one is then declined by the guard).
                XCTAssertEqual(n, 1, c.id); XCTAssertTrue(exact, c.id)
            }
            // Per document: all-prop-001 logs 0 (its `all: initial` drops the declared `normal`).
            print("NB-ios SAME \(stem): \(runs) declared-normal run(s)")
        }
    }
}
