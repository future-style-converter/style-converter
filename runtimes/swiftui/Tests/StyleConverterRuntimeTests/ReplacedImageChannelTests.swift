//
//  ReplacedImageChannelTests.swift
//  wave-39 lane A2 — unit pins for the REPLACED-ELEMENT image channel.
//
//  Covers the pure and simulator-reachable halves:
//    * ReplacedBoxSizing — the CSS 2.1 §10.3.2 / §10.6.2 used-size table, the
//      one decision both natives must agree on to the pixel (the Kotlin twin
//      ReplacedImageChannelTest pins the identical rows);
//    * ReplacedBoxSizing.isDefinite — the LengthValue half of that agreement,
//      which is the piece that CAN drift (the two platforms read definiteness
//      off different representations of the same wire);
//    * ReplacedImageContent.isCandidate / fit / position — the identity gate
//      and the two css-images-3 §5.5 readings;
//    * DocumentImageRegistry's DECLINE behaviour — the half that matters most,
//      because a silent decline would paint a wrong picture with nothing in
//      any log.
//
//  The DECODE half is exercised for real by the device gate (a css-writing-
//  modes / css-grid section run against the frozen refs), not here: what a
//  unit test can prove about ImageIO is only that a non-image declines.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ReplacedImageChannelTests: XCTestCase {

    // ── ReplacedBoxSizing: the four §10.3.2 rows ────────────────────────────

    func testBothAxesDefiniteFillTheBoxTheStyleChainProduced() {
        XCTAssertEqual(
            ReplacedBoxSizing.mode(widthDefinite: true, heightDefinite: true, aspectRatio: 2),
            .fillBoth)
        // Ratio is irrelevant once both axes are declared — §10.3.2 rule 1
        // gives the used size straight from the declarations, and honouring a
        // ratio here would silently letterbox a box the author sized.
        XCTAssertEqual(
            ReplacedBoxSizing.mode(widthDefinite: true, heightDefinite: true, aspectRatio: nil),
            .fillBoth)
    }

    func testOneDeclaredAxisDerivesTheOtherFromTheIntrinsicRatio() {
        XCTAssertEqual(
            ReplacedBoxSizing.mode(widthDefinite: true, heightDefinite: false, aspectRatio: 2),
            .widthFillsRatioHeight)
        XCTAssertEqual(
            ReplacedBoxSizing.mode(widthDefinite: false, heightDefinite: true, aspectRatio: 2),
            .heightFillsRatioWidth)
    }

    func testNeitherAxisDeclaredUsesTheIntrinsicSize() {
        // The css-writing-modes img-intrinsic-size-contribution family's row:
        // the `<img>` carries no Width/Height at all, so the box must hug the
        // raster's own 200×100 — which is the whole assertion of those tests.
        XCTAssertEqual(
            ReplacedBoxSizing.mode(widthDefinite: false, heightDefinite: false, aspectRatio: 2),
            .intrinsic)
    }

    func testAnUnusableRatioDowngradesToIntrinsicNeverToFill() {
        // §10.3.2 rule 2 is conditioned on "has an intrinsic ratio"; without
        // one the used size falls to the intrinsic dimension. Falling through
        // to .fillBoth instead would stretch the raster across an axis nothing
        // declared — the silent distortion this table exists to prevent.
        for bad: Double? in [nil, 0, -1, .nan, .infinity] {
            XCTAssertEqual(
                ReplacedBoxSizing.mode(widthDefinite: true, heightDefinite: false, aspectRatio: bad),
                .intrinsic, "ratio=\(String(describing: bad))")
            XCTAssertEqual(
                ReplacedBoxSizing.mode(widthDefinite: false, heightDefinite: true, aspectRatio: bad),
                .intrinsic, "ratio=\(String(describing: bad))")
        }
    }

    // ── the definiteness reading (the piece that CAN drift from Kotlin) ────

    func testIsDefiniteMirrorsTheKotlinHasDefiniteSizeRows() {
        // Resolved absolute length ⇒ definite.
        XCTAssertTrue(ReplacedBoxSizing.isDefinite(.exact(px: 100)))
        // Percentage ⇒ definite: it resolves against the parent at layout
        // time, so the box has a used size even though the number is not
        // known yet. (Kotlin's twin says the same at its "percentage" row.)
        XCTAssertTrue(ReplacedBoxSizing.isDefinite(.relative(value: 50, unit: .percent, pxFallback: nil)))
        // `em`/`vw`/`calc` serialise with no px — the wire leaves them
        // unresolved BY DESIGN (CLAUDE.md's "null means runtime-dependent"),
        // and treating one as definite would size a box from a number nobody
        // computed.
        XCTAssertFalse(ReplacedBoxSizing.isDefinite(.relative(value: 4, unit: .em, pxFallback: nil)))
        XCTAssertFalse(ReplacedBoxSizing.isDefinite(.calc(expression: "100% - 10px")))
        XCTAssertFalse(ReplacedBoxSizing.isDefinite(.auto))
        XCTAssertFalse(ReplacedBoxSizing.isDefinite(.intrinsic(kind: .maxContent)))
        XCTAssertFalse(ReplacedBoxSizing.isDefinite(.none))
        XCTAssertFalse(ReplacedBoxSizing.isDefinite(.unknown))
        // An ABSENT declaration is the overwhelmingly common case (the
        // css-writing-modes `<img>` has neither axis) and must be indefinite.
        XCTAssertFalse(ReplacedBoxSizing.isDefinite(nil))
    }

    // ── the identity gate ──────────────────────────────────────────────────

    private func component(tag: String?, src: String?) -> IRComponent {
        IRComponent(
            id: "c", name: "c", properties: [],
            selectors: nil, media: nil, children: nil, slot: nil,
            text: nil, pseudos: nil,
            meta: IRMeta(sourceTag: tag,
                         attrs: src == nil ? nil : IRAttrs(src: src)))
    }

    func testIsCandidateNeedsBothAReplacedTagAndANonBlankSource() {
        for tag in ["img", "embed", "object", "video", "IMG"] {
            XCTAssertTrue(ReplacedImageContent.isCandidate(component(tag: tag, src: "css/s/a.png")), tag)
        }
        // A replaced tag with no source renders as its ALT TEXT in a browser —
        // which is the existing text/placeholder path. Claiming it here would
        // paint nothing where something was painted before.
        XCTAssertFalse(ReplacedImageContent.isCandidate(component(tag: "img", src: nil)))
        XCTAssertFalse(ReplacedImageContent.isCandidate(component(tag: "img", src: "   ")))
        // Non-replaced tags keep their normal content path. `input` is the
        // load-bearing one: it belongs to the UA-widget lane, whose mount
        // branch sits beside this one, and the two sets must stay disjoint.
        for tag in ["input", "div", "p", "select"] {
            XCTAssertFalse(ReplacedImageContent.isCandidate(component(tag: tag, src: "css/s/a.png")), tag)
        }
        XCTAssertFalse(ReplacedImageContent.isCandidate(component(tag: nil, src: "css/s/a.png")))
    }

    // ── object-fit / object-position readings ──────────────────────────────

    func testObjectFitDefaultsToFillAndReadsEveryKeyword() {
        // §5.5's initial value is `fill`; an element declaring nothing must
        // behave exactly as CSS says rather than as the platform's default.
        XCTAssertEqual(ReplacedImageContent.fit(of: []), .fill)
        let cases: [(String, ReplacedImageContent.Fit)] = [
            ("contain", .contain), ("cover", .cover), ("none", .none),
            ("scale-down", .scaleDown), ("fill", .fill),
        ]
        for (kw, want) in cases {
            let p = IRProperty(type: "ObjectFit", data: .string(kw))
            XCTAssertEqual(ReplacedImageContent.fit(of: [p]), want, kw)
        }
        // An unrecognised keyword falls to the initial rather than to a
        // platform guess — no silent fallthrough.
        XCTAssertEqual(
            ReplacedImageContent.fit(of: [IRProperty(type: "ObjectFit", data: .string("wat"))]),
            .fill)
    }

    func testObjectPositionBindsKeywordsToTheirOwnAxisInEitherOrder() {
        XCTAssertEqual(ReplacedImageContent.position(of: []), .center)
        // css-values-4 <position>: "bottom right" and "right bottom" are the
        // same position, so the axis binding cannot be positional.
        let bottomRight = IRProperty(type: "ObjectPosition", data: .object([
            "x": .string("bottom"), "y": .string("right"),
        ]))
        XCTAssertEqual(ReplacedImageContent.position(of: [bottomRight]),
                       Alignment(horizontal: .trailing, vertical: .bottom))
        let rightBottom = IRProperty(type: "ObjectPosition", data: .object([
            "x": .string("right"), "y": .string("bottom"),
        ]))
        XCTAssertEqual(ReplacedImageContent.position(of: [rightBottom]),
                       Alignment(horizontal: .trailing, vertical: .bottom))
        // The modern parser shape {"type":"keyword","value":"LEFT"} reads too.
        let left = IRProperty(type: "ObjectPosition", data: .object([
            "x": .object(["type": .string("keyword"), "value": .string("LEFT")]),
            "y": .object(["type": .string("keyword"), "value": .string("TOP")]),
        ]))
        XCTAssertEqual(ReplacedImageContent.position(of: [left]),
                       Alignment(horizontal: .leading, vertical: .top))
        // A percent/length offset falls back to centre — a DOCUMENTED limit
        // (SwiftUI's Alignment has no per-axis pixel channel here), not an
        // approximation that would move the raster outside its clip.
        let pct = IRProperty(type: "ObjectPosition", data: .object([
            "x": .object(["type": .string("percentage"), "percentage": .double(25)]),
            "y": .object(["type": .string("percentage"), "percentage": .double(75)]),
        ]))
        XCTAssertEqual(ReplacedImageContent.position(of: [pct]), .center)
    }

    // ── the registry's decline contract ────────────────────────────────────

    override func tearDown() {
        DocumentImageRegistry.shared.clear()
        super.tearDown()
    }

    func testAnUnconfiguredRegistryDeclinesEverySourceAndSaysSo() {
        DocumentImageRegistry.shared.clear()
        XCTAssertNil(DocumentImageRegistry.shared.resolve("css/s/a.png"))
        let r = DocumentImageRegistry.shared.lastReport
        XCTAssertEqual(r.requested, 1)
        XCTAssertEqual(r.decoded, 0)
        XCTAssertEqual(r.declined, ["css/s/a.png"])
    }

    func testABlankSourceIsNotARequestAtAll() {
        DocumentImageRegistry.shared.clear()
        XCTAssertNil(DocumentImageRegistry.shared.resolve(nil))
        XCTAssertNil(DocumentImageRegistry.shared.resolve(""))
        XCTAssertNil(DocumentImageRegistry.shared.resolve("   "))
        // Never a candidate ⇒ never counted. A decline count inflated by
        // non-requests would make the harness's per-document line lie about
        // how much this run actually failed to deliver.
        XCTAssertEqual(DocumentImageRegistry.shared.lastReport.requested, 0)
    }

    func testADeclineIsCachedSoOneBadSourceCannotSpamThePerBoxLog() {
        DocumentImageRegistry.shared.clear()
        for _ in 0..<5 { XCTAssertNil(DocumentImageRegistry.shared.resolve("css/s/missing.png")) }
        // css-grid's abspos family paints ONE support image from 41 boxes; a
        // per-box re-attempt would log 41 identical declines and re-stat a
        // file already known to be absent.
        XCTAssertEqual(DocumentImageRegistry.shared.lastReport.requested, 1)
        XCTAssertEqual(DocumentImageRegistry.shared.lastReport.declined, ["css/s/missing.png"])
    }

    func testAnSVGSourceIsDeclinedByNameNotByAFailedDecode() throws {
        // 19 of the 28 depth-48 replaced-source tests are SVG (the css-ui
        // box-sizing cluster) and ImageIO ships no SVG decoder — iOS reads SVG
        // only from a compiled asset catalog. The format gate exists so the log
        // says THAT, instead of the indistinguishable "returned no raster" a
        // corrupt PNG would produce.
        let dir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("w39a2-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        try "<svg viewBox=\"0 0 100 100\"/>".write(
            to: dir.appendingPathComponent("r1-1.svg"), atomically: true, encoding: .utf8)
        DocumentImageRegistry.shared.configure(baseDirectory: dir)
        XCTAssertNil(DocumentImageRegistry.shared.resolve("r1-1.svg"))
        XCTAssertEqual(DocumentImageRegistry.shared.lastReport.declined, ["r1-1.svg"])
    }

    func testConfigureReplacesThePreviousDocumentsCache() {
        DocumentImageRegistry.shared.clear()
        _ = DocumentImageRegistry.shared.resolve("css/s/a.png")
        XCTAssertEqual(DocumentImageRegistry.shared.lastReport.requested, 1)
        // The inbox renders many documents in one process. A raster cached
        // under a path the NEXT document also uses would paint the previous
        // document's picture even though this one's delivery failed.
        DocumentImageRegistry.shared.configure(baseDirectory: nil)
        XCTAssertEqual(DocumentImageRegistry.shared.lastReport.requested, 0)
        XCTAssertEqual(DocumentImageRegistry.shared.lastReport.declined, [])
    }
}
