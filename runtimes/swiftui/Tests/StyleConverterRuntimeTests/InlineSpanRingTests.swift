import XCTest
@testable import StyleConverterRuntime

/// Wave 47, lane Z6 — the STYLED-SPAN RING's pure pins
/// (StyleEngine/typography/inline/InlineSpanRing.swift): admission
/// (which member styling may ride the fold, which refuses with the
/// property named) and the range alignment through the label's string
/// surgery (soft-hyphen strip + the greedy pre-break's newline / space /
/// hyphen ops). Wire shapes below are the exact payloads wave46-final's
/// victims carry (block-ellipsis-004's span, inset-014's u), plus one
/// synthetic per admission rule. Byte-parallel with the Compose twin's
/// InlineSpanRingTest.kt.
final class InlineSpanRingTests: XCTestCase {

    // MARK: - Wire helpers

    /// The IR sRGB color leaf ({"srgb":{r,g,b[,a]}}).
    private func srgb(_ r: Double, _ g: Double, _ b: Double, _ a: Double? = nil) -> IRValue {
        var o: [String: IRValue] = ["r": .double(r), "g": .double(g), "b": .double(b)]
        if let a { o["a"] = .double(a) }
        return .object(["srgb": .object(o)])
    }

    private func admitted(_ admission: InlineSpanRing.Admission,
                          file: StaticString = #filePath, line: UInt = #line)
        -> (InlineSpanRing.Style, [String])? {
        guard case .admitted(let style, let losses) = admission else {
            XCTFail("expected admission, got \(admission)", file: file, line: line)
            return nil
        }
        return (style, losses)
    }

    private func refusal(_ admission: InlineSpanRing.Admission) -> String? {
        guard case .refused(let reason) = admission else { return nil }
        return reason
    }

    // MARK: - Admission

    func testColorWeightStyleAndEmSizeRideAsOneAttribution() {
        // block-ellipsis-004's span payload, verbatim shapes.
        let admission = InlineSpanRing.admit(
            tag: "span",
            properties: [
                IRProperty(type: "Color", data: srgb(0.5019607843137255, 0, 0.5019607843137255)),
                IRProperty(type: "FontWeight",
                           data: .object(["weight": .int(700), "original": .string("bold")])),
                IRProperty(type: "FontStyle", data: .string("italic")),
                IRProperty(type: "FontSize",
                           data: .object(["original": .object([
                               "type": .string("length"),
                               "original": .object(["v": .double(1.5), "u": .string("EM")]),
                           ])])),
            ],
            hostProperties: [])
        guard let (style, losses) = admitted(admission) else { return }
        XCTAssertEqual(style.fontWeight, 700)
        XCTAssertTrue(style.italic)
        XCTAssertEqual(style.fontSizeEm, 1.5)
        XCTAssertNil(style.fontSizePx)
        XCTAssertEqual(style.ink?.r ?? 0, 0.50196, accuracy: 1e-4)
        // No border longhands → nothing lost, nothing to log.
        XCTAssertTrue(losses.isEmpty)
    }

    func testFontSizeShapesPxPercentageKeywordAndUnresolvable() {
        // A DynamicValueResolver-resolved px wire.
        let px = InlineSpanRing.admit(
            tag: "span",
            properties: [IRProperty(type: "FontSize", data: .object(["px": .double(24)]))],
            hostProperties: [])
        XCTAssertEqual(admitted(px)?.0.fontSizePx, 24)
        // css-fonts-4 §2.4 percentage — a parent-relative factor.
        let pct = InlineSpanRing.admit(
            tag: "span",
            properties: [IRProperty(type: "FontSize",
                                    data: .object(["original": .object([
                                        "type": .string("percentage"),
                                        "value": .double(120)])]))],
            hostProperties: [])
        XCTAssertEqual(admitted(pct)?.0.fontSizeEm ?? 0, 1.2, accuracy: 1e-6)
        // The absolute keyword ladder (x-large = 24px — the shared table).
        let kw = InlineSpanRing.admit(
            tag: "span",
            properties: [IRProperty(type: "FontSize",
                                    data: .object(["original": .object([
                                        "type": .string("absolute"),
                                        "keyword": .string("x-large")])]))],
            hostProperties: [])
        XCTAssertEqual(admitted(kw)?.0.fontSizePx, 24)
        // An unresolvable unit (vw has no static base here) refuses.
        let vw = InlineSpanRing.admit(
            tag: "span",
            properties: [IRProperty(type: "FontSize",
                                    data: .object(["original": .object([
                                        "type": .string("length"),
                                        "original": .object(["v": .double(5), "u": .string("VW")])])]))],
            hostProperties: [])
        XCTAssertEqual(refusal(vw), "member-prop:FontSize-unresolved")
    }

    func testUTagSeedsUnderlineDecorationListAddsLineThroughOverlineRefuses() {
        // HTML rendering §15.3.3: `u { text-decoration: underline }`.
        let u = InlineSpanRing.admit(tag: "u", properties: [], hostProperties: [])
        XCTAssertEqual(admitted(u)?.0.underline, true)
        // A wire list adds line-through (css-text-decor-3 §2.1 ||),
        // in the wire's SHOUTY underscore spelling.
        let strike = InlineSpanRing.admit(
            tag: "span",
            properties: [IRProperty(type: "TextDecorationLine",
                                    data: .array([.string("LINE_THROUGH")]))],
            hostProperties: [])
        XCTAssertEqual(admitted(strike)?.0.lineThrough, true)
        // Per-range overline exists on neither platform — refuse, named.
        let over = InlineSpanRing.admit(
            tag: "span",
            properties: [IRProperty(type: "TextDecorationLine",
                                    data: .array([.string("overline")]))],
            hostProperties: [])
        XCTAssertEqual(refusal(over), "member-prop:TextDecorationLine:overline")
    }

    func testDecorationColorMustEqualTheEffectiveInk() {
        // inset-014's shape: decoration black over host-inherited black —
        // equal, so the segment-colored underline is exact.
        let equal = InlineSpanRing.admit(
            tag: "u",
            properties: [IRProperty(type: "TextDecorationColor", data: srgb(0, 0, 0))],
            hostProperties: [IRProperty(type: "Color", data: srgb(0, 0, 0))])
        XCTAssertNotNil(admitted(equal))
        // inset-011's shape: blue decoration over black text — the
        // built-in underline would paint the wrong color; refuse (kept
        // byte-parallel with Compose, whose spans CANNOT color it).
        let diverging = InlineSpanRing.admit(
            tag: "u",
            properties: [IRProperty(type: "TextDecorationColor", data: srgb(0, 0, 1))],
            hostProperties: [IRProperty(type: "Color", data: srgb(0, 0, 0))])
        XCTAssertEqual(refusal(diverging), "member-prop:TextDecorationColor-divergence")
        // Unknown effective ink (no member Color, no host Color) cannot
        // prove equality — refuse rather than guess.
        let unknown = InlineSpanRing.admit(
            tag: "u",
            properties: [IRProperty(type: "TextDecorationColor", data: srgb(0, 0, 0))],
            hostProperties: [])
        XCTAssertEqual(refusal(unknown), "member-prop:TextDecorationColor-divergence")
    }

    func testBordersAreAStatedLossStylelessBordersInertUnknownPropsRefuse() {
        // 004's 2px solid blue box: admitted, but REPORTED as a loss so
        // the seam logs the dropped ink (repo no-silent-fallthrough).
        let bordered = InlineSpanRing.admit(
            tag: "span",
            properties: [
                IRProperty(type: "BorderTopStyle", data: .string("SOLID")),
                IRProperty(type: "BorderTopWidth", data: .object(["px": .double(2)])),
                IRProperty(type: "BorderTopColor", data: srgb(0, 0, 1)),
            ],
            hostProperties: [])
        let losses = admitted(bordered)?.1 ?? []
        XCTAssertEqual(losses.count, 1)
        XCTAssertTrue(losses[0].hasPrefix("border-box-ink("))
        // Width without a style paints nothing (css-backgrounds-3 §3.2):
        // no loss to report.
        let styleless = InlineSpanRing.admit(
            tag: "span",
            properties: [
                IRProperty(type: "BorderTopWidth", data: .object(["px": .double(2)])),
                IRProperty(type: "BorderTopColor", data: srgb(0, 0, 1)),
            ],
            hostProperties: [])
        XCTAssertEqual(admitted(styleless)?.1.isEmpty, true)
        // Anything outside the ring refuses with the type named (032's
        // pre-wrap hangs span — the hanging-whitespace ring's wall).
        let ws = InlineSpanRing.admit(
            tag: "span",
            properties: [IRProperty(type: "WhiteSpace", data: .string("PRE_WRAP"))],
            hostProperties: [])
        XCTAssertEqual(refusal(ws), "member-prop:WhiteSpace")
    }

    // MARK: - Alignment

    func testAlignmentIdentityWhenNoSurgeryRan() {
        let map = InlineSpanRing.alignment(original: "Line 1\nLine 2",
                                           transformed: "Line 1\nLine 2")
        XCTAssertEqual(map?[0], 0)
        XCTAssertEqual(map?[7], 7)
        XCTAssertEqual(map?[13], 13)
    }

    func testAlignmentSpaceToNewlineReplacementKeepsRangesExact() {
        // The greedy pre-break rewrites the break space as '\n'.
        let map = InlineSpanRing.alignment(original: "aaa bbb", transformed: "aaa\nbbb")
        XCTAssertEqual(map?[4], 4)
        XCTAssertEqual(map?[7], 7)
    }

    func testAlignmentSoftHyphenDeletionShiftsRangesLeft() {
        // SoftHyphenPolicy (`hyphens: none`) deletes the U+00AD.
        let map = InlineSpanRing.alignment(original: "ab\u{00AD}cd", transformed: "abcd")
        XCTAssertEqual(map?[3], 2)
        XCTAssertEqual(map?[5], 4)
    }

    func testAlignmentHyphenInsertionAndSpaceCollapseBothAlign() {
        // A taken soft-hyphen break: shy deleted, U+2010 + '\n' inserted.
        let hyphen = InlineSpanRing.alignment(original: "high\u{00AD}way",
                                              transformed: "high\u{2010}\nway")
        XCTAssertEqual(hyphen?[5], 6)
        // The breaker's collapse-split drops a doubled space.
        let collapse = InlineSpanRing.alignment(original: "a  b", transformed: "a b")
        XCTAssertEqual(collapse?[3], 2)
    }

    func testAlignmentUnmodeledSurgeryAnswersNil() {
        // A case rewrite is outside the op set — the label then renders
        // the fold un-styled (degraded style, never wrong glyphs).
        XCTAssertNil(InlineSpanRing.alignment(original: "abc", transformed: "ABC"))
    }
}
